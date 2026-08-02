package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.AppStartPage
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
    override fun galleryPreferencesFlow(categoryName: String): Flow<GalleryPreferences> =
        preferences.map { GalleryPreferences.from(it, categoryName) }
    override val saveDestinationPreferencesFlow: Flow<SaveDestinationPreferences> =
        preferences.map(SaveDestinationPreferences::from)

    var lastUpdatedGlobalPresentation: FileListingPreferences? = null
    var lastUpdatedAppStartPage: AppStartPage? = null
    var lastUpdatedRecentPresentation: FileListingPreferences? = null
    var lastUpdatedHomeRecentCarouselLimit: Int? = null
    var lastUpdatedShowHiddenFiles: Boolean? = null
    var lastUpdatedBrowserScrollbarEnabled: Boolean? = null
    var lastUpdatedGalleryScrollbarEnabled: Boolean? = null
    var lastUpdatedImageGalleryShowFileDetails: Boolean? = null
    var lastUpdatedImageGalleryAspectRatio: Boolean? = null
    var lastUpdatedImageGallerySectioned: Boolean? = null
    var lastUpdatedCategoryGrouping: dev.qtremors.arcile.core.storage.domain.CategoryGrouping? = null
    var lastUpdatedCategoryLibraryPage: dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage? = null
    var lastUpdatedAlbumPresentation: FileListingPreferences? = null
    var lastUpdatedImageGalleryPresentation: FileListingPreferences? = null
    var lastUpdatedAlbumAspectRatio: Boolean? = null
    var lastUpdatedPath: String? = null
    var lastUpdatedPathPresentation: FileListingPreferences? = null
    var lastUpdatedDefaultSaveToArcilePath: String? = null

    override suspend fun updateAppStartPage(page: AppStartPage) {
        lastUpdatedAppStartPage = page
        preferences.value = preferences.value.copy(appStartPage = page)
    }

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

    override suspend fun updateShowFileDetails(categoryName: String, show: Boolean) {
        lastUpdatedImageGalleryShowFileDetails = show
        preferences.value = preferences.value.copy(
            categoryShowFileDetails =
                preferences.value.categoryShowFileDetails + (categoryName to show)
        )
    }

    override suspend fun updateAspectRatio(categoryName: String, enabled: Boolean) {
        lastUpdatedImageGalleryAspectRatio = enabled
        preferences.value = preferences.value.copy(
            categoryAspectRatios = preferences.value.categoryAspectRatios + (categoryName to enabled)
        )
    }

    override suspend fun updateSectioned(categoryName: String, enabled: Boolean) {
        lastUpdatedImageGallerySectioned = enabled
        preferences.value = preferences.value.copy(
            categorySectioned = preferences.value.categorySectioned + (categoryName to enabled)
        )
    }

    override suspend fun updateGrouping(
        categoryName: String,
        grouping: dev.qtremors.arcile.core.storage.domain.CategoryGrouping
    ) {
        lastUpdatedCategoryGrouping = grouping
        preferences.value = preferences.value.copy(
            categoryGroupings = preferences.value.categoryGroupings + (categoryName to grouping)
        )
    }

    override suspend fun updateDefaultPage(
        categoryName: String,
        page: dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
    ) {
        lastUpdatedCategoryLibraryPage = page
        preferences.value = preferences.value.copy(
            categoryDefaultPages = preferences.value.categoryDefaultPages + (categoryName to page)
        )
    }

    override suspend fun updateFolderPresentation(
        categoryName: String,
        presentation: FileListingPreferences
    ) {
        lastUpdatedAlbumPresentation = presentation
        updatePathPresentation(
            "category_${categoryName}_folders",
            presentation,
            applyToSubfolders = false
        )
    }

    override suspend fun updateItemPresentation(
        categoryName: String,
        presentation: FileListingPreferences
    ) {
        lastUpdatedImageGalleryPresentation = presentation
        updatePathPresentation(
            "category_${categoryName}_items",
            presentation,
            applyToSubfolders = false
        )
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

    override suspend fun updateFileOpenBehavior(
        categoryName: String,
        behavior: dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
    ) {
        preferences.value = preferences.value.copy(
            fileOpenBehaviors = preferences.value.fileOpenBehaviors + (categoryName to behavior)
        )
    }

    override suspend fun updateCategoryGrouping(
        categoryName: String,
        grouping: dev.qtremors.arcile.core.storage.domain.CategoryGrouping
    ) {
        preferences.value = preferences.value.copy(
            categoryGroupings = preferences.value.categoryGroupings +
                (categoryName to grouping)
        )
    }

    override suspend fun updateCategoryDefaultPage(
        categoryName: String,
        page: dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
    ) {
        preferences.value = preferences.value.copy(
            categoryDefaultPages = preferences.value.categoryDefaultPages +
                (categoryName to page)
        )
    }

    override suspend fun updateCategoryShowFileDetails(categoryName: String, show: Boolean) {
        preferences.value = preferences.value.copy(
            categoryShowFileDetails = preferences.value.categoryShowFileDetails +
                (categoryName to show)
        )
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
