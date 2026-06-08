package dev.qtremors.arcile.feature.imagegallery

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.shared.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.shared.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.shared.ui.EmptyState
import dev.qtremors.arcile.shared.ui.EmptyStateVariant
import dev.qtremors.arcile.shared.ui.SearchTopBar
import dev.qtremors.arcile.shared.ui.SplitButtonGroup
import dev.qtremors.arcile.shared.ui.SortOptionDialog
import dev.qtremors.arcile.shared.ui.ToolbarAction
import dev.qtremors.arcile.shared.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.shared.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.shared.ui.lists.FileGrid
import dev.qtremors.arcile.shared.ui.lists.FileList
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.utils.formatFileSize
import kotlinx.coroutines.flow.SharedFlow

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ImageGalleryScreen(
    state: ImageGalleryState,
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    onShareSelected: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onConfirmDelete: () -> Unit,
    onTogglePermanentDelete: () -> Unit,
    onToggleShred: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onOpenProperties: () -> Unit,
    onDismissProperties: () -> Unit,
    onRefresh: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onPresentationChange: (BrowserPresentationPreferences) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onClearError: () -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit = {},
    nativeRequestFlow: SharedFlow<android.content.IntentSender>? = null
) {
    val haptics = rememberArcileHaptics()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }
    var showOverflowMenu by rememberSaveable { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            onConfirmDelete()
        }
    }

    LaunchedEffect(nativeRequestFlow) {
        nativeRequestFlow?.collect { sender ->
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            haptics.error()
            onFeedback(ArcileFeedbackEvent(error, ArcileFeedbackSeverity.Error))
            onClearError()
        }
    }

    BackHandler(enabled = isSelectionMode || showSearchBar) {
        if (isSelectionMode) {
            onClearSelection()
        } else {
            showSearchBar = false
            onClearSearch()
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            when {
                isSelectionMode -> ImageGallerySelectionTopBar(
                    selectedCount = state.selectedFiles.size,
                    selectedSize = formatFileSize(
                        state.files.filter { state.selectedFiles.contains(it.absolutePath) }.sumOf { it.size }
                    ),
                    onClearSelection = onClearSelection
                )
                showSearchBar -> SearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = {
                        showSearchBar = false
                        onClearSearch()
                    },
                    placeholder = stringResource(R.string.image_gallery_search_placeholder)
                )
                else -> TopAppBar(
                    title = { Text(stringResource(R.string.image_gallery_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
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
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_more_options)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflowMenu,
                            onDismissRequest = { showOverflowMenu = false }
                        ) {
                            if (state.presentation.viewMode == BrowserViewMode.GRID) {
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(stringResource(R.string.image_gallery_show_file_details))
                                            Text(
                                                text = stringResource(R.string.image_gallery_show_file_details_description),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    trailingIcon = {
                                        Switch(
                                            checked = state.showFileDetails,
                                            onCheckedChange = null
                                        )
                                    },
                                    onClick = {
                                        onShowFileDetailsChange(!state.showFileDetails)
                                        showOverflowMenu = false
                                    }
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            ImageGalleryContent(
                state = state,
                contentPadding = padding,
                onOpenFile = onOpenFile,
                onToggleSelection = onToggleSelection,
                onSelectMultiple = onSelectMultiple,
                onSelectAlbum = onSelectAlbum,
                onRefresh = onRefresh
            )

            ImageGallerySelectionBar(
                isVisible = isSelectionMode,
                contentPadding = padding,
                onSelectAll = onSelectAll,
                onShareSelected = onShareSelected,
                onRequestDeleteSelected = onRequestDeleteSelected,
                onOpenProperties = onOpenProperties
            )
        }
    }

    if (state.showTrashConfirmation || state.showPermanentDeleteConfirmation || state.showMixedDeleteExplanation) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked = state.isPermanentDeleteChecked || state.showMixedDeleteExplanation,
            isPermanentDeleteToggleEnabled = state.isPermanentDeleteToggleEnabled && !state.showMixedDeleteExplanation,
            onConfirm = if (state.showMixedDeleteExplanation) ({}) else onConfirmDelete,
            onDismiss = onDismissDeleteConfirmation,
            onTogglePermanentDelete = onTogglePermanentDelete,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = onToggleShred
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

    if (showPresentationSheet) {
        SortOptionDialog(
            title = stringResource(R.string.image_gallery_view_sort_title),
            selectedPreferences = state.presentation,
            showApplyToSubfolders = false,
            onDismiss = { showPresentationSheet = false },
            onApply = { preferences, _ -> onPresentationChange(preferences) }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ImageGalleryContent(
    state: ImageGalleryState,
    contentPadding: PaddingValues,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    onSelectAlbum: (String?) -> Unit,
    onRefresh: () -> Unit
) {
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val gridStatesByAlbum = remember { mutableMapOf<String, LazyGridState>() }
    val listStatesByAlbum = remember { mutableMapOf<String, LazyListState>() }
    val albumScrollKey = state.selectedAlbumPath ?: "__all__"
    val topPadding = contentPadding.calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        if (state.selectedFiles.isNotEmpty()) MaterialTheme.spacing.toolbarBottomGap else MaterialTheme.spacing.screenGutter

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            ArcilePullRefreshIndicator(
                isRefreshing = state.isRefreshing,
                state = pullRefreshState
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding)
        ) {
            ImageGalleryAlbumChips(
                state = state,
                onSelectAlbum = onSelectAlbum
            )

            when {
                state.isLoading && state.files.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LoadingIndicator()
                    }
                }
                state.files.isEmpty() && state.searchQuery.isBlank() -> {
                    EmptyState(
                        variant = EmptyStateVariant.Search,
                        title = stringResource(R.string.image_gallery_empty_title),
                        description = stringResource(R.string.image_gallery_empty_description),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                state.displayedFiles.isEmpty() -> {
                    EmptyState(
                        variant = EmptyStateVariant.Search,
                        title = stringResource(R.string.no_results_found),
                        description = stringResource(R.string.no_results_description, state.searchQuery),
                        modifier = Modifier.fillMaxSize()
                    )
                }
                else -> {
                    if (state.presentation.viewMode == BrowserViewMode.GRID) {
                        val gridState = remember(albumScrollKey) {
                            gridStatesByAlbum.getOrPut(albumScrollKey) { LazyGridState() }
                        }
                        FileGrid(
                            files = state.displayedFiles,
                            selectedFiles = state.selectedFiles,
                            onNavigateTo = {},
                            onOpenFile = onOpenFile,
                            onToggleSelection = onToggleSelection,
                            onSelectMultiple = onSelectMultiple,
                            gridState = gridState,
                            minCellSize = state.presentation.gridMinCellSize.dp,
                            showThumbnails = state.presentation.showThumbnails,
                            showDetails = state.showFileDetails,
                            thumbnailLoadingPaused = state.isRefreshing,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 12.dp, top = 8.dp, end = 12.dp, bottom = bottomPadding)
                        )
                    } else {
                        val listState = remember(albumScrollKey) {
                            listStatesByAlbum.getOrPut(albumScrollKey) { LazyListState() }
                        }
                        FileList(
                            files = state.displayedFiles,
                            selectedFiles = state.selectedFiles,
                            onNavigateTo = {},
                            onOpenFile = onOpenFile,
                            onToggleSelection = onToggleSelection,
                            onSelectMultiple = onSelectMultiple,
                            listState = listState,
                            zoom = state.presentation.listZoom,
                            showThumbnails = state.presentation.showThumbnails,
                            showDetails = true,
                            thumbnailLoadingPaused = state.isRefreshing,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = bottomPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageGalleryAlbumChips(
    state: ImageGalleryState,
    onSelectAlbum: (String?) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            FilterChip(
                selected = state.selectedAlbumPath == null,
                onClick = { onSelectAlbum(null) },
                label = { Text(stringResource(R.string.image_gallery_all_album, state.files.size)) },
                leadingIcon = {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            )
        }
        items(state.albums, key = { it.path ?: it.label }) { album ->
            FilterChip(
                selected = state.selectedAlbumPath == album.path,
                onClick = { onSelectAlbum(album.path) },
                label = {
                    Text(
                        text = "${album.label} (${album.count})",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            )
        }
    }
}

@Composable
private fun ImageGallerySelectionTopBar(
    selectedCount: Int,
    selectedSize: String,
    onClearSelection: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(stringResource(R.string.selected_count, selectedCount))
                Text(
                    text = selectedSize,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
            }
        }
    )
}

@Composable
private fun ImageGallerySelectionBar(
    isVisible: Boolean,
    contentPadding: PaddingValues,
    onSelectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onOpenProperties: () -> Unit
) {
    if (!isVisible) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = contentPadding.calculateBottomPadding()),
        contentAlignment = Alignment.BottomCenter
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            AssistChip(
                onClick = onSelectAll,
                label = { Text(stringResource(R.string.select_all)) },
                leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) }
            )
            AssistChip(
                onClick = onShareSelected,
                label = { Text(stringResource(R.string.share)) },
                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) }
            )
            AssistChip(
                onClick = onRequestDeleteSelected,
                label = { Text(stringResource(R.string.delete)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
            )
            AssistChip(
                onClick = onOpenProperties,
                label = { Text(stringResource(R.string.action_properties)) },
                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) }
            )
        }
    }
}
