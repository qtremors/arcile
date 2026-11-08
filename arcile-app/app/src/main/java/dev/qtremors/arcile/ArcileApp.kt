package dev.qtremors.arcile

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import dev.qtremors.arcile.security.VaultAutoLockController
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import android.app.Application
import android.os.StrictMode
import coil3.request.crossfade
import coil3.request.addLastModifiedToFileCacheKey
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.gif.GifDecoder
import coil3.gif.AnimatedImageDecoder
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import okio.Path.Companion.toOkioPath
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.StorageCacheInvalidationObserver
import dev.qtremors.arcile.core.storage.data.ThumbnailCacheRecord
import dev.qtremors.arcile.core.storage.data.ThumbnailCacheStore
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.ui.image.ApkIconFetcher
import dev.qtremors.arcile.core.ui.image.ArchiveEntryImageFetcher
import dev.qtremors.arcile.core.ui.image.AudioAlbumArtFetcher
import dev.qtremors.arcile.core.ui.image.GlobalThumbnailStatePersistence
import dev.qtremors.arcile.core.ui.image.PdfThumbnailFetcher
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.image.ThumbnailKeyer
import dev.qtremors.arcile.core.ui.image.ThumbnailStatePersistence
import dev.qtremors.arcile.core.ui.image.VideoThumbnailFetcher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultThumbnailCache
import dev.qtremors.arcile.core.vault.data.VaultThumbnailFetcher
import javax.inject.Inject
import dev.qtremors.arcile.core.ui.security.SensitiveMemory
import dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper
import dev.qtremors.arcile.core.operation.android.apk.ApkPackageParser

@HiltAndroidApp
class ArcileApp : Application(), SingletonImageLoader.Factory {
    val appSessionTracker = AppSessionTracker()

    @Inject
    lateinit var mutationJournal: dagger.Lazy<MutationJournal>

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var thumbnailCacheStore: dagger.Lazy<ThumbnailCacheStore>

    @Inject
    lateinit var storageCacheInvalidationObserver: dagger.Lazy<StorageCacheInvalidationObserver>

    @Inject
    lateinit var vaultRepository: VaultRepository

    @Inject
    lateinit var vaultThumbnailCache: VaultThumbnailCache

    @Inject
    lateinit var vaultSecurityPreferences: VaultSecurityPreferences

    internal lateinit var vaultAutoLockController: VaultAutoLockController
        private set

    /** Viewer activities run in their own processes (`:imageviewer`, `:pdfviewer`, `:texteditor`, `:videoviewer`). */
    private val isMainProcess: Boolean by lazy { getProcessName() == packageName }

