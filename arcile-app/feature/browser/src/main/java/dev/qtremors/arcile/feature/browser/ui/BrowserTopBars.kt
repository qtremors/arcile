package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.Alignment
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Tab
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.AppStartPage
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.feature.browser.BrowserUiState
import dev.qtremors.arcile.feature.browser.browserPathTitle
import dev.qtremors.arcile.core.ui.ArcileTopBar
import dev.qtremors.arcile.core.ui.ArcileTopBarMenuAction
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
    appStartPage: AppStartPage?,
    onAppStartPageChange: (AppStartPage) -> Unit,
    onBackClick: () -> Unit,
    onSelectionChanged: () -> Unit,
    workspaceTabs: @Composable () -> Unit,
    workspaceTabsVisible: Boolean,
    workspaceTabsEnabled: Boolean,
    onWorkspaceTabsEnabledChange: ((Boolean) -> Unit)?,
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
                placeholder = searchPlaceholder,
                filtersActive = state.activeSearchFilters.hasActiveFilters
            )

            ActiveFiltersRow(
                filters = state.activeSearchFilters,
                onClearFilter = { clearedFilters -> searchIntents.onSearchFiltersChange(clearedFilters) }
            )
            CollapsingWorkspaceTabs(workspaceTabsVisible, workspaceTabs)
        }
    } else {
        Column {
            val selectedSizeFormatted = if (state.selectedFiles.isNotEmpty()) {
                formatFileSize(state.selectedFilesTotalSize)
            } else {
                null
            }
            val browseTitle = stringResource(R.string.browse_title)
            val rootStorageTitle = stringResource(R.string.root_storage)
            val currentPathTitle = browserPathTitle(
                path = state.currentPath,
                isRootStorageScope = state.isRootStorageScope,
                volumeName = state.displayState.currentVolume
                    ?.takeIf { it.path == state.currentPath }
                    ?.name,
                fallback = browseTitle,
                rootStorageTitle = rootStorageTitle
            )

            ArcileTopBar(
                title = when {
                    state.archiveContext != null -> state.archiveContext.archiveName
                    state.isCategoryScreen -> state.activeCategoryName
                    else -> currentPathTitle
                },
                selectionCount = state.selectedFiles.size,
                selectedSize = selectedSizeFormatted,
                scrollBehavior = scrollBehavior,
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
                menuActions = buildList {
                    if (onWorkspaceTabsEnabledChange != null) {
                        add(
                            ArcileTopBarMenuAction(
                                label = stringResource(R.string.browser_tabs),
                                icon = Icons.Default.Tab,
                                selected = workspaceTabsEnabled,
                                onClick = {
                                    onWorkspaceTabsEnabledChange(!workspaceTabsEnabled)
                                }
                            )
                        )
                    }
                    if (appStartPage != null) {
                        add(
                            ArcileTopBarMenuAction(
                                label = stringResource(R.string.home_title),
                                icon = Icons.Default.Home,
                                selected = appStartPage == AppStartPage.HOME,
                                onClick = { onAppStartPageChange(AppStartPage.HOME) }
                            )
                        )
                        add(
                            ArcileTopBarMenuAction(
                                label = stringResource(R.string.browse_title),
                                icon = Icons.Default.Folder,
                                selected = appStartPage == AppStartPage.BROWSER,
                                onClick = { onAppStartPageChange(AppStartPage.BROWSER) }
                            )
                        )
                    }
                },
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
                                    val label = currentPathTitle
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
            CollapsingWorkspaceTabs(workspaceTabsVisible, workspaceTabs)
        }
    }
}

@Composable
private fun CollapsingWorkspaceTabs(
    visible: Boolean,
    content: @Composable () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { -it } +
            expandVertically(expandFrom = Alignment.Top) +
            fadeIn(),
        exit = slideOutVertically { -it } +
            shrinkVertically(shrinkTowards = Alignment.Top) +
            fadeOut()
    ) {
        content()
    }
}
