package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.ExperimentalSharedTransitionApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.presentation.browser.BrowserState
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.utils.formatFileSize
import dev.qtremors.arcile.presentation.filterAndSortFiles
import dev.qtremors.arcile.presentation.ClipboardOperation
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.ui.components.Breadcrumbs
import dev.qtremors.arcile.presentation.ui.components.PasteConflictDialog
import dev.qtremors.arcile.presentation.ui.components.SearchFiltersBottomSheet
import dev.qtremors.arcile.presentation.ui.components.SearchTopBar
import dev.qtremors.arcile.presentation.ui.components.SortOptionDialog
import dev.qtremors.arcile.presentation.ui.components.TopBarAction
import dev.qtremors.arcile.presentation.ui.components.dialogs.MixedDeleteExplanationDialog
import dev.qtremors.arcile.presentation.ui.components.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.presentation.ui.components.dialogs.RenameDialog
import dev.qtremors.arcile.presentation.ui.components.dialogs.CreateFolderDialog
import dev.qtremors.arcile.presentation.ui.components.dialogs.CreateFileDialog
import dev.qtremors.arcile.presentation.ui.components.lists.ActiveFiltersRow
import dev.qtremors.arcile.presentation.ui.components.lists.FileGrid
import dev.qtremors.arcile.presentation.ui.components.lists.FileList
import dev.qtremors.arcile.presentation.ui.components.lists.FileItemRow
import dev.qtremors.arcile.presentation.ui.components.menus.ExpandableFabMenu
import dev.qtremors.arcile.presentation.ui.components.EmptyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import dev.qtremors.arcile.domain.FileCategories
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import dev.qtremors.arcile.R
import java.io.File
import dev.qtremors.arcile.presentation.utils.rememberDateFormatter
import java.util.Date

import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.qtremors.arcile.presentation.ui.components.ArcilePullRefreshIndicator
import dev.qtremors.arcile.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.domain.BrowserViewMode

