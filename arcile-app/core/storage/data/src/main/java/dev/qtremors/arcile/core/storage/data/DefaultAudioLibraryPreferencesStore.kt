package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping

class DefaultAudioLibraryPreferencesStore(
    private val dataSource: BrowserPreferencesDataSource
) : AudioLibraryPreferencesStore {
    override val audioLibraryPreferencesFlow = dataSource.audioLibraryPreferencesFlow

    override suspend fun updateAudioPresentation(presentation: FileListingPreferences) =
        dataSource.updateAudioPresentation(presentation)

    override suspend fun updateAudioFolderPresentation(presentation: FileListingPreferences) =
        dataSource.updateAudioFolderPresentation(presentation)

    override suspend fun updateAudioGrouping(grouping: CategoryGrouping) =
        dataSource.updateAudioGrouping(grouping)

    override suspend fun updateAudioDefaultPage(tab: CategoryLibraryPage) =
        dataSource.updateAudioDefaultPage(tab)

    override suspend fun updateAudioShowFileDetails(show: Boolean) =
        dataSource.updateAudioShowFileDetails(show)

    override suspend fun updateFavorite(path: String, isFavorite: Boolean) =
        dataSource.updateAudioFavorite(path, isFavorite)

    override suspend fun updatePinnedFolder(path: String, isPinned: Boolean) =
        dataSource.updateAudioPinnedFolder(path, isPinned)

    override suspend fun updateFolderCover(folderPath: String, coverPath: String?) =
        dataSource.updateAudioFolderCover(folderPath, coverPath.orEmpty())
}
