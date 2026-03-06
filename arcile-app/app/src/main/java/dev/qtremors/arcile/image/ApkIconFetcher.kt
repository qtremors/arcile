package dev.qtremors.arcile.image

import android.content.Context
import android.graphics.drawable.Drawable
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import dev.qtremors.arcile.domain.FileCategories
import java.io.File

class ApkIconFetcher(
    private val data: File,
    private val options: Options
) : Fetcher {
    @Suppress("DEPRECATION")
    override suspend fun fetch(): FetchResult? {
        val packageManager = options.context.packageManager
        val packageInfo = packageManager.getPackageArchiveInfo(data.absolutePath, 0)
        
        return if (packageInfo != null) {
            val appInfo = packageInfo.applicationInfo
            if (appInfo != null) {
                appInfo.sourceDir = data.absolutePath
                appInfo.publicSourceDir = data.absolutePath
                val icon = appInfo.loadIcon(packageManager)
                if (icon != null) {
                    DrawableResult(
                        drawable = icon,
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                } else null
            } else null
        } else {
            null
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
}
