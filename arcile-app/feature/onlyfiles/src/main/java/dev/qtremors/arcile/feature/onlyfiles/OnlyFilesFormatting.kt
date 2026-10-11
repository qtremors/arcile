package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Description
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.ArcileOpenExtensions
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata

internal fun nodeIcon(node: VaultNodeMetadata) = when {
    node.isDirectory -> Icons.Default.Folder
    node.isViewableImage() -> Icons.Default.Image
    node.isViewableVideo() -> Icons.Default.Movie
    else -> Icons.Outlined.Description
}

internal fun VaultNodeMetadata.isViewableImage(): Boolean =
    category() == FileCategories.Images || extension.equals("avif", ignoreCase = true)

internal fun VaultNodeMetadata.isViewableVideo(): Boolean =
    category() == FileCategories.Videos

internal fun VaultNodeMetadata.isViewableAudio(): Boolean = category() == FileCategories.Audio

internal fun VaultNodeMetadata.isViewablePdf(): Boolean =
    mimeType.equals("application/pdf", ignoreCase = true) || extension.equals("pdf", ignoreCase = true)

internal fun VaultNodeMetadata.isViewableText(): Boolean =
    extension.lowercase() in ArcileOpenExtensions.text || mimeType?.startsWith("text/", ignoreCase = true) == true

private fun VaultNodeMetadata.category() = FileCategories.getCategoryForFile(extension, mimeType)

internal fun formatBytes(context: Context, bytes: Long): String = formatFileSize(context, bytes)
