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

class ApkIconFetcher(
    private val data: File,
    private val options: Options
) : Fetcher {
    @Suppress("DEPRECATION")
    override suspend fun fetch(): FetchResult? {
        if (!data.exists() || !data.isFile || data.length() > ThumbnailPolicy.MAX_APK_BYTES) return null
        return ThumbnailWorkCoordinator.withExpensivePermit {
        val packageManager = options.context.packageManager
        val packageInfo = packageManager.getPackageArchiveInfo(data.absolutePath, 0)
        
        if (packageInfo != null) {
            val appInfo = packageInfo.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = data.absolutePath
                appInfo.publicSourceDir = data.absolutePath
                val icon = appInfo.loadIcon(packageManager)
                if (icon != null) {
                    val targetSize = ThumbnailTargetSize.fromOptions(
                        options,
                        maxPx = ThumbnailTargetSize.MAX_EXPENSIVE_PX
                    )
                    DrawableResult(
                        drawable = icon.toBoundedDrawable(options.context, targetSize),
                        isSampled = true,
                        dataSource = DataSource.DISK
                    )
                } else null
            } else null
        } else {
            null
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
                ApkIconFetcher(data.file, options)
            } else {
                null
            }
        }
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
        Bitmap.createBitmap(outWidth, outHeight, Bitmap.Config.ARGB_8888)
    }.getOrElse { return this }
    val canvas = Canvas(bitmap)
    setBounds(0, 0, outWidth, outHeight)
    runCatching { draw(canvas) }.getOrElse { return this }
    return BitmapDrawable(context.resources, bitmap)
}
