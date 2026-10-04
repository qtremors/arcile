package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.presentation.PropertiesUiModel
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.SearchFilters

internal enum class AudioCollectionKind { SONGS, FOLDERS, ALBUMS, ARTISTS, GENRES, PLAYLISTS }

internal val audioNavigationSections = listOf(
    AudioCollectionKind.SONGS, AudioCollectionKind.FOLDERS, AudioCollectionKind.ALBUMS,
    AudioCollectionKind.ARTISTS, AudioCollectionKind.PLAYLISTS
)

internal fun audioNavigationPage(section: AudioCollectionKind): Int =
    audioNavigationSections.indexOf(section).coerceAtLeast(0)

internal enum class AudioSongFilter { ALL, FAVORITES, RECENTLY_PLAYED, MOST_PLAYED }

internal enum class AudioCollectionType {
    Directory,
    Favorites,
    Artist,
    Album,
    Genre,
    Playlist
}

internal data class AudioCollection(
    val key: String,
    val title: String,
    val subtitle: String?,
    val tracks: List<AudioTrack>,
    val customCoverPath: String? = null,
    val isPinned: Boolean = false,
    val kind: AudioCollectionType = AudioCollectionType.Directory
) {
    val isFavorites: Boolean get() = kind == AudioCollectionType.Favorites
    val isDirectory: Boolean get() = kind == AudioCollectionType.Directory
    val coverTrack: AudioTrack?
        get() = tracks.firstOrNull { it.file.reference == customCoverPath }
            ?: tracks.maxByOrNull { it.file.lastModified }
            ?: tracks.firstOrNull()
    val newestModified: Long get() = tracks.maxOfOrNull { it.file.lastModified } ?: 0L
    val totalSize: Long get() = tracks.sumOf { it.file.size }
}

internal data class AudioLibraryState(
    val tracks: List<AudioTrack> = emptyList(),
    val visibleTracks: List<AudioTrack> = emptyList(),
    val collections: List<AudioCollection> = emptyList(),
    val collectionKind: AudioCollectionKind = AudioCollectionKind.SONGS,
    val presentedCollectionKind: AudioCollectionKind = AudioCollectionKind.SONGS,
    val songFilter: AudioSongFilter = AudioSongFilter.ALL,
    val playCounts: Map<String, Int> = emptyMap(),
    val lastPlayedAt: Map<String, Long> = emptyMap(),
    val playlists: List<AudioPlaylist> = emptyList(),
    val sectionPresentations: Map<AudioCollectionKind, FileListingPreferences> = emptyMap(),
    val tab: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val defaultPage: CategoryLibraryPage = CategoryLibraryPage.ITEMS,
    val defaultSection: AudioCollectionKind = AudioCollectionKind.SONGS,
    val query: String = "",
    val searchFilters: SearchFilters = SearchFilters(),
    val audioPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.NAME_ASC,
        viewMode = FileViewMode.LIST,
        gridMinCellSize = 136f,
        showThumbnails = true
    ),
    val collectionPresentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.NAME_ASC,
        viewMode = FileViewMode.GRID,
        gridMinCellSize = 160f,
        showThumbnails = true
    ),
    val grouping: CategoryGrouping = CategoryGrouping.NONE,
    val showFileDetails: Boolean = true,
    val musicOnly: Boolean = false,
    val scrollbarEnabled: Boolean = true,
    val favoritePaths: Set<String> = emptySet(),
    val pinnedFolderPaths: Set<String> = emptySet(),
    val folderCoverPaths: Map<String, String> = emptyMap(),
    val favoriteSearchAliases: Set<String> = emptySet(),
    val collectionFilter: AudioCollection? = null,
    val selectedPaths: Set<String> = emptySet(),
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val isShredChecked: Boolean = false,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: PropertiesUiModel? = null,
    val clipboardState: ClipboardState? = null,
    val activeFileOperation: OperationUiState? = null,
    val pasteConflicts: List<FileConflict> = emptyList(),
    val pasteDestinationPath: String? = null,
    val showPasteConflictDialog: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: UiText? = null
)

internal fun AudioLibraryState.presentationFor(section: AudioCollectionKind): FileListingPreferences =
    when (section) {
        AudioCollectionKind.SONGS -> audioPresentation
        AudioCollectionKind.FOLDERS -> collectionPresentation
        else -> sectionPresentations[section] ?: collectionPresentation
    }

internal fun AudioLibraryState.trackPresentation(): FileListingPreferences =
    if (collectionFilter?.kind == AudioCollectionType.Album) {
        audioPresentation.copy(viewMode = FileViewMode.LIST)
    } else {
        audioPresentation
    }
