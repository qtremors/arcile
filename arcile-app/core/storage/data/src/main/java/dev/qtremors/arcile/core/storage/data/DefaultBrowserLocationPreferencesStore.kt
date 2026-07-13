package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences

class DefaultBrowserLocationPreferencesStore(
    private val dataSource: BrowserPreferencesDataSource
) : BrowserLocationPreferencesStore {
    override val locationPreferencesFlow = dataSource.locationPreferencesFlow

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
}
