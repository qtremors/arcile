package dev.qtremors.arcile.image

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import dev.qtremors.arcile.domain.FileCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AudioAlbumArtFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(Dispatchers.IO) {
        val context = options.context
        val targetSize = 500

        // Use native loadThumbnail on API 29+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val projection = arrayOf(MediaStore.Audio.Media._ID)
                val selection = "${MediaStore.Audio.Media.DATA} = ?"
                val selectionArgs = arrayOf(file.absolutePath)
                val uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

                context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))
                        val contentUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                        
                        val bitmap = context.contentResolver.loadThumbnail(contentUri, Size(targetSize, targetSize), null)
                        return@withContext DrawableResult(
                            drawable = BitmapDrawable(context.resources, bitmap),
                            isSampled = true,
                            dataSource = DataSource.DISK
                        )
                    }
                }
            } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
                // Fallback to MediaMetadataRetriever below
            }
        }

        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val art = retriever.embeddedPicture
            if (art != null) {
                // First decode bounds only to get dimensions
                val decodeOptions = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(art, 0, art.size, decodeOptions)

                // Calculate a reasonable sample size (target max ~500px)
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
                    ?: return@withContext null 
                DrawableResult(
                    drawable = BitmapDrawable(context.resources, bitmap),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } else {
                null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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
