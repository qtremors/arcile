package dev.qtremors.arcile.core.storage.data.provider

import android.app.usage.StorageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import dev.qtremors.arcile.core.storage.data.StorageClassificationRepository
import dev.qtremors.arcile.core.storage.data.util.mergeStorageClassifications
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

interface VolumeProvider {
    val activeStorageRoots: List<String>
    fun observeStorageVolumes(): Flow<List<StorageVolume>>
    suspend fun getStorageVolumes(): Result<List<StorageVolume>>
    suspend fun currentVolumes(): List<StorageVolume>
    fun invalidateCache()
}

class DefaultVolumeProvider(
    private val context: Context,
    private val classificationRepo: StorageClassificationRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : VolumeProvider {
    private val appContext = context.applicationContext
    private val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    private val _activeStorageRoots = AtomicReference<List<String>>(emptyList())
    override val activeStorageRoots: List<String>
        get() = _activeStorageRoots.get()

    private val cachedVolumes = AtomicReference<List<StorageVolume>?>(null)

    init {
        runCatching {
            _activeStorageRoots.set(discoverPlatformVolumes().map { it.path })
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            applicationScope.launch(dispatchers.io) {
                _activeStorageRoots.set(discoverPlatformVolumes().map { it.path })
            }
        }
    }

    override fun invalidateCache() {
        cachedVolumes.set(null)
    }

    private fun discoverPlatformVolumes(): List<StorageVolume> {
        val cached = cachedVolumes.get()
        if (cached != null) return cached

        val discovered = linkedMapOf<String, StorageVolume>()
        val primaryPath = Environment.getExternalStorageDirectory().canonicalPath

        fun addVolume(
            path: String,
            platformVolume: android.os.storage.StorageVolume? = null
        ) {
            runCatching {
                val canonicalPath = File(path).canonicalPath
                val rootFile = File(canonicalPath)
                if (!rootFile.exists()) return

                val stat = StatFs(canonicalPath)
                val isPrimary = canonicalPath == primaryPath
                val platformCapacity = if (isPrimary) primaryStorageCapacity() else null
                val totalBytes = platformCapacity?.first ?: stat.totalBytes
                val freeBytes = platformCapacity?.second ?: stat.availableBytes
                val isRemovable = platformVolume?.isRemovable ?: !isPrimary
                val storageKey = if (isPrimary) {
                    "primary"
                } else {
                    platformVolume?.uuid?.takeIf { it.isNotBlank() }
                        ?.let { "uuid:${it.lowercase(Locale.US)}" }
                        ?: "path:${canonicalPath.lowercase(Locale.US)}"
                }
                val preferredName = platformVolume?.getDescription(appContext)
                val fallbackName = rootFile.name.takeIf { it.isNotBlank() }
                    ?: canonicalPath.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: if (isPrimary) "Internal Storage" else "External Storage"
                val id = buildString {
                    append(if (isPrimary) "primary" else "volume")
                    append(':')
                    append(platformVolume?.uuid ?: canonicalPath.lowercase(Locale.US))
                }

                discovered[id] = StorageVolume(
                    id = id,
                    storageKey = storageKey,
                    name = preferredName?.takeIf { it.isNotBlank() } ?: fallbackName,
                    path = canonicalPath,
                    totalBytes = totalBytes,
                    freeBytes = freeBytes,
                    isPrimary = isPrimary,
                    isRemovable = isRemovable,
                )
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            storageManager.storageVolumes.forEach { volume ->
                val directory = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    volume.directory
                } else {
                    null
                }
                directory?.let { addVolume(it.absolutePath, volume) }
            }
        }

        addVolume(primaryPath, storageManager.getStorageVolume(File(primaryPath)))

        appContext.getExternalFilesDirs(null).forEach { file ->
            if (file == null) return@forEach
            val path = file.absolutePath
            val androidIndex = path.indexOf("/Android/data/")
            if (androidIndex == -1) return@forEach
            val volumeRoot = path.substring(0, androidIndex)
            addVolume(volumeRoot, storageManager.getStorageVolume(File(volumeRoot)))
        }

        val volumes = discovered.values.sortedWith(
            compareBy<StorageVolume> { !it.isPrimary }.thenBy { it.name.lowercase(Locale.US) }
        )
        _activeStorageRoots.set(volumes.map { it.path })
        cachedVolumes.set(volumes)
        return volumes
    }

    private fun primaryStorageCapacity(): Pair<Long, Long>? {
        return runCatching {
            val statsManager = appContext.getSystemService(StorageStatsManager::class.java)
            val uuid = StorageManager.UUID_DEFAULT
            statsManager.getTotalBytes(uuid) to statsManager.getFreeBytes(uuid)
        }.getOrNull()
            ?.takeIf { (totalBytes, freeBytes) -> totalBytes > 0L && freeBytes >= 0L }
    }

    override fun observeStorageVolumes(): Flow<List<StorageVolume>> {
        val platformVolumesFlow = callbackFlow {
            val scope = this
            fun emitVolumes() {
                scope.launch(dispatchers.io) {
                    send(discoverPlatformVolumes())
                }
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    invalidateCache()
                    emitVolumes()
                }
            }
            val volumeCallback = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                object : StorageManager.StorageVolumeCallback() {
                    override fun onStateChanged(volume: android.os.storage.StorageVolume) {
                        invalidateCache()
                        emitVolumes()
                    }
                }
            } else {
                null
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_CHECKING)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                addAction(Intent.ACTION_MEDIA_UNMOUNTABLE)
                addDataScheme("file")
            }

            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && volumeCallback != null) {
                storageManager.registerStorageVolumeCallback(
                    ContextCompat.getMainExecutor(appContext),
                    volumeCallback
                )
            }
            emitVolumes()
            awaitClose {
                appContext.unregisterReceiver(receiver)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && volumeCallback != null) {
                    storageManager.unregisterStorageVolumeCallback(volumeCallback)
                }
            }
        }.distinctUntilChanged()

        return combine(
            platformVolumesFlow,
            classificationRepo.observeClassifications()
        ) { volumes, classifications ->
            mergeStorageClassifications(volumes, classifications)
        }.distinctUntilChanged()
    }

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = withContext(dispatchers.io) {
        try {
            val volumes = discoverPlatformVolumes()
            val classifications = classificationRepo.observeClassifications().first()
            Result.success(mergeStorageClassifications(volumes, classifications))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun currentVolumes(): List<StorageVolume> =
        getStorageVolumes().getOrNull().orEmpty()
}
