package dev.qtremors.arcile.image

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import dev.qtremors.arcile.domain.FileCategories
import java.io.File

class AudioAlbumArtFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val art = retriever.embeddedPicture
            if (art != null) {
                // First decode bounds only to get dimensions
                val decodeOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(art, 0, art.size, decodeOptions)

                // Calculate a reasonable sample size (target max ~500px)
                val targetSize = 500
                var sampleSize = 1
                while (decodeOptions.outWidth / sampleSize > targetSize || 
                       decodeOptions.outHeight / sampleSize > targetSize) {
                    sampleSize *= 2
                }

                // Decode actual bitmap with downsampling
                decodeOptions.apply {
                    inJustDecodeBounds = false
                    inSampleSize = sampleSize
                }
                
                val bitmap = BitmapFactory.decodeByteArray(art, 0, art.size, decodeOptions)
                DrawableResult(
                    drawable = BitmapDrawable(options.context.resources, bitmap),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.extension.lowercase() in FileCategories.Audio.extensions) {
                AudioAlbumArtFetcher(data, options)
            } else {
                null
            }
        }
    }
}
