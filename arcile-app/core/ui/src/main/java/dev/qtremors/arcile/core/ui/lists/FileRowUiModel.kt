package dev.qtremors.arcile.core.ui.lists

import androidx.compose.runtime.Immutable
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.ui.image.ThumbnailKey
import dev.qtremors.arcile.core.ui.image.ThumbnailType
import java.io.File
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import dev.qtremors.arcile.core.presentation.formatFileSize
import java.text.DateFormat
import java.util.Date

@Immutable
data class FileRowUiModel(
    val file: FileModel,
    val formattedDate: String,
    val subtitle: String,
    val folderStats: FolderStats?,
    val iconType: FileIconType,
    val isHidden: Boolean,
    val thumbnailSizePx: Int,
    val thumbnailKey: ThumbnailKey
) {
    val absolutePath: String get() = file.reference
    val isDirectory: Boolean get() = file.isDirectory
}

enum class FileIconType {
    Directory,
    Image,
    Video,
    Audio,
    Model,
    Apk,
    Generic
}

fun FileModel.toFileRowUiModel(
    formatter: DateFormat,
    folderStats: FolderStats? = null,
    thumbnailSizePx: Int = 128,
    context: Context? = null,
    fileSizeFormatter: ((Long) -> String)? = null
): FileRowUiModel {
    val normalizedExtension = extension.lowercase()
    val iconType = when {
        isDirectory -> FileIconType.Directory
        normalizedExtension in FileCategories.Images.extensions -> FileIconType.Image
        normalizedExtension in FileCategories.Videos.extensions -> FileIconType.Video
        normalizedExtension in FileCategories.Audio.extensions -> FileIconType.Audio
        normalizedExtension == "glb" -> FileIconType.Model
        normalizedExtension in FileCategories.APKs.extensions -> FileIconType.Apk
        else -> FileIconType.Generic
    }
    val subtitle = if (isDirectory) {
        ""
    } else {
        fileSizeFormatter?.invoke(size)
            ?: context?.let { formatFileSize(it, size) }
            ?: ""
    }

    return FileRowUiModel(
        file = this,
        formattedDate = formatter.format(Date(lastModified)),
        subtitle = subtitle,
        folderStats = folderStats,
        iconType = iconType,
        isHidden = isHidden || name.startsWith("."),
        thumbnailSizePx = thumbnailSizePx,
        thumbnailKey = ThumbnailKey.from(this)
    )
}

val FileRowUiModel.canShowThumbnail: Boolean
    get() = file.canShowThumbnail

val FileModel.canShowThumbnail: Boolean
    get() = !isDirectory && ThumbnailKey.from(this).type != ThumbnailType.Unsupported

fun FileModel.thumbnailRequestData(archiveThumbnailData: Any? = null): Any? {
    if (isDirectory) return null
    val key = ThumbnailKey.from(this)
    if (key.type == ThumbnailType.Unsupported) return null
    return archiveThumbnailData ?: when (key.type) {
        ThumbnailType.Audio,
        ThumbnailType.Video,
        ThumbnailType.Pdf,
        ThumbnailType.Apk -> key
        ThumbnailType.Image -> nodeRef.contentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
            ?: File(reference)
        ThumbnailType.Unsupported -> null
    }
}

fun FileRowUiModel.thumbnailRequestData(archiveThumbnailData: Any? = null): Any =
    file.thumbnailRequestData(archiveThumbnailData)
        ?: file.nodeRef.contentUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
        ?: File(file.reference)

@Composable
fun FileRowUiModel.displaySubtitle(): String {
    if (!isDirectory) {
        return subtitle.ifBlank { formatFileSize(LocalContext.current, file.size) }
    }
    val stats = folderStats
    if (stats == null) return stringResource(R.string.folder_stats_pending)
    if (stats.status == FolderStatsStatus.Unavailable) return stringResource(R.string.folder_stats_unavailable)
    val filesLabel = pluralStringResource(
        R.plurals.folder_stats_files,
        stats.fileCount.toInt(),
        stats.fileCount
    )
    val context = LocalContext.current
    return stringResource(R.string.folder_stats_summary, filesLabel, formatFileSize(context, stats.totalBytes))
}
