package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.RecentFilesPreferencesStore

class DefaultRecentFilesPreferencesStore(
    private val dataSource: BrowserPreferencesDataSource
) : RecentFilesPreferencesStore {
    override val recentFilesPreferencesFlow = dataSource.recentFilesPreferencesFlow

    override suspend fun updateRecentPresentation(presentation: FileListingPreferences) =
        dataSource.updateRecentPresentation(presentation)

    override suspend fun updateHomeRecentCarouselLimit(limit: Int) =
        dataSource.updateHomeRecentCarouselLimit(limit)
}
