package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon

import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import dev.qtremors.arcile.ui.theme.ExpressiveSquircleShape
import dev.qtremors.arcile.ui.theme.ExpressivePillShape
import dev.qtremors.arcile.ui.theme.ExpressiveShapes

import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.presentation.FileManagerState
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.utils.formatFileSize
import dev.qtremors.arcile.presentation.filterAndSortFiles
import dev.qtremors.arcile.presentation.ClipboardOperation
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.ui.components.Breadcrumbs
import dev.qtremors.arcile.presentation.ui.components.SearchFiltersBottomSheet
import dev.qtremors.arcile.presentation.ui.components.SearchTopBar
import dev.qtremors.arcile.presentation.ui.components.SortOptionDialog
import dev.qtremors.arcile.presentation.ui.components.TopBarAction
import kotlinx.coroutines.launch

import dev.qtremors.arcile.domain.FileCategories
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Full-featured file browser screen.
 *
 * Supports list and grid views, multi-select with range selection, inline search with filters,
 * file creation, rename, delete (via trash), copy/cut/paste clipboard, share, and pull-to-refresh.
 *
 * @param state Current [FileManagerState] containing file list, selection, search, and clipboard state.
 * @param storageRootPath Absolute path of the storage root — used for breadcrumb display.
 * @param onNavigateBack Called when the user navigates back (hardware back or top bar back button).
 * @param onNavigateTo Called with the target directory path when the user opens a folder.
 * @param onOpenFile Called with the file path when the user opens a non-directory file.
 * @param onToggleSelection Toggles selection state for the given file path.
 * @param onSelectMultiple Selects all provided file paths (used for range selection).
 * @param onClearSelection Clears all current selections.
 * @param onCreateFolder Creates a new directory with the given name in the current directory.
 * @param onCreateFile Creates a new empty file with the given name in the current directory.
 * @param onDeleteSelected Moves all currently selected items to trash.
 * @param onRenameFile Renames the file at [path] to [newName].
 * @param onSearchQueryChange Updates the search query in the ViewModel.
 * @param onClearSearch Clears the active search query.
 * @param onSortOptionChange Updates the sort option for the current directory listing.
 * @param onGridViewChange Switches between list and grid view layouts.
 * @param onClearError Clears the current error message from state.
 * @param onCopySelected Stages selected files for a copy operation.
 * @param onCutSelected Stages selected files for a move operation.
 * @param onPasteFromClipboard Executes the pending clipboard operation in the current directory.
 * @param onCancelClipboard Cancels the current clipboard operation.
 * @param onShareSelected Launches the system share sheet for selected files.
 * @param isRefreshing `true` while a pull-to-refresh reload is in progress.
 * @param onRefresh Triggers a directory reload (invoked by pull-to-refresh).
 * @param onSearchFiltersChange Updates the active search filters.
 * @param onToggleSearchFilterMenu Opens or closes the search filter bottom sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun FileManagerScreen(
    state: FileManagerState,
    storageRootPath: String,
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    onClearSelection: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameFile: (String, String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onSortOptionChange: (FileSortOption) -> Unit,
    onGridViewChange: (Boolean) -> Unit,
    onClearError: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onCancelClipboard: () -> Unit,
    onShareSelected: () -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onSearchFiltersChange: (SearchFilters) -> Unit = {},
    onToggleSearchFilterMenu: (Boolean) -> Unit = {}
) {
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showCreateFileDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSearchBar by rememberSaveable { mutableStateOf(state.browserSearchQuery.isNotEmpty()) }
    
    var isFabExpanded by remember { mutableStateOf(false) }
    val fabIconRotation by animateFloatAsState(
        targetValue = if (isFabExpanded) 45f else 0f,
        label = "fabRotation"
    )

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    // Always show full folder contents — search results only appear in the dropdown
    val displayedFiles = remember(state.files, state.browserSortOption) {
        filterAndSortFiles(state.files, "", state.browserSortOption)
    }

    LaunchedEffect(state.browserSearchQuery) {
        if (state.browserSearchQuery.isNotEmpty()) {
            showSearchBar = true
        }
    }

    BackHandler {
        if (showSearchBar) {
            showSearchBar = false
            onClearSearch()
        } else {
            onNavigateBack()
        }
    }

    // Show clipboard feedback snackbar
    LaunchedEffect(state.clipboardState) {
        state.clipboardState?.let { clipboard ->
            val action = if (clipboard.operation == ClipboardOperation.COPY) "copied" else "cut"
            val count = clipboard.sourcePaths.size
            coroutineScope.launch {
                snackbarHostState.showSnackbar("$count item(s) $action to clipboard")
            }
        }
    }

    // Show error as Snackbar instead of blocking dialog
    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            coroutineScope.launch {
                snackbarHostState.showSnackbar(errorMsg)
                onClearError()
            }
        }
    }

    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            if (showSearchBar) {
                Column {
                    SearchTopBar(
                        query = state.browserSearchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = {
                            showSearchBar = false
                            onClearSearch()
                        },
                        onFilterClick = { onToggleSearchFilterMenu(true) },
                        placeholder = "Search all files..."
                    )
                    ActiveFiltersRow(
                        filters = state.activeSearchFilters,
                        onClearFilter = { clearedFilters -> onSearchFiltersChange(clearedFilters) }
                    )
                }
            } else {
                ArcileTopBar(
                    title = "Browse",
                    selectionCount = state.selectedFiles.size,
                    showBackArrow = true,
                    showGridViewAction = true,
                    isGridView = state.isGridView,
                    hasClipboardItems = state.clipboardState != null,
                    scrollBehavior = scrollBehavior,
                    onBackClick = onNavigateBack,
                    onClearSelection = onClearSelection,
                    onSearchClick = { showSearchBar = true },
                    onSortClick = { showSortDialog = true },
                    onPasteClick = onPasteFromClipboard,
                    onCancelPaste = onCancelClipboard,
                    onActionSelected = { action ->
                        when (action) {
                            TopBarAction.NewFolder -> showCreateFolderDialog = true
                            TopBarAction.DeleteSelected -> showDeleteConfirmation = true
                            TopBarAction.Rename -> if (state.selectedFiles.size == 1) showRenameDialog = true
                            TopBarAction.GridView -> onGridViewChange(!state.isGridView)
                            TopBarAction.Copy -> onCopySelected()
                            TopBarAction.Cut -> onCutSelected()
                            TopBarAction.Share -> onShareSelected()
                            TopBarAction.SelectAll -> onSelectMultiple(displayedFiles.map { it.absolutePath })
                            else -> {}
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (state.selectedFiles.isEmpty() && !showSearchBar) {
                Box {
                    // Dismiss scrim behind FAB menu
                    if (isFabExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .clickable { isFabExpanded = false }
                        )
                    }
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        ExpandableFabMenu(
                            isExpanded = isFabExpanded,
                            onToggleExpand = { isFabExpanded = !isFabExpanded },
                            fabIconRotation = fabIconRotation,
                            onCreateFileClick = {
                                isFabExpanded = false
                                showCreateFileDialog = true
                            },
                            onCreateFolderClick = {
                                isFabExpanded = false
                                showCreateFolderDialog = true
                            }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // When search has completed, show search results instead of browse content
            val searchHasCompleted = showSearchBar && state.browserSearchQuery.isNotEmpty() && !state.isSearching

            Column(modifier = Modifier.fillMaxSize()) {
                if (searchHasCompleted) {
                    // Search results in the content area
                    if (state.searchResults.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Default.SearchOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No results for \"${state.browserSearchQuery}\"",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(state.searchResults, key = { it.absolutePath }) { file ->
                                FileItemRow(
                                    file = file,
                                    formattedDate = formatter.format(Date(file.lastModified)),
                                    isSelected = false,
                                    onClick = {
                                        showSearchBar = false
                                        onClearSearch()
                                        if (file.isDirectory) {
                                            onNavigateTo(file.absolutePath)
                                        } else {
                                            onOpenFile(file.absolutePath)
                                        }
                                    },
                                    onLongClick = {}
                                )
                            }
                        }
                    }
                } else if (showSearchBar && state.isSearching) {
                    // Loading indicator while search is running
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                } else {
                    // Normal browse content
                    Breadcrumbs(
                        path = state.currentPath,
                        storageRootPath = storageRootPath,
                        onPathSegmentClick = { path ->
                            onNavigateTo(path)
                        }
                    )
                    
                    val pullRefreshState = rememberPullToRefreshState()
                    
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = pullRefreshState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        indicator = {
                            val pullDistance = pullRefreshState.distanceFraction
                            val yOffset = (-40.dp + (80.dp * pullDistance)).coerceIn(-40.dp, 40.dp)
                            
                            if (isRefreshing || pullDistance > 0f) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopCenter)
                                        .graphicsLayer {
                                            translationY = if (isRefreshing) 40.dp.toPx() else yOffset.toPx()
                                            alpha = if (isRefreshing) 1f else pullDistance.coerceIn(0f, 1f)
                                        }
                                        .padding(top = 8.dp)
                                ) {
                                    Card(
                                        shape = androidx.compose.foundation.shape.CircleShape,
                                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            LoadingIndicator(modifier = Modifier.size(24.dp))
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        if (state.isLoading && !isRefreshing) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                LoadingIndicator()
                            }
                        } else if (displayedFiles.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        Icons.Default.FolderOff,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Empty Directory",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        } else if (state.isGridView) {
                            FileGrid(
                                files = displayedFiles,
                                selectedFiles = state.selectedFiles,
                                onNavigateTo = onNavigateTo,
                                onOpenFile = onOpenFile,
                                onToggleSelection = onToggleSelection,
                                onSelectMultiple = onSelectMultiple,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            FileList(
                                files = displayedFiles,
                                selectedFiles = state.selectedFiles,
                                onNavigateTo = onNavigateTo,
                                onOpenFile = onOpenFile,
                                onToggleSelection = onToggleSelection,
                                onSelectMultiple = onSelectMultiple,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.isSearchFilterMenuVisible) {
        SearchFiltersBottomSheet(
            currentFilters = state.activeSearchFilters,
            onApplyFilters = { onSearchFiltersChange(it) },
            onDismiss = { onToggleSearchFilterMenu(false) }
        )
    }

    // Dialogs
    if (showCreateFolderDialog) {
            CreateFolderDialog(
                onDismiss = { showCreateFolderDialog = false },
                onConfirm = { name ->
                    onCreateFolder(name)
                    showCreateFolderDialog = false
                }
            )
        }

        if (showDeleteConfirmation) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmation = false },
                title = { Text("Delete ${state.selectedFiles.size} item(s)?") },
                text = { Text("Selected items will be moved to the Trash Bin. You can restore them later.") },
                confirmButton = {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            showDeleteConfirmation = false
                            onDeleteSelected()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Text("Delete")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showCreateFileDialog) {
            CreateFileDialog(
                onDismiss = { showCreateFileDialog = false },
                onConfirm = { fileName ->
                    showCreateFileDialog = false
                    onCreateFile(fileName)
                }
            )
        }

        if (showRenameDialog && state.selectedFiles.size == 1) {
            val selectedPath = state.selectedFiles.first()
            val currentName = selectedPath.substringAfterLast('/')
            RenameDialog(
                currentName = currentName,
                onDismiss = { showRenameDialog = false },
                onConfirm = { newName ->
                    onRenameFile(selectedPath, newName)
                    showRenameDialog = false
                }
            )
        }

        if (showSortDialog) {
            SortOptionDialog(
                title = "Sort current folder",
                selectedOption = state.browserSortOption,
                onDismiss = { showSortDialog = false },
                onOptionSelected = { option ->
                    onSortOptionChange(option)
                    showSortDialog = false
                }
            )
        }


    }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileList(
    files: List<FileModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(files.size, key = { index -> files[index].absolutePath }) { index ->
            val file = files[index]
            FileItemRow(
                modifier = Modifier.animateItem(),
                file = file,
                formattedDate = formatter.format(Date(file.lastModified)),
                isSelected = selectedFiles.contains(file.absolutePath),
                onClick = {
                    if (selectedFiles.isNotEmpty()) {
                        lastInteractedIndex = index
                        onToggleSelection(file.absolutePath)
                    } else if (file.isDirectory) {
                        onNavigateTo(file.absolutePath)
                    } else {
                        onOpenFile(file.absolutePath)
                    }
                },
                onLongClick = {
                    if (selectedFiles.isNotEmpty() && lastInteractedIndex != null && lastInteractedIndex != index) {
                        val start = minOf(lastInteractedIndex!!, index)
                        val end = maxOf(lastInteractedIndex!!, index)
                        val rangePaths = files.subList(start, end + 1).map { it.absolutePath }
                        onSelectMultiple(rangePaths)
                        lastInteractedIndex = index
                    } else {
                        lastInteractedIndex = index
                        onToggleSelection(file.absolutePath)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGrid(
    files: List<FileModel>,
    selectedFiles: Set<String>,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    val formatter = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }
    var lastInteractedIndex by remember { mutableStateOf<Int?>(null) }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(files.size, key = { index -> files[index].absolutePath }) { index ->
            val file = files[index]
            FileGridItem(
                file = file,
                formattedDate = formatter.format(Date(file.lastModified)),
                isSelected = selectedFiles.contains(file.absolutePath),
                onClick = {
                    if (selectedFiles.isNotEmpty()) {
                        lastInteractedIndex = index
                        onToggleSelection(file.absolutePath)
                    } else if (file.isDirectory) {
                        onNavigateTo(file.absolutePath)
                    } else {
                        onOpenFile(file.absolutePath)
                    }
                },
                onLongClick = { 
                    if (selectedFiles.isNotEmpty() && lastInteractedIndex != null && lastInteractedIndex != index) {
                        val start = minOf(lastInteractedIndex!!, index)
                        val end = maxOf(lastInteractedIndex!!, index)
                        val rangePaths = files.subList(start, end + 1).map { it.absolutePath }
                        onSelectMultiple(rangePaths)
                        lastInteractedIndex = index
                    } else {
                        lastInteractedIndex = index
                        onToggleSelection(file.absolutePath)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    file: FileModel,
    formattedDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedSurfaceColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "listItemColor"
    )
    


    val animatedHorizontalPadding by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 0.dp,
        label = "listItemHPadding"
    )
    val animatedVerticalPadding by animateDpAsState(
        targetValue = if (isSelected) 4.dp else 0.dp,
        label = "listItemVPadding"
    )

    Surface(
        shape = if (isSelected) ExpressiveShapes.large else ExpressiveSquircleShape,
        color = animatedSurfaceColor,
        modifier = modifier
            .padding(horizontal = animatedHorizontalPadding, vertical = animatedVerticalPadding)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        ListItem(
            leadingContent = {
                if (!file.isDirectory && (FileCategories.Images.extensions.contains(file.name.substringAfterLast('.').lowercase()) || 
                    FileCategories.Videos.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                    FileCategories.APKs.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                    FileCategories.Audio.extensions.contains(file.name.substringAfterLast('.').lowercase()))) {
                    AsyncImage(
                        model = File(file.absolutePath),
                        contentDescription = "Thumbnail",
                        modifier = Modifier.size(40.dp).clip(ExpressiveSquircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                        contentDescription = if (file.isDirectory) "Folder" else "File",
                        tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(40.dp)
                    )
                }
            },
            headlineContent = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(formattedDate)
                    if (!file.isDirectory) {
                        Text(formatFileSize(file.size))
                    }
                }
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileGridItem(
    file: FileModel,
    formattedDate: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = 0.8f,
            stiffness = 380f
        ),
        label = "gridItemScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = androidx.compose.foundation.LocalIndication.current,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = if (isSelected) ExpressiveShapes.large else ExpressiveSquircleShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!file.isDirectory && (FileCategories.Images.extensions.contains(file.name.substringAfterLast('.').lowercase()) || 
                FileCategories.Videos.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                FileCategories.APKs.extensions.contains(file.name.substringAfterLast('.').lowercase()) ||
                FileCategories.Audio.extensions.contains(file.name.substringAfterLast('.').lowercase()))) {
                AsyncImage(
                    model = File(file.absolutePath),
                    contentDescription = "Thumbnail",
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                    contentDescription = if (file.isDirectory) "Folder" else "File",
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
            Text(
                text = file.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(formattedDate)
                    if (!file.isDirectory) {
                        append(" | ")
                        append(formatFileSize(file.size))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}



@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Folder") },
        shape = ExpressiveSquircleShape,
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder Name") },
                singleLine = true
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create File") },
        shape = ExpressiveSquircleShape,
        text = {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                label = { Text("File Name (e.g., text.txt)") },
                singleLine = true
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(fileName) },
                enabled = fileName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun RenameDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(currentName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        shape = ExpressiveSquircleShape,
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New Name") },
                singleLine = true
            )
        },
        confirmButton = {
            FilledTonalButton(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotBlank() && newName != currentName
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ExpandableFabMenu(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    fabIconRotation: Float,
    onCreateFileClick: () -> Unit,
    onCreateFolderClick: () -> Unit
) {
    val fabShape = if (isExpanded) ExpressiveSquircleShape else ExpressiveShapes.large

    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + slideInVertically(initialOffsetY = { 50 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { 50 })
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = onCreateFileClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = ExpressivePillShape,
                    text = { Text("New File") },
                    icon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = "New File") }
                )
                ExtendedFloatingActionButton(
                    onClick = onCreateFolderClick,
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    shape = ExpressivePillShape,
                    text = { Text("New Folder") },
                    icon = { Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder") }
                )
            }
        }
        FloatingActionButton(
            onClick = onToggleExpand,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            shape = fabShape
        ) {
            Icon(
                Icons.Default.Add, 
                contentDescription = "Create new",
                modifier = Modifier.rotate(fabIconRotation)
            )
        }
    }
}

@Composable
fun ActiveFiltersRow(
    filters: SearchFilters,
    onClearFilter: (SearchFilters) -> Unit
) {
    val activeChips = mutableListOf<Pair<String, SearchFilters>>()
    
    if (filters.itemType != null && filters.itemType != "Any") {
        activeChips.add(Pair(filters.itemType, filters.copy(itemType = null)))
    }
    
    if (filters.fileType != null) {
        activeChips.add(Pair(filters.fileType, filters.copy(fileType = null)))
    }
    if (filters.minSize != null || filters.maxSize != null) {
        val label = when {
            filters.minSize != null && filters.maxSize != null -> "Size: ${filters.minSize / (1024*1024)}MB - ${filters.maxSize / (1024*1024)}MB"
            filters.minSize != null -> "Size: >${filters.minSize / (1024*1024)}MB"
            else -> "Size: <${filters.maxSize!! / (1024*1024)}MB"
        }
        activeChips.add(Pair(label, filters.copy(minSize = null, maxSize = null)))
    }
    if (filters.minDateMillis != null || filters.maxDateMillis != null) {
        activeChips.add(Pair("Date Filter", filters.copy(minDateMillis = null, maxDateMillis = null)))
    }

    if (activeChips.isNotEmpty()) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activeChips) { (label, updatedFilter) ->
                InputChip(
                    selected = true,
                    onClick = { onClearFilter(updatedFilter) },
                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                    trailingIcon = {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                )
            }
        }
    }
}
