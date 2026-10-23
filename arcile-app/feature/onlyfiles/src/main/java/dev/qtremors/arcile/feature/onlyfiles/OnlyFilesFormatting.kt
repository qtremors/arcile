package dev.qtremors.arcile.feature.onlyfiles

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.outlined.Description
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.BuiltInFileOpen
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.storage.domain.FileOpenPreferences
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata

internal fun nodeIcon(node: VaultNodeMetadata) = when {
    node.isDirectory -> Icons.Default.Folder
    node.isViewableImage() -> Icons.Default.Image
    node.isViewableVideo() -> Icons.Default.Movie
    else -> Icons.Outlined.Description
}

internal fun VaultNodeMetadata.builtInOpen(): BuiltInFileOpen =
    if (isDirectory) BuiltInFileOpen.EXTERNAL else BuiltInFileOpen.resolve(
        name, extension, mimeType ?: "image/avif".takeIf { extension.equals("avif", ignoreCase = true) }
    )

internal fun VaultNodeMetadata.isViewableImage(): Boolean = builtInOpen() == BuiltInFileOpen.IMAGE
internal fun VaultNodeMetadata.isViewableVideo(): Boolean = builtInOpen() == BuiltInFileOpen.VIDEO
internal fun VaultNodeMetadata.isViewableText(): Boolean = builtInOpen() == BuiltInFileOpen.TEXT

private fun VaultNodeMetadata.category() = FileCategories.getCategoryForFile(extension, mimeType)

internal fun VaultNodeMetadata.prefersExternalOpen(behaviors: Map<String, FileOpenBehavior>): Boolean =
    !isDirectory && ref.capabilities.canOpenWith &&
        FileOpenPreferences.effectiveBehavior(
            behaviors = behaviors,
            extension = FileOpenPreferences.extensionForPath(name, extension),
            category = category()
        ) == FileOpenBehavior.EXTERNAL

internal fun formatBytes(context: Context, bytes: Long): String = formatFileSize(context, bytes)
