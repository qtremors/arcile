package dev.qtremors.arcile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dev.qtremors.arcile.image.ApkIconFetcher
import dev.qtremors.arcile.image.AudioAlbumArtFetcher
import dev.qtremors.arcile.image.VideoThumbnailFetcher
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class ArcileApp : Application(), ImageLoaderFactory {
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
                add(VideoThumbnailFetcher.Factory())
                add(VideoFrameDecoder.Factory())
                add(ApkIconFetcher.Factory())
                add(AudioAlbumArtFetcher.Factory())
            }
            .crossfade(true)
            .build()
    }
}
