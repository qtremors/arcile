package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

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
            imagePresentation = preferences.exactPathPresentationOptions[
                IMAGE_GALLERY_PRESENTATION_PATH
            ],
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
