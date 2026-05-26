package dev.qtremors.arcile.presentation.ui.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.BrowserViewMode
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.presentation.browser.BrowserState
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.ui.components.SearchTopBar
import dev.qtremors.arcile.presentation.ui.components.TopBarAction
import dev.qtremors.arcile.presentation.ui.components.lists.ActiveFiltersRow
import dev.qtremors.arcile.utils.formatFileSize
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserTopBars(
    state: BrowserState,
    displayedFiles: List<FileModel>,
    showSearchBar: Boolean,
    onShowSearchBarChange: (Boolean) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    dialogVisibility: BrowserDialogVisibility,
    actions: BrowserUiActions,
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
                onQueryChange = actions.onSearchQueryChange,
                onClose = {
                    onShowSearchBarChange(false)
                    actions.onClearSearch()
                },
                onFilterClick = { actions.onToggleSearchFilterMenu(true) },
                placeholder = searchPlaceholder
            )

            ActiveFiltersRow(
                filters = state.activeSearchFilters,
                onClearFilter = { clearedFilters -> actions.onSearchFiltersChange(clearedFilters) }
            )
        }
    } else {
        val selectedSizeFormatted = if (state.selectedFiles.isNotEmpty()) {
            formatFileSize(state.selectedFilesTotalSize)
        } else {
            null
        }

        ArcileTopBar(
            title = if (state.isCategoryScreen) state.activeCategoryName else stringResource(R.string.browse_title),
            selectionCount = state.selectedFiles.size,
            selectedSize = selectedSizeFormatted,
            showBackArrow = true,
            showSearchAction = true,
            showSortAction = !state.isVolumeRootScreen,
            showNewFolderAction = !state.isVolumeRootScreen && !state.isCategoryScreen,
            showPinAction = !state.isVolumeRootScreen && !state.isCategoryScreen && state.currentPath.isNotEmpty(),
            isGridView = state.browserViewMode == BrowserViewMode.GRID,
            scrollBehavior = scrollBehavior,
            onBackClick = onBackClick,
            onClearSelection = actions.onClearSelection,
            onSearchClick = { onShowSearchBarChange(true) },
            onSortClick = { dialogVisibility.showSortDialog = true },
            onActionSelected = { action ->
                when (action) {
                    TopBarAction.NewFolder -> dialogVisibility.showCreateFolderDialog = true
                    TopBarAction.PinToQuickAccess -> {
                        state.currentPath.takeIf { it.isNotEmpty() }?.let { path ->
                            val label = File(path).name
                            actions.onPinToQuickAccess(path, label)
                            onShowPinnedSnackbar(label)
                        }
                    }
                    TopBarAction.DeleteSelected -> actions.onRequestDeleteSelected()
                    TopBarAction.Rename -> if (state.selectedFiles.size == 1) {
                        dialogVisibility.showRenameDialog = true
                    }
                    TopBarAction.Copy -> actions.onCopySelected()
                    TopBarAction.Cut -> actions.onCutSelected()
                    TopBarAction.Share -> actions.onShareSelected()
                    TopBarAction.SelectAll -> {
                        onSelectionChanged()
                        actions.onSelectAll(displayedFiles.map { it.absolutePath })
                    }
                    TopBarAction.InvertSelection -> {
                        onSelectionChanged()
                        actions.onInvertSelection(displayedFiles.map { it.absolutePath })
                    }
                    TopBarAction.Properties -> actions.onOpenProperties()
                    else -> Unit
                }
            }
        )
    }
}
