package dev.qtremors.arcile.feature.onlyfiles

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Description
import dev.qtremors.arcile.core.vault.domain.VaultNode

internal fun nodeIcon(node: VaultNode) = when {
    node.isDirectory -> Icons.Default.Folder
    node.isViewableImage() -> Icons.Default.Image
    node.isViewableVideo() -> Icons.Default.Movie
    else -> Icons.Outlined.Description
}

internal fun VaultNode.isViewableImage(): Boolean =
    mimeType?.startsWith("image/") == true || extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")

internal fun VaultNode.isViewableVideo(): Boolean =
    mimeType?.startsWith("video/") == true || extension in setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp", "avi", "ts")

internal fun formatBytes(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    bytes < 1024L * 1024L * 1024L -> "${bytes / (1024L * 1024L)} MB"
    else -> "%.1f GB".format(bytes.toDouble() / (1024.0 * 1024.0 * 1024.0))
}
