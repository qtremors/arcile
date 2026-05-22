package dev.qtremors.arcile.image

import android.content.ContentUris
import android.graphics.drawable.BitmapDrawable
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
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.CoroutineContext
import java.io.File

class VideoThumbnailFetcher(
    private val file: File,
    private val options: Options,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : Fetcher {
    companion object {
        /**
         * Limit concurrent thumbnail fetches to avoid saturating ContentResolver/MediaStore
         * which can cause UI lag even for non-visible items.
         */
        private val semaphore = kotlinx.coroutines.sync.Semaphore(2)
    }

    override suspend fun fetch(): FetchResult? = semaphore.withPermit {
        withContext(ioContext) {
            // Add a delay to allow UI animations (like storage bar) to complete smoothly
            // before starting intensive thumbnail fetching.
            kotlinx.coroutines.delay(300)
            
            val context = options.context
            val reqWidth = (options.size.width as? coil.size.Dimension.Pixels)?.px ?: 512
            val reqHeight = (options.size.height as? coil.size.Dimension.Pixels)?.px ?: 512
            val targetSize = maxOf(reqWidth, reqHeight).coerceIn(128, 1024)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    val projection = arrayOf(MediaStore.Video.Media._ID)
                    val selection = "${MediaStore.Video.Media.DATA} = ?"
                    val selectionArgs = arrayOf(file.absolutePath)
                    val uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI

                    context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID))
                            val contentUri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                            
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
                }
            }

            // Fallback for older APIs or if not in MediaStore
            null
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.extension.lowercase() in FileCategories.Videos.extensions) {
                VideoThumbnailFetcher(data, options)
            } else {
                null
            }
        }
    }
}
