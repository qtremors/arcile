package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import dev.qtremors.arcile.presentation.ui.components.ToolbarAction
import dev.qtremors.arcile.presentation.ui.components.SplitButtonGroup
import dev.qtremors.arcile.presentation.ui.components.ArcileSnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import dev.qtremors.arcile.presentation.ui.components.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.presentation.ui.components.SearchTopBar
import dev.qtremors.arcile.presentation.ui.components.ArcilePullRefreshIndicator
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff

import androidx.compose.material3.LoadingIndicator



import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.utils.rememberDateFormatter
import java.util.Date
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesState
import dev.qtremors.arcile.presentation.containingFolderPath
import dev.qtremors.arcile.presentation.ui.components.lists.FileItemRow
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R

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
    onLoadMore: () -> Unit = {},
    onOpenProperties: () -> Unit = {},
    onDismissProperties: () -> Unit = {},
    onOpenContainingFolder: (String) -> Unit = {},
    nativeRequestFlow: kotlinx.coroutines.flow.SharedFlow<android.content.IntentSender>? = null
) {

    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    val formatter = rememberDateFormatter("MMM dd, yyyy  h:mm a")
    val todayLabel = stringResource(R.string.today)
    val yesterdayLabel = stringResource(R.string.yesterday)

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            when (state.pendingNativeAction) {
                dev.qtremors.arcile.presentation.recentfiles.RecentNativeAction.TRASH -> onConfirmDelete()
                null -> {}
            }
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            snackbarHostState.showSnackbar(errorMsg)
            onClearError()
        }
    }

    androidx.compose.runtime.LaunchedEffect(nativeRequestFlow) {
        nativeRequestFlow?.collect { sender ->
            launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
        }
    }

    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }

    // Intercept system back to clear selection before navigating away
    BackHandler(enabled = isSelectionMode || showSearchBar) {
        if (isSelectionMode) {
            onClearSelection()
        } else {
            showSearchBar = false
            onClearSearch()
        }
    }




    val snackbarPadding = if (isSelectionMode) 80.dp else 0.dp

    Scaffold(
        snackbarHost = {
            ArcileSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = snackbarPadding)
            )
        },
        topBar = {
            if (isSelectionMode) {
                TopAppBar(
                    title = { Text(stringResource(R.string.selected_count, state.selectedFiles.size)) },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.clear_selection))
                        }
                    },
                    actions = {},
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            } else if (showSearchBar) {
                SearchTopBar(
                    query = state.searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onClose = {
                        showSearchBar = false
                        onClearSearch()
                    },
                    placeholder = stringResource(R.string.search_recent_files_placeholder)
                )
            } else {
                TopAppBar(
                    title = { Text(stringResource(R.string.recent_files_title)) },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        val topActions = listOf(
                            ToolbarAction(
                                icon = Icons.Default.Search,
                                contentDescription = stringResource(R.string.action_search),
                                onClick = { showSearchBar = true }
                            ),
                            ToolbarAction(
                                icon = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.action_sort),
                                onClick = { /* show sort dialog if needed */ }
                            )
                        )
                        SplitButtonGroup(actions = topActions)
                    },
                    scrollBehavior = scrollBehavior
                )
            }

        },
        bottomBar = {},
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
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            if (showLoading && state.recentFiles.isEmpty() && !state.isPullToRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    LoadingIndicator()
                }
            } else if (state.recentFiles.isEmpty() && !state.isLoading && !showSearchBar) {
                EmptyState(
                    icon = Icons.Default.History,
                    title = stringResource(R.string.no_recent_files),
                    description = stringResource(R.string.no_recent_files_description),
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
                    icon = Icons.Default.SearchOff,
                    title = stringResource(R.string.no_results_found),
                    description = stringResource(R.string.no_results_description, state.searchQuery),
                    modifier = Modifier.fillMaxSize()
                )

            } else {
                val filesToDisplay = if (showSearchBar) {
                    state.searchResults
                } else {
                    state.recentFiles
                }
                val groupFormat = rememberDateFormatter("EEEE, MMM dd")
                val groupedFiles = remember(filesToDisplay, showSearchBar, state.todayStart, state.yesterdayStart, groupFormat, todayLabel, yesterdayLabel) {
                if (showSearchBar) {
                    // Don't group search results by date, just show flat
                    mapOf("" to filesToDisplay)
                } else {
                    filesToDisplay.groupBy { file ->
                        when {
                            file.lastModified >= state.todayStart -> todayLabel
                            file.lastModified >= state.yesterdayStart -> yesterdayLabel
                            else -> groupFormat.format(Date(file.lastModified))
                        }
                    }

                }}



                val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                
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

                val pullRefreshState = androidx.compose.material3.pulltorefresh.rememberPullToRefreshState()

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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = padding.calculateTopPadding()),
                        state = listState,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            bottom = padding.calculateBottomPadding() + 100.dp
                        )
                    ) {
                    if (filesToDisplay.isEmpty() && !state.isLoading) {
                        item {
                            EmptyState(
                                icon = Icons.Default.History,
                                title = stringResource(R.string.no_recent_files),
                                description = stringResource(R.string.no_recent_files_description),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    groupedFiles.forEach { (dateHeader, files) ->
                        @OptIn(ExperimentalFoundationApi::class)
                        if (dateHeader.isNotEmpty()) {
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.95f))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = dateHeader,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
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
                                onClick = {
                                    if (isSelectionMode) onToggleSelection(file.absolutePath)
                                    else onOpenFile(file.absolutePath)
                                },
                                onLongClick = {
                                    onToggleSelection(file.absolutePath)
                                }
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
            }

            // Floating Selection Toolbar Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.BottomCenter
            ) {
                val mainActions = mutableListOf<dev.qtremors.arcile.presentation.ui.components.ToolbarAction>()
                mainActions.add(dev.qtremors.arcile.presentation.ui.components.ToolbarAction(
                    icon = androidx.compose.material.icons.Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.select_all),
                    onClick = onSelectAll
                ))
                mainActions.add(dev.qtremors.arcile.presentation.ui.components.ToolbarAction(
                    icon = androidx.compose.material.icons.Icons.Default.Share,
                    contentDescription = stringResource(R.string.share),
                    onClick = onShareSelected
                ))
                mainActions.add(dev.qtremors.arcile.presentation.ui.components.ToolbarAction(
                    icon = androidx.compose.material.icons.Icons.Default.Delete,
                    contentDescription = stringResource(R.string.delete),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onRequestDeleteSelected
                ))
                
                dev.qtremors.arcile.presentation.ui.components.FloatingSelectionToolbar(
                    isVisible = isSelectionMode,
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
                                        imageVector = androidx.compose.material.icons.Icons.Default.MoreVert,
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
                                val menuActions = remember(onOpenProperties, state.selectedFiles) {
                                    mutableListOf<@Composable () -> Unit>().apply {
                                        if (state.selectedFiles.size == 1) {
                                            add {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.open_containing_folder)) },
                                                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) },
                                                    onClick = {
                                                        showMoreMenu = false
                                                        containingFolderPath(state.selectedFiles.first())?.let(onOpenContainingFolder)
                                                    }
                                                )
                                            }
                                        }
                                        add {
                                            androidx.compose.material3.DropdownMenuItem(
                                                text = { Text(stringResource(R.string.properties_title)) },
                                                leadingIcon = { Icon(androidx.compose.material.icons.Icons.Default.Info, contentDescription = null) },
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
                                        menuActions.size == 1 -> RoundedCornerShape(24.dp)
                                        index == 0 -> RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 4.dp, bottomEnd = 4.dp)
                                        index == menuActions.size - 1 -> RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
                                        else -> RoundedCornerShape(4.dp)
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
    }

    if (state.showTrashConfirmation || state.showPermanentDeleteConfirmation) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedFiles.size,
            isPermanentDeleteChecked = state.isPermanentDeleteChecked,
            isPermanentDeleteToggleEnabled = state.isPermanentDeleteToggleEnabled,
            onConfirm = onConfirmDelete,
            onDismiss = onDismissDeleteConfirmation,
            onTogglePermanentDelete = onTogglePermanentDelete
        )
    }

    if (state.showMixedDeleteExplanation) {
        AlertDialog(
            onDismissRequest = onDismissDeleteConfirmation,
            title = { Text(stringResource(R.string.mixed_selection_title)) },
            text = { Text(stringResource(R.string.mixed_selection_description)) },
            confirmButton = {
                TextButton(onClick = onDismissDeleteConfirmation) {
                    Text(stringResource(R.string.ok))
                }
            }
        )
    }

    if (state.isPropertiesVisible) {
        dev.qtremors.arcile.presentation.ui.components.dialogs.PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = onDismissProperties
        )
    }
}
}
