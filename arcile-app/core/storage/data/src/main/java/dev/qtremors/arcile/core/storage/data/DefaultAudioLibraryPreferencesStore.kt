package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.AudioLibraryDefaultTab
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping

class DefaultAudioLibraryPreferencesStore(
    private val dataSource: BrowserPreferencesDataSource
) : AudioLibraryPreferencesStore {
    override val audioLibraryPreferencesFlow = dataSource.audioLibraryPreferencesFlow

    override suspend fun updateAudioPresentation(presentation: FileListingPreferences) =
        dataSource.updateAudioPresentation(presentation)

    override suspend fun updateAudioFolderPresentation(presentation: FileListingPreferences) =
        dataSource.updateAudioFolderPresentation(presentation)

    override suspend fun updateAudioGrouping(grouping: ImageGalleryGrouping) =
        dataSource.updateAudioGrouping(grouping)

    override suspend fun updateAudioDefaultTab(tab: AudioLibraryDefaultTab) =
        dataSource.updateAudioDefaultTab(tab)

    override suspend fun updateAudioShowFileDetails(show: Boolean) =
        dataSource.updateAudioShowFileDetails(show)
}
