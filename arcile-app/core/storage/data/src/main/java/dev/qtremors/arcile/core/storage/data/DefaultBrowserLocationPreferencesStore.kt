package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileOpenBehavior
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping

class DefaultBrowserLocationPreferencesStore(
    private val dataSource: BrowserPreferencesDataSource
) : BrowserLocationPreferencesStore {
    override val locationPreferencesFlow = dataSource.locationPreferencesFlow

    override suspend fun updateAppStartPage(page: AppStartPage) =
        dataSource.updateAppStartPage(page)

    override suspend fun updateGlobalPresentation(presentation: FileListingPreferences) =
        dataSource.updateGlobalPresentation(presentation)

    override suspend fun updateShowHiddenFiles(show: Boolean) =
        dataSource.updateShowHiddenFiles(show)

    override suspend fun updateBrowserScrollbarEnabled(enabled: Boolean) =
        dataSource.updateBrowserScrollbarEnabled(enabled)

    override suspend fun updatePathPresentation(
        path: String,
        presentation: FileListingPreferences?,
        applyToSubfolders: Boolean
    ) = dataSource.updatePathPresentation(path, presentation, applyToSubfolders)

    override suspend fun updateLastOpenedLocation(path: String, volumeId: String?) =
        dataSource.updateLastOpenedLocation(path, volumeId)

    override suspend fun updateFileOpenBehavior(categoryName: String, behavior: FileOpenBehavior) =
        dataSource.updateFileOpenBehavior(categoryName, behavior)

    override suspend fun updateCategoryGrouping(
        categoryName: String,
        grouping: CategoryGrouping
    ) = dataSource.updateCategoryGrouping(categoryName, grouping)

    override suspend fun updateCategoryDefaultPage(
        categoryName: String,
        page: CategoryLibraryPage
    ) = dataSource.updateCategoryDefaultPage(categoryName, page)

    override suspend fun updateCategoryShowFileDetails(
        categoryName: String,
        show: Boolean
    ) = dataSource.updateCategoryShowFileDetails(categoryName, show)
}
