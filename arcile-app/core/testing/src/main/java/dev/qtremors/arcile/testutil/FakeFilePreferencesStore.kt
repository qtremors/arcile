package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.GalleryPreferences
import dev.qtremors.arcile.core.storage.domain.GalleryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.RecentFilesPreferences
import dev.qtremors.arcile.core.storage.domain.RecentFilesPreferencesStore
import dev.qtremors.arcile.core.storage.domain.SaveDestinationPreferences
import dev.qtremors.arcile.core.storage.domain.SaveDestinationPreferencesStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeFilePreferencesStore(
    initialPreferences: BrowserPreferences = BrowserPreferences()
) : BrowserLocationPreferencesStore,
    RecentFilesPreferencesStore,
    GalleryPreferencesStore,
    SaveDestinationPreferencesStore {
    private val preferences = MutableStateFlow(initialPreferences)
    override val locationPreferencesFlow: Flow<BrowserLocationPreferences> =
        preferences.map(BrowserLocationPreferences::from)
    override val recentFilesPreferencesFlow: Flow<RecentFilesPreferences> =
        preferences.map(RecentFilesPreferences::from)
    override val galleryPreferencesFlow: Flow<GalleryPreferences> =
        preferences.map(GalleryPreferences::from)
    override val saveDestinationPreferencesFlow: Flow<SaveDestinationPreferences> =
        preferences.map(SaveDestinationPreferences::from)

    var lastUpdatedGlobalPresentation: FileListingPreferences? = null
    var lastUpdatedRecentPresentation: FileListingPreferences? = null
    var lastUpdatedHomeRecentCarouselLimit: Int? = null
    var lastUpdatedShowHiddenFiles: Boolean? = null
    var lastUpdatedBrowserScrollbarEnabled: Boolean? = null
    var lastUpdatedGalleryScrollbarEnabled: Boolean? = null
    var lastUpdatedImageGalleryShowFileDetails: Boolean? = null
    var lastUpdatedImageGalleryAspectRatio: Boolean? = null
    var lastUpdatedImageGallerySectioned: Boolean? = null
    var lastUpdatedImageGalleryGrouping: dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping? = null
    var lastUpdatedImageGalleryDefaultTab: dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab? = null
    var lastUpdatedAlbumPresentation: FileListingPreferences? = null
    var lastUpdatedImageGalleryPresentation: FileListingPreferences? = null
    var lastUpdatedAlbumAspectRatio: Boolean? = null
    var lastUpdatedPath: String? = null
    var lastUpdatedPathPresentation: FileListingPreferences? = null
    var lastUpdatedDefaultSaveToArcilePath: String? = null

    override suspend fun updateGlobalPresentation(presentation: FileListingPreferences) {
        lastUpdatedGlobalPresentation = presentation
        preferences.value = preferences.value.copy(globalPresentation = presentation)
    }

    override suspend fun updateRecentPresentation(presentation: FileListingPreferences) {
        lastUpdatedRecentPresentation = presentation
        preferences.value = preferences.value.copy(recentPresentation = presentation)
    }

    override suspend fun updateHomeRecentCarouselLimit(limit: Int) {
        val normalized = BrowserPreferences.normalizeHomeRecentCarouselLimit(limit)
        lastUpdatedHomeRecentCarouselLimit = normalized
        preferences.value = preferences.value.copy(homeRecentCarouselLimit = normalized)
    }

    override suspend fun updateShowHiddenFiles(show: Boolean) {
        lastUpdatedShowHiddenFiles = show
        preferences.value = preferences.value.copy(showHiddenFiles = show)
    }

    override suspend fun updateBrowserScrollbarEnabled(enabled: Boolean) {
        lastUpdatedBrowserScrollbarEnabled = enabled
        preferences.value = preferences.value.copy(browserScrollbarEnabled = enabled)
    }

    override suspend fun updateGalleryScrollbarEnabled(enabled: Boolean) {
        lastUpdatedGalleryScrollbarEnabled = enabled
        preferences.value = preferences.value.copy(galleryScrollbarEnabled = enabled)
    }

    override suspend fun updateImageGalleryShowFileDetails(show: Boolean) {
        lastUpdatedImageGalleryShowFileDetails = show
        preferences.value = preferences.value.copy(imageGalleryShowFileDetails = show)
    }

    override suspend fun updateImageGalleryAspectRatio(enabled: Boolean) {
        lastUpdatedImageGalleryAspectRatio = enabled
        preferences.value = preferences.value.copy(imageGalleryAspectRatio = enabled)
    }

    override suspend fun updateImageGallerySectioned(enabled: Boolean) {
        lastUpdatedImageGallerySectioned = enabled
        preferences.value = preferences.value.copy(imageGallerySectioned = enabled)
    }

    override suspend fun updateImageGalleryGrouping(grouping: dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping) {
        lastUpdatedImageGalleryGrouping = grouping
        preferences.value = preferences.value.copy(imageGalleryGrouping = grouping)
    }

    override suspend fun updateImageGalleryDefaultTab(tab: dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab) {
        lastUpdatedImageGalleryDefaultTab = tab
        preferences.value = preferences.value.copy(imageGalleryDefaultTab = tab)
    }

    override suspend fun updateAlbumPresentation(presentation: FileListingPreferences) {
        lastUpdatedAlbumPresentation = presentation
        preferences.value = preferences.value.copy(albumPresentation = presentation)
    }

    override suspend fun updateImageGalleryPresentation(presentation: FileListingPreferences) {
        lastUpdatedImageGalleryPresentation = presentation
        updatePathPresentation("image_gallery", presentation, applyToSubfolders = false)
    }

    override suspend fun updateAlbumAspectRatio(enabled: Boolean) {
        lastUpdatedAlbumAspectRatio = enabled
        preferences.value = preferences.value.copy(albumAspectRatio = enabled)
    }

    override suspend fun updatePathPresentation(
        path: String,
        presentation: FileListingPreferences?,
        applyToSubfolders: Boolean
    ) {
        lastUpdatedPath = path
        lastUpdatedPathPresentation = presentation
        val updatedMap = if (applyToSubfolders) {
            preferences.value.pathPresentationOptions.toMutableMap().apply {
                if (presentation == null) remove(path) else put(path, presentation)
            }
        } else {
            preferences.value.exactPathPresentationOptions.toMutableMap().apply {
                if (presentation == null) remove(path) else put(path, presentation)
            }
        }
        preferences.value = if (applyToSubfolders) {
            preferences.value.copy(pathPresentationOptions = updatedMap)
        } else {
            preferences.value.copy(exactPathPresentationOptions = updatedMap)
        }
    }

    override suspend fun updateLastOpenedLocation(path: String, volumeId: String?) {
        lastUpdatedPath = path
        preferences.value = preferences.value.copy(lastOpenedPath = path, lastOpenedVolumeId = volumeId)
    }

    override suspend fun updateDefaultSaveToArcilePath(path: String?) {
        lastUpdatedDefaultSaveToArcilePath = path
        preferences.value = preferences.value.copy(defaultSaveToArcilePath = path)
    }

    override suspend fun updateFavorite(path: String, isFavorite: Boolean) {
        val currentFavorites = preferences.value.favoriteFiles
        val newFavorites = if (isFavorite) {
            currentFavorites + path
        } else {
            currentFavorites - path
        }
        preferences.value = preferences.value.copy(favoriteFiles = newFavorites)
    }

    override suspend fun updatePinnedAlbum(albumPath: String, isPinned: Boolean) {
        val currentPinned = preferences.value.pinnedAlbums
        val newPinned = if (isPinned) {
            currentPinned + albumPath
        } else {
            currentPinned - albumPath
        }
        preferences.value = preferences.value.copy(pinnedAlbums = newPinned)
    }

    override suspend fun updateAlbumCover(albumPath: String, coverPath: String) {
        val currentCovers = preferences.value.albumCovers
        val newCovers = if (coverPath.isEmpty()) {
            currentCovers - albumPath
        } else {
            currentCovers + (albumPath to coverPath)
        }
        preferences.value = preferences.value.copy(albumCovers = newCovers)
    }

}
