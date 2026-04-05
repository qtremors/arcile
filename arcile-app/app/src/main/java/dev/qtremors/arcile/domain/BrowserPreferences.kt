package dev.qtremors.arcile.domain

import dev.qtremors.arcile.presentation.FileSortOption

enum class BrowserViewMode {
    LIST,
    GRID
}

data class BrowserPresentationPreferences(
    val sortOption: FileSortOption = DEFAULT_SORT_OPTION,
    val viewMode: BrowserViewMode = DEFAULT_VIEW_MODE,
    val listZoom: Float = DEFAULT_LIST_ZOOM,
    val gridMinCellSize: Float = DEFAULT_GRID_MIN_CELL_SIZE
) {
    companion object {
        val DEFAULT_SORT_OPTION: FileSortOption = FileSortOption.NAME_ASC
        val DEFAULT_CATEGORY_SORT_OPTION: FileSortOption = FileSortOption.DATE_NEWEST
        val DEFAULT_VIEW_MODE: BrowserViewMode = BrowserViewMode.LIST
        const val DEFAULT_LIST_ZOOM: Float = 1f
        const val DEFAULT_GRID_MIN_CELL_SIZE: Float = 132f
        const val MIN_LIST_ZOOM: Float = 0.85f
        const val MAX_LIST_ZOOM: Float = 1.25f
        const val MIN_GRID_MIN_CELL_SIZE: Float = 96f
        const val MAX_GRID_MIN_CELL_SIZE: Float = 196f
    }

    fun normalized(): BrowserPresentationPreferences = copy(
        listZoom = listZoom.coerceIn(MIN_LIST_ZOOM, MAX_LIST_ZOOM),
        gridMinCellSize = gridMinCellSize.coerceIn(MIN_GRID_MIN_CELL_SIZE, MAX_GRID_MIN_CELL_SIZE)
    )
}

data class BrowserPreferences(
    val globalPresentation: BrowserPresentationPreferences = BrowserPresentationPreferences(),
    val pathPresentationOptions: Map<String, BrowserPresentationPreferences> = emptyMap(),
    val exactPathPresentationOptions: Map<String, BrowserPresentationPreferences> = emptyMap()
) {
    val globalSortOption: FileSortOption
        get() = globalPresentation.sortOption

    val pathSortOptions: Map<String, FileSortOption>
        get() = pathPresentationOptions.mapValues { it.value.sortOption }

    val exactPathSortOptions: Map<String, FileSortOption>
        get() = exactPathPresentationOptions.mapValues { it.value.sortOption }

    fun getPresentationForPath(path: String): BrowserPresentationPreferences {
        var currentPath = path.trimEnd('/')
        if (currentPath.isEmpty()) currentPath = "/"

        exactPathPresentationOptions[currentPath]?.let { return it }

        while (currentPath.isNotEmpty()) {
            pathPresentationOptions[currentPath]?.let { return it }
            val lastSlash = currentPath.lastIndexOf('/')
            if (lastSlash > 0) {
                currentPath = currentPath.substring(0, lastSlash)
            } else if (lastSlash == 0) {
                pathPresentationOptions["/"]?.let { return it }
                break
            } else {
                break
            }
        }
        return globalPresentation
    }

    fun getPresentationForCategory(categoryName: String): BrowserPresentationPreferences {
        val key = "category_$categoryName"
        return exactPathPresentationOptions[key]
            ?: pathPresentationOptions[key]
            ?: globalPresentation.copy(sortOption = BrowserPresentationPreferences.DEFAULT_CATEGORY_SORT_OPTION)
    }

    fun getSortOptionForPath(path: String): FileSortOption = getPresentationForPath(path).sortOption

    fun getSortOptionForCategory(categoryName: String): FileSortOption =
        getPresentationForCategory(categoryName).sortOption
}
