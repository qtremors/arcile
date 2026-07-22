package dev.qtremors.arcile.feature.videoplayer

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.storagePathName
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun fileModelFromPath(path: String): FileModel {
    val name = storagePathName(path).ifBlank { path }
    val extension = name.substringAfterLast('.', "").lowercase()
    return FileModel(
        name = name,
        absolutePath = path,
        size = 0L,
        lastModified = 0L,
        isDirectory = false,
        extension = extension,
        isHidden = name.startsWith("."),
        mimeType = when (extension) {
            "mp4" -> "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            "avi" -> "video/avi"
            "mov" -> "video/quicktime"
            "3gp" -> "video/3gpp"
            "ts" -> "video/mp2t"
            else -> null
        }
    )
}

internal fun FileModel.openableReference(): String =
    nodeRef.contentUri?.takeIf { it.isNotBlank() } ?: absolutePath

internal fun viewerPositionLabel(currentPage: Int, total: Int): String {
    if (total <= 0) return "0/0"
    return "${currentPage.coerceIn(0, total - 1) + 1}/$total"
}

internal fun formatViewerDateTime(
    timestampMillis: Long,
    locale: Locale = Locale.getDefault(),
    timeZone: TimeZone = TimeZone.getDefault()
): String? {
    if (timestampMillis <= 0L) return null
    return SimpleDateFormat("MMM d, yyyy • h:mm a", locale)
        .apply { this.timeZone = timeZone }
        .format(Date(timestampMillis))
}

internal fun formatResolution(width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    return "$width x $height"
}

internal fun viewerParentPath(path: String): String? {
    val normalized = path.trimEnd('/', '\\')
    val separatorIndex = normalized.lastIndexOfAny(charArrayOf('/', '\\'))
    return normalized.substring(0, separatorIndex).takeIf { separatorIndex > 0 }
}
