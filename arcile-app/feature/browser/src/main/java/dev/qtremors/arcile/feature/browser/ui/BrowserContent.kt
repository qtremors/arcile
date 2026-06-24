package dev.qtremors.arcile.feature.browser.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.image.ArchiveEntryThumbnailData
import dev.qtremors.arcile.shared.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.shared.ui.Breadcrumbs
import dev.qtremors.arcile.shared.ui.EmptyState
import dev.qtremors.arcile.shared.ui.EmptyStateVariant
import dev.qtremors.arcile.shared.ui.FolderTabsRow
import dev.qtremors.arcile.shared.ui.lists.FileGridRows
import dev.qtremors.arcile.shared.ui.lists.FileItemRow
import dev.qtremors.arcile.shared.ui.lists.FileListRows
import dev.qtremors.arcile.shared.ui.lists.VolumeRootList
import dev.qtremors.arcile.shared.ui.rememberDateOnlyFormatter
import dev.qtremors.arcile.shared.ui.scrollbar.ArcileFastScrollbar
import dev.qtremors.arcile.shared.ui.scrollbar.LazyGridScrollbarState
import dev.qtremors.arcile.shared.ui.scrollbar.LazyListScrollbarState
import dev.qtremors.arcile.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.ui.theme.bounceClickable
import dev.qtremors.arcile.ui.theme.spacing
import java.util.Date
import kotlin.math.abs

private data class BrowserContentKey(
    val isSearch: Boolean,
    val path: String,
    val category: String?,
    val isRoot: Boolean
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun BrowserContent(
    state: BrowserState,
    displayedFiles: List<FileModel>,
    currentPresentation: BrowserPresentationPreferences,
    showSearchBar: Boolean,
    showLoading: Boolean,
    isRefreshing: Boolean,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    scaffoldPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    listState: LazyListState,
    gridState: LazyGridState,
    actions: BrowserUiActions,
    onShowSearchBarChange: (Boolean) -> Unit,
    onSwitchCategoryFolderTab: (Int) -> Unit
) {
    val categoryFolderTabs = state.displayState.categoryFolderTabs
    val selectedCategoryFolderTabIndex = state.displayState.selectedCategoryFolderTabIndex
    val searchHasCompleted = showSearchBar && state.browserSearchQuery.isNotEmpty() && !state.isSearching
    val targetKey = BrowserContentKey(
        isSearch = searchHasCompleted,
        path = state.currentPath,
        category = state.activeCategoryName,
        isRoot = state.isVolumeRootScreen
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = scaffoldPadding.calculateTopPadding())
    ) {
        if (!targetKey.isSearch && !(showSearchBar && state.isSearching)) {
            if (state.archiveContext != null) {
                ArchiveBreadcrumbs(
                    archiveName = state.archiveContext.archiveName,
                    entryPrefix = state.archiveContext.entryPrefix,
                    onArchiveRootClick = { actions.onNavigateTo(state.archiveContext.archivePath) },
                    onEntryClick = {
                        actions.onNavigateTo(
                            ArchiveEntryThumbnailData.virtualPath(state.archiveContext.archivePath, it)
                        )
                    }
                )
            } else if (!state.isVolumeRootScreen && !state.isCategoryScreen) {
                Breadcrumbs(
                    currentPath = state.currentPath,
                    storageVolumes = state.storageVolumes,
                    onPathSegmentClick = { path -> actions.onNavigateTo(path) }
                )
            }

            val currentVolume = state.displayState.currentVolume
            if (currentVolume?.kind == StorageKind.OTG || currentVolume?.kind == StorageKind.EXTERNAL_UNCLASSIFIED) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    androidx.compose.material3.Text(
                        text = if (currentVolume.kind == StorageKind.OTG) {
                            stringResource(R.string.browsing_temp_usb)
                        } else {
                            stringResource(R.string.browsing_unclassified)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            if (state.isCategoryScreen) {
                FolderTabsRow(
                    tabs = categoryFolderTabs,
                    selectedPath = state.selectedFolderTabPath,
                    onSelectTab = actions.onSelectFolderTab
                )
            }
        }

        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = actions.onRefresh,
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(
                    if (state.isCategoryScreen && categoryFolderTabs.size > 1 && !targetKey.isSearch && !showSearchBar) {
                        Modifier.pointerInput(categoryFolderTabs, selectedCategoryFolderTabIndex) {
                            var horizontalDrag = 0f
                            detectHorizontalDragGestures(
                                onDragStart = { horizontalDrag = 0f },
                                onHorizontalDrag = { change, dragAmount ->
                                    horizontalDrag += dragAmount
                                    if (abs(horizontalDrag) > 96f) {
                                        change.consume()
                                        onSwitchCategoryFolderTab(if (horizontalDrag < 0f) 1 else -1)
                                        horizontalDrag = 0f
                                    }
                                },
                                onDragEnd = { horizontalDrag = 0f },
                                onDragCancel = { horizontalDrag = 0f }
                            )
                        }
                    } else {
                        Modifier
                    }
                ),
            indicator = {
                ArcilePullRefreshIndicator(
                    isRefreshing = isRefreshing,
                    state = pullRefreshState
                )
            }
        ) {
            when {
                targetKey.isSearch -> {
                    BrowserSearchResults(
                        state = state,
                        currentPresentation = currentPresentation,
                        actions = actions,
                        onShowSearchBarChange = onShowSearchBarChange
                    )
                }
                showSearchBar && state.isSearching -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                }
                else -> {
                    BrowserListingContent(
                        state = state,
                        displayedFiles = displayedFiles,
                        currentPresentation = currentPresentation,
                        showLoading = showLoading,
                        isRefreshing = isRefreshing,
                        bottomContentPadding = bottomContentPadding,
                        scaffoldPadding = scaffoldPadding,
                        layoutDirection = layoutDirection,
                        listState = listState,
                        gridState = gridState,
                        actions = actions
                    )
                }
            }
        }
    }
}

