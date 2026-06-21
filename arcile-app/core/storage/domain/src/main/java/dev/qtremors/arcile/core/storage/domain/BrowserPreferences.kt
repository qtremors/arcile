package dev.qtremors.arcile.core.storage.domain

import dev.qtremors.arcile.core.storage.domain.FileSortOption

enum class BrowserViewMode {
    LIST,
    GRID
}

enum class ImageGalleryGrouping {
    NONE,
    DAY,
    WEEK,
    MONTH
}

enum class ImageGalleryDefaultTab {
    PHOTOS,
    ALBUMS
}

data class BrowserPresentationPreferences(
    val sortOption: FileSortOption = DEFAULT_SORT_OPTION,
    val viewMode: BrowserViewMode = DEFAULT_VIEW_MODE,
    val listZoom: Float = DEFAULT_LIST_ZOOM,
    val gridMinCellSize: Float = DEFAULT_GRID_MIN_CELL_SIZE,
    val showThumbnails: Boolean = DEFAULT_SHOW_THUMBNAILS
) {
    companion object {
        val DEFAULT_SORT_OPTION: FileSortOption = FileSortOption.NAME_ASC
        val DEFAULT_CATEGORY_SORT_OPTION: FileSortOption = FileSortOption.DATE_NEWEST
        val DEFAULT_VIEW_MODE: BrowserViewMode = BrowserViewMode.LIST
        const val DEFAULT_LIST_ZOOM: Float = 1f
        const val DEFAULT_GRID_MIN_CELL_SIZE: Float = 132f
        const val DEFAULT_SHOW_THUMBNAILS: Boolean = true
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
    val recentPresentation: BrowserPresentationPreferences = BrowserPresentationPreferences(
        sortOption = BrowserPresentationPreferences.DEFAULT_CATEGORY_SORT_OPTION
    ),
    val pathPresentationOptions: Map<String, BrowserPresentationPreferences> = emptyMap(),
    val exactPathPresentationOptions: Map<String, BrowserPresentationPreferences> = emptyMap(),
    val homeRecentCarouselLimit: Int = DEFAULT_HOME_RECENT_CAROUSEL_LIMIT,
    val showHiddenFiles: Boolean = true,
    val imageGalleryShowFileDetails: Boolean = true,
    val imageGalleryAspectRatio: Boolean = false,
    val imageGallerySectioned: Boolean = false,
    val imageGalleryGrouping: ImageGalleryGrouping = ImageGalleryGrouping.MONTH,
    val imageGalleryDefaultTab: ImageGalleryDefaultTab = ImageGalleryDefaultTab.PHOTOS,
    val albumPresentation: BrowserPresentationPreferences = BrowserPresentationPreferences(
        sortOption = FileSortOption.NAME_ASC,
        viewMode = BrowserViewMode.GRID,
        gridMinCellSize = 160f
    ),
    val albumAspectRatio: Boolean = false,
    val favoriteFiles: Set<String> = emptySet(),
    val pinnedAlbums: Set<String> = emptySet(),
    val albumCovers: Map<String, String> = emptyMap(),
    val lastOpenedPath: String? = null,
    val lastOpenedVolumeId: String? = null,
    val defaultSaveToArcilePath: String? = null
) {
    companion object {
        const val MIN_HOME_RECENT_CAROUSEL_LIMIT = 0
        const val DEFAULT_HOME_RECENT_CAROUSEL_LIMIT = 20
        const val MAX_HOME_RECENT_CAROUSEL_LIMIT = 48

        fun normalizeHomeRecentCarouselLimit(limit: Int): Int =
            limit.coerceIn(MIN_HOME_RECENT_CAROUSEL_LIMIT, MAX_HOME_RECENT_CAROUSEL_LIMIT)
    }

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
