package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.SelectAll
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
    onDefaultPageChange: (CategoryLibraryPage) -> Unit,
    onSelectAll: () -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val menuActions = buildList {
        CategoryLibraryPage.entries.forEach { tab ->
            add(
                CategoryMenuAction(
                    label = stringResource(
                        if (tab == CategoryLibraryPage.ITEMS) {
                            R.string.audio_open_to_audio
                        } else {
                            R.string.audio_open_to_folders
                        }
                    ),
                    icon = if (tab == CategoryLibraryPage.ITEMS) {
                        Icons.Default.MusicNote
                    } else {
                        Icons.Default.Folder
                    },
                    selected = state.defaultPage == tab,
                    onClick = { onDefaultPageChange(tab) }
                )
            )
        }
        val hasItems = if (
            currentTab == CategoryLibraryPage.ITEMS ||
            state.folderFilter != null
        ) {
            state.visibleTracks.isNotEmpty()
        } else {
            state.folders.isNotEmpty()
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
        selectedCountText = stringResource(R.string.audio_selected_count, selectedCount),
        selectedSizeText = formatFileSize(selectedSize),
        onClearSelection = onClearSelection,
        onSelectAll = onSelectAll,
        onInvertSelection = onInvertSelection,
        modifier = modifier
    )
}

@Composable
internal fun AudioLibraryBottomBar(
    state: AudioLibraryState,
    currentTab: CategoryLibraryPage,
    selectedTracks: List<AudioTrack>,
    isChromeVisible: Boolean,
    selectionBackProgress: Float,
    onSelectTab: (CategoryLibraryPage) -> Unit,
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
    onPaste: () -> Unit,
    onCancelClipboard: () -> Unit,
    onShowClipboardContents: () -> Unit,
    onClearActiveFileOperation: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelectionMode = selectedTracks.isNotEmpty()
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
                    canPaste = state.folderFilter != null,
                    onPaste = onPaste,
                    onCancel = onCancelClipboard,
                    onShowContents = onShowClipboardContents,
                    onClearCompleted = onClearActiveFileOperation
                )
            } else {
                CategoryNavigationBar(
                    tabs = listOf(
                        CategoryTabSpec(
                            label = stringResource(R.string.audio_tracks),
                            icon = Icons.Default.MusicNote,
                            selected = currentTab == CategoryLibraryPage.ITEMS,
                            onClick = { onSelectTab(CategoryLibraryPage.ITEMS) }
                        ),
                        CategoryTabSpec(
                            label = stringResource(R.string.audio_folders),
                            icon = Icons.Default.Folder,
                            selected = currentTab == CategoryLibraryPage.FOLDERS,
                            onClick = { onSelectTab(CategoryLibraryPage.FOLDERS) }
                        )
                    )
                )
            }
        },
        selectionContent = {
            AudioSelectionActionsBar(
                canUseSingleTrackActions = selectedTracks.size == 1,
                allSelectedFavorite = selectedTracks.all {
                    it.file.absolutePath in state.favoritePaths
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
                onToggleFavorite = onToggleFavorite
            )
        }
    )
}
