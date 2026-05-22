package dev.qtremors.arcile.image

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
    private val ioContext: CoroutineContext = Dispatchers.IO
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(ioContext) {
        if (!file.exists() || !file.isFile) return@withContext null

        try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { renderer ->
                    if (renderer.pageCount <= 0) return@withContext null
                    renderer.openPage(0).use { page ->
                        val requestedWidth = (options.size.width as? coil.size.Dimension.Pixels)?.px ?: 512
                        val requestedHeight = (options.size.height as? coil.size.Dimension.Pixels)?.px ?: 512
                        val maxSize = maxOf(requestedWidth, requestedHeight).coerceIn(128, 1024)
                        val scale = minOf(
                            maxSize.toFloat() / page.width.coerceAtLeast(1),
                            maxSize.toFloat() / page.height.coerceAtLeast(1)
                        )
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                        DrawableResult(
                            drawable = BitmapDrawable(options.context.resources, bitmap),
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
}