@Composable
private fun ArchiveBreadcrumbs(
    archiveName: String,
    entryPrefix: String?,
    onArchiveRootClick: () -> Unit,
    onEntryClick: (String) -> Unit
) {
    val segments = entryPrefix
        ?.trim('/')
        ?.split('/')
        ?.filter { it.isNotBlank() }
        .orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = archiveName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(ExpressiveShapes.medium)
                .bounceClickable(onClick = onArchiveRootClick)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
        var running = ""
        segments.forEach { segment ->
            Text("/", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            running = if (running.isBlank()) segment else "$running/$segment"
            val target = running
            Text(
                text = segment,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(ExpressiveShapes.medium)
                    .bounceClickable { onEntryClick(target) }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun BrowserSearchResults(
    state: BrowserState,
    currentPresentation: BrowserPresentationPreferences,
    actions: BrowserUiActions,
    onShowSearchBarChange: (Boolean) -> Unit
) {
    if (state.searchResults.isEmpty()) {
        EmptyState(
            variant = EmptyStateVariant.Search,
            title = stringResource(R.string.no_results_found),
            description = stringResource(R.string.no_results_description, state.browserSearchQuery),
            modifier = Modifier.fillMaxSize()
        )
    } else {
        val formatter = rememberDateOnlyFormatter()
        val searchListState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = searchListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    bottom = WindowInsets.navigationBars
                        .asPaddingValues()
                        .calculateBottomPadding() + MaterialTheme.spacing.screenGutter
                )
            ) {
                items(
                    items = state.searchResults,
                    key = { it.absolutePath },
                    contentType = { if (it.isDirectory) "directory" else "file" }
                ) { file ->
                    FileItemRow(
                        file = file,
                        formattedDate = formatter.format(Date(file.lastModified)),
                        isSelected = false,
                        showThumbnails = currentPresentation.showThumbnails,
                        onClick = {
                            onShowSearchBarChange(false)
                            actions.onClearSearch()
                            if (file.isDirectory) {
                                actions.onNavigateTo(file.absolutePath)
                            } else if (state.archiveContext == null) {
                                actions.onOpenFile(file.absolutePath)
                            }
                        },
                        onLongClick = {}
                    )
                }
            }
            ArcileFastScrollbar(
                scrollbarState = LazyListScrollbarState(searchListState),
                labelForIndex = { index -> state.searchResults.getOrNull(index)?.let { formatter.format(Date(it.lastModified)) }.orEmpty() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                enabled = state.browserScrollbarEnabled
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BrowserListingContent(
    state: BrowserState,
    displayedFiles: List<FileModel>,
    currentPresentation: BrowserPresentationPreferences,
    showLoading: Boolean,
    isRefreshing: Boolean,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    scaffoldPadding: PaddingValues,
    layoutDirection: LayoutDirection,
    listState: LazyListState,
    gridState: LazyGridState,
    actions: BrowserUiActions
) {
    if (showLoading && state.files.isEmpty() && !isRefreshing) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            LoadingIndicator()
        }
    } else if (state.isVolumeRootScreen) {
        VolumeRootList(
            volumes = state.storageVolumes,
            onNavigateTo = actions.onNavigateTo,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = 8.dp,
                bottom = bottomContentPadding,
                start = scaffoldPadding.calculateLeftPadding(layoutDirection),
                end = scaffoldPadding.calculateRightPadding(layoutDirection)
            )
        )
    } else if (displayedFiles.isEmpty() && !state.isLoading) {
        EmptyState(
            variant = EmptyStateVariant.Folder,
            title = stringResource(R.string.empty_directory),
            description = if (state.isCategoryScreen && state.selectedFolderTabPath != null) {
                stringResource(R.string.empty_folder_tab_description)
            } else {
                stringResource(R.string.empty_directory_description)
            },
            modifier = Modifier.fillMaxSize()
        )
    } else if (state.browserViewMode == BrowserViewMode.GRID) {
        Box(modifier = Modifier.fillMaxSize()) {
            FileGridRows(
                rows = state.displayState.visibleGridRows,
                selectedFiles = state.selectedFiles,
                onNavigateTo = actions.onNavigateTo,
                onOpenFile = if (state.archiveContext == null) actions.onOpenFile else { _ -> },
                onToggleSelection = actions.onToggleSelection,
                onSelectMultiple = actions.onSelectMultiple,
                showThumbnails = currentPresentation.showThumbnails,
                thumbnailLoadingPaused = state.activeFileOperation?.terminalStatus == null && state.activeFileOperation != null,
                modifier = Modifier.fillMaxSize(),
                gridState = gridState,
                minCellSize = state.browserGridMinCellSize.dp,
                folderStatsLoadingPaths = state.folderStatsLoadingPaths,
                openImageFromThumbnailInSelectionMode = state.archiveContext == null,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = bottomContentPadding,
                    start = 8.dp,
                    end = 8.dp
                )
            )
            val formatter = rememberDateOnlyFormatter()
            ArcileFastScrollbar(
                scrollbarState = LazyGridScrollbarState(gridState),
                labelForIndex = { index -> state.displayState.visibleFiles.getOrNull(index)?.let { formatter.format(Date(it.lastModified)) }.orEmpty() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                enabled = state.browserScrollbarEnabled
            )
        }
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            FileListRows(
                rows = state.displayState.visibleListRows,
                selectedFiles = state.selectedFiles,
                onNavigateTo = actions.onNavigateTo,
                onOpenFile = if (state.archiveContext == null) actions.onOpenFile else { _ -> },
                onToggleSelection = actions.onToggleSelection,
                onSelectMultiple = actions.onSelectMultiple,
                showThumbnails = currentPresentation.showThumbnails,
                thumbnailLoadingPaused = state.activeFileOperation?.terminalStatus == null && state.activeFileOperation != null,
                modifier = Modifier.fillMaxSize(),
                listState = listState,
                zoom = state.browserListZoom,
                folderStatsLoadingPaths = state.folderStatsLoadingPaths,
                openImageFromThumbnailInSelectionMode = state.archiveContext == null,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = bottomContentPadding
                )
            )
            val formatter = rememberDateOnlyFormatter()
            ArcileFastScrollbar(
                scrollbarState = LazyListScrollbarState(listState),
                labelForIndex = { index -> state.displayState.visibleFiles.getOrNull(index)?.let { formatter.format(Date(it.lastModified)) }.orEmpty() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight(),
                contentPadding = PaddingValues(bottom = bottomContentPadding),
                enabled = state.browserScrollbarEnabled
            )
        }
    }
}