/**
 * Full-featured file browser screen.
 *
 * Supports list and grid views, multi-select with range selection, inline search with filters,
 * file creation, rename, delete (via trash), copy/cut/paste clipboard, share, and pull-to-refresh.
 *
 * @param state Current [BrowserState] containing file list, selection, search, and clipboard state.
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
private data class FileManagerContentKey(
    val isSearch: Boolean,
    val path: String,
    val category: String?,
    val isRoot: Boolean
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun BrowserScreen(
    state: BrowserState,
    onNavigateBack: () -> Unit,
    onNavigateTo: (String) -> Unit,
    onOpenFile: (String) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectMultiple: (List<String>) -> Unit,
    onClearSelection: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onCreateFile: (String) -> Unit,
    onRequestDeleteSelected: () -> Unit,
    onConfirmDelete: () -> Unit,
    onTogglePermanentDelete: () -> Unit,
    onDismissDeleteConfirmation: () -> Unit,
    onRenameFile: (String, String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onPresentationChange: (BrowserPresentationPreferences, Boolean) -> Unit,
    onClearError: () -> Unit,
    onCopySelected: () -> Unit,
    onCutSelected: () -> Unit,
    onPasteFromClipboard: () -> Unit,
    onCancelClipboard: () -> Unit,
    onShareSelected: () -> Unit,
    onDismissConflictDialog: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onSearchFiltersChange: (dev.qtremors.arcile.domain.SearchFilters) -> Unit = {},
    onToggleSearchFilterMenu: (Boolean) -> Unit = {},
    onResolvingConflicts: (Map<String, dev.qtremors.arcile.domain.ConflictResolution>) -> Unit = {},
    onPinToQuickAccess: (String, String) -> Unit = { _, _ -> },
    onNativeRequestResult: (Boolean) -> Unit = {},
    nativeRequestFlow: kotlinx.coroutines.flow.SharedFlow<android.content.IntentSender>? = null
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

    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        onNativeRequestResult(result.resultCode == android.app.Activity.RESULT_OK)
    }

    LaunchedEffect(nativeRequestFlow) {
        nativeRequestFlow?.collect { sender ->
            launcher.launch(androidx.activity.result.IntentSenderRequest.Builder(sender).build())
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    var showLoading by remember(state.isLoading) { mutableStateOf(false) }
    LaunchedEffect(state.isLoading) {
        if (state.isLoading) {
            delay(150)
            showLoading = true
        } else {
            showLoading = false
        }
    }

    // Always show full folder contents — search results only appear in the dropdown
    val displayedFiles = remember(state.files, state.browserSortOption) {
        filterAndSortFiles(state.files, "", state.browserSortOption)
    }
    val currentPresentation = remember(
        state.browserSortOption,
        state.browserViewMode,
        state.browserListZoom,
        state.browserGridMinCellSize
    ) {
        BrowserPresentationPreferences(
            sortOption = state.browserSortOption,
            viewMode = state.browserViewMode,
            listZoom = state.browserListZoom,
            gridMinCellSize = state.browserGridMinCellSize
        )
    }
    val currentVolume = remember(state.currentVolumeId, state.storageVolumes) {
        state.storageVolumes.firstOrNull { it.id == state.currentVolumeId }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

    LaunchedEffect(state.browserSortOption, state.currentPath, state.activeCategoryName) {
        if (displayedFiles.isNotEmpty()) {
            listState.scrollToItem(0)
            gridState.scrollToItem(0)
        }
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
            val action = if (clipboard.operation == ClipboardOperation.COPY) context.getString(R.string.clipboard_copied) else context.getString(R.string.clipboard_cut)
            val count = clipboard.sourcePaths.size
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.clipboard_feedback, count, action))
            }
        }
    }

    // Show error as Snackbar instead of blocking dialog
    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            onClearError()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(errorMsg)
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
                    val searchPlaceholder = if (state.isCategoryScreen) {
                        "Search ${state.activeCategoryName.lowercase()}..."
                    } else {
                        stringResource(R.string.search_placeholder)
                    }
                    SearchTopBar(
                        query = state.browserSearchQuery,
                        onQueryChange = onSearchQueryChange,
                        onClose = {
                            showSearchBar = false
                            onClearSearch()
                        },
                        onFilterClick = { onToggleSearchFilterMenu(true) },
                        placeholder = searchPlaceholder
                    )

                    ActiveFiltersRow(
                        filters = state.activeSearchFilters,
                        onClearFilter = { clearedFilters -> onSearchFiltersChange(clearedFilters) }
                    )
                }
            } else {
                ArcileTopBar(
                    title = stringResource(R.string.browse_title),
                    selectionCount = state.selectedFiles.size,
                    showBackArrow = true,
                    showSortAction = !state.isVolumeRootScreen,
                    showNewFolderAction = !state.isVolumeRootScreen && !state.isCategoryScreen,
                    showPinAction = !state.isVolumeRootScreen && !state.isCategoryScreen && state.currentPath.isNotEmpty(),
                    isGridView = state.browserViewMode == BrowserViewMode.GRID,
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
                            TopBarAction.PinToQuickAccess -> {
                                state.currentPath?.let { path ->
                                    val label = java.io.File(path).name
                                    onPinToQuickAccess(path, label)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar("Pinned '$label' to Quick Access")
                                    }
                                }
                            }
                            TopBarAction.DeleteSelected -> onRequestDeleteSelected()
                            TopBarAction.Rename -> if (state.selectedFiles.size == 1) showRenameDialog = true
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
            if (state.selectedFiles.isEmpty() && !showSearchBar && !state.isVolumeRootScreen && !state.isCategoryScreen) {
                Box {
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        dev.qtremors.arcile.presentation.ui.components.menus.ExpandableFabMenu(
                            isExpanded = isFabExpanded,
                            onToggleExpand = { isFabExpanded = !isFabExpanded },
                            fabIconRotation = fabIconRotation,
                            items = listOf(
                                dev.qtremors.arcile.presentation.ui.components.menus.FabMenuItem(
                                    label = "New Folder",
                                    icon = androidx.compose.material.icons.Icons.Default.CreateNewFolder,
                                    onClick = {
                                        isFabExpanded = false
                                        showCreateFolderDialog = true
                                    }
                                ),
                                dev.qtremors.arcile.presentation.ui.components.menus.FabMenuItem(
                                    label = "New File",
                                    icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile,
                                    onClick = {
                                        isFabExpanded = false
                                        showCreateFileDialog = true
                                    }
                                )
                            )
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

            val targetKey = FileManagerContentKey(
                isSearch = searchHasCompleted,
                path = state.currentPath,
                category = state.activeCategoryName,
                isRoot = state.isVolumeRootScreen
            )

            Column(modifier = Modifier.fillMaxSize()) {
                if (targetKey.isSearch) {
                            // Search results in the content area
                            if (state.searchResults.isEmpty()) {
                                EmptyState(
                                    icon = Icons.Default.SearchOff,
                                    title = stringResource(R.string.no_results_found),
                                    description = stringResource(R.string.no_results_description, state.browserSearchQuery),
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                val formatter = rememberDateFormatter("MMM dd, yyyy")
                                LazyColumn(modifier = Modifier.weight(1f)) {
                                    items(state.searchResults, key = { "${it.absolutePath}_${it.hashCode()}" }) { file ->
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
                            if (!state.isVolumeRootScreen) {
                                Breadcrumbs(
                                    currentPath = state.currentPath,
                                    storageVolumes = state.storageVolumes,
                                    onPathSegmentClick = { path ->
                                        onNavigateTo(path)
                                    }
                                )
                            }

                            if (currentVolume?.kind == StorageKind.OTG || currentVolume?.kind == StorageKind.EXTERNAL_UNCLASSIFIED) {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    shape = MaterialTheme.shapes.extraLarge,
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                                ) {
                                    Text(
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

                            val pullRefreshState = rememberPullToRefreshState()

                            PullToRefreshBox(
                                isRefreshing = isRefreshing,
                                onRefresh = onRefresh,
                                state = pullRefreshState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                indicator = {
                                    ArcilePullRefreshIndicator(
                                        isRefreshing = isRefreshing,
                                        state = pullRefreshState
                                    )
                                }
                            ) {
                                if (showLoading && state.files.isEmpty() && !isRefreshing) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        LoadingIndicator()
                                    }
                                } else if (state.isVolumeRootScreen) {
                                    dev.qtremors.arcile.presentation.ui.components.lists.VolumeRootList(
                                        volumes = state.storageVolumes,
                                        onNavigateTo = onNavigateTo,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (displayedFiles.isEmpty() && !state.isLoading) {
                                    EmptyState(
                                        icon = Icons.Default.FolderOff,
                                        title = stringResource(R.string.empty_directory),
                                        description = stringResource(R.string.empty_directory_description),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else if (state.browserViewMode == BrowserViewMode.GRID && !state.isVolumeRootScreen) {
                                     FileGrid(
                                         files = displayedFiles,
                                         selectedFiles = state.selectedFiles,
                                        onNavigateTo = onNavigateTo,
                                        onOpenFile = onOpenFile,
                                         onToggleSelection = onToggleSelection,
                                         onSelectMultiple = onSelectMultiple,
                                         modifier = Modifier.fillMaxSize(),
                                         gridState = gridState,
                                         minCellSize = state.browserGridMinCellSize.dp,
                                         folderStatsByPath = state.folderStatsByPath,
                                         folderStatsLoadingPaths = state.folderStatsLoadingPaths
                                     )
                                 } else {
                                     FileList(
                                        files = displayedFiles,
                                        selectedFiles = state.selectedFiles,
                                        onNavigateTo = onNavigateTo,
                                        onOpenFile = onOpenFile,
                                         onToggleSelection = onToggleSelection,
                                         onSelectMultiple = onSelectMultiple,
                                         modifier = Modifier.fillMaxSize(),
                                         listState = listState,
                                         zoom = state.browserListZoom,
                                         folderStatsByPath = state.folderStatsByPath,
                                         folderStatsLoadingPaths = state.folderStatsLoadingPaths
                                     )
                                 }
                            }
                }
            }

            if (isFabExpanded) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { isFabExpanded = false }
                        )
                )
            }
        }
    }

    if (state.isSearchFilterMenuVisible) {
        SearchFiltersBottomSheet(
            currentFilters = state.activeSearchFilters,
            onApplyFilters = { onSearchFiltersChange(it) },
            onDismiss = { onToggleSearchFilterMenu(false) },
            showCategoryFilter = !state.isCategoryScreen
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
            MixedDeleteExplanationDialog(
                onDismiss = onDismissDeleteConfirmation
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
                title = stringResource(R.string.sort_folder_title),
                selectedPreferences = currentPresentation,
                showApplyToSubfolders = !state.isCategoryScreen,
                onDismiss = { showSortDialog = false },
                onApply = { presentation, applyToSubfolders ->
                    onPresentationChange(presentation, applyToSubfolders)
                    showSortDialog = false
                }
            )
        }

        if (state.showConflictDialog && state.pasteConflicts.isNotEmpty()) {
            PasteConflictDialog(
                conflicts = state.pasteConflicts,
                onResolve = onResolvingConflicts,
                onDismiss = onDismissConflictDialog
            )
        }


    }
