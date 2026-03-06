package dev.qtremors.arcile

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.VideoFrameDecoder
import dev.qtremors.arcile.image.ApkIconFetcher
import dev.qtremors.arcile.image.AudioAlbumArtFetcher

class ArcileApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameDecoder.Factory())
                add(ApkIconFetcher.Factory())
                add(AudioAlbumArtFetcher.Factory())
            }
            .build()
    }
}
