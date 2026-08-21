package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferences

internal fun BrowserNavigationState.applyNavigationPreferences(
    preferences: BrowserLocationPreferences
): BrowserNavigationState {
    val presentation = when {
        isCategoryScreen -> preferences.getPresentationForCategory(activeCategoryName)
        currentPath.isNotEmpty() -> preferences.getPresentationForPath(currentPath)
        else -> preferences.globalPresentation
    }
    return withValues(
        browserSortOption = presentation.sortOption,
        browserViewMode = presentation.viewMode,
        browserListZoom = presentation.listZoom,
        browserGridMinCellSize = presentation.gridMinCellSize,
        browserShowThumbnails = presentation.showThumbnails,
        browserScrollbarEnabled = preferences.scrollbarEnabled,
        showHiddenFiles = preferences.showHiddenFiles
    ).withUpdatedDisplayState()
}
