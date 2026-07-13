package dev.qtremors.arcile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
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
import dev.qtremors.arcile.core.ui.image.ThumbnailStatePersistence
import dev.qtremors.arcile.core.ui.image.VideoThumbnailFetcher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ArcileApp : Application(), ImageLoaderFactory {
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

    override fun onCreate() {
        super.onCreate()
        storageCacheInvalidationObserver.register()
        GlobalThumbnailStatePersistence.delegate = RoomThumbnailStatePersistence(
            thumbnailCacheStore = thumbnailCacheStore,
            applicationScope = applicationScope
        )
        applicationScope.launch {
            mutationJournal.cleanupAbandonedMutations()
            val snapshot = thumbnailCacheStore.snapshot()
            GlobalThumbnailStatePersistence.restore(
                loadedVariantKeys = snapshot.loadedVariantKeys,
                failedIdentityKeys = snapshot.failedIdentityKeys
            )
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.33)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.08)
                    .build()
            }
            .components {
                add(ImageDecoderDecoder.Factory())
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
