package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

/**
 * Compatibility facade while Browser consumers migrate to focused preference stores.
 */
interface BrowserPreferencesStore {
    val preferencesFlow: Flow<BrowserPreferences>

    suspend fun updateGlobalPresentation(presentation: FileListingPreferences)
    suspend fun updateRecentPresentation(presentation: FileListingPreferences)
    suspend fun updateHomeRecentCarouselLimit(limit: Int)
    suspend fun updateShowHiddenFiles(show: Boolean)
    suspend fun updateBrowserScrollbarEnabled(enabled: Boolean)
    suspend fun updateGalleryScrollbarEnabled(enabled: Boolean)
    suspend fun updateImageGalleryShowFileDetails(show: Boolean)
    suspend fun updateImageGalleryAspectRatio(enabled: Boolean)
    suspend fun updateImageGallerySectioned(enabled: Boolean)
    suspend fun updateImageGalleryGrouping(grouping: ImageGalleryGrouping)
    suspend fun updateImageGalleryDefaultTab(tab: ImageGalleryDefaultTab)
    suspend fun updateAlbumPresentation(presentation: FileListingPreferences)
    suspend fun updateAlbumAspectRatio(enabled: Boolean)
    suspend fun updatePathPresentation(
        path: String,
        presentation: FileListingPreferences?,
        applyToSubfolders: Boolean = false
    )
    suspend fun updateLastOpenedLocation(path: String, volumeId: String?)
    suspend fun updateDefaultSaveToArcilePath(path: String?)
    suspend fun updateFavorite(path: String, isFavorite: Boolean)
    suspend fun updatePinnedAlbum(albumPath: String, isPinned: Boolean)
    suspend fun updateAlbumCover(albumPath: String, coverPath: String)
}
