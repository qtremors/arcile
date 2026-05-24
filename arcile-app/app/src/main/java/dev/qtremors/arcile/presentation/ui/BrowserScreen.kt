@file:Suppress("LocalContextGetResourceValueCall")

package dev.qtremors.arcile.presentation.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import dev.qtremors.arcile.presentation.ui.components.dialogs.CreateFakeFileDialog
import dev.qtremors.arcile.presentation.ui.components.dialogs.CreateFolderDialog
import dev.qtremors.arcile.presentation.ui.components.dialogs.CreateFileDialog
import dev.qtremors.arcile.presentation.ui.components.rememberArcileHaptics
import dev.qtremors.arcile.ui.theme.LocalSemanticColors

import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding
import dev.qtremors.arcile.ui.theme.spacing
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.ArchiveFormat
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.presentation.browser.BrowserState
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.presentation.buildFolderTabs
import dev.qtremors.arcile.presentation.filterFilesByFolderTab
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.utils.formatFileSize
import dev.qtremors.arcile.presentation.filterAndSortFiles
import dev.qtremors.arcile.presentation.ClipboardOperation
import dev.qtremors.arcile.presentation.ui.components.ArcileSnackbarHost
import dev.qtremors.arcile.presentation.ui.components.ArcileTopBar
import dev.qtremors.arcile.presentation.ui.components.Breadcrumbs
import dev.qtremors.arcile.presentation.ui.components.FolderTabsRow
import dev.qtremors.arcile.presentation.ui.components.PasteConflictDialog
import dev.qtremors.arcile.presentation.ui.components.SearchFiltersBottomSheet
import dev.qtremors.arcile.presentation.ui.components.SearchTopBar
import dev.qtremors.arcile.presentation.ui.components.SortOptionDialog
import dev.qtremors.arcile.presentation.ui.components.TopBarAction
import dev.qtremors.arcile.presentation.ui.components.ToolbarAction
import dev.qtremors.arcile.presentation.ui.components.dialogs.PropertiesDialog
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
import kotlinx.coroutines.flow.collect

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
import dev.qtremors.arcile.presentation.browser.BrowserFileOperationUiState
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
import dev.qtremors.arcile.presentation.operations.OperationCompletionStatus
import dev.qtremors.arcile.presentation.operations.rememberSmoothedProgress
import dev.qtremors.arcile.presentation.asString
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures

