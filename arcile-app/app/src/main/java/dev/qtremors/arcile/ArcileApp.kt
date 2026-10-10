package dev.qtremors.arcile

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
    lateinit var mutationJournal: MutationJournal

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var thumbnailCacheStore: ThumbnailCacheStore

    @Inject
    lateinit var storageCacheInvalidationObserver: StorageCacheInvalidationObserver

    @Inject
    lateinit var vaultRepository: VaultRepository

    @Inject
    lateinit var vaultThumbnailCache: VaultThumbnailCache

    override fun onCreate() {
        super.onCreate()
        if (getProcessName() == packageName) {
            val priorPolicy = StrictMode.allowThreadDiskWrites()
            try {
                ExternalFileAccessHelper.quarantinePrivatePlaintextFallbacks(this)
            } finally {
                StrictMode.setThreadPolicy(priorPolicy)
            }
            applicationScope.launch {
                ApkPackageParser.cleanupAbandonedStaging(this@ArcileApp)
                ExternalFileAccessHelper.clearQuarantinedPrivatePlaintextFallbacks(this@ArcileApp)
                mutationJournal.cleanupAbandonedMutations()
            }
        }
        storageCacheInvalidationObserver.register()
        SensitiveMemory.clearDelegate = { SingletonImageLoader.get(this).memoryCache?.clear() }
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) {
                SensitiveMemory.clear()
                applicationScope.launch { vaultRepository.lockAll() }
            }
        })
        GlobalThumbnailStatePersistence.delegate = RoomThumbnailStatePersistence(
            thumbnailCacheStore = thumbnailCacheStore,
            applicationScope = applicationScope
        )
        applicationScope.launch {
            val snapshot = thumbnailCacheStore.snapshot()
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
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("image_cache").toOkioPath())
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
    override fun recordLoaded(key: ThumbnailKey, thumbnailSizePx: Int) {
        applicationScope.launch {
            thumbnailCacheStore.recordLoaded(key.toCacheRecord(thumbnailSizePx))
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
