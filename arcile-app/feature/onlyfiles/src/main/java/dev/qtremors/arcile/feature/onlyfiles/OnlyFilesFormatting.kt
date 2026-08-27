package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Description
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata

internal fun nodeIcon(node: VaultNodeMetadata) = when {
    node.isDirectory -> Icons.Default.Folder
    node.isViewableImage() -> Icons.Default.Image
    node.isViewableVideo() -> Icons.Default.Movie
    else -> Icons.Outlined.Description
}

internal fun VaultNodeMetadata.isViewableImage(): Boolean =
    mimeType?.startsWith("image/") == true || extension in setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif")

internal fun VaultNodeMetadata.isViewableVideo(): Boolean =
    mimeType?.startsWith("video/") == true || extension in setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp", "avi", "ts")

internal fun formatBytes(context: Context, bytes: Long): String = formatFileSize(context, bytes)
