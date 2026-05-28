package dev.qtremors.arcile.feature.trash

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import dev.qtremors.arcile.ui.theme.spacing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import dev.qtremors.arcile.shared.ui.ToolbarAction
import dev.qtremors.arcile.shared.ui.SplitButtonGroup
import dev.qtremors.arcile.shared.ui.ArcileSnackbarHost
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.rememberScrollState
import dev.qtremors.arcile.shared.ui.SearchTopBar
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Info
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width


import kotlinx.coroutines.delay
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.isIndexed
import androidx.compose.foundation.clickable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import dev.qtremors.arcile.feature.trash.TrashFilter
import dev.qtremors.arcile.feature.trash.TrashPropertiesUiModel
import dev.qtremors.arcile.feature.trash.TrashState
import dev.qtremors.arcile.feature.trash.TrashSortOption
import dev.qtremors.arcile.shared.ui.EmptyState
import dev.qtremors.arcile.shared.ui.EmptyStateVariant
import dev.qtremors.arcile.feature.trash.ui.EmptyTrashDialog
import dev.qtremors.arcile.feature.trash.ui.TrashList

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.feature.trash.R
import dev.qtremors.arcile.core.ui.asString

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrashScreen(
    state: TrashState,
    onNavigateBack: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onClearSelection: () -> Unit,
    onRestoreSelected: () -> Unit,
    onEmptyTrash: () -> Unit,
    onClearError: () -> Unit,
    onDismissDestinationPicker: () -> Unit,
    onRestoreToDestination: (List<String>, String) -> Unit,
    onPermanentlyDeleteSelected: () -> Unit,
    onDismissPermanentDelete: () -> Unit,
    onSelectAll: () -> Unit,
    onSearchQueryChange: (String) -> Unit = {},
    onClearSearch: () -> Unit = {},
    onSortChange: (TrashSortOption) -> Unit = {},
    onFilterChange: (TrashFilter) -> Unit = {},
    onOpenProperties: () -> Unit = {},
    onDismissProperties: () -> Unit = {},
    onClearSnackbarMessage: () -> Unit = {},
    nativeRequestFlow: kotlinx.coroutines.flow.SharedFlow<android.content.IntentSender>? = null
) {
    val haptics = rememberArcileHaptics()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            when (state.pendingNativeAction) {
                dev.qtremors.arcile.feature.trash.NativeAction.RESTORE -> onRestoreSelected()
                dev.qtremors.arcile.feature.trash.NativeAction.RESTORE_TO_DESTINATION -> {
                    if (state.pendingDestinationPath != null && state.pendingRestoreIds.isNotEmpty()) {
                        onRestoreToDestination(state.pendingRestoreIds, state.pendingDestinationPath)
                    }
                }
                dev.qtremors.arcile.feature.trash.NativeAction.EMPTY -> onEmptyTrash()
                null -> {}
            }
        }
    }

    var showLoading by remember { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (state.isLoading) {
            delay(150)
            showLoading = true
        } else {
            showLoading = false
        }
    }

    LaunchedEffect(nativeRequestFlow) {
        nativeRequestFlow?.collect { sender ->
            launcher.launch(IntentSenderRequest.Builder(sender).build())
        }
    }

    var showEmptyTrashConfirmation by rememberSaveable { mutableStateOf(false) }
    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotBlank()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            haptics.error()
            snackbarHostState.showSnackbar(errorMsg.asString(context))
            onClearError()
        }
    }
    LaunchedEffect(state.snackbarMessage) {
        state.snackbarMessage?.let { message ->
            snackbarHostState.showSnackbar(message.asString(context))
            onClearSnackbarMessage()
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

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val snackbarPadding = if (isSelectionMode) 80.dp else 0.dp
    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        (if (isSelectionMode) MaterialTheme.spacing.toolbarBottomGap else MaterialTheme.spacing.screenGutter)
    var showSortDialog by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
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
                            IconButton(onClick = onClearSelection) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_selection))
                            }
                        } else {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                            }
                        }
                    },
                    actions = {
                        if (!isSelectionMode) {
                            val topActions = listOf(
                                dev.qtremors.arcile.shared.ui.ToolbarAction(
                                    icon = Icons.Default.Search,
                                    contentDescription = stringResource(R.string.action_search),
                                    onClick = { showSearchBar = true }
                                ),
                                dev.qtremors.arcile.shared.ui.ToolbarAction(
                                    icon = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.action_sort),
                                    onClick = { showSortDialog = true }
                                )
                            )
                            dev.qtremors.arcile.shared.ui.SplitButtonGroup(actions = topActions)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = if (isSelectionMode) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
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

            Box(modifier = Modifier.weight(1f)) {
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

            // Floating Selection Toolbar Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.BottomCenter
            ) {
                val mainActions = mutableListOf<dev.qtremors.arcile.shared.ui.ToolbarAction>()
                mainActions.add(dev.qtremors.arcile.shared.ui.ToolbarAction(
                    icon = Icons.Default.SelectAll,
                    contentDescription = stringResource(R.string.select_all),
                    onClick = onSelectAll
                ))
                mainActions.add(dev.qtremors.arcile.shared.ui.ToolbarAction(
                    icon = Icons.Default.Restore,
                    contentDescription = stringResource(R.string.restore),
                    onClick = onRestoreSelected
                ))
                mainActions.add(dev.qtremors.arcile.shared.ui.ToolbarAction(
                    icon = Icons.Default.DeleteForever,
                    contentDescription = stringResource(R.string.delete_permanently),
                    tint = MaterialTheme.colorScheme.error,
                    onClick = onPermanentlyDeleteSelected
                ))
                
                dev.qtremors.arcile.shared.ui.FloatingSelectionToolbar(
                    isVisible = isSelectionMode,
                    actions = mainActions,
                    moreContent = {
                        var showMoreMenu by rememberSaveable { mutableStateOf(false) }
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
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            ) {
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
                    }
                )
            }
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
            dev.qtremors.arcile.shared.ui.dialogs.DeleteConfirmationDialog(
                selectedCount = state.selectedFiles.size,
                isPermanentDeleteChecked = true,
                isPermanentDeleteToggleEnabled = false,
                onConfirm = onPermanentlyDeleteSelected,
                onDismiss = onDismissPermanentDelete,
                onTogglePermanentDelete = {}
            )
        }

        if (state.showDestinationPicker) {
            val indexedVolumes = state.availableVolumes.filter { it.kind.isIndexed }
            if (indexedVolumes.isEmpty()) {
                AlertDialog(
                    onDismissRequest = onDismissDestinationPicker,
                    title = { Text(stringResource(R.string.no_restore_destination_title)) },
                    text = { Text(stringResource(R.string.no_restore_destination_description)) },
                    confirmButton = {
                        TextButton(onClick = onDismissDestinationPicker) {
                            Text(stringResource(R.string.dismiss))
                        }
                    }
                )
            } else {
                AlertDialog(
                    onDismissRequest = onDismissDestinationPicker,
                    title = { Text(stringResource(R.string.select_restore_destination_title)) },
                    text = {
                        Column {
                            Text(stringResource(R.string.select_restore_destination_description))
                            Spacer(modifier = Modifier.height(8.dp))
                            LazyColumn {
                                items(indexedVolumes) { volume ->
                                    ListItem(
                                        headlineContent = { Text(volume.name) },
                                        supportingContent = { Text(volume.path, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                        modifier = Modifier.clickable {
                                            onRestoreToDestination(state.selectedTrashIdsForDestination, volume.path)
                                            onDismissDestinationPicker()
                                        },
                                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = onDismissDestinationPicker) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                )
            }
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

@Composable
private fun TrashPropertiesDialog(
    properties: TrashPropertiesUiModel?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.properties_title)) },
        text = {
            val model = properties ?: return@AlertDialog
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(model.title, style = MaterialTheme.typography.titleMedium)
                model.rows.forEach { (label, value) ->
                    Column {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = value,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ok))
            }
        }
    )
}

@Composable
fun TrashInfoCard() {
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

