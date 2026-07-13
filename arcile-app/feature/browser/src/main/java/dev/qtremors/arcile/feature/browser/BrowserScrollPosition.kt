package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable

@Immutable
internal data class BrowserScrollPosition(
    val listIndex: Int,
    val listOffset: Int,
    val gridIndex: Int,
    val gridOffset: Int
)

internal fun BrowserUiState.scrollPositionKey(): String =
    "$browserSortOption|$currentPath|${activeCategoryName.orEmpty()}|" +
        "${selectedFolderTabPath.orEmpty()}|${archiveContext?.archivePath.orEmpty()}|" +
        archiveContext?.entryPrefix.orEmpty()
