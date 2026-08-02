package dev.qtremors.arcile.core.storage.domain

enum class FileViewMode {
    LIST,
    GRID
}

enum class CategoryGrouping {
    NONE,
    DAY,
    WEEK,
    MONTH
}

enum class CategoryLibraryPage {
    ITEMS,
    FOLDERS
}

enum class AppStartPage {
    HOME,
    BROWSER
}

data class FileListingPreferences(
    val sortOption: FileSortOption = DEFAULT_SORT_OPTION,
    val viewMode: FileViewMode = DEFAULT_VIEW_MODE,
    val listZoom: Float = DEFAULT_LIST_ZOOM,
    val gridMinCellSize: Float = DEFAULT_GRID_MIN_CELL_SIZE,
    val showThumbnails: Boolean = DEFAULT_SHOW_THUMBNAILS
) {
    companion object {
        val DEFAULT_SORT_OPTION: FileSortOption = FileSortOption.NAME_ASC
        val DEFAULT_CATEGORY_SORT_OPTION: FileSortOption = FileSortOption.DATE_NEWEST
        val DEFAULT_VIEW_MODE: FileViewMode = FileViewMode.LIST
        const val DEFAULT_LIST_ZOOM: Float = 1f
        const val DEFAULT_GRID_MIN_CELL_SIZE: Float = 132f
        const val DEFAULT_SHOW_THUMBNAILS: Boolean = true
        const val MIN_LIST_ZOOM: Float = 0.85f
        const val MAX_LIST_ZOOM: Float = 1.25f
        const val MIN_GRID_MIN_CELL_SIZE: Float = 96f
        const val MAX_GRID_MIN_CELL_SIZE: Float = 196f
    }

    fun normalized(): FileListingPreferences = copy(
        listZoom = listZoom.coerceIn(MIN_LIST_ZOOM, MAX_LIST_ZOOM),
        gridMinCellSize = gridMinCellSize.coerceIn(MIN_GRID_MIN_CELL_SIZE, MAX_GRID_MIN_CELL_SIZE)
    )
}

data class BrowserPreferences(
    val appStartPage: AppStartPage = AppStartPage.HOME,
    val globalPresentation: FileListingPreferences = FileListingPreferences(),
    val recentPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION
    ),
    val pathPresentationOptions: Map<String, FileListingPreferences> = emptyMap(),
    val exactPathPresentationOptions: Map<String, FileListingPreferences> = emptyMap(),
    val homeRecentCarouselLimit: Int = DEFAULT_HOME_RECENT_CAROUSEL_LIMIT,
    val showHiddenFiles: Boolean = true,
    val imageGalleryShowFileDetails: Boolean = true,
    val imageGalleryAspectRatio: Boolean = false,
    val imageGallerySectioned: Boolean = false,
    val imageGalleryGrouping: CategoryGrouping = CategoryGrouping.MONTH,
    val imageGalleryDefaultPage: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val audioPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.DATE_NEWEST,
        viewMode = FileViewMode.LIST,
        gridMinCellSize = 136f
    ),
    val audioFolderPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.DATE_NEWEST,
        viewMode = FileViewMode.LIST,
        gridMinCellSize = 160f
    ),
    val audioGrouping: CategoryGrouping = CategoryGrouping.MONTH,
    val audioDefaultPage: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val audioShowFileDetails: Boolean = true,
    val audioFavoriteFiles: Set<String> = emptySet(),
    val audioPinnedFolders: Set<String> = emptySet(),
    val audioFolderCovers: Map<String, String> = emptyMap(),
    val categoryGroupings: Map<String, CategoryGrouping> = emptyMap(),
    val categoryDefaultPages: Map<String, CategoryLibraryPage> = emptyMap(),
    val categoryShowFileDetails: Map<String, Boolean> = emptyMap(),
    val categoryAspectRatios: Map<String, Boolean> = emptyMap(),
    val categorySectioned: Map<String, Boolean> = emptyMap(),
    val albumPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.NAME_ASC,
        viewMode = FileViewMode.GRID,
        gridMinCellSize = 160f
    ),
    val albumAspectRatio: Boolean = false,
    val favoriteFiles: Set<String> = emptySet(),
    val pinnedAlbums: Set<String> = emptySet(),
    val albumCovers: Map<String, String> = emptyMap(),
    val lastOpenedPath: String? = null,
    val lastOpenedVolumeId: String? = null,
    val fileOpenBehaviors: Map<String, FileOpenBehavior> = emptyMap(),
    val defaultSaveToArcilePath: String? = null,
    val browserScrollbarEnabled: Boolean = true,
    val galleryScrollbarEnabled: Boolean = true
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

    fun getPresentationForPath(path: String): FileListingPreferences {
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

    fun getPresentationForCategory(
        categoryName: String,
        page: CategoryLibraryPage? = null
    ): FileListingPreferences {
        val categoryKey = "category_$categoryName"
        val key = page?.let { "${categoryKey}_${it.preferenceSuffix}" } ?: categoryKey
        return exactPathPresentationOptions[key]
            ?: pathPresentationOptions[key]
            ?: if (page != null) {
                exactPathPresentationOptions[categoryKey] ?: pathPresentationOptions[categoryKey]
            } else {
                null
            }
            ?: globalPresentation.copy(sortOption = FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION)
    }

    fun getDefaultPageForCategory(categoryName: String): CategoryLibraryPage =
        categoryDefaultPages[categoryName] ?: CategoryLibraryPage.ITEMS

    fun getSortOptionForPath(path: String): FileSortOption = getPresentationForPath(path).sortOption

    fun getSortOptionForCategory(categoryName: String): FileSortOption =
        getPresentationForCategory(categoryName).sortOption
}

val CategoryLibraryPage.preferenceSuffix: String
    get() = when (this) {
        CategoryLibraryPage.ITEMS -> "items"
        CategoryLibraryPage.FOLDERS -> "folders"
    }
