package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.ui.category.CategoryBottomChrome
import dev.qtremors.arcile.core.ui.category.CategoryFloatingTopBar
import dev.qtremors.arcile.core.ui.category.CategoryMenuAction
import dev.qtremors.arcile.core.ui.category.CategoryNavigationBar
import dev.qtremors.arcile.core.ui.category.CategorySelectionTopBar
import dev.qtremors.arcile.core.ui.category.CategoryTabSpec

@Composable
internal fun AudioLibraryFloatingTopBar(
    state: AudioLibraryState,
    currentTab: CategoryLibraryPage,
    showSearchBar: Boolean,
    onSearchClick: () -> Unit,
    onCloseSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchFiltersChange: (SearchFilters) -> Unit,
    onViewSort: () -> Unit,
    onDefaultSectionChange: (AudioCollectionKind) -> Unit,
    onSelectAll: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuActions = buildList {
        audioNavigationSections.forEach { section ->
            add(
                CategoryMenuAction(
                    label = stringResource(R.string.audio_open_to_page, section.displayName()),
                    icon = when (section) {
                        AudioCollectionKind.SONGS -> Icons.Default.MusicNote
                        AudioCollectionKind.FOLDERS -> Icons.Default.Folder
                        AudioCollectionKind.ALBUMS -> Icons.Default.Album
                        AudioCollectionKind.ARTISTS -> Icons.Default.Person
                        AudioCollectionKind.GENRES -> Icons.Default.Category
                        AudioCollectionKind.PLAYLISTS -> Icons.Default.QueueMusic
                    },
                    selected = state.defaultSection == section,
                    onClick = { onDefaultSectionChange(section) }
                )
            )
        }
        val hasItems = if (
            currentTab == CategoryLibraryPage.ITEMS ||
            state.collectionFilter != null
        ) {
            state.visibleTracks.isNotEmpty()
        } else {
            state.collections.isNotEmpty()
        }
        if (hasItems) {
            add(
                CategoryMenuAction(
                    label = stringResource(dev.qtremors.arcile.core.ui.R.string.select_all),
                    icon = Icons.Default.SelectAll,
                    onClick = onSelectAll
                )
            )
        }
    }
    CategoryFloatingTopBar(
        query = state.query,
        searchPlaceholder = stringResource(R.string.audio_search),
        showSearchBar = showSearchBar,
        menuActions = menuActions,
        onSearchClick = onSearchClick,
        onCloseSearch = onCloseSearch,
        onQueryChange = onQueryChange,
        searchFilters = state.searchFilters,
        onSearchFiltersChange = onSearchFiltersChange,
        onViewSort = onViewSort,
        onNavigateBack = onNavigateBack,
        modifier = modifier
    )
}

@Composable
internal fun AudioSelectionTopBar(
    selectedCount: Int,
    selectedSize: Long,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    modifier: Modifier = Modifier
) {
    CategorySelectionTopBar(
        selectedCountText = androidx.compose.ui.res.pluralStringResource(R.plurals.audio_selected_count, selectedCount, selectedCount),
        selectedSizeText = formatFileSize(androidx.compose.ui.platform.LocalContext.current, selectedSize),
        onClearSelection = onClearSelection,
        onSelectAll = onSelectAll,
        onInvertSelection = onInvertSelection,
        modifier = modifier
    )
}

@Composable
internal fun AudioLibraryBottomBar(
    state: AudioLibraryState,
    currentSection: AudioCollectionKind,
    selectedTracks: List<AudioTrack>,
    isChromeVisible: Boolean,
    selectionBackProgress: Float,
    onSelectSection: (AudioCollectionKind) -> Unit,
    onPlaySelected: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onRenameSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onShareSelected: () -> Unit,
    onOpenProperties: () -> Unit,
    onCreateZip: () -> Unit,
    onOpenWith: () -> Unit,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit,
    onEditAudio: () -> Unit,
    onPaste: () -> Unit,
    onCancelClipboard: () -> Unit,
    onShowClipboardContents: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelectionMode = selectedTracks.isNotEmpty()
    val sectionScroll = rememberScrollState()
    LaunchedEffect(currentSection) {
        sectionScroll.animateScrollTo(audioNavigationSections.indexOf(currentSection).coerceAtLeast(0) * 64)
    }
    val keepChromeVisible = isChromeVisible ||
        isSelectionMode ||
        state.clipboardState != null ||
        state.activeFileOperation != null

    CategoryBottomChrome(
        visible = keepChromeVisible,
        selectionMode = isSelectionMode,
        selectionBackProgress = selectionBackProgress,
        modifier = modifier,
        normalContent = {
            if (state.clipboardState != null || state.activeFileOperation != null) {
                AudioClipboardToolbar(
                    state = state,
                    canPaste = state.collectionFilter != null,
                    onPaste = onPaste,
                    onCancel = onCancelClipboard,
                    onShowContents = onShowClipboardContents
                )
            } else {
                CategoryNavigationBar(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(sectionScroll),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    tabs = audioNavigationSections.map { section ->
                        CategoryTabSpec(
                            label = section.displayName(),
                            icon = when (section) {
                                AudioCollectionKind.SONGS -> Icons.Default.MusicNote
                                AudioCollectionKind.FOLDERS -> Icons.Default.Folder
                                AudioCollectionKind.ALBUMS -> Icons.Default.Album
                                AudioCollectionKind.ARTISTS -> Icons.Default.Person
                                AudioCollectionKind.GENRES -> Icons.Default.Category
                                AudioCollectionKind.PLAYLISTS -> Icons.Default.QueueMusic
                            },
                            selected = currentSection == section,
                            onClick = { onSelectSection(section) }
                        )
                    }
                )
            }
        },
        selectionContent = {
            AudioSelectionActionsBar(
                canUseSingleTrackActions = selectedTracks.size == 1,
                allSelectedFavorite = selectedTracks.all {
                    it.file.reference in state.favoritePaths
                },
                onPlay = onPlaySelected,
                onCopy = onCopySelected,
                onCut = onCutSelected,
                onRename = onRenameSelected,
                onDelete = onDeleteSelected,
                onShare = onShareSelected,
                onProperties = onOpenProperties,
                onCreateZip = onCreateZip,
                onOpenWith = onOpenWith,
                onToggleFavorite = onToggleFavorite,
                onAddToPlaylist = onAddToPlaylist,
                onEditTags = onEditTags,
                onEditAudio = onEditAudio
            )
        }
    )
}
