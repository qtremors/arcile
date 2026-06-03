package dev.qtremors.arcile.image

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
import java.io.File
import java.util.zip.ZipFile
import kotlin.coroutines.CoroutineContext

class ArchiveEntryImageFetcher(
    private val data: ArchiveEntryThumbnailData,
    private val options: Options,
    private val ioContext: CoroutineContext = Dispatchers.IO
) : Fetcher {
    override suspend fun fetch(): FetchResult? = withContext(ioContext) {
        if (data.sizeBytes > ThumbnailPolicy.MAX_IMAGE_BYTES) return@withContext null
        val archive = File(data.archivePath)
        if (!archive.isFile || !archive.extension.equals("zip", ignoreCase = true)) return@withContext null
        val extension = data.entryPath.substringAfterLast('.', missingDelimiterValue = "").lowercase()
        if (extension !in FileCategories.Images.extensions) return@withContext null
        if (data.entryPath.contains("..") || data.entryPath.startsWith("/") || data.entryPath.startsWith("\\")) return@withContext null

        try {
            ZipFile(archive).use { zip ->
                val entry = zip.getEntry(data.entryPath) ?: return@withContext null
                if (entry.isDirectory || entry.size > ThumbnailPolicy.MAX_IMAGE_BYTES) return@withContext null
                zip.getInputStream(entry).use { input ->
                    val bitmap = BitmapFactory.decodeStream(input) ?: return@withContext null
                    DrawableResult(
                        drawable = BitmapDrawable(options.context.resources, bitmap),
                        isSampled = false,
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
}
