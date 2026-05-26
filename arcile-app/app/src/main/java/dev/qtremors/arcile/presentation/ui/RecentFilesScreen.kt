package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import dev.qtremors.arcile.ui.theme.spacing
import dev.qtremors.arcile.ui.theme.menuGroupFirst
import dev.qtremors.arcile.ui.theme.menuGroupLast
import dev.qtremors.arcile.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.ui.theme.menuGroupSingle
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.R
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.presentation.containingFolderPath
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesState
import dev.qtremors.arcile.presentation.ui.components.ArcilePullRefreshIndicator
import dev.qtremors.arcile.presentation.ui.components.ArcileSnackbarHost
import dev.qtremors.arcile.presentation.ui.components.rememberArcileHaptics
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import dev.qtremors.arcile.presentation.ui.components.EmptyStateVariant
import dev.qtremors.arcile.presentation.ui.components.SearchFiltersBottomSheet
import dev.qtremors.arcile.presentation.ui.components.SearchTopBar
import dev.qtremors.arcile.presentation.ui.components.SortOptionDialog
import dev.qtremors.arcile.presentation.ui.components.SplitButtonGroup
import dev.qtremors.arcile.presentation.ui.components.ToolbarAction
import dev.qtremors.arcile.presentation.ui.components.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.presentation.ui.components.dialogs.PropertiesDialog
import dev.qtremors.arcile.presentation.ui.components.lists.ActiveFiltersRow
import dev.qtremors.arcile.presentation.ui.components.lists.FileGrid
import dev.qtremors.arcile.presentation.ui.components.lists.FileItemRow
import dev.qtremors.arcile.presentation.ui.components.lists.FileList
import dev.qtremors.arcile.presentation.utils.rememberDateFormatter
import kotlinx.coroutines.delay
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun RecentFilesScreen(
    state: RecentFilesState,
    onNavigateBack: () -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onConfirmDelete: () -> Unit,
    onTogglePermanentDelete: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onShareSelected: () -> Unit,
    onSelectAll: () -> Unit,
    onRefresh: () -> Unit,
    onClearError: () -> Unit = {},
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onSearchFiltersChange: (SearchFilters) -> Unit = {},
    onPresentationChange: (BrowserPresentationPreferences) -> Unit = {},
    onSelectMultiple: (List<String>) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onOpenProperties: () -> Unit = {},
    onDismissProperties: () -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    nativeRequestFlow: kotlinx.coroutines.flow.SharedFlow<android.content.IntentSender>? = null
) {
    val haptics = rememberArcileHaptics()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            when (state.pendingNativeAction) {
                dev.qtremors.arcile.presentation.recentfiles.RecentNativeAction.TRASH -> onConfirmDelete()
                null -> {}
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            haptics.error()
            snackbarHostState.showSnackbar(errorMsg.asString(context))
            onClearError()
        }
    }

    LaunchedEffect(nativeRequestFlow) {
        nativeRequestFlow?.collect { sender ->
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }
    var showFilterSheet by rememberSaveable { mutableStateOf(false) }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = isSelectionMode || showSearchBar) {
        if (isSelectionMode) {
            onClearSelection()
        } else {
            showSearchBar = false
            onClearSearch()
        }
    }

    val filesToDisplay = if (showSearchBar) state.searchResults else state.displayedRecentFiles
    val snackbarPadding = if (isSelectionMode) 80.dp else 0.dp

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            ArcileSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = snackbarPadding)
            )
        },
        topBar = {
            when {
                isSelectionMode -> SelectionTopBar(
                    selectedCount = state.selectedFiles.size,
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
                        },
                        scrollBehavior = scrollBehavior
                    )
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
                        contentPadding = padding,
                        onOpenFile = onOpenFile,
                        onToggleSelection = onToggleSelection,
                        onSelectMultiple = onSelectMultiple,
                        onRefresh = onRefresh,
                        onLoadMore = onLoadMore
                    )
                }
            }

            SelectionToolbar(
                isVisible = isSelectionMode,
                selectedFiles = state.selectedFiles,
                contentPadding = padding,
                onSelectAll = onSelectAll,
                onShareSelected = onShareSelected,
                onRequestDeleteSelected = onRequestDeleteSelected,
                onOpenProperties = onOpenProperties,
                onOpenContainingFolder = onOpenContainingFolder
            )
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
            decision = state.deleteDecision
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
            onDismiss = onDismissProperties
        )
    }

    if (showFilterSheet) {
        SearchFiltersBottomSheet(
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
            onApply = { preferences, _ -> onPresentationChange(preferences) }
        )
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClearSelection: () -> Unit
) {
    TopAppBar(
        title = { Text(stringResource(R.string.selected_count, selectedCount)) },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.clear_selection))
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RecentFilesContent(
    state: RecentFilesState,
    filesToDisplay: List<dev.qtremors.arcile.core.storage.domain.FileModel>,
    showSearchBar: Boolean,
    formatter: java.text.DateFormat,
    todayLabel: String,
    yesterdayLabel: String,
    contentPadding: PaddingValues,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit
) {
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()
    val topPadding = contentPadding.calculateTopPadding()
    val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + (if (isSelectionMode) MaterialTheme.spacing.toolbarBottomGap else MaterialTheme.spacing.screenGutter)

    androidx.compose.material3.pulltorefresh.PullToRefreshBox(
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
        val isGroupingEnabled = shouldGroupRecentFiles(showSearchBar, state.presentation)

        if (state.presentation.viewMode == BrowserViewMode.GRID) {
            val gridState = rememberLazyGridState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val totalItems = gridState.layoutInfo.totalItemsCount
                    val lastVisibleItem = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    totalItems > 0 && lastVisibleItem >= totalItems - 5
                }
            }
            LaunchedEffect(shouldLoadMore, state.isLoadingMore, state.hasMore, showSearchBar) {
                if (shouldLoadMore && !state.isLoadingMore && state.hasMore && !showSearchBar) {
                    onLoadMore()
                }
            }
            
            if (isGroupingEnabled) {
                val groupFormat = rememberDateFormatter("EEEE, MMM dd")
                val groupedFiles = remember(filesToDisplay, state.todayStart, state.yesterdayStart, groupFormat, todayLabel, yesterdayLabel) {
                    filesToDisplay.groupBy { file ->
                        when {
                            file.lastModified >= state.todayStart -> todayLabel
                            file.lastModified >= state.yesterdayStart -> yesterdayLabel
                            else -> groupFormat.format(Date(file.lastModified))
                        }
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = state.presentation.gridMinCellSize.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding),
                    state = gridState,
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomPadding),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    groupedFiles.forEach { (dateHeader, files) ->
                        stickyHeader {
                            RecentDateHeaderPill(dateHeader = dateHeader)
                        }
                        gridItemsIndexed(
                            items = files,
                            key = { index, file -> "$dateHeader-$index-${file.absolutePath}" },
                            contentType = { _, file -> if (file.isDirectory) "directory" else "file" }
                        ) { _, file ->
                            dev.qtremors.arcile.presentation.ui.components.lists.FileGridItem(
                                modifier = Modifier.animateItem(),
                                file = file,
                                formattedDate = formatter.format(Date(file.lastModified)),
                                isSelected = state.selectedFiles.contains(file.absolutePath),
                                showThumbnails = state.presentation.showThumbnails,
                                onClick = {
                                    if (isSelectionMode) onToggleSelection(file.absolutePath) else onOpenFile(file.absolutePath)
                                },
                                onLongClick = { onToggleSelection(file.absolutePath) }
                            )
                        }
                    }
                    if (state.isLoadingMore) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            } else {
                FileGrid(
                    files = filesToDisplay,
                    selectedFiles = state.selectedFiles,
                    onNavigateTo = {},
                    onOpenFile = onOpenFile,
                    onToggleSelection = onToggleSelection,
                    onSelectMultiple = onSelectMultiple,
                    gridState = gridState,
                    minCellSize = state.presentation.gridMinCellSize.dp,
                    showThumbnails = state.presentation.showThumbnails,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = topPadding),
                    contentPadding = PaddingValues(start = 16.dp, top = 16.dp, end = 16.dp, bottom = bottomPadding)
                )
            }
        } else if (isGroupingEnabled) {
            val listState = rememberLazyListState()
            val groupFormat = rememberDateFormatter("EEEE, MMM dd")
            val groupedFiles = remember(filesToDisplay, state.todayStart, state.yesterdayStart, groupFormat, todayLabel, yesterdayLabel) {
                filesToDisplay.groupBy { file ->
                    when {
                        file.lastModified >= state.todayStart -> todayLabel
                        file.lastModified >= state.yesterdayStart -> yesterdayLabel
                        else -> groupFormat.format(Date(file.lastModified))
                    }
                }
            }
            val shouldLoadMore by remember {
                derivedStateOf {
                    val totalItems = listState.layoutInfo.totalItemsCount
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    totalItems > 0 && lastVisibleItem >= totalItems - 5
                }
            }
            LaunchedEffect(shouldLoadMore, state.isLoadingMore, state.hasMore, showSearchBar) {
                if (shouldLoadMore && !state.isLoadingMore && state.hasMore && !showSearchBar) {
                    onLoadMore()
                }
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                state = listState,
                contentPadding = PaddingValues(bottom = bottomPadding)
            ) {
                groupedFiles.forEach { (dateHeader, files) ->
                    stickyHeader {
                        RecentDateHeaderPill(dateHeader = dateHeader)
                    }
                    itemsIndexed(
                        items = files,
                        key = { index, file -> "$dateHeader-$index-${file.absolutePath}" },
                        contentType = { _, file -> if (file.isDirectory) "directory" else "file" }
                    ) { _, file ->
                        FileItemRow(
                            file = file,
                            formattedDate = formatter.format(Date(file.lastModified)),
                            isSelected = state.selectedFiles.contains(file.absolutePath),
                            zoom = state.presentation.listZoom,
                            showThumbnails = state.presentation.showThumbnails,
                            onClick = {
                                if (isSelectionMode) onToggleSelection(file.absolutePath) else onOpenFile(file.absolutePath)
                            },
                            onLongClick = { onToggleSelection(file.absolutePath) }
                        )
                    }
                }
                if (state.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            LoadingIndicator()
                        }
                    }
                }
            }
        } else {
            val listState = rememberLazyListState()
            val shouldLoadMore by remember {
                derivedStateOf {
                    val totalItems = listState.layoutInfo.totalItemsCount
                    val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    totalItems > 0 && lastVisibleItem >= totalItems - 5
                }
            }
            LaunchedEffect(shouldLoadMore, state.isLoadingMore, state.hasMore, showSearchBar) {
                if (shouldLoadMore && !state.isLoadingMore && state.hasMore && !showSearchBar) {
                    onLoadMore()
                }
            }
            FileList(
                files = filesToDisplay,
                selectedFiles = state.selectedFiles,
                onNavigateTo = {},
                onOpenFile = onOpenFile,
                onToggleSelection = onToggleSelection,
                onSelectMultiple = onSelectMultiple,
                listState = listState,
                zoom = state.presentation.listZoom,
                showThumbnails = state.presentation.showThumbnails,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topPadding),
                contentPadding = PaddingValues(bottom = bottomPadding)
            )
        }
    }
}

