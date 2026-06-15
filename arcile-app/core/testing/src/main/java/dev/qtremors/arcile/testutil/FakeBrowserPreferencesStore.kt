package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBrowserPreferencesStore(
    initialPreferences: BrowserPreferences = BrowserPreferences()
) : BrowserPreferencesStore {
    private val preferences = MutableStateFlow(initialPreferences)
    override val preferencesFlow: Flow<BrowserPreferences> = preferences

    var lastUpdatedGlobalPresentation: BrowserPresentationPreferences? = null
    var lastUpdatedRecentPresentation: BrowserPresentationPreferences? = null
    var lastUpdatedHomeRecentCarouselLimit: Int? = null
    var lastUpdatedShowHiddenFiles: Boolean? = null
    var lastUpdatedImageGalleryShowFileDetails: Boolean? = null
    var lastUpdatedImageGalleryAspectRatio: Boolean? = null
    var lastUpdatedImageGallerySectioned: Boolean? = null
    var lastUpdatedImageGalleryGrouping: dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping? = null
    var lastUpdatedImageGalleryDefaultTab: dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab? = null
    var lastUpdatedAlbumPresentation: BrowserPresentationPreferences? = null
    var lastUpdatedAlbumAspectRatio: Boolean? = null
    var lastUpdatedPath: String? = null
    var lastUpdatedPathPresentation: BrowserPresentationPreferences? = null

    override suspend fun updateGlobalPresentation(presentation: BrowserPresentationPreferences) {
        lastUpdatedGlobalPresentation = presentation
        preferences.value = preferences.value.copy(globalPresentation = presentation)
    }

    override suspend fun updateRecentPresentation(presentation: BrowserPresentationPreferences) {
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

    override suspend fun updateAlbumPresentation(presentation: BrowserPresentationPreferences) {
        lastUpdatedAlbumPresentation = presentation
        preferences.value = preferences.value.copy(albumPresentation = presentation)
    }

    override suspend fun updateAlbumAspectRatio(enabled: Boolean) {
        lastUpdatedAlbumAspectRatio = enabled
        preferences.value = preferences.value.copy(albumAspectRatio = enabled)
    }

    override suspend fun updatePathPresentation(
        path: String,
        presentation: BrowserPresentationPreferences?,
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

    override suspend fun updateFavorite(path: String, isFavorite: Boolean) {
        val currentFavorites = preferences.value.favoriteFiles
        val newFavorites = if (isFavorite) {
            currentFavorites + path
        } else {
            currentFavorites - path
        }
        preferences.value = preferences.value.copy(favoriteFiles = newFavorites)
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
