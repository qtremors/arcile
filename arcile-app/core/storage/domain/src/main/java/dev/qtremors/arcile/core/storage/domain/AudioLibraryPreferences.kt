package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

data class AudioLibraryPreferences(
    val audioPresentation: FileListingPreferences = SharedFilePreferences().audioPresentation,
    val collectionPresentation: FileListingPreferences = SharedFilePreferences().audioCollectionPresentation,
    val grouping: CategoryGrouping = CategoryGrouping.NONE,
    val defaultPage: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val showFileDetails: Boolean = true,
    val scrollbarEnabled: Boolean = true,
    val favoriteFiles: Set<String> = emptySet(),
    val pinnedFolders: Set<String> = emptySet(),
    val folderCovers: Map<String, String> = emptyMap()
) {
    companion object {
        fun from(preferences: SharedFilePreferences) = AudioLibraryPreferences(
            audioPresentation = preferences.audioPresentation,
            collectionPresentation = preferences.audioCollectionPresentation,
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
    suspend fun updateAudioCollectionPresentation(presentation: FileListingPreferences)
    suspend fun updateAudioGrouping(grouping: CategoryGrouping)
    suspend fun updateAudioDefaultPage(tab: CategoryLibraryPage)
    suspend fun updateAudioShowFileDetails(show: Boolean)
    suspend fun updateFavorite(path: String, isFavorite: Boolean)
    suspend fun clearMigratedFavorites()
    suspend fun updatePinnedFolder(path: String, isPinned: Boolean)
    suspend fun updateFolderCover(folderPath: String, coverPath: String?)
}
