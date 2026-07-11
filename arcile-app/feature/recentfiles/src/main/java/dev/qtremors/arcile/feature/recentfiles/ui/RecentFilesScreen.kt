package dev.qtremors.arcile.feature.recentfiles.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import dev.qtremors.arcile.core.ui.theme.spacing
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.feature.recentfiles.RecentFilesState
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.SearchFiltersSheet
import dev.qtremors.arcile.core.ui.SearchTopBar
import dev.qtremors.arcile.core.ui.SortOptionDialog
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.lists.ActiveFiltersRow
import dev.qtremors.arcile.core.ui.lists.FileGrid
import dev.qtremors.arcile.core.ui.lists.FileItemRow
import dev.qtremors.arcile.core.ui.lists.FileList
import dev.qtremors.arcile.core.ui.rememberDateFormatter
import kotlinx.coroutines.delay
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun RecentFilesScreen(
    state: RecentFilesState,
    navigationActions: RecentNavigationActions,
    selectionActions: RecentSelectionActions,
    deleteActions: RecentDeleteActions,
    searchActions: RecentSearchActions,
    contentActions: RecentContentActions
) {
    val onNavigateBack = navigationActions.navigateBack
    val onOpenFile = navigationActions.openFile
    val onOpenContainingFolder = navigationActions.openContainingFolder
    val onToggleSelection = selectionActions.toggle
    val onClearSelection = selectionActions.clear
    val onShareSelected = selectionActions.share
    val onSelectAll = selectionActions.selectAll
    val onSelectMultiple = selectionActions.selectMultiple
    val onOpenProperties = selectionActions.openProperties
    val onDismissProperties = selectionActions.dismissProperties
    val onRequestDeleteSelected = deleteActions.request
    val onConfirmDelete = deleteActions.confirm
    val onTogglePermanentDelete = deleteActions.togglePermanent
    val onToggleShred = deleteActions.toggleShred
    val onDismissDeleteConfirmation = deleteActions.dismissConfirmation
    val onSearchQueryChange = searchActions.queryChange
    val onClearSearch = searchActions.clear
    val onSearchFiltersChange = searchActions.filtersChange
    val onPresentationChange = searchActions.presentationChange
    val onLoadMore = searchActions.loadMore
    val onRefresh = contentActions.refresh
    val onClearError = contentActions.clearError
    val onFeedback = contentActions.feedback
    val haptics = rememberArcileHaptics()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)

    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            haptics.error()
            onFeedback(ArcileFeedbackEvent(errorMsg, ArcileFeedbackSeverity.Error))
            onClearError()
        }
    }

    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }

    var backProgress by remember { mutableStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }

    PredictiveBackHandler(enabled = isSelectionMode || showSearchBar) { progressFlow ->
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            if (isSelectionMode) {
                onClearSelection()
            } else {
                showSearchBar = false
                onClearSearch()
            }
        } catch (e: Exception) {
            // Cancelled
        } finally {
            isBackPredicting = false
            backProgress = 0f
        }
    }

    val filesToDisplay = if (showSearchBar) state.searchResults else state.displayedRecentFiles
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {},
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (isBackPredicting) {
                            translationY = -backProgress * size.height.toFloat()
                            alpha = 1f - backProgress
                        }
                    }
            ) {
                when {
                    isSelectionMode -> RecentSelectionTopBar(
                        selectedCount = state.selectedFiles.size,
                        selectedSize = dev.qtremors.arcile.core.presentation.formatFileSize(state.selectedFilesTotalSize),
                        onClearSelection = onClearSelection
                    )
                    showSearchBar -> Column {
                        SearchTopBar(
                            query = state.searchQuery,
                            onQueryChange = onSearchQueryChange,
                            onClose = {
                                showSearchBar = false
                                onClearSearch()
                            },
                            onFilterClick = { showFilterSheet = true },
                            placeholder = stringResource(R.string.search_recent_files_placeholder)
                        )
                        ActiveFiltersRow(
                            filters = state.activeSearchFilters,
                            onClearFilter = onSearchFiltersChange
                        )
                    }
                    else -> Column {
                        TopAppBar(
                            title = { Text(stringResource(R.string.recent_files_title)) },
                            navigationIcon = {
                                IconButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.clip(CircleShape)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            },
                            actions = {
                                SplitButtonGroup(
                                    actions = listOf(
                                        ToolbarAction(
                                            icon = Icons.Default.Search,
                                            contentDescription = stringResource(R.string.action_search),
                                            onClick = { showSearchBar = true }
                                        ),
                                        ToolbarAction(
                                            icon = Icons.AutoMirrored.Filled.Sort,
                                            contentDescription = stringResource(R.string.action_sort),
                                            onClick = { showPresentationSheet = true }
                                        )
                                    )
                                )
                            },
                            scrollBehavior = scrollBehavior
                        )
                    }
                }
            }
        }
    ) { padding ->
        var showLoading by remember { mutableStateOf(false) }
        LaunchedEffect(state.isLoading) {
            if (state.isLoading) {
                delay(150)
                showLoading = true
            } else {
                showLoading = false
            }
        }

        val pullRefreshState = rememberPullToRefreshState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PullToRefreshBox(
                isRefreshing = state.isPullToRefreshing,
                onRefresh = onRefresh,
                state = pullRefreshState,
                modifier = Modifier.fillMaxSize(),
                indicator = {
                    ArcilePullRefreshIndicator(
                        isRefreshing = state.isPullToRefreshing,
                        state = pullRefreshState
                    )
                }
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        showLoading && state.recentFiles.isEmpty() && !state.isPullToRefreshing -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                        state.recentFiles.isEmpty() && !state.isLoading && !showSearchBar -> {
                            EmptyState(
                                variant = EmptyStateVariant.Recent,
                                title = stringResource(R.string.no_recent_files),
                                description = stringResource(R.string.no_recent_files_description),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        showSearchBar && state.isSearching -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                        showSearchBar && state.searchQuery.isNotEmpty() && state.searchResults.isEmpty() -> {
                            EmptyState(
                                variant = EmptyStateVariant.Search,
                                title = stringResource(R.string.no_results_found),
                                description = stringResource(R.string.no_results_description, state.searchQuery),
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            RecentFilesContent(
                                state = state,
                                filesToDisplay = filesToDisplay,
                                showSearchBar = showSearchBar,
                                formatter = formatter,
                                todayLabel = todayLabel,
                                yesterdayLabel = yesterdayLabel,
                                contentPadding = PaddingValues(),
                                onOpenFile = onOpenFile,
                                onToggleSelection = onToggleSelection,
                                onSelectMultiple = onSelectMultiple,
                                onLoadMore = onLoadMore
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (isBackPredicting && isSelectionMode) {
                                    translationY = backProgress * 150.dp.toPx()
                                    alpha = 1f - backProgress
                                }
                            }
                    ) {
                        RecentSelectionToolbar(
                            isVisible = isSelectionMode,
                            selectedFiles = state.selectedFiles,
                            contentPadding = PaddingValues(),
                            onSelectAll = onSelectAll,
                            onShareSelected = onShareSelected,
                            onRequestDeleteSelected = onRequestDeleteSelected,
                            onOpenProperties = onOpenProperties,
                            onOpenContainingFolder = onOpenContainingFolder
                        )
                    }
                }
            }
        }
    }

    if (state.showTrashConfirmation || state.showPermanentDeleteConfirmation) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked = state.isPermanentDeleteChecked,
            isPermanentDeleteToggleEnabled = state.isPermanentDeleteToggleEnabled,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDeleteConfirmation,
            onTogglePermanentDelete = onTogglePermanentDelete,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = onToggleShred
        )
    }

    if (state.showMixedDeleteExplanation) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked = true,
            isPermanentDeleteToggleEnabled = false,
            onConfirm = {},
            onDismiss = onDismissDeleteConfirmation,
            onTogglePermanentDelete = {},
            decision = state.deleteDecision
        )
    }

    if (state.isPropertiesVisible) {
        PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = {
                onDismissProperties()
                onClearSelection()
            }
        )
    }

    if (showFilterSheet) {
        SearchFiltersSheet(
            currentFilters = state.activeSearchFilters,
            onApplyFilters = onSearchFiltersChange,
            onDismiss = { showFilterSheet = false },
            showCategoryFilter = true
        )
    }

    if (showPresentationSheet) {
        SortOptionDialog(
            title = stringResource(R.string.recent_sort_title),
            selectedPreferences = state.presentation,
            showApplyToSubfolders = false,
            onDismiss = { showPresentationSheet = false },
            onApply = { preferences, _ -> onPresentationChange(preferences) },
            minDateMillis = state.activeSearchFilters.minDateMillis,
            maxDateMillis = state.activeSearchFilters.maxDateMillis,
            onDateRangeChange = { minDate, maxDate ->
                onSearchFiltersChange(state.activeSearchFilters.copy(minDateMillis = minDate, maxDateMillis = maxDate))
            },
            minSize = state.activeSearchFilters.minSize,
            maxSize = state.activeSearchFilters.maxSize,
            onSizeRangeChange = { minSizeVal, maxSizeVal ->
                onSearchFiltersChange(state.activeSearchFilters.copy(minSize = minSizeVal, maxSize = maxSizeVal))
            }
        )
    }

}
