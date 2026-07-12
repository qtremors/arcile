package dev.qtremors.arcile.core.ui.image

import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import dev.qtremors.arcile.core.storage.domain.FileCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withPermit
import kotlin.coroutines.CoroutineContext
import java.io.File

class VideoThumbnailFetcher(
    private val file: File,
    private val options: Options,
    private val contentUri: String? = null,
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
            val targetSize = ThumbnailTargetSize.fromOptions(options)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    contentUri?.let { uri ->
                        val bitmap = context.contentResolver.loadThumbnail(Uri.parse(uri), Size(targetSize, targetSize), null)
                        return@withContext DrawableResult(
                            drawable = BitmapDrawable(context.resources, bitmap),
                            isSampled = true,
                            dataSource = DataSource.DISK
                        )
                    }

                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    // Fall back to MediaMetadataRetriever below for raw browser paths or provider failures.
                }
            }

            if (contentUri == null && (!file.exists() || !file.isFile)) return@withContext null

            val retriever = MediaMetadataRetriever()
            try {
                if (contentUri != null) {
                    retriever.setDataSource(context, Uri.parse(contentUri))
                } else {
                    retriever.setDataSource(file.absolutePath)
                }
                val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        -1,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        targetSize,
                        targetSize
                    )
                } else {
                    retriever.getFrameAtTime(-1, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                } ?: return@withContext null

                DrawableResult(
                    drawable = BitmapDrawable(context.resources, bitmap),
                    isSampled = true,
                    dataSource = DataSource.DISK
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                null
            } finally {
                retriever.release()
            }
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

    class KeyFactory : Fetcher.Factory<ThumbnailKey> {
        override fun create(data: ThumbnailKey, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.type == ThumbnailType.Video) {
                VideoThumbnailFetcher(data.file, options, data.contentUri)
            } else {
                null
            }
        }
    }
}
