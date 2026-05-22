package dev.qtremors.arcile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.qtremors.arcile.data.MutationJournal
import dev.qtremors.arcile.di.ApplicationScope
import dev.qtremors.arcile.image.ApkIconFetcher
import dev.qtremors.arcile.image.AudioAlbumArtFetcher
import dev.qtremors.arcile.image.PdfThumbnailFetcher
import dev.qtremors.arcile.image.VideoThumbnailFetcher
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ArcileApp : Application(), ImageLoaderFactory {
    @Inject
    lateinit var mutationJournal: MutationJournal

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            mutationJournal.cleanupAbandonedMutations()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .build()
            }
            .components {
                add(PdfThumbnailFetcher.Factory())
                add(VideoThumbnailFetcher.Factory())
                add(VideoFrameDecoder.Factory())
                add(ApkIconFetcher.Factory())
                add(AudioAlbumArtFetcher.Factory())
            }
            .crossfade(true)
            .build()
    }
}
