package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

data class BrowserLocationPreferences(
    val globalPresentation: FileListingPreferences = FileListingPreferences(),
    val pathPresentationOptions: Map<String, FileListingPreferences> = emptyMap(),
    val exactPathPresentationOptions: Map<String, FileListingPreferences> = emptyMap(),
    val showHiddenFiles: Boolean = true,
    val lastOpenedPath: String? = null,
    val lastOpenedVolumeId: String? = null,
    val scrollbarEnabled: Boolean = true
) {
    fun getPresentationForPath(path: String): FileListingPreferences {
        var currentPath = path.trimEnd('/').ifEmpty { "/" }
        exactPathPresentationOptions[currentPath]?.let { return it }

        while (currentPath.isNotEmpty()) {
            pathPresentationOptions[currentPath]?.let { return it }
            val lastSlash = currentPath.lastIndexOf('/')
            when {
                lastSlash > 0 -> currentPath = currentPath.substring(0, lastSlash)
                lastSlash == 0 -> {
                    pathPresentationOptions["/"]?.let { return it }
                    break
                }
                else -> break
            }
        }
        return globalPresentation
    }

    fun getPresentationForCategory(categoryName: String): FileListingPreferences {
        val key = "category_$categoryName"
        return exactPathPresentationOptions[key]
            ?: pathPresentationOptions[key]
            ?: globalPresentation.copy(sortOption = FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION)
    }

    companion object {
        fun from(preferences: BrowserPreferences) = BrowserLocationPreferences(
            globalPresentation = preferences.globalPresentation,
            pathPresentationOptions = preferences.pathPresentationOptions,
            exactPathPresentationOptions = preferences.exactPathPresentationOptions,
            showHiddenFiles = preferences.showHiddenFiles,
            lastOpenedPath = preferences.lastOpenedPath,
            lastOpenedVolumeId = preferences.lastOpenedVolumeId,
            scrollbarEnabled = preferences.browserScrollbarEnabled
        )
    }
}

data class RecentFilesPreferences(
    val presentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileListingPreferences.DEFAULT_CATEGORY_SORT_OPTION
    ),
    val homeCarouselLimit: Int = BrowserPreferences.DEFAULT_HOME_RECENT_CAROUSEL_LIMIT
) {
    companion object {
        fun from(preferences: BrowserPreferences) = RecentFilesPreferences(
            presentation = preferences.recentPresentation,
            homeCarouselLimit = preferences.homeRecentCarouselLimit
        )
    }
}

data class GalleryPreferences(
    val globalShowThumbnails: Boolean = FileListingPreferences.DEFAULT_SHOW_THUMBNAILS,
    val imagePresentation: FileListingPreferences? = null,
    val showFileDetails: Boolean = true,
    val aspectRatio: Boolean = false,
    val sectioned: Boolean = false,
    val grouping: ImageGalleryGrouping = ImageGalleryGrouping.MONTH,
    val defaultTab: ImageGalleryDefaultTab = ImageGalleryDefaultTab.PHOTOS,
    val albumPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.NAME_ASC,
        viewMode = FileViewMode.GRID,
        gridMinCellSize = 160f
    ),
    val albumAspectRatio: Boolean = false,
    val favoriteFiles: Set<String> = emptySet(),
    val pinnedAlbums: Set<String> = emptySet(),
    val albumCovers: Map<String, String> = emptyMap(),
    val scrollbarEnabled: Boolean = true
) {
    companion object {
        private const val IMAGE_GALLERY_PRESENTATION_PATH = "image_gallery"

        fun from(preferences: BrowserPreferences) = GalleryPreferences(
            globalShowThumbnails = preferences.globalPresentation.showThumbnails,
            imagePresentation = preferences.exactPathPresentationOptions[IMAGE_GALLERY_PRESENTATION_PATH],
            showFileDetails = preferences.imageGalleryShowFileDetails,
            aspectRatio = preferences.imageGalleryAspectRatio,
            sectioned = preferences.imageGallerySectioned,
            grouping = preferences.imageGalleryGrouping,
            defaultTab = preferences.imageGalleryDefaultTab,
            albumPresentation = preferences.albumPresentation,
            albumAspectRatio = preferences.albumAspectRatio,
            favoriteFiles = preferences.favoriteFiles,
            pinnedAlbums = preferences.pinnedAlbums,
            albumCovers = preferences.albumCovers,
            scrollbarEnabled = preferences.galleryScrollbarEnabled
        )
    }
}

data class SaveDestinationPreferences(
    val defaultPath: String? = null
) {
    companion object {
        fun from(preferences: BrowserPreferences) =
            SaveDestinationPreferences(defaultPath = preferences.defaultSaveToArcilePath)
    }
}

interface BrowserLocationPreferencesStore {
    val locationPreferencesFlow: Flow<BrowserLocationPreferences>
    suspend fun updateGlobalPresentation(presentation: FileListingPreferences)
    suspend fun updateShowHiddenFiles(show: Boolean)
    suspend fun updateBrowserScrollbarEnabled(enabled: Boolean)
    suspend fun updatePathPresentation(
        path: String,
        presentation: FileListingPreferences?,
        applyToSubfolders: Boolean = false
    )
    suspend fun updateLastOpenedLocation(path: String, volumeId: String?)
}

interface RecentFilesPreferencesStore {
    val recentFilesPreferencesFlow: Flow<RecentFilesPreferences>
    suspend fun updateRecentPresentation(presentation: FileListingPreferences)
    suspend fun updateHomeRecentCarouselLimit(limit: Int)
}

interface GalleryPreferencesStore {
    val galleryPreferencesFlow: Flow<GalleryPreferences>
    suspend fun updateImageGalleryPresentation(presentation: FileListingPreferences)
    suspend fun updateGalleryScrollbarEnabled(enabled: Boolean)
    suspend fun updateImageGalleryShowFileDetails(show: Boolean)
    suspend fun updateImageGalleryAspectRatio(enabled: Boolean)
    suspend fun updateImageGallerySectioned(enabled: Boolean)
    suspend fun updateImageGalleryGrouping(grouping: ImageGalleryGrouping)
    suspend fun updateImageGalleryDefaultTab(tab: ImageGalleryDefaultTab)
    suspend fun updateAlbumPresentation(presentation: FileListingPreferences)
    suspend fun updateAlbumAspectRatio(enabled: Boolean)
    suspend fun updateFavorite(path: String, isFavorite: Boolean)
    suspend fun updatePinnedAlbum(albumPath: String, isPinned: Boolean)
    suspend fun updateAlbumCover(albumPath: String, coverPath: String)
}

interface SaveDestinationPreferencesStore {
    val saveDestinationPreferencesFlow: Flow<SaveDestinationPreferences>
    suspend fun updateDefaultSaveToArcilePath(path: String?)
}
