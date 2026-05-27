package dev.qtremors.arcile.shared.ui.lists

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.image.ThumbnailKey
import dev.qtremors.arcile.image.ThumbnailType
import dev.qtremors.arcile.utils.formatFileSize
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
    val absolutePath: String get() = file.absolutePath
    val isDirectory: Boolean get() = file.isDirectory
}

enum class FileIconType {
    Directory,
    Image,
    Video,
    Audio,
    Apk,
    Generic
}

fun FileModel.toFileRowUiModel(
    formatter: DateFormat,
    folderStats: FolderStats? = null,
    thumbnailSizePx: Int = 128
): FileRowUiModel {
    val normalizedExtension = extension.lowercase()
    val iconType = when {
        isDirectory -> FileIconType.Directory
        normalizedExtension in FileCategories.Images.extensions -> FileIconType.Image
        normalizedExtension in FileCategories.Videos.extensions -> FileIconType.Video
        normalizedExtension in FileCategories.Audio.extensions -> FileIconType.Audio
        normalizedExtension in FileCategories.APKs.extensions -> FileIconType.Apk
        else -> FileIconType.Generic
    }
    val subtitle = if (isDirectory) {
        ""
    } else {
        formatFileSize(size)
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
    get() = !isDirectory && thumbnailKey.type != ThumbnailType.Unsupported

@Composable
fun FileRowUiModel.displaySubtitle(isFolderStatsLoading: Boolean = false): String {
    if (!isDirectory) return subtitle
    val stats = folderStats
    if (isFolderStatsLoading || stats == null || stats.status == FolderStatsStatus.Unavailable) {
        return stringResource(R.string.folder_label)
    }
    val filesLabel = pluralStringResource(
        R.plurals.folder_stats_files,
        stats.fileCount.toInt(),
        stats.fileCount
    )
    return stringResource(R.string.folder_stats_summary, filesLabel, formatFileSize(stats.totalBytes))
}
