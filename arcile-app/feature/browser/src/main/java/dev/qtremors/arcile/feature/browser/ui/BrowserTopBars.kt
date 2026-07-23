package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.storagePathName
import dev.qtremors.arcile.feature.browser.BrowserUiState
import dev.qtremors.arcile.core.ui.ArcileTopBar
import dev.qtremors.arcile.core.ui.SearchTopBar
import dev.qtremors.arcile.core.ui.TopBarAction
import dev.qtremors.arcile.core.ui.lists.ActiveFiltersRow
import dev.qtremors.arcile.core.presentation.formatFileSize

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserTopBars(
    state: BrowserUiState,
    displayedFiles: List<FileModel>,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    dialogVisibility: BrowserDialogVisibility,
    searchIntents: BrowserSearchIntents,
    selectionIntents: BrowserSelectionIntents,
    mutationIntents: BrowserMutationIntents,
    clipboardIntents: BrowserClipboardIntents,
    onToggleHiddenFiles: () -> Unit,
    onBackClick: () -> Unit,
    onSelectionChanged: () -> Unit,
    onShowPinnedSnackbar: (String) -> Unit
) {
    if (showSearchBar) {
        Column {
            val searchPlaceholder = if (state.isCategoryScreen) {
                stringResource(R.string.search_category_placeholder, state.activeCategoryName.lowercase())
            } else {
                stringResource(R.string.search_placeholder)
            }
            SearchTopBar(
                query = state.browserSearchQuery,
                onQueryChange = searchIntents.onSearchQueryChange,
                onClose = {
                    onShowSearchBarChange(false)
                    searchIntents.onClearSearch()
                },
                onFilterClick = { searchIntents.onToggleSearchFilterMenu(true) },
                placeholder = searchPlaceholder
            )

            ActiveFiltersRow(
                filters = state.activeSearchFilters,
                onClearFilter = { clearedFilters -> searchIntents.onSearchFiltersChange(clearedFilters) }
            )
        }
    } else {
        val selectedSizeFormatted = if (state.selectedFiles.isNotEmpty()) {
            formatFileSize(state.selectedFilesTotalSize)
        } else {
            null
        }

        ArcileTopBar(
            title = when {
                state.archiveContext != null -> state.archiveContext.archiveName
                state.isCategoryScreen -> state.activeCategoryName
                else -> stringResource(R.string.browse_title)
            },
            selectionCount = state.selectedFiles.size,
            selectedSize = selectedSizeFormatted,
            options = dev.qtremors.arcile.core.ui.ArcileTopBarOptions(
                showBackArrow = true,
                showSearchAction = true,
                showSortAction = !state.isVolumeRootScreen,
                showNewFolderAction = !state.isVolumeRootScreen &&
                    !state.isCategoryScreen &&
                    state.archiveContext == null,
                showPinAction = !state.isVolumeRootScreen &&
                    !state.isCategoryScreen &&
                    state.currentPath.isNotEmpty() &&
                    state.archiveContext == null,
                showHiddenFilesAction = state.archiveContext == null,
                areHiddenFilesShown = state.showHiddenFiles,
                isGridView = state.browserViewMode == FileViewMode.GRID
            ),
            scrollBehavior = scrollBehavior,
            actions = dev.qtremors.arcile.core.ui.ArcileTopBarActions(
                onBackClick = onBackClick,
                onClearSelection = selectionIntents.onClearSelection,
                onSearchClick = { onShowSearchBarChange(true) },
                onSortClick = { dialogVisibility.showSortDialog = true },
                onActionSelected = { action ->
                when (action) {
                    TopBarAction.NewFolder -> dialogVisibility.showCreateFolderDialog = true
                    TopBarAction.PinToQuickAccess -> {
                        state.currentPath.takeIf { it.isNotEmpty() }?.let { path ->
                            val label = storagePathName(path)
                            selectionIntents.onPinToQuickAccess(path, label)
                            onShowPinnedSnackbar(label)
                        }
                    }
                    TopBarAction.DeleteSelected -> mutationIntents.onRequestDeleteSelected()
                    TopBarAction.Rename -> if (state.selectedFiles.size == 1) {
                        dialogVisibility.showRenameDialog = true
                    }
                    TopBarAction.Copy -> clipboardIntents.onCopySelected()
                    TopBarAction.Cut -> clipboardIntents.onCutSelected()
                    TopBarAction.Share -> selectionIntents.onShareSelected()
                    TopBarAction.SelectAll -> {
                        onSelectionChanged()
                        selectionIntents.onSelectAll(displayedFiles.map { it.absolutePath })
                    }
                    TopBarAction.InvertSelection -> {
                        onSelectionChanged()
                        selectionIntents.onInvertSelection(displayedFiles.map { it.absolutePath })
                    }
                    TopBarAction.Properties -> selectionIntents.onOpenProperties()
                    TopBarAction.ToggleHiddenFiles -> onToggleHiddenFiles()
                    else -> Unit
                }
                }
            )
        )
    }
}
