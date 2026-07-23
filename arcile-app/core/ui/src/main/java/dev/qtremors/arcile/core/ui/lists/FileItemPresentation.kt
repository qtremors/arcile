package dev.qtremors.arcile.core.ui.lists

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import dev.qtremors.arcile.core.storage.domain.FileModel

@Immutable
data class FileItemPresentation(
    val zoom: Float = 1f,
    val showThumbnails: Boolean = true,
    val showDetails: Boolean = true,
    val thumbnailLoadingPaused: Boolean = false,
    val openImageFromThumbnailInSelectionMode: Boolean = false,
    val thumbnailData: ((FileModel, Int) -> Any?)? = null
)

internal fun TextStyle.scaled(zoom: Float): TextStyle = copy(
    fontSize = fontSize * zoom,
    lineHeight = lineHeight * zoom
)