    override fun onCreate() {
        super.onCreate()
        if (isMainProcess) {
            val priorPolicy = StrictMode.allowThreadDiskWrites()
            try {
                ExternalFileAccessHelper.quarantinePrivatePlaintextFallbacks(this)
            } finally {
                StrictMode.setThreadPolicy(priorPolicy)
            }
            applicationScope.launch {
                ApkPackageParser.cleanupAbandonedStaging(this@ArcileApp)
                ExternalFileAccessHelper.clearQuarantinedPrivatePlaintextFallbacks(this@ArcileApp)
                mutationJournal.get().cleanupAbandonedMutations()
            }
        }
        // Storage watching and thumbnail bookkeeping belong to the main process only; viewers would duplicate them.
        if (isMainProcess) storageCacheInvalidationObserver.get().register()
        SensitiveMemory.clearDelegate = { SingletonImageLoader.get(this).memoryCache?.clear() }
        vaultAutoLockController = VaultAutoLockController(
            vaultSecurityPreferences.settings,
            CoroutineScope(applicationScope.coroutineContext + Dispatchers.Main.immediate),
            SystemClock::elapsedRealtime, vaultRepository::lockAll, SensitiveMemory::clear
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                vaultAutoLockController.onForeground()
                if (isMainProcess) storageCacheInvalidationObserver.get().onForeground()
            }

            override fun onStop(owner: LifecycleOwner) {
                vaultAutoLockController.onBackground()
                if (isMainProcess) storageCacheInvalidationObserver.get().onBackground()
            }
        })
        ContextCompat.registerReceiver(this, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> vaultAutoLockController.onScreenOff()
                    Intent.ACTION_SCREEN_ON -> vaultAutoLockController.onScreenOn()
                }
            }
        }, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        if (!isMainProcess) return
        GlobalThumbnailStatePersistence.delegate = RoomThumbnailStatePersistence(
            thumbnailCacheStore = thumbnailCacheStore.get(),
            applicationScope = applicationScope
        )
        applicationScope.launch {
            val snapshot = thumbnailCacheStore.get().snapshot()
            GlobalThumbnailStatePersistence.restore(
                loadedVariantKeys = snapshot.loadedVariantKeys,
                failedIdentityKeys = snapshot.failedIdentityKeys
            )
        }
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .addLastModifiedToFileCacheKey(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25)
                    .build()
            }
            .diskCache {
                // Coil's disk cache is single-process; each viewer process gets its own directory.
                val cacheName = if (isMainProcess) "image_cache" else "image_cache_${getProcessName().substringAfter(':')}"
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(cacheName).toOkioPath())
                    .maxSizePercent(0.08)
                    .build()
            }
            .components {
                add(ThumbnailKeyer())
                add(AnimatedImageDecoder.Factory())
                add(GifDecoder.Factory())
                add(SvgDecoder.Factory())
                add(PdfThumbnailFetcher.Factory())
                add(PdfThumbnailFetcher.KeyFactory())
                add(VideoThumbnailFetcher.Factory())
                add(VideoThumbnailFetcher.KeyFactory())
                add(VideoFrameDecoder.Factory())
                add(ApkIconFetcher.Factory())
                add(ApkIconFetcher.KeyFactory())
                add(AudioAlbumArtFetcher.Factory())
                add(AudioAlbumArtFetcher.KeyFactory())
                add(ArchiveEntryImageFetcher.Factory())
                add(VaultThumbnailFetcher.Factory(vaultThumbnailCache))
            }
            .crossfade(true)
            .build()
    }
}

private class RoomThumbnailStatePersistence(
    private val thumbnailCacheStore: ThumbnailCacheStore,
    private val applicationScope: CoroutineScope
) : ThumbnailStatePersistence {
    private val pendingLoaded = ArrayList<ThumbnailCacheRecord>()
    private var flushJob: Job? = null

    /** Collects loaded thumbnails for a short window and writes them in one transaction. */
    override fun recordLoaded(key: ThumbnailKey, thumbnailSizePx: Int) {
        val record = key.toCacheRecord(thumbnailSizePx)
        synchronized(pendingLoaded) {
            pendingLoaded += record
            if (flushJob?.isActive == true) return
            flushJob = applicationScope.launch {
                delay(LOADED_FLUSH_DELAY_MS)
                val batch = synchronized(pendingLoaded) { pendingLoaded.toList().also { pendingLoaded.clear() } }
                thumbnailCacheStore.recordLoaded(batch)
            }
        }
    }

    override fun recordFailure(key: ThumbnailKey, thumbnailSizePx: Int) {
        applicationScope.launch {
            thumbnailCacheStore.recordFailure(key.toCacheRecord(thumbnailSizePx))
        }
    }

    override fun clearFailure(key: ThumbnailKey) {
        applicationScope.launch {
            thumbnailCacheStore.clearFailure(key.identityKey.cacheKey)
        }
    }

    override fun clear() {
        applicationScope.launch {
            thumbnailCacheStore.clear()
        }
    }

    private companion object {
        const val LOADED_FLUSH_DELAY_MS = 1_500L
    }

    private fun ThumbnailKey.toCacheRecord(thumbnailSizePx: Int): ThumbnailCacheRecord {
        val variantKey = variantKey(thumbnailSizePx)
        return ThumbnailCacheRecord(
            identityKey = identityKey.cacheKey,
            variantKey = variantKey.cacheKey,
            source = identityKey.source,
            extension = extension,
            sizeBytes = sizeBytes,
            lastModified = lastModifiedMillis,
            contentUri = contentUri,
            type = type.name,
            sizeBucketPx = variantKey.sizeBucketPx
        )
    }
}
