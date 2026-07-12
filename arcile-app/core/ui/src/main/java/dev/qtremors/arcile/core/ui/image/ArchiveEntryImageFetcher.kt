package dev.qtremors.arcile.core.ui.image

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import dev.qtremors.arcile.core.storage.domain.FileCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import kotlin.coroutines.CoroutineContext

class ArchiveEntryImageFetcher(
    private val data: ArchiveEntryThumbnailData,
    private val options: Options,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(ioContext) {
        if (data.sizeBytes > MAX_ARCHIVE_THUMBNAIL_BYTES) return@withContext null
        val archive = File(data.archivePath)
        if (!archive.isFile || !archive.extension.equals("zip", ignoreCase = true)) return@withContext null
        val extension = data.entryPath.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (extension !in FileCategories.Images.extensions) return@withContext null
        if (data.entryPath.contains("..") || data.entryPath.startsWith("/") || data.entryPath.startsWith("\\")) return@withContext null

        try {
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry(data.entryPath) ?: return@withContext null
                if (entry.isDirectory || entry.size > MAX_ARCHIVE_THUMBNAIL_BYTES) return@withContext null
                zip.getInputStream(entry).use { input ->
                    val bytes = input.readBoundedBytes(MAX_ARCHIVE_THUMBNAIL_BYTES) ?: return@withContext null
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
                    val width = bounds.outWidth
                    val height = bounds.outHeight
                    if (width <= 0 || height <= 0) return@withContext null
                    if (width.toLong() * height.toLong() > MAX_ARCHIVE_THUMBNAIL_PIXELS) return@withContext null
                    val targetSize = ThumbnailTargetSize.fromOptions(options)
                    val decodeOptions = BitmapFactory.Options().apply {
                        inSampleSize = calculateInSampleSize(width, height, targetSize)
                    }
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOptions) ?: return@withContext null
                    DrawableResult(
                        drawable = BitmapDrawable(options.context.resources, bitmap),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            null
        }
    }

    class Factory : Fetcher.Factory<ArchiveEntryThumbnailData> {
        override fun create(data: ArchiveEntryThumbnailData, options: Options, imageLoader: ImageLoader): Fetcher =
            ArchiveEntryImageFetcher(data, options)
    }

    companion object {
        const val MAX_ARCHIVE_THUMBNAIL_BYTES = ThumbnailPolicy.MAX_IMAGE_BYTES
        const val MAX_ARCHIVE_THUMBNAIL_PIXELS = 36L * 1_000L * 1_000L
    }
}

private fun InputStream.readBoundedBytes(maxBytes: Long): ByteArray? {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(128 * 1024)
    var total = 0L
    while (true) {
        val read = read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) return null
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

private fun calculateInSampleSize(width: Int, height: Int, targetSize: Int): Int {
    var sampleSize = 1
    while (width / sampleSize > targetSize || height / sampleSize > targetSize) {
        sampleSize *= 2
    }
    return sampleSize
}
