package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping

internal enum class AudioLibraryTab {
    AUDIO,
    FOLDERS
}

internal data class AudioFolder(
    val key: String,
    val title: String,
    val subtitle: String?,
    val tracks: List<AudioTrack>
) {
    val coverTrack: AudioTrack
        get() = tracks.maxByOrNull { it.file.lastModified } ?: tracks.first()
    val newestModified: Long get() = tracks.maxOf { it.file.lastModified }
    val totalSize: Long get() = tracks.sumOf { it.file.size }
}

internal data class AudioLibraryState(
    val tracks: List<AudioTrack> = emptyList(),
    val visibleTracks: List<AudioTrack> = emptyList(),
    val folders: List<AudioFolder> = emptyList(),
    val tab: AudioLibraryTab = AudioLibraryTab.AUDIO,
    val defaultTab: AudioLibraryTab = AudioLibraryTab.AUDIO,
    val query: String = "",
    val audioPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.DATE_NEWEST,
        viewMode = FileViewMode.LIST,
        gridMinCellSize = 136f,
        showThumbnails = true
    ),
    val folderPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.DATE_NEWEST,
        viewMode = FileViewMode.LIST,
        gridMinCellSize = 160f,
        showThumbnails = true
    ),
    val grouping: ImageGalleryGrouping = ImageGalleryGrouping.MONTH,
    val showFileDetails: Boolean = true,
    val folderFilter: AudioFolder? = null,
    val selectedPaths: Set<String> = emptySet(),
    val clipboardState: ClipboardState? = null,
    val activeFileOperation: OperationUiState? = null,
    val pasteConflicts: List<FileConflict> = emptyList(),
    val pasteDestinationPath: String? = null,
    val showPasteConflictDialog: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val playerExpanded: Boolean = false,
    val error: UiText? = null
)