/**
 * Full-featured file browser screen.
 *
 * Supports list and grid views, multi-select with range selection, inline search with filters,
 * file creation, rename, delete (via trash), copy/cut/paste clipboard, share, and pull-to-refresh.
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
    onCreateFakeFile: (String, Long) -> Unit,
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
    onClearFileOperationStatusMessage: () -> Unit = {},
    onOpenProperties: () -> Unit = {},
    onDismissProperties: () -> Unit = {},
    onClearActiveFileOperation: () -> Unit = {},
    onDismissConflictDialog: () -> Unit = {},
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
    onSearchFiltersChange: (dev.qtremors.arcile.domain.SearchFilters) -> Unit = {},
    onToggleSearchFilterMenu: (Boolean) -> Unit = {},
    onResolvingConflicts: (Map<String, dev.qtremors.arcile.domain.ConflictResolution>) -> Unit = {},
    onPinToQuickAccess: (String, String) -> Unit = { _, _ -> },
    onNativeRequestResult: (Boolean) -> Unit = {},
    onInvertSelection: (List<String>) -> Unit = {},
    onSelectAll: (List<String>) -> Unit = {},
    onRemoveFromClipboard: (String) -> Unit = {},
    onSelectFolderTab: (String?) -> Unit = {},
    onExtractSelectedArchive: (String?) -> Unit = {},
    onExtractSelectedArchiveToFolder: (String?) -> Unit = {},
    onCreateZipFromSelection: () -> Unit = {},
    onCreateArchiveFromSelection: (String, ArchiveFormat, String?) -> Unit = { _, _, _ -> },
    onUndoLastTrashMove: () -> Unit = {},
    onClearPendingTrashUndo: () -> Unit = {},
    nativeRequestFlow: kotlinx.coroutines.flow.SharedFlow<android.content.IntentSender>? = null
) {
    val haptics = rememberArcileHaptics()
    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateFileDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateFakeFileDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateArchiveDialog by rememberSaveable { mutableStateOf(false) }
    var showExtractArchiveDialog by rememberSaveable { mutableStateOf(false) }
    var showSortDialog by rememberSaveable { mutableStateOf(false) }
    var showClipboardContents by rememberSaveable { mutableStateOf(false) }
    var showSearchBar by rememberSaveable { mutableStateOf(state.browserSearchQuery.isNotEmpty()) }
    
    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
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

    val tabFilteredFiles = remember(state.files, state.selectedFolderTabPath) {
        filterFilesByFolderTab(state.files, state.selectedFolderTabPath)
    }
    val displayedFiles = remember(tabFilteredFiles, state.browserSortOption) {
        filterAndSortFiles(tabFilteredFiles, "", state.browserSortOption)
    }
    val sortedCategoryFiles = remember(state.files, state.browserSortOption) {
        filterAndSortFiles(state.files, "", state.browserSortOption)
    }
    val currentPresentation = remember(
        state.browserSortOption,
        state.browserViewMode,
        state.browserListZoom,
        state.browserGridMinCellSize,
        state.browserShowThumbnails
    ) {
        BrowserPresentationPreferences(
            sortOption = state.browserSortOption,
            viewMode = state.browserViewMode,
            listZoom = state.browserListZoom,
            gridMinCellSize = state.browserGridMinCellSize,
            showThumbnails = state.browserShowThumbnails
        )
    }
    val currentVolume = remember(state.currentVolumeId, state.storageVolumes) {
        state.storageVolumes.firstOrNull { it.id == state.currentVolumeId }
    }
    val categoryFolderTabs = remember(state.isCategoryScreen, sortedCategoryFiles, context) {
        if (state.isCategoryScreen) {
            buildFolderTabs(sortedCategoryFiles, context.getString(R.string.all_files))
        } else {
            emptyList()
        }
    }
    val selectedCategoryFolderTabIndex = remember(categoryFolderTabs, state.selectedFolderTabPath) {
        categoryFolderTabs.indexOfFirst { it.path == state.selectedFolderTabPath }.takeIf { it >= 0 } ?: 0
    }
    val switchCategoryFolderTab: (Int) -> Unit = { direction ->
        if (categoryFolderTabs.size > 1) {
            val nextIndex = (selectedCategoryFolderTabIndex + direction)
                .coerceIn(0, categoryFolderTabs.lastIndex)
            if (nextIndex != selectedCategoryFolderTabIndex) {
                onSelectFolderTab(categoryFolderTabs[nextIndex].path)
            }
        }
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

    val hasModal = showCreateFolderDialog ||
        showCreateFileDialog ||
        showCreateFakeFileDialog ||
        showCreateArchiveDialog ||
        showExtractArchiveDialog ||
        showRenameDialog ||
        showSortDialog ||
        showClipboardContents ||
        state.showConflictDialog ||
        state.isPropertiesVisible ||
        state.showTrashConfirmation ||
        state.showPermanentDeleteConfirmation ||
        state.showMixedDeleteExplanation ||
        isFabExpanded
    val backState = BrowserBackState(
        hasModal = hasModal,
        hasSheet = state.isSearchFilterMenuVisible,
        hasSearch = showSearchBar,
        hasSelection = state.selectedFiles.isNotEmpty(),
        canNavigateFolderUp = !state.isVolumeRootScreen && !state.isCategoryScreen && state.currentPath.isNotBlank(),
        canPopRoute = true
    )
    val handleBrowserBack: () -> Unit = {
        when (resolveBrowserBackAction(backState)) {
            BrowserBackAction.CloseModal -> when {
                showCreateFolderDialog -> showCreateFolderDialog = false
                showCreateFileDialog -> showCreateFileDialog = false
                showCreateFakeFileDialog -> showCreateFakeFileDialog = false
                showCreateArchiveDialog -> showCreateArchiveDialog = false
                showExtractArchiveDialog -> showExtractArchiveDialog = false
                showRenameDialog -> showRenameDialog = false
                showSortDialog -> showSortDialog = false
                showClipboardContents -> showClipboardContents = false
                state.showConflictDialog -> onDismissConflictDialog()
                state.isPropertiesVisible -> onDismissProperties()
                state.showTrashConfirmation || state.showPermanentDeleteConfirmation || state.showMixedDeleteExplanation -> onDismissDeleteConfirmation()
                isFabExpanded -> isFabExpanded = false
            }
            BrowserBackAction.CloseSheet -> onToggleSearchFilterMenu(false)
            BrowserBackAction.CloseSearch -> {
                showSearchBar = false
                onClearSearch()
            }
            BrowserBackAction.ClearSelection -> onClearSelection()
            BrowserBackAction.NavigateFolderUp,
            BrowserBackAction.PopRoute,
            BrowserBackAction.ExitApp -> onNavigateBack()
        }
    }

    PredictiveBackHandler {
        it.collect { }
        handleBrowserBack()
    }

    LaunchedEffect(state.clipboardState) {
        state.clipboardState?.let { clipboard ->
            val action = if (clipboard.operation == ClipboardOperation.COPY) context.getString(R.string.clipboard_copied) else context.getString(R.string.clipboard_cut)
            val count = clipboard.files.size
            coroutineScope.launch {
                snackbarHostState.showSnackbar(context.getString(R.string.clipboard_feedback, count, action))
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            onClearError()
            haptics.error()
            coroutineScope.launch {
                snackbarHostState.showSnackbar(errorMsg.asString(context))
            }
        }
    }
    
    @Composable
    fun ActiveFileOperationFabInternal(
        operation: BrowserFileOperationUiState,
        onCancel: () -> Unit
    ) {
        val byteProgress = operation.totalBytes
            ?.takeIf { it > 0L }
            ?.let { total -> ((operation.bytesCopied ?: 0L).toFloat() / total.toFloat()).coerceIn(0f, 1f) }
        val itemProgress = operation.totalItems
            .takeIf { it > 0 }
            ?.let { operation.completedItems.toFloat() / it.toFloat() }
            ?.coerceIn(0f, 1f)
        val progress = byteProgress ?: itemProgress

        val containerColor = if (operation.isCancelling) {
            MaterialTheme.colorScheme.errorContainer
        } else {
            MaterialTheme.colorScheme.primaryContainer
        }
        val contentColor = if (operation.isCancelling) {
            MaterialTheme.colorScheme.onErrorContainer
        } else {
            MaterialTheme.colorScheme.onPrimaryContainer
        }
        val operationLabel = when {
            operation.isCancelling -> stringResource(R.string.file_operation_cancelling)
            operation.type == BulkFileOperationType.MOVE -> stringResource(R.string.file_operation_moving)
            else -> stringResource(R.string.file_operation_copying)
        }
        val progressLabel = if ((operation.totalBytes ?: 0L) > 0L) {
            stringResource(
                R.string.file_operation_progress_bytes,
                operationLabel,
                formatFileSize(operation.bytesCopied ?: 0L),
                formatFileSize(operation.totalBytes ?: 0L)
            )
        } else if (operation.totalItems > 0) {
            stringResource(
                R.string.file_operation_progress,
                operationLabel,
                operation.completedItems,
                operation.totalItems
            )
        } else {
            operationLabel
        }
        val secondaryLabel = operation.currentPath
            ?.substringAfterLast(File.separatorChar)
            ?.takeIf { it.isNotBlank() }
        val operationIcon = if (operation.type == BulkFileOperationType.MOVE) {
            Icons.Default.ContentCut
        } else {
            Icons.Default.ContentCopy
        }

        ExtendedFloatingActionButton(
            onClick = onCancel,
            modifier = Modifier.testTag("active_file_operation_fab"),
            containerColor = containerColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.extraLarge,
            icon = {
                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (progress == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = contentColor
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = contentColor,
                            trackColor = contentColor.copy(alpha = 0.24f)
                        )
                    }
                    Icon(
                        imageVector = operationIcon,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                }
            },
            text = {
                Column {
                    Text(
                        text = progressLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    secondaryLabel?.let { label ->
                        Text(
                            text = label,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
            }
        )
    }

    LaunchedEffect(state.fileOperationStatusMessage) {
        state.fileOperationStatusMessage?.let { message ->
            val snackbarMessage = message.asString(context)
            onClearFileOperationStatusMessage()
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = snackbarMessage,
                    actionLabel = if (state.pendingTrashUndoIds.isNotEmpty()) context.getString(R.string.undo) else null,
                    withDismissAction = state.pendingTrashUndoIds.isNotEmpty()
                )
                if (result == SnackbarResult.ActionPerformed) {
                    onUndoLastTrashMove()
                } else if (state.pendingTrashUndoIds.isNotEmpty()) {
                    onClearPendingTrashUndo()
                }
            }
        }
    }

    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val isSelectionMode = state.selectedFiles.isNotEmpty()
    val isClipboardActive = state.clipboardState != null
    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 
        (if (isSelectionMode || isClipboardActive) MaterialTheme.spacing.toolbarBottomGap else MaterialTheme.spacing.screenGutter)
    val snackbarPadding = if (isSelectionMode || isClipboardActive) 80.dp else 0.dp
    val layoutDirection = LocalLayoutDirection.current

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
                Column {
                    val searchPlaceholder = if (state.isCategoryScreen) {
                        stringResource(R.string.search_category_placeholder, state.activeCategoryName.lowercase())
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
                val selectedSizeFormatted = if (state.selectedFiles.isNotEmpty()) {
                    formatFileSize(state.selectedFilesTotalSize)
                } else null

                ArcileTopBar(
                    title = if (state.isCategoryScreen) state.activeCategoryName else stringResource(R.string.browse_title),
                    selectionCount = state.selectedFiles.size,
                    selectedSize = selectedSizeFormatted,
                    showBackArrow = true,
                    showSearchAction = true,
                    showSortAction = !state.isVolumeRootScreen,
                    showNewFolderAction = !state.isVolumeRootScreen && !state.isCategoryScreen,
                    showPinAction = !state.isVolumeRootScreen && !state.isCategoryScreen && state.currentPath.isNotEmpty(),
                    isGridView = state.browserViewMode == BrowserViewMode.GRID,
                    scrollBehavior = scrollBehavior,
                    onBackClick = handleBrowserBack,
                    onClearSelection = onClearSelection,
                    onSearchClick = { showSearchBar = true },
                    onSortClick = { showSortDialog = true },
                    onActionSelected = { action ->
                        when (action) {
                            TopBarAction.NewFolder -> showCreateFolderDialog = true
                            TopBarAction.PinToQuickAccess -> {
                                state.currentPath?.let { path ->
                                    val label = java.io.File(path).name
                                    onPinToQuickAccess(path, label)
                                    coroutineScope.launch {
                                        snackbarHostState.showSnackbar(context.getString(R.string.quick_access_pinned, label))
                                    }
                                }
                            }
                            TopBarAction.DeleteSelected -> onRequestDeleteSelected()
                            TopBarAction.Rename -> if (state.selectedFiles.size == 1) showRenameDialog = true
                            TopBarAction.Copy -> onCopySelected()
                            TopBarAction.Cut -> onCutSelected()
                            TopBarAction.Share -> onShareSelected()
                            TopBarAction.SelectAll -> {
                                haptics.selectionChanged()
                                onSelectAll(displayedFiles.map { it.absolutePath })
                            }
                            TopBarAction.InvertSelection -> {
                                haptics.selectionChanged()
                                onInvertSelection(displayedFiles.map { it.absolutePath })
                            }
                            TopBarAction.Properties -> onOpenProperties()
                            else -> {}
                        }
                    }
                )
            }
        },
        bottomBar = {},
        floatingActionButton = {
            if (state.selectedFiles.isEmpty() && !showSearchBar && !state.isVolumeRootScreen && !state.isCategoryScreen && state.clipboardState == null && state.activeFileOperation == null) {
                Box(modifier = Modifier.navigationBarsPadding()) {
                    Box(modifier = Modifier.align(Alignment.BottomEnd)) {
                        dev.qtremors.arcile.presentation.ui.components.menus.ExpandableFabMenu(
                            isExpanded = isFabExpanded,
                            onToggleExpand = { isFabExpanded = !isFabExpanded },
                            fabIconRotation = fabIconRotation,
                            items = listOf(
                                dev.qtremors.arcile.presentation.ui.components.menus.FabMenuItem(
                                    label = stringResource(R.string.new_folder),
                                    icon = androidx.compose.material.icons.Icons.Default.CreateNewFolder,
                                    onClick = {
                                        isFabExpanded = false
                                        showCreateFolderDialog = true
                                    }
                                ),
                                dev.qtremors.arcile.presentation.ui.components.menus.FabMenuItem(
                                    label = stringResource(R.string.new_file),
                                    icon = androidx.compose.material.icons.Icons.AutoMirrored.Filled.InsertDriveFile,
                                    onClick = {
                                        isFabExpanded = false
                                        showCreateFileDialog = true
                                    }
                                ),
                                dev.qtremors.arcile.presentation.ui.components.menus.FabMenuItem(
                                    label = stringResource(R.string.new_fake_file),
                                    icon = androidx.compose.material.icons.Icons.Default.Extension,
                                    onClick = {
                                        isFabExpanded = false
                                        showCreateFakeFileDialog = true
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
                .fillMaxSize()
        ) {
            val searchHasCompleted = showSearchBar && state.browserSearchQuery.isNotEmpty() && !state.isSearching

            val targetKey = FileManagerContentKey(
                isSearch = searchHasCompleted,
                path = state.currentPath,
                category = state.activeCategoryName,
                isRoot = state.isVolumeRootScreen
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
            ) {
                if (targetKey.isSearch) {
                    if (state.searchResults.isEmpty()) {
                        EmptyState(
                            icon = Icons.Default.SearchOff,
                            title = stringResource(R.string.no_results_found),
                            description = stringResource(R.string.no_results_description, state.browserSearchQuery),
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        val formatter = rememberDateFormatter("MMM dd, yyyy")
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(
                                bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + MaterialTheme.spacing.screenGutter
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
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        LoadingIndicator()
                    }
                } else {
                    if (!state.isVolumeRootScreen && !state.isCategoryScreen) {
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

                    if (state.isCategoryScreen) {
                        FolderTabsRow(
                            tabs = categoryFolderTabs,
                            selectedPath = state.selectedFolderTabPath,
                            onSelectTab = onSelectFolderTab
                        )
                    }

                    val pullRefreshState = rememberPullToRefreshState()

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = onRefresh,
                        state = pullRefreshState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .then(
                                if (state.isCategoryScreen && categoryFolderTabs.size > 1) {
                                    Modifier.pointerInput(categoryFolderTabs, selectedCategoryFolderTabIndex) {
                                        var horizontalDrag = 0f
                                        detectHorizontalDragGestures(
                                            onDragStart = { horizontalDrag = 0f },
                                            onHorizontalDrag = { change, dragAmount ->
                                                horizontalDrag += dragAmount
                                                if (kotlin.math.abs(horizontalDrag) > 96f) {
                                                    change.consume()
                                                    switchCategoryFolderTab(if (horizontalDrag < 0f) 1 else -1)
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
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    top = 8.dp,
                                    bottom = bottomContentPadding,
                                    start = padding.calculateLeftPadding(layoutDirection),
                                    end = padding.calculateRightPadding(layoutDirection)
                                )
                            )
                        } else if (displayedFiles.isEmpty() && !state.isLoading) {
                            EmptyState(
                                icon = Icons.Default.FolderOff,
                                title = stringResource(R.string.empty_directory),
                                description = if (state.isCategoryScreen && state.selectedFolderTabPath != null) {
                                    stringResource(R.string.empty_folder_tab_description)
                                } else {
                                    stringResource(R.string.empty_directory_description)
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (state.browserViewMode == BrowserViewMode.GRID) {
                             FileGrid(
                                 files = displayedFiles,
                                 selectedFiles = state.selectedFiles,
                                onNavigateTo = onNavigateTo,
                                onOpenFile = onOpenFile,
                                 onToggleSelection = onToggleSelection,
                                 onSelectMultiple = onSelectMultiple,
                                 showThumbnails = currentPresentation.showThumbnails,
                                 thumbnailLoadingPaused = state.activeFileOperation?.terminalStatus == null && state.activeFileOperation != null,
                                 modifier = Modifier.fillMaxSize(),
                                 gridState = gridState,
                                 minCellSize = state.browserGridMinCellSize.dp,
                                 folderStatsByPath = state.folderStatsByPath,
                                 folderStatsLoadingPaths = state.folderStatsLoadingPaths,
                                 contentPadding = PaddingValues(
                                     top = 8.dp,
                                     bottom = bottomContentPadding,
                                     start = 8.dp,
                                     end = 8.dp
                                 )
                             )
                         } else {
                             FileList(
                                files = displayedFiles,
                                selectedFiles = state.selectedFiles,
                                onNavigateTo = onNavigateTo,
                                onOpenFile = onOpenFile,
                                 onToggleSelection = onToggleSelection,
                                 onSelectMultiple = onSelectMultiple,
                                 showThumbnails = currentPresentation.showThumbnails,
                                 thumbnailLoadingPaused = state.activeFileOperation?.terminalStatus == null && state.activeFileOperation != null,
                                 modifier = Modifier.fillMaxSize(),
                                 listState = listState,
                                 zoom = state.browserListZoom,
                                 folderStatsByPath = state.folderStatsByPath,
                                 folderStatsLoadingPaths = state.folderStatsLoadingPaths,
                                 contentPadding = PaddingValues(
                                     top = 8.dp,
                                     bottom = bottomContentPadding
                                 )
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

            // Floating Selection Toolbar Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.BottomCenter
            ) {
                if (state.selectedFiles.isNotEmpty()) {
                    val selectedArchive = state.selectedFiles.singleOrNull()?.let { ArchiveFormat.isSupported(it) } == true
                    val mainActions = mutableListOf<ToolbarAction>()
                    mainActions.add(ToolbarAction(
                        icon = Icons.Default.ContentCopy,
                        contentDescription = stringResource(R.string.action_copy),
                        onClick = onCopySelected
                    ))
                    mainActions.add(ToolbarAction(
                        icon = Icons.Default.ContentCut,
                        contentDescription = stringResource(R.string.action_cut),
                        onClick = onCutSelected
                    ))
                    mainActions.add(ToolbarAction(
                        icon = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.action_delete_selected),
                        tint = MaterialTheme.colorScheme.error,
                        onClick = onRequestDeleteSelected
                    ))
                    if (state.selectedFiles.size == 1) {
                        mainActions.add(ToolbarAction(
                            icon = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.action_rename),
                            onClick = { showRenameDialog = true }
                        ))
                    }
                    
                    dev.qtremors.arcile.presentation.ui.components.FloatingSelectionToolbar(
                        isVisible = true,
                        actions = mainActions,
                        moreContent = {
                            var showSelectionMenu by rememberSaveable { mutableStateOf(false) }
                            Box {
                                Surface(
                                    onClick = { showSelectionMenu = true },
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    shadowElevation = 4.dp,
                                    tonalElevation = 4.dp,
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
                                    expanded = showSelectionMenu,
                                    onDismissRequest = { showSelectionMenu = false }
                                ) {
                                    val menuActions = remember(onShareSelected, displayedFiles, state.selectedFiles) {
                                        mutableListOf<@Composable () -> Unit>().apply {
                                            add {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.archive_compress_zip)) },
                                                    leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                                                    onClick = {
                                                        showSelectionMenu = false
                                                        showCreateArchiveDialog = true
                                                    }
                                                )
                                            }
                                            if (selectedArchive) {
                                                add {
                                                    androidx.compose.material3.DropdownMenuItem(
                                                        text = { Text(stringResource(R.string.archive_extract_here)) },
                                                        leadingIcon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
                                                        onClick = {
                                                            showSelectionMenu = false
                                                            showExtractArchiveDialog = true
                                                        }
                                                    )
                                                }
                                            }
                                            add {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.share)) },
                                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                                    onClick = {
                                                        showSelectionMenu = false
                                                        onShareSelected()
                                                    }
                                                )
                                            }
                                            add {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.select_all)) },
                                                    leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                                    onClick = {
                                                        showSelectionMenu = false
                                                        onSelectMultiple(displayedFiles.map { it.absolutePath })
                                                    }
                                                )
                                            }
                                            add {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.select_range)) },
                                                    leadingIcon = { Icon(Icons.Default.SelectAll, contentDescription = null) },
                                                    onClick = {
                                                        showSelectionMenu = false
                                                        val selectedIndexes = displayedFiles.mapIndexedNotNull { index, file ->
                                                            index.takeIf { state.selectedFiles.contains(file.absolutePath) }
                                                        }
                                                        if (selectedIndexes.isNotEmpty()) {
                                                            val start = selectedIndexes.minOrNull() ?: 0
                                                            val end = selectedIndexes.maxOrNull() ?: start
                                                            onSelectMultiple(displayedFiles.subList(start, end + 1).map { it.absolutePath })
                                                        }
                                                    }
                                                )
                                            }
                                            add {
                                                androidx.compose.material3.DropdownMenuItem(
                                                    text = { Text(stringResource(R.string.properties_title)) },
                                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                                    onClick = {
                                                        showSelectionMenu = false
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
                } else if (state.clipboardState != null || state.activeFileOperation != null) {
                    val clipboard = state.clipboardState
                    val activeOp = state.activeFileOperation

                    // ── Smoothed Progress Engine ──
                    val smoothedState = rememberSmoothedProgress()

                    // Feed raw progress into the smoothing engine
                    LaunchedEffect(activeOp) {
                        if (activeOp != null) {
                            // Detect terminal status — snap to full fill instantly
                            val terminal = activeOp.terminalStatus
                            if (terminal != null) {
                                smoothedState.markComplete(terminal)
                                when (terminal) {
                                    OperationCompletionStatus.SUCCESS -> haptics.success()
                                    OperationCompletionStatus.FAILED,
                                    OperationCompletionStatus.CANCELLED -> haptics.error()
                                }
                            } else {
                                // Initialize on first progress event
                                if (smoothedState.operationStartTime == 0L) {
                                    smoothedState.reset(activeOp.startTimeMillis)
                                }
                                val byteProgress = activeOp.totalBytes
                                    ?.takeIf { it > 0L }
                                    ?.let { total -> ((activeOp.bytesCopied ?: 0L).toFloat() / total.toFloat()).coerceIn(0f, 1f) }
                                val itemProgress = activeOp.totalItems
                                    .takeIf { it > 0 }
                                    ?.let { activeOp.completedItems.toFloat() / it.toFloat() }
                                    ?.coerceIn(0f, 1f)
                                val rawProgress = byteProgress ?: itemProgress
                                if (rawProgress != null) {
                                    smoothedState.updateTarget(rawProgress)
                                }
                            }
                        } else {
                            // Operation cleared from ViewModel — reset for next time
                            smoothedState.reset()
                        }
                    }

                    // Auto-dismiss: clear operation state after animation finishes
                    LaunchedEffect(smoothedState.isAnimationFinished) {
                        if (smoothedState.isAnimationFinished) {
                            onClearActiveFileOperation()
                            smoothedState.reset()
                        }
                    }

                    val hasActiveProgress = activeOp != null
                    val smoothedProgress = smoothedState.displayedProgress

                    // Determine pill fill color based on terminal status
                    val successColor = LocalSemanticColors.current.success.copy(alpha = 0.25f)
                    val failureColor = MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                    val inProgressColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                    val progressFillColor = when (activeOp?.terminalStatus) {
                        OperationCompletionStatus.SUCCESS -> successColor
                        OperationCompletionStatus.FAILED,
                        OperationCompletionStatus.CANCELLED -> failureColor
                        null -> inProgressColor
                    }

                    val actions = if (activeOp != null && activeOp.terminalStatus == null) {
                        listOf(
                            ToolbarAction(
                                icon = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_cancel_transfer),
                                containerColor = MaterialTheme.colorScheme.error,
                                tint = Color.White,
                                onClick = onCancelClipboard
                            )
                        )
                    } else if (activeOp == null) {
                        listOf(
                            ToolbarAction(
                                icon = Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.action_paste_here),
                                onClick = onPasteFromClipboard
                            ),
                            ToolbarAction(
                                icon = Icons.Default.Close,
                                contentDescription = stringResource(R.string.action_cancel_transfer),
                                containerColor = MaterialTheme.colorScheme.error,
                                tint = Color.White,
                                onClick = onCancelClipboard
                            )
                        )
                    } else {
                        // Terminal state — no actions while showing completion fill
                        emptyList()
                    }

                    dev.qtremors.arcile.presentation.ui.components.FloatingSelectionToolbar(
                        isVisible = true,
                        actions = actions,
                        startContent = {
                            Surface(
                                onClick = { if (activeOp == null) showClipboardContents = true },
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                                tonalElevation = 4.dp,
                                shadowElevation = 2.dp,
                                modifier = Modifier
                                    .height(56.dp)
                                    .padding(end = 8.dp)
                                    .widthIn(min = 140.dp)
                                    .animateContentSize()
                            ) {
                                // drawBehind paints the progress fill inside the
                                // existing pill shape without adding a child Box
                                // that could alter the pill's measured size.
                                Box(
                                    contentAlignment = Alignment.CenterStart,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .then(
                                            if (hasActiveProgress) {
                                                Modifier.drawBehind {
                                                    val fillWidth = size.width * smoothedProgress
                                                    drawRect(
                                                        color = progressFillColor,
                                                        size = Size(fillWidth, size.height)
                                                    )
                                                }
                                            } else {
                                                Modifier
                                            }
                                        )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        val opIcon = when {
                                            activeOp?.type == BulkFileOperationType.MOVE || clipboard?.operation == ClipboardOperation.CUT -> Icons.Default.ContentCut
                                            activeOp?.type == BulkFileOperationType.DELETE || activeOp?.type == BulkFileOperationType.TRASH -> Icons.Default.Delete
                                            activeOp?.type == BulkFileOperationType.CREATE_ARCHIVE -> Icons.Default.FolderZip
                                            activeOp?.type == BulkFileOperationType.EXTRACT_ARCHIVE -> Icons.Default.Unarchive
                                            activeOp?.type == BulkFileOperationType.CREATE_FAKE -> Icons.Default.Extension
                                            else -> Icons.Default.ContentCopy
                                        }
                                        val opTint = if (activeOp?.type == BulkFileOperationType.DELETE || activeOp?.type == BulkFileOperationType.TRASH) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                        Icon(
                                            imageVector = opIcon,
                                            contentDescription = null,
                                            tint = opTint,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column(
                                            verticalArrangement = Arrangement.Center,
                                            modifier = Modifier.weight(1f, fill = false)
                                        ) {
                                            val operationTitle = when (activeOp?.type) {
                                                BulkFileOperationType.CREATE_ARCHIVE -> stringResource(R.string.file_operation_creating_archive)
                                                BulkFileOperationType.EXTRACT_ARCHIVE -> stringResource(R.string.file_operation_extracting_archive)
                                                else -> {
                                                    val itemCount = activeOp?.totalItems ?: clipboard?.files?.size ?: 0
                                                    if (itemCount == 1) "1 item" else "$itemCount items"
                                                }
                                            }
                                            Text(
                                                text = operationTitle,
                                                style = MaterialTheme.typography.labelLarge,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            
                                            val subtitle = if (activeOp != null) {
                                                if (activeOp.totalBytes != null && activeOp.totalBytes!! > 0L) {
                                                    val remaining = activeOp.totalBytes!! - (activeOp.bytesCopied ?: 0L)
                                                    formatFileSize(remaining.coerceAtLeast(0L))
                                                } else {
                                                    "${activeOp.completedItems} / ${activeOp.totalItems}"
                                                }
                                            } else {
                                                formatFileSize(clipboard?.totalSize ?: 0L)
                                            }
                                            
                                            Text(
                                                text = subtitle,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    )
                }
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

    if (showCreateFileDialog) {
        CreateFileDialog(
            onDismiss = { showCreateFileDialog = false },
            onConfirm = { fileName ->
                showCreateFileDialog = false
                onCreateFile(fileName)
            }
        )
    }

    if (showCreateFakeFileDialog) {
        CreateFakeFileDialog(
            onDismiss = { showCreateFakeFileDialog = false },
            onConfirm = { fileName, size ->
                showCreateFakeFileDialog = false
                onCreateFakeFile(fileName, size)
            }
        )
    }

    if (showCreateArchiveDialog && state.selectedFiles.isNotEmpty()) {
        val defaultName = remember(state.selectedFiles) {
            state.selectedFiles.singleOrNull()
                ?.let { File(it).nameWithoutExtension }
                ?.ifBlank { "Archive" }
                ?: "Archive"
        }
        CreateArchiveDialog(
            defaultName = defaultName,
            selectedCount = state.selectedFiles.size,
            destinationPath = state.currentPath,
            onDismiss = { showCreateArchiveDialog = false },
            onConfirm = { name, format, password ->
                showCreateArchiveDialog = false
                onCreateArchiveFromSelection(name, format, password)
            }
        )
    }

    if (showExtractArchiveDialog && state.selectedFiles.size == 1) {
        ExtractArchiveDialog(
            archiveName = File(state.selectedFiles.first()).name,
            onDismiss = { showExtractArchiveDialog = false },
            onExtractHere = { password ->
                showExtractArchiveDialog = false
                onExtractSelectedArchive(password)
            },
            onExtractToFolder = { password ->
                showExtractArchiveDialog = false
                onExtractSelectedArchiveToFolder(password)
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

    if (state.isPropertiesVisible) {
        PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = onDismissProperties
        )
    }

    if (showClipboardContents && state.clipboardState != null) {
        dev.qtremors.arcile.presentation.ui.components.dialogs.ClipboardContentsDialog(
            state = state.clipboardState,
            onRemoveItem = onRemoveFromClipboard,
            onDismiss = { showClipboardContents = false }
        )
    }
}

@Composable
private fun CreateArchiveDialog(
    defaultName: String,
    selectedCount: Int,
    destinationPath: String,
    onDismiss: () -> Unit,
    onConfirm: (String, ArchiveFormat, String?) -> Unit
) {
    var archiveName by rememberSaveable(defaultName) { mutableStateOf(defaultName) }
    var format by rememberSaveable { mutableStateOf(ArchiveFormat.ZIP) }
    var usePassword by rememberSaveable { mutableStateOf(false) }
    var password by rememberSaveable { mutableStateOf("") }
    var confirmPassword by rememberSaveable { mutableStateOf("") }
    val trimmedName = archiveName.trim()
    val passwordError = usePassword && password != confirmPassword
    val canCreate = trimmedName.isNotEmpty() && (!usePassword || (password.isNotEmpty() && !passwordError))

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.archive_create_summary, selectedCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = archiveName,
                    onValueChange = { archiveName = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.archive_name)) },
                    suffix = { Text(".${format.extension}") },
                    isError = trimmedName.isEmpty()
                )
                Column {
                    ArchiveFormatChoice(ArchiveFormat.ZIP, format, onSelect = { format = it })
                    ArchiveFormatChoice(ArchiveFormat.SEVEN_Z, format, onSelect = { format = it })
                }
                InputChip(
                    selected = usePassword,
                    onClick = { usePassword = !usePassword },
                    label = { Text(stringResource(R.string.archive_password_protect)) }
                )
                if (usePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.archive_password)) }
                    )
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.archive_confirm_password)) },
                        isError = passwordError,
                        supportingText = if (passwordError) {
                            { Text(stringResource(R.string.archive_password_mismatch)) }
                        } else {
                            null
                        }
                    )
                }
                Text(
                    text = stringResource(R.string.archive_destination, destinationPath),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = {
                    onConfirm(trimmedName, format, password.takeIf { usePassword && it.isNotEmpty() })
                }
            ) {
                Text(stringResource(R.string.archive_create_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun ArchiveFormatChoice(
    option: ArchiveFormat,
    selected: ArchiveFormat,
    onSelect: (ArchiveFormat) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable { onSelect(option) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = option == selected, onClick = { onSelect(option) })
        Text(option.displayName, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ExtractArchiveDialog(
    archiveName: String,
    onDismiss: () -> Unit,
    onExtractHere: (String?) -> Unit,
    onExtractToFolder: (String?) -> Unit
) {
    var password by rememberSaveable { mutableStateOf("") }
    var usePassword by rememberSaveable { mutableStateOf(false) }
    val archivePassword = password.takeIf { usePassword && it.isNotEmpty() }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Unarchive, contentDescription = null) },
        title = { Text(stringResource(R.string.archive_extract_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = archiveName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                InputChip(
                    selected = usePassword,
                    onClick = { usePassword = !usePassword },
                    label = { Text(stringResource(R.string.archive_has_password)) }
                )
                if (usePassword) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.archive_password)) },
                        supportingText = { Text(stringResource(R.string.archive_password_hint)) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExtractToFolder(archivePassword) }) {
                Text(stringResource(R.string.archive_extract_to_folder))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                TextButton(onClick = { onExtractHere(archivePassword) }) {
                    Text(stringResource(R.string.archive_extract_here))
                }
            }
        }
    )
}
