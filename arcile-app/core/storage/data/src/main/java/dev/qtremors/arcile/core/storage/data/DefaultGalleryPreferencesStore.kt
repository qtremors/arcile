package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.GalleryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.preferenceSuffix

class DefaultGalleryPreferencesStore(
    private val dataSource: BrowserPreferencesDataSource
) : GalleryPreferencesStore {
    override val galleryPreferencesFlow = dataSource.galleryPreferencesFlow

    override fun galleryPreferencesFlow(categoryName: String) =
        dataSource.galleryPreferencesFlow(categoryName)

    override suspend fun updateItemPresentation(
        categoryName: String,
        presentation: FileListingPreferences
    ) = dataSource.updatePathPresentation(
        path = "category_${categoryName}_${CategoryLibraryPage.ITEMS.preferenceSuffix}",
        presentation = presentation,
        applyToSubfolders = false
    )

    override suspend fun updateGalleryScrollbarEnabled(enabled: Boolean) =
        dataSource.updateGalleryScrollbarEnabled(enabled)

    override suspend fun updateShowFileDetails(categoryName: String, show: Boolean) =
        dataSource.updateCategoryShowFileDetails(categoryName, show)

    override suspend fun updateAspectRatio(categoryName: String, enabled: Boolean) =
        dataSource.updateCategoryAspectRatio(categoryName, enabled)

    override suspend fun updateSectioned(categoryName: String, enabled: Boolean) =
        dataSource.updateCategorySectioned(categoryName, enabled)

    override suspend fun updateGrouping(categoryName: String, grouping: CategoryGrouping) =
        dataSource.updateCategoryGrouping(categoryName, grouping)

    override suspend fun updateDefaultPage(categoryName: String, page: CategoryLibraryPage) =
        dataSource.updateCategoryDefaultPage(categoryName, page)

    override suspend fun updateFolderPresentation(
        categoryName: String,
        presentation: FileListingPreferences
    ) = dataSource.updatePathPresentation(
        path = "category_${categoryName}_${CategoryLibraryPage.FOLDERS.preferenceSuffix}",
        presentation = presentation,
        applyToSubfolders = false
    )

    override suspend fun updateAlbumAspectRatio(enabled: Boolean) =
        dataSource.updateAlbumAspectRatio(enabled)

    override suspend fun updateFavorite(path: String, isFavorite: Boolean) =
        dataSource.updateFavorite(path, isFavorite)

    override suspend fun updatePinnedAlbum(albumPath: String, isPinned: Boolean) =
        dataSource.updatePinnedAlbum(albumPath, isPinned)

    override suspend fun updateAlbumCover(albumPath: String, coverPath: String) =
        dataSource.updateAlbumCover(albumPath, coverPath)
}