@Composable
private fun RecentDateHeaderPill(dateHeader: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.primary,
            tonalElevation = 3.dp,
            shadowElevation = 2.dp
        ) {
            Text(
                text = dateHeader,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
            )
        }
    }
}

@Composable
private fun SelectionToolbar(
    isVisible: Boolean,
    selectedFiles: Set<String>,
    contentPadding: PaddingValues,
    onSelectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onOpenProperties: () -> Unit,
    onOpenContainingFolder: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.BottomCenter
    ) {
        val mainActions = listOf(
            ToolbarAction(
                icon = Icons.Default.SelectAll,
                contentDescription = stringResource(R.string.select_all),
                onClick = onSelectAll
            ),
            ToolbarAction(
                icon = Icons.Default.Share,
                contentDescription = stringResource(R.string.share),
                onClick = onShareSelected
            ),
            ToolbarAction(
                icon = Icons.Default.Delete,
                contentDescription = stringResource(R.string.delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = onRequestDeleteSelected
            )
        )

        dev.qtremors.arcile.presentation.ui.components.FloatingSelectionToolbar(
            isVisible = isVisible,
            actions = mainActions,
            moreContent = {
                var showMoreMenu by remember { mutableStateOf(false) }
                Box {
                    Surface(
                        onClick = { showMoreMenu = true },
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_more_options),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    androidx.compose.material3.DropdownMenu(
                        shape = MaterialTheme.shapes.extraLarge,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        val menuActions = remember(onOpenProperties, selectedFiles) {
                            mutableListOf<@Composable () -> Unit>().apply {
                                if (selectedFiles.size == 1) {
                                    add {
                                        androidx.compose.material3.DropdownMenuItem(
                                            text = { Text(stringResource(R.string.open_containing_folder)) },
                                            leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                            onClick = {
                                                showMoreMenu = false
                                                containingFolderPath(selectedFiles.first())?.let(onOpenContainingFolder)
                                            }
                                        )
                                    }
                                }
                                add {
                                    androidx.compose.material3.DropdownMenuItem(
                                        text = { Text(stringResource(R.string.properties_title)) },
                                        leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                        onClick = {
                                            showMoreMenu = false
                                            onOpenProperties()
                                        }
                                    )
                                }
                            }
                        }

                        menuActions.forEachIndexed { index, action ->
                            val shape = when {
                                menuActions.size == 1 -> MaterialTheme.shapes.menuGroupSingle
                                index == 0 -> MaterialTheme.shapes.menuGroupFirst
                                index == menuActions.size - 1 -> MaterialTheme.shapes.menuGroupLast
                                else -> MaterialTheme.shapes.menuGroupMiddle
                            }
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(shape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
                                action()
                            }
                        }
                    }
                }
            }
        )
    }
}

private fun shouldGroupRecentFiles(
    showSearchBar: Boolean,
    presentation: BrowserPresentationPreferences
): Boolean = !showSearchBar &&
    presentation.sortOption in setOf(FileSortOption.DATE_NEWEST, FileSortOption.DATE_OLDEST)
