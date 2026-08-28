package dev.qtremors.arcile.core.ui.image

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext
import java.io.File

class PdfThumbnailFetcher(
    private val file: File,
    private val options: Options,
    private val contentUri: String? = null,
    private val declaredSizeBytes: Long? = null,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(ioContext) {
        if (contentUri == null && (!file.exists() || !file.isFile)) return@withContext null
        val sizeBytes = declaredSizeBytes ?: file.length()
        if (sizeBytes > ThumbnailPolicy.MAX_PDF_BYTES) return@withContext null

        try {
            val descriptor = contentUri?.let { uri ->
                options.context.contentResolver.openFileDescriptor(uri.toUri(), "r")
            } ?: ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor?.use { openedDescriptor ->
                PdfRenderer(openedDescriptor).use { renderer ->
                    if (renderer.pageCount <= 0) return@withContext null
                    renderer.openPage(0).use { page ->
                        val maxSize = ThumbnailTargetSize.fromOptions(options)
                        val scale = minOf(
                            maxSize.toFloat() / page.width.coerceAtLeast(1),
                            maxSize.toFloat() / page.height.coerceAtLeast(1)
                        )
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = createBitmap(width, height)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        DrawableResult(
                            drawable = bitmap.toDrawable(options.context.resources),
                            isSampled = true,
                            dataSource = DataSource.DISK
                        )
                    }
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.extension.equals("pdf", ignoreCase = true)) {
                PdfThumbnailFetcher(data, options)
            } else {
                null
            }
        }
    }

    class KeyFactory : Fetcher.Factory<ThumbnailKey> {
        override fun create(data: ThumbnailKey, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.type == ThumbnailType.Pdf) {
                PdfThumbnailFetcher(
                    file = data.file,
                    options = options,
                    contentUri = data.contentUri,
                    declaredSizeBytes = data.sizeBytes
                )
            } else {
                null
            }
        }
    }
}
