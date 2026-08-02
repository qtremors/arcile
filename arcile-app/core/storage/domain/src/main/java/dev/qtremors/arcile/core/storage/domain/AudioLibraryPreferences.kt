package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

data class AudioLibraryPreferences(
    val audioPresentation: FileListingPreferences = BrowserPreferences().audioPresentation,
    val folderPresentation: FileListingPreferences = BrowserPreferences().audioFolderPresentation,
    val grouping: CategoryGrouping = CategoryGrouping.MONTH,
    val defaultPage: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val showFileDetails: Boolean = true,
    val scrollbarEnabled: Boolean = true,
    val favoriteFiles: Set<String> = emptySet(),
    val pinnedFolders: Set<String> = emptySet(),
    val folderCovers: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(preferences: BrowserPreferences) = AudioLibraryPreferences(
            audioPresentation = preferences.audioPresentation,
            folderPresentation = preferences.audioFolderPresentation,
            grouping = preferences.audioGrouping,
            defaultPage = preferences.audioDefaultPage,
            showFileDetails = preferences.audioShowFileDetails,
            scrollbarEnabled = preferences.galleryScrollbarEnabled,
            favoriteFiles = preferences.audioFavoriteFiles,
            pinnedFolders = preferences.audioPinnedFolders,
            folderCovers = preferences.audioFolderCovers
        )
    }
}

interface AudioLibraryPreferencesStore {
    val audioLibraryPreferencesFlow: Flow<AudioLibraryPreferences>
    suspend fun updateAudioPresentation(presentation: FileListingPreferences)
    suspend fun updateAudioFolderPresentation(presentation: FileListingPreferences)
    suspend fun updateAudioGrouping(grouping: CategoryGrouping)
    suspend fun updateAudioDefaultPage(tab: CategoryLibraryPage)
    suspend fun updateAudioShowFileDetails(show: Boolean)
    suspend fun updateFavorite(path: String, isFavorite: Boolean)
    suspend fun updatePinnedFolder(path: String, isPinned: Boolean)
    suspend fun updateFolderCover(folderPath: String, coverPath: String?)
}
