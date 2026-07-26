package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

data class AudioLibraryPreferences(
    val audioPresentation: FileListingPreferences = BrowserPreferences().audioPresentation,
    val folderPresentation: FileListingPreferences = BrowserPreferences().audioFolderPresentation,
    val grouping: ImageGalleryGrouping = ImageGalleryGrouping.MONTH,
    val defaultTab: AudioLibraryDefaultTab = AudioLibraryDefaultTab.AUDIO,
    val showFileDetails: Boolean = true,
    val scrollbarEnabled: Boolean = true
) {
    companion object {
        fun from(preferences: BrowserPreferences) = AudioLibraryPreferences(
            audioPresentation = preferences.audioPresentation,
            folderPresentation = preferences.audioFolderPresentation,
            grouping = preferences.audioGrouping,
            defaultTab = preferences.audioDefaultTab,
            showFileDetails = preferences.audioShowFileDetails,
            scrollbarEnabled = preferences.galleryScrollbarEnabled
        )
    }
}

interface AudioLibraryPreferencesStore {
    val audioLibraryPreferencesFlow: Flow<AudioLibraryPreferences>
    suspend fun updateAudioPresentation(presentation: FileListingPreferences)
    suspend fun updateAudioFolderPresentation(presentation: FileListingPreferences)
    suspend fun updateAudioGrouping(grouping: ImageGalleryGrouping)
    suspend fun updateAudioDefaultTab(tab: AudioLibraryDefaultTab)
    suspend fun updateAudioShowFileDetails(show: Boolean)
}
