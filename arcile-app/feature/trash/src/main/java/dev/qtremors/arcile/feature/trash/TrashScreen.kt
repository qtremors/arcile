package dev.qtremors.arcile.feature.trash

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import dev.qtremors.arcile.core.ui.theme.spacing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.theme.bounceClickable
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.qtremors.arcile.core.ui.SearchTopBar
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width


import kotlinx.coroutines.delay
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.feature.trash.TrashFilter
import dev.qtremors.arcile.feature.trash.TrashPropertiesUiModel
import dev.qtremors.arcile.feature.trash.TrashState
import dev.qtremors.arcile.feature.trash.TrashSortOption
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.feature.trash.ui.EmptyTrashDialog
import dev.qtremors.arcile.feature.trash.ui.TrashList

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.presentation.UiText

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun TrashScreen(
    state: TrashState,
    navigationActions: TrashNavigationActions,
    selectionActions: TrashSelectionActions,
    restoreActions: TrashRestoreActions,
    deleteActions: TrashDeleteActions,
    presentationActions: TrashPresentationActions,
    feedbackActions: TrashFeedbackActions
) {
    val onNavigateBack = navigationActions.navigateBack
    val onToggleSelection = selectionActions.toggle
    val onClearSelection = selectionActions.clear
    val onSelectAll = selectionActions.selectAll
    val onOpenProperties = selectionActions.openProperties
    val onDismissProperties = selectionActions.dismissProperties
    val onRestoreSelected = restoreActions.restoreSelected
    val onDismissDestinationPicker = restoreActions.dismissDestinationPicker
    val onRestoreToDestination = restoreActions.restoreToDestination
    val onUndoLastRestore = restoreActions.undoLastRestore
    val onClearPendingRestoreUndo = restoreActions.clearPendingUndo
    val onEmptyTrash = deleteActions.emptyTrash
    val onPermanentlyDeleteSelected = deleteActions.permanentlyDeleteSelected
    val onDismissPermanentDelete = deleteActions.dismissPermanentDelete
    val onSearchQueryChange = presentationActions.searchQueryChange
    val onClearSearch = presentationActions.clearSearch
    val onSortChange = presentationActions.sortChange
    val onFilterChange = presentationActions.filterChange
    val onRefresh = presentationActions.refresh
    val onClearError = feedbackActions.clearError
    val onClearSnackbarMessage = feedbackActions.clearSnackbarMessage
    val onFeedback = feedbackActions.feedback
    val haptics = rememberArcileHaptics()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (state.isLoading) {
            delay(150)
            showLoading = true
        } else {
            showLoading = false
        }
    }

    var showEmptyTrashConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotBlank()) }

    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            haptics.error()
            onFeedback(ArcileFeedbackEvent(errorMsg, ArcileFeedbackSeverity.Error))
            onClearError()
        }
    }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            onFeedback(
                ArcileFeedbackEvent(
                    message = message,
                    severity = ArcileFeedbackSeverity.Success,
                    actionLabel = state.pendingRestoreUndoPaths.takeIf { it.isNotEmpty() }?.let {
                        UiText.StringResource(R.string.undo)
                    },
                    onAction = state.pendingRestoreUndoPaths.takeIf { it.isNotEmpty() }?.let {
                        { onUndoLastRestore() }
                    },
                    onDismiss = state.pendingRestoreUndoPaths.takeIf { it.isNotEmpty() }?.let {
                        { onClearPendingRestoreUndo() }
                    }
                )
            )
            onClearSnackbarMessage()
        }
    }

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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        (if (isSelectionMode) MaterialTheme.spacing.toolbarBottomGap else MaterialTheme.spacing.screenGutter)
    var showSortDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection),
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
                if (showSearchBar) {
                    SearchTopBar(
                        query = state.searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = {
                            showSearchBar = false
                            onClearSearch()
                        },
                        placeholder = stringResource(R.string.search_trash_placeholder)
                    )
                } else {
                    LargeTopAppBar(
                        title = {
                            Text(
                                text = if (isSelectionMode) stringResource(R.string.selected_count, state.selectedFiles.size) else stringResource(R.string.trash_bin),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        scrollBehavior = scrollBehavior,
                        navigationIcon = {
                            if (isSelectionMode) {
                                IconButton(
                                    onClick = onClearSelection,
                                    modifier = Modifier.clip(CircleShape)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                                }
                            } else {
                                IconButton(
                                    onClick = onNavigateBack,
                                    modifier = Modifier.clip(CircleShape)
                                ) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                                }
                            }
                        },
                        actions = {
                            if (!isSelectionMode) {
                                val topActions = listOf(
                                    dev.qtremors.arcile.core.ui.ToolbarAction(
                                        icon = Icons.Default.Search,
                                        contentDescription = stringResource(R.string.action_search),
                                        onClick = { showSearchBar = true }
                                    ),
                                    dev.qtremors.arcile.core.ui.ToolbarAction(
                                        icon = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = stringResource(R.string.action_sort),
                                        onClick = { showSortDialog = true }
                                    )
                                )
                                dev.qtremors.arcile.core.ui.SplitButtonGroup(actions = topActions)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = if (isSelectionMode) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }
        },
        bottomBar = {},
        floatingActionButton = {
            if (!isSelectionMode && state.trashFiles.isNotEmpty() && !showSearchBar) {
                ExtendedFloatingActionButton(
                    modifier = Modifier.navigationBarsPadding(),
                    text = { Text(stringResource(R.string.empty_trash)) },
                    icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null) },
                    onClick = { showEmptyTrashConfirmation = true },
                    shape = ExpressiveShapes.large,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    expanded = true
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
            ) {
            if (!showSearchBar && state.trashFiles.isNotEmpty()) {
                TrashFilterRow(
                    selected = state.filter,
                    onFilterChange = onFilterChange
                )
            }
            if (!isSelectionMode && !showSearchBar && state.trashFiles.isNotEmpty()) {
                TrashInfoCard()
            }

            if (onRefresh != null) {
                val pullRefreshState = rememberPullToRefreshState()
                PullToRefreshBox(
                    isRefreshing = state.isLoading,
                    onRefresh = onRefresh,
                    state = pullRefreshState,
                    modifier = Modifier.weight(1f),
                    indicator = {
                        ArcilePullRefreshIndicator(
                            isRefreshing = state.isLoading,
                            state = pullRefreshState
                        )
                    }
                ) {
                    TrashBody(
                        state = state,
                        showLoading = showLoading,
                        showSearchBar = showSearchBar,
                        bottomContentPadding = bottomContentPadding,
                        onToggleSelection = onToggleSelection
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1f)) {
                    TrashBody(
                        state = state,
                        showLoading = showLoading,
                        showSearchBar = showSearchBar,
                        bottomContentPadding = bottomContentPadding,
                        onToggleSelection = onToggleSelection
                    )
                }
            }
            }

            TrashSelectionToolbar(
                isVisible = isSelectionMode,
                isBackPredicting = isBackPredicting,
                backProgress = backProgress,
                contentPadding = padding,
                actions = TrashSelectionToolbarActions(
                    selectAll = onSelectAll,
                    restore = onRestoreSelected,
                    deletePermanently = onPermanentlyDeleteSelected,
                    openProperties = onOpenProperties
                )
            )
        }

        if (showEmptyTrashConfirmation) {
            EmptyTrashDialog(
                onDismissRequest = { showEmptyTrashConfirmation = false },
                onConfirm = {
                    showEmptyTrashConfirmation = false
                    onEmptyTrash()
                }
            )
        }

        if (state.showPermanentDeleteConfirmation) {
            dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog(
                selectedCount = state.selectedFiles.size,
                isPermanentDeleteChecked = true,
                isPermanentDeleteToggleEnabled = false,
                onConfirm = onPermanentlyDeleteSelected,
                onDismiss = onDismissPermanentDelete,
                onTogglePermanentDelete = {}
            )
        }

        if (state.showDestinationPicker) {
            RestoreDestinationDialog(
                state = state,
                onDismiss = onDismissDestinationPicker,
                onRestore = onRestoreToDestination
            )
        }

        if (showSortDialog) {
            TrashSortDialog(
                selected = state.sortOption,
                onDismiss = { showSortDialog = false },
                onSelect = {
                    onSortChange(it)
                    showSortDialog = false
                }
            )
        }

        if (state.isPropertiesVisible) {
            TrashPropertiesDialog(
                properties = state.properties,
                onDismiss = onDismissProperties
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TrashBody(
    state: TrashState,
    showLoading: Boolean,
    showSearchBar: Boolean,
    bottomContentPadding: androidx.compose.ui.unit.Dp,
    onToggleSelection: (String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (showLoading && state.trashFiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else if (state.trashFiles.isEmpty() && !state.isLoading && !showSearchBar) {
            EmptyState(
                variant = EmptyStateVariant.Trash,
                title = stringResource(R.string.trash_is_empty),
                description = stringResource(R.string.trash_empty_description),
                modifier = Modifier.fillMaxSize()
            )
        } else if (showSearchBar && state.isSearching) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LoadingIndicator()
            }
        } else if (showSearchBar && state.searchQuery.isNotEmpty() && state.searchResults.isEmpty()) {
            EmptyState(
                variant = EmptyStateVariant.Search,
                title = stringResource(R.string.no_results_found),
                description = stringResource(R.string.no_results_description, state.searchQuery),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            TrashList(
                files = if (showSearchBar) state.searchResults else state.visibleTrashFiles,
                selectedFiles = state.selectedFiles,
                onToggleSelection = onToggleSelection,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    bottom = bottomContentPadding
                )
            )
        }
    }
}

@Composable
internal fun TrashInfoCard() {
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = androidx.compose.material3.CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.trash_info_title),
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.trash_info_description),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

