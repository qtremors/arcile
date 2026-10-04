package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

data class GalleryPreferences(
    val globalShowThumbnails: Boolean = FileListingPreferences.DEFAULT_SHOW_THUMBNAILS,
    val itemPresentation: FileListingPreferences? = null,
    val showFileDetails: Boolean = true,
    val aspectRatio: Boolean = false,
    val sectioned: Boolean = false,
    val grouping: CategoryGrouping = CategoryGrouping.MONTH,
    val defaultPage: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val folderPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.NAME_ASC,
        viewMode = FileViewMode.GRID,
        gridMinCellSize = 160f
    ),
    val folderAspectRatio: Boolean = false,
    val favoriteFiles: Set<String> = emptySet(),
    val pinnedFolders: Set<String> = emptySet(),
    val folderCovers: Map<String, String> = emptyMap(),
    val scrollbarEnabled: Boolean = true
) {
    companion object {
        private const val IMAGE_GALLERY_PRESENTATION_PATH = "image_gallery"

        fun from(
            preferences: SharedFilePreferences,
            categoryName: String = FileCategories.Images.id.value
        ): GalleryPreferences {
            val legacyItemPresentation = preferences.exactPathPresentationOptions[
                IMAGE_GALLERY_PRESENTATION_PATH
            ]
            val itemPresentation = preferences.categoryPresentationOrNull(
                categoryName,
                CategoryLibraryPage.ITEMS
            ) ?: legacyItemPresentation
            val folderPresentation = preferences.categoryPresentationOrNull(
                categoryName,
                CategoryLibraryPage.FOLDERS
            ) ?: preferences.folderPresentation
            return GalleryPreferences(
            globalShowThumbnails = preferences.globalPresentation.showThumbnails,
            itemPresentation = itemPresentation,
            showFileDetails = preferences.categoryShowFileDetails[categoryName]
                ?: preferences.imageGalleryShowFileDetails,
            aspectRatio = preferences.categoryAspectRatios[categoryName]
                ?: preferences.imageGalleryAspectRatio,
            sectioned = preferences.categorySectioned[categoryName]
                ?: preferences.imageGallerySectioned,
            grouping = preferences.categoryGroupings[categoryName]
                ?: preferences.imageGalleryGrouping,
            defaultPage = preferences.categoryDefaultPages[categoryName]
                ?: preferences.imageGalleryDefaultPage,
            folderPresentation = folderPresentation,
            folderAspectRatio = preferences.folderAspectRatio,
            favoriteFiles = preferences.favoriteFiles,
            pinnedFolders = preferences.pinnedFolders,
            folderCovers = preferences.folderCovers,
            scrollbarEnabled = preferences.galleryScrollbarEnabled
        )
        }
    }
}

interface GalleryPreferencesStore {
    val galleryPreferencesFlow: Flow<GalleryPreferences>
    fun galleryPreferencesFlow(categoryName: String): Flow<GalleryPreferences>
    suspend fun updateItemPresentation(
        categoryName: String,
        presentation: FileListingPreferences
    )
    suspend fun updateGalleryScrollbarEnabled(enabled: Boolean)
    suspend fun updateShowFileDetails(categoryName: String, show: Boolean)
    suspend fun updateAspectRatio(categoryName: String, enabled: Boolean)
    suspend fun updateSectioned(categoryName: String, enabled: Boolean)
    suspend fun updateGrouping(categoryName: String, grouping: CategoryGrouping)
    suspend fun updateDefaultPage(categoryName: String, page: CategoryLibraryPage)
    suspend fun updateFolderPresentation(
        categoryName: String,
        presentation: FileListingPreferences
    )
    suspend fun updateFolderAspectRatio(enabled: Boolean)
    suspend fun updateFavorite(path: String, isFavorite: Boolean)
    suspend fun updatePinnedFolder(folderPath: String, isPinned: Boolean)
    suspend fun updateFolderCover(folderPath: String, coverPath: String)
}

private fun SharedFilePreferences.categoryPresentationOrNull(
    categoryName: String,
    page: CategoryLibraryPage
): FileListingPreferences? {
    val key = "category_${categoryName}_${page.preferenceSuffix}"
    return exactPathPresentationOptions[key] ?: pathPresentationOptions[key]
}
