package dev.qtremors.arcile.core.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import dev.qtremors.arcile.core.storage.domain.FileCategories
import java.io.File
import java.io.FileOutputStream
import android.net.Uri
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toDrawable
import androidx.core.net.toUri

class ApkIconFetcher(
    private val data: File,
    private val options: Options,
    private val contentUri: String? = null,
    private val declaredSizeBytes: Long? = null,
    private val declaredExtension: String = data.extension
) : Fetcher {
    @Suppress("DEPRECATION")
    override suspend fun fetch(): FetchResult? {
        if (contentUri == null && (!data.exists() || !data.isFile)) return null
        if ((declaredSizeBytes ?: data.length()) > ThumbnailPolicy.MAX_APK_BYTES) return null
        return ThumbnailWorkCoordinator.withExpensivePermit {
            withApkCapabilityFile(
                context = options.context,
                source = data,
                contentUri = contentUri,
                extension = declaredExtension
            ) { capabilityFile ->
                withApkPreviewFile(options.context, capabilityFile) { apkFile ->
                    val packageManager = options.context.packageManager
                    val packageInfo = packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
                        ?: return@withApkPreviewFile null
                    val appInfo = packageInfo.applicationInfo
                        ?: return@withApkPreviewFile null
                    appInfo.sourceDir = apkFile.absolutePath
                    appInfo.publicSourceDir = apkFile.absolutePath
                    val icon = appInfo.loadIcon(packageManager)
                        ?: return@withApkPreviewFile null
                    val targetSize = ThumbnailTargetSize.fromOptions(
                        options,
                        maxPx = ThumbnailTargetSize.MAX_EXPENSIVE_PX
                    )
                    DrawableResult(
                        drawable = icon.toBoundedDrawable(options.context, targetSize),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                }
            }
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.extension.lowercase() in FileCategories.APKs.extensions) {
                ApkIconFetcher(data, options)
            } else {
                null
            }
        }
    }

    class KeyFactory : Fetcher.Factory<ThumbnailKey> {
        override fun create(data: ThumbnailKey, options: Options, imageLoader: ImageLoader): Fetcher? {
            return if (data.type == ThumbnailType.Apk) {
                ApkIconFetcher(
                    data = data.file,
                    options = options,
                    contentUri = data.contentUri,
                    declaredSizeBytes = data.sizeBytes,
                    declaredExtension = data.extension
                )
            } else {
                null
            }
        }
    }
}

internal inline fun <T> withApkCapabilityFile(
    context: Context,
    source: File,
    contentUri: String?,
    extension: String,
    block: (File) -> T?
): T? {
    if (contentUri.isNullOrBlank()) return block(source)
    val safeExtension = extension.lowercase().takeIf { it in FileCategories.APKs.extensions }
        ?: return null
    val temporary = File.createTempFile("arcile_capability_apk_", ".$safeExtension", context.cacheDir)
    return try {
        context.contentResolver.openInputStream(contentUri.toUri())?.use { input ->
            FileOutputStream(temporary).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                var total = 0L
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= ThumbnailPolicy.MAX_APK_BYTES)
                    output.write(buffer, 0, count)
                }
            }
        } ?: return null
        block(temporary)
    } finally {
        temporary.delete()
    }
}

private fun Drawable.toBoundedDrawable(context: Context, targetSize: Int): Drawable {
    val width = runCatching { intrinsicWidth }.getOrNull()?.takeIf { it > 0 } ?: targetSize
    val height = runCatching { intrinsicHeight }.getOrNull()?.takeIf { it > 0 } ?: targetSize
    if (this is BitmapDrawable && bitmap.width <= targetSize && bitmap.height <= targetSize) return this
    val scale = minOf(targetSize.toFloat() / width, targetSize.toFloat() / height, 1f)
    val outWidth = (width * scale).toInt().coerceAtLeast(1)
    val outHeight = (height * scale).toInt().coerceAtLeast(1)
    val bitmap = runCatching {
        createBitmap(outWidth, outHeight)
    }.getOrElse { return this }
    val canvas = Canvas(bitmap)
    setBounds(0, 0, outWidth, outHeight)
    runCatching { draw(canvas) }.getOrElse { return this }
    return bitmap.toDrawable(context.resources)
}
