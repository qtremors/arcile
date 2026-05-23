package dev.qtremors.arcile.presentation.ui.components.lists

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.FolderStatsStatus
import dev.qtremors.arcile.utils.formatFileSize
import java.text.DateFormat
import java.util.Date

@Immutable
data class FileRowUiModel(
    val file: FileModel,
    val formattedDate: String,
    val subtitle: String,
    val iconType: FileIconType,
    val isHidden: Boolean,
    val thumbnailSizePx: Int
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
        folderStats.toPlainFolderSubtitle()
    } else {
        formatFileSize(size)
    }

    return FileRowUiModel(
        file = this,
        formattedDate = formatter.format(Date(lastModified)),
        subtitle = subtitle,
        iconType = iconType,
        isHidden = isHidden || name.startsWith("."),
        thumbnailSizePx = thumbnailSizePx
    )
}

val FileRowUiModel.canShowThumbnail: Boolean
    get() = !isDirectory && iconType != FileIconType.Generic

private fun FolderStats?.toPlainFolderSubtitle(): String {
    if (this == null || status == FolderStatsStatus.Unavailable) return "Folder"
    val fileLabel = if (fileCount == 1L) "1 file" else "$fileCount files"
    return "$fileLabel • ${formatFileSize(totalBytes)}"
}
