package dev.qtremors.arcile.data.provider

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.core.content.ContextCompat
import dev.qtremors.arcile.data.StorageClassificationRepository
import dev.qtremors.arcile.data.util.mergeStorageClassifications
import dev.qtremors.arcile.domain.StorageVolume
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
}

class DefaultVolumeProvider(
    private val context: Context,
    private val classificationRepo: StorageClassificationRepository
) : VolumeProvider {
    private val appContext = context.applicationContext
    private val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    private val _activeStorageRoots = AtomicReference<List<String>>(emptyList())
    override val activeStorageRoots: List<String>
        get() = _activeStorageRoots.get()

    private val cachedVolumes = AtomicReference<List<StorageVolume>?>(null)

    init {
        _activeStorageRoots.set(discoverPlatformVolumes().map { it.path })
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
                val totalBytes = stat.totalBytes
                val freeBytes = stat.availableBytes
                val isPrimary = canonicalPath == primaryPath
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

    override fun observeStorageVolumes(): Flow<List<StorageVolume>> {
        val platformVolumesFlow = callbackFlow {
            val scope = this
            fun emitVolumes() {
                scope.launch(Dispatchers.IO) {
                    send(discoverPlatformVolumes())
                }
            }

            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    cachedVolumes.set(null)
                    emitVolumes()
                }
            }

            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_MEDIA_MOUNTED)
                addAction(Intent.ACTION_MEDIA_UNMOUNTED)
                addAction(Intent.ACTION_MEDIA_REMOVED)
                addAction(Intent.ACTION_MEDIA_EJECT)
                addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
                addDataScheme("file")
            }

            ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            emitVolumes()
            awaitClose { appContext.unregisterReceiver(receiver) }
        }.distinctUntilChanged()

        return combine(
            platformVolumesFlow,
            classificationRepo.observeClassifications()
        ) { volumes, classifications ->
            mergeStorageClassifications(volumes, classifications)
        }.distinctUntilChanged()
    }

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = withContext(Dispatchers.IO) {
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