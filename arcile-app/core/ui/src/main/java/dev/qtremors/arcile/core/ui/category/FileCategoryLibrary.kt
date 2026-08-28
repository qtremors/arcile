@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.core.ui.category

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.presentation.formatFileSize
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.ui.ArcileDropdownMenu
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.EmptyState
import dev.qtremors.arcile.core.ui.EmptyStateVariant
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileSnackbarHost
import dev.qtremors.arcile.core.ui.ExpressiveFilterChip
import dev.qtremors.arcile.core.ui.ExpressiveSegmentedRow
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.ArcilePullRefreshIndicator
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import dev.qtremors.arcile.core.ui.theme.ExpressiveShapes
import dev.qtremors.arcile.core.ui.scrollbar.ArcileFastScrollbar
import dev.qtremors.arcile.core.ui.scrollbar.LazyGridScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.LazyListScrollbarState
import dev.qtremors.arcile.core.ui.scrollbar.ScrollbarState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

data class CategoryFolderSummary(
    val path: String,
    val label: String,
    val itemCount: Int,
    val totalSize: Long,
    val lastModified: Long,
    val preview: FileModel?
)

data class CategoryLibraryLabels(
    val searchPlaceholder: String,
    val filesTab: String,
    val foldersTab: String,
    val filesIcon: ImageVector,
    val emptyFilesTitle: String,
    val emptyFilesDescription: String,
    val emptyFoldersTitle: String,
    val emptyFoldersDescription: String,
    val viewSortFilesTitle: String,
    val viewSortFoldersTitle: String,
    val selectedCount: (Int) -> String
)

data class CategoryLibraryFileActionCallbacks(
    val state: CategoryFileActionState,
    val onCopy: () -> Unit,
    val onCut: () -> Unit,
    val onDelete: () -> Unit,
    val onConfirmDelete: () -> Unit,
    val onDismissDelete: () -> Unit,
    val onTogglePermanentDelete: () -> Unit,
    val onToggleShred: () -> Unit,
    val onRename: (String) -> Unit,
    val onCreateZip: () -> Unit,
    val onOpenProperties: () -> Unit,
    val onDismissProperties: () -> Unit,
    val onPasteToFolder: (String) -> Unit,
    val onCancelClipboard: () -> Unit,
    val onRemoveFromClipboard: (String) -> Unit,
    val onClearActiveOperation: () -> Unit,
    val onClearError: () -> Unit,
    val onResolvePasteConflicts: (Map<String, ConflictResolution>) -> Unit,
    val onDismissPasteConflictDialog: () -> Unit
)

private enum class FileCategoryBackAction {
    ClearSelection,
    CloseSearch,
    CloseFolder,
    NavigateBack
}
@Composable
internal fun FileCategoryLibraryContent(
    files: List<FileModel>,
    folders: List<CategoryFolderSummary>,
    selectedPaths: Set<String>,
    query: String,
    searchFilters: SearchFilters,
    tab: CategoryLibraryPage,
    itemPresentation: FileListingPreferences,
    folderPresentation: FileListingPreferences,
    defaultPage: CategoryLibraryPage,
    grouping: CategoryGrouping,
    showFileDetails: Boolean,
    scrollbarEnabled: Boolean,
    isLoading: Boolean,
    folderFilterLabel: String?,
    folderFilterPath: String?,
    labels: CategoryLibraryLabels,
    onNavigateBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchFiltersChange: (SearchFilters) -> Unit,
    onTabChange: (CategoryLibraryPage) -> Unit,
    onPresentationChange: (CategoryLibraryPage, FileListingPreferences) -> Unit,
    onDefaultPageChange: (CategoryLibraryPage) -> Unit,
    onGroupingChange: (CategoryGrouping) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onClearFolderFilter: () -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onShareSelection: () -> Unit,
    onOpenSelectionWith: () -> Unit,
    fileActions: CategoryLibraryFileActionCallbacks,
    fileItem: @Composable (
        file: FileModel,
        selected: Boolean,
        selectionMode: Boolean,
        onClick: () -> Unit,
        onLongClick: () -> Unit,
        modifier: Modifier
    ) -> Unit,
    folderItem: @Composable (
        folder: CategoryFolderSummary,
        onClick: () -> Unit,
        modifier: Modifier
    ) -> Unit,
    onOpenFile: (FileModel) -> Unit,
    onOpenFolder: (CategoryFolderSummary) -> Unit
) {
    var searchVisible by rememberSaveable {
        mutableStateOf(query.isNotBlank() || searchFilters.hasActiveFilters)
    }
    var optionsVisible by rememberSaveable { mutableStateOf(false) }
    val shellState = rememberCategoryLibraryShellState()
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showClipboardContents by rememberSaveable { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backAction by remember { mutableStateOf<FileCategoryBackAction?>(null) }
    val selectionMode = selectedPaths.isNotEmpty()
    val folderOpen = folderFilterLabel != null
    val activePresentationPage = if (
        tab == CategoryLibraryPage.FOLDERS && folderOpen
    ) {
        CategoryLibraryPage.ITEMS
    } else {
        tab
    }
    val activePresentation = when (activePresentationPage) {
        CategoryLibraryPage.ITEMS -> itemPresentation
        CategoryLibraryPage.FOLDERS -> folderPresentation
    }
    val pagerState = rememberPagerState(
        initialPage = tab.page,
        pageCount = { CategoryLibraryPage.entries.size }
    )
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionErrorMessage = fileActions.state.error?.asString()

    LaunchedEffect(tab) {
        if (pagerState.currentPage != tab.page) {
            pagerState.animateScrollToPage(tab.page)
        }
    }
    LaunchedEffect(pagerState.settledPage) {
        val settledTab = CategoryLibraryPage.entries[pagerState.settledPage]
        if (settledTab != tab) onTabChange(settledTab)
    }
    LaunchedEffect(actionErrorMessage) {
        actionErrorMessage?.let {
            snackbarHostState.showSnackbar(it)
            fileActions.onClearError()
        }
    }
    LaunchedEffect(showClipboardContents, fileActions.state.clipboardState) {
        if (showClipboardContents && fileActions.state.clipboardState == null) {
            showClipboardContents = false
        }
    }

    PredictiveBackHandler { progress ->
        backAction = when {
            selectionMode -> FileCategoryBackAction.ClearSelection
            searchVisible -> FileCategoryBackAction.CloseSearch
            folderOpen -> FileCategoryBackAction.CloseFolder
            else -> FileCategoryBackAction.NavigateBack
        }
        try {
            progress.collect { event -> backProgress = event.progress }
            when (requireNotNull(backAction)) {
                FileCategoryBackAction.ClearSelection -> onClearSelection()
                FileCategoryBackAction.CloseSearch -> {
                    searchVisible = false
                    onQueryChange("")
                }
                FileCategoryBackAction.CloseFolder -> onClearFolderFilter()
                FileCategoryBackAction.NavigateBack -> onNavigateBack()
            }
        } catch (_: CancellationException) {
            // Leave category state unchanged when the gesture is cancelled.
        } finally {
            backProgress = 0f
            backAction = null
        }
    }

    val selectTab: (CategoryLibraryPage) -> Unit = { destination ->
        coroutineScope.launch { pagerState.animateScrollToPage(destination.page) }
    }

    CategoryLibraryShell(
        state = shellState,
        selectionMode = selectionMode,
        searchVisible = searchVisible,
        exitBackProgress = if (
            backAction == FileCategoryBackAction.NavigateBack
        ) {
            backProgress
        } else {
            0f
        },
        chromeBackProgress = if (
            backAction == FileCategoryBackAction.ClearSelection ||
            backAction == FileCategoryBackAction.CloseSearch
        ) {
            backProgress
        } else {
            0f
        },
        topChrome = {
            if (selectionMode) {
                CategorySelectionTopBar(
                    selectedCountText = labels.selectedCount(selectedPaths.size),
                    selectedSizeText = formatFileSize(
                        androidx.compose.ui.platform.LocalContext.current,
                        files.filter { it.absolutePath in selectedPaths }.sumOf(FileModel::size)
                    ),
                    onClearSelection = onClearSelection,
                    onSelectAll = onSelectAll,
                    onInvertSelection = onInvertSelection,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                val hasCurrentItems = when {
                    tab == CategoryLibraryPage.ITEMS || folderOpen -> files.isNotEmpty()
                    else -> folders.isNotEmpty()
                }
                CategoryFloatingTopBar(
                    query = query,
                    searchPlaceholder = labels.searchPlaceholder,
                    showSearchBar = searchVisible,
                    menuActions = buildList {
                        CategoryLibraryPage.entries.forEach { destination ->
                            add(
                                CategoryMenuAction(
                                    label = when (destination) {
                                        CategoryLibraryPage.ITEMS -> labels.filesTab
                                        CategoryLibraryPage.FOLDERS -> labels.foldersTab
                                    },
                                    icon = when (destination) {
                                        CategoryLibraryPage.ITEMS -> labels.filesIcon
                                        CategoryLibraryPage.FOLDERS -> Icons.Default.Folder
                                    },
                                    selected = defaultPage == destination,
                                    onClick = { onDefaultPageChange(destination) }
                                )
                            )
                        }
                        if (tab == CategoryLibraryPage.ITEMS && hasCurrentItems) {
                            add(
                                CategoryMenuAction(
                                    label = stringResource(R.string.select_all),
                                    icon = Icons.Default.SelectAll,
                                    onClick = onSelectAll
                                )
                            )
                        }
                        add(
                            CategoryMenuAction(
                                label = stringResource(R.string.refresh),
                                icon = Icons.Default.Refresh,
                                onClick = onRefresh
                            )
                        )
                    },
                    onSearchClick = {
                        shellState.revealChrome()
                        searchVisible = true
                    },
                    onCloseSearch = {
                        searchVisible = false
                        onQueryChange("")
                    },
                    onQueryChange = onQueryChange,
                    searchFilters = searchFilters,
                    onSearchFiltersChange = onSearchFiltersChange,
                    onViewSort = { optionsVisible = true },
                    onNavigateBack = {
                        if (folderFilterLabel != null) onClearFolderFilter() else onNavigateBack()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        bottomChrome = { isChromeVisible ->
            CategoryBottomChrome(
                visible = isChromeVisible ||
                    fileActions.state.clipboardState != null ||
                    fileActions.state.activeOperation != null,
                selectionMode = selectionMode,
                selectionBackProgress = if (
                    backAction == FileCategoryBackAction.ClearSelection
                ) {
                    backProgress
                } else {
                    0f
                },
                normalContent = {
                    if (
                        fileActions.state.clipboardState != null ||
                        fileActions.state.activeOperation != null
                    ) {
                        CategoryClipboardToolbar(
                            state = fileActions.state,
                            canPaste = folderFilterPath != null,
                            onPaste = {
                                folderFilterPath?.let(fileActions.onPasteToFolder)
                            },
                            onCancel = fileActions.onCancelClipboard,
                            onShowContents = { showClipboardContents = true }
                        )
                    } else {
                        CategoryNavigationBar(
                            tabs = listOf(
                                CategoryTabSpec(
                                    label = labels.filesTab,
                                    icon = labels.filesIcon,
                                    selected = tab == CategoryLibraryPage.ITEMS,
                                    onClick = { selectTab(CategoryLibraryPage.ITEMS) }
                                ),
                                CategoryTabSpec(
                                    label = labels.foldersTab,
                                    icon = Icons.Default.Folder,
                                    selected = tab == CategoryLibraryPage.FOLDERS,
                                    onClick = { selectTab(CategoryLibraryPage.FOLDERS) }
                                )
                            )
                        )
                    }
                },
                selectionContent = {
                    CategoryLibrarySelectionActions(
                        canOpenWith = selectedPaths.size == 1,
                        canRename = selectedPaths.size == 1,
                        actions = fileActions,
                        onShowRename = { showRenameDialog = true },
                        onShare = onShareSelection,
                        onOpenWith = onOpenSelectionWith
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    ) { shellContentPadding ->
        val topContentPadding = shellContentPadding.calculateTopPadding()
        val bottomContentPadding = shellContentPadding.calculateBottomPadding()
        Box(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !selectionMode
        ) { page ->
            val pageTab = CategoryLibraryPage.entries[page]
            val showingFolderContents =
                pageTab == CategoryLibraryPage.FOLDERS && folderOpen
            val pagePresentation = when {
                showingFolderContents -> itemPresentation
                pageTab == CategoryLibraryPage.ITEMS -> itemPresentation
                else -> folderPresentation
            }
            val hasPageItems = if (
                pageTab == CategoryLibraryPage.ITEMS || showingFolderContents
            ) {
                files.isNotEmpty()
            } else {
                folders.isNotEmpty()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (
                            backAction == FileCategoryBackAction.CloseFolder &&
                            showingFolderContents
                        ) {
                            translationX = backProgress * 120.dp.toPx()
                            alpha = 1f - backProgress * 0.5f
                        }
                    }
            ) {
                CategoryPullRefreshPage(
                    isRefreshing = isLoading && hasPageItems,
                    onRefresh = onRefresh
                ) {
                    if (
                        pageTab == CategoryLibraryPage.ITEMS ||
                        showingFolderContents
                    ) {
                        when {
                        isLoading && files.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                        files.isEmpty() -> {
                            EmptyState(
                                variant = EmptyStateVariant.Search,
                                title = labels.emptyFilesTitle,
                                description = labels.emptyFilesDescription,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            CategoryFilesContent(
                                files = files,
                                selectedPaths = selectedPaths,
                                presentation = pagePresentation,
                                grouping = grouping,
                                scrollbarEnabled = scrollbarEnabled,
                                topPadding = topContentPadding,
                                bottomPadding = bottomContentPadding,
                                onToggleSelection = onToggleSelection,
                                onSelectPaths = onSelectPaths,
                                onOpenFile = onOpenFile,
                                fileItem = fileItem
                            )
                        }
                        }
                    } else {
                        when {
                        isLoading && folders.isEmpty() -> {
                            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                LoadingIndicator()
                            }
                        }
                        folders.isEmpty() -> {
                            EmptyState(
                                variant = EmptyStateVariant.Folder,
                                title = labels.emptyFoldersTitle,
                                description = labels.emptyFoldersDescription,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        else -> {
                            CategoryFoldersContent(
                                folders = folders,
                                presentation = pagePresentation,
                                scrollbarEnabled = scrollbarEnabled,
                                clipboardAvailable = fileActions.state.clipboardState != null,
                                topPadding = topContentPadding,
                                bottomPadding = bottomContentPadding,
                                onOpenFolder = onOpenFolder,
                                onPasteToFolder = fileActions.onPasteToFolder,
                                folderItem = folderItem
                            )
                        }
                        }
                    }
                }
            }
        }

        ArcileSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 88.dp)
        )
        }
    }

    if (optionsVisible) {
        CategoryLibraryViewOptionsSheet(
            title = if (tab == CategoryLibraryPage.ITEMS) {
                labels.viewSortFilesTitle
            } else {
                labels.viewSortFoldersTitle
            },
            presentation = activePresentation,
            grouping = grouping,
            showFileDetails = showFileDetails,
            isFolderPage = activePresentationPage == CategoryLibraryPage.FOLDERS,
            onApply = { presentation, updatedGrouping, updatedShowFileDetails ->
                onPresentationChange(activePresentationPage, presentation)
                if (activePresentationPage == CategoryLibraryPage.ITEMS) {
                    onGroupingChange(updatedGrouping)
                    onShowFileDetailsChange(updatedShowFileDetails)
                }
                optionsVisible = false
            },
            onDismiss = { optionsVisible = false }
        )
    }
    if (showRenameDialog && selectedPaths.size == 1) {
        val selected = files.firstOrNull { it.absolutePath in selectedPaths }
        if (selected != null) {
            RenameDialog(
                currentName = selected.name,
                onDismiss = { showRenameDialog = false },
                onConfirm = {
                    fileActions.onRename(it)
                    showRenameDialog = false
                }
            )
        }
    }
    if (fileActions.state.showPasteConflictDialog && fileActions.state.pasteConflicts.isNotEmpty()) {
        PasteConflictDialog(
            conflicts = fileActions.state.pasteConflicts,
            onResolve = fileActions.onResolvePasteConflicts,
            onDismiss = fileActions.onDismissPasteConflictDialog
        )
    }
    if (showClipboardContents) {
        fileActions.state.clipboardState?.let { clipboard ->
            ClipboardContentsDialog(
                state = clipboard,
                onRemoveItem = fileActions.onRemoveFromClipboard,
                onDismiss = { showClipboardContents = false }
            )
        }
    }
    if (
        fileActions.state.showTrashConfirmation ||
        fileActions.state.showPermanentDeleteConfirmation ||
        fileActions.state.showMixedDeleteExplanation
    ) {
        DeleteConfirmationDialog(
            selectedCount = selectedPaths.size,
            isPermanentDeleteChecked =
                fileActions.state.isPermanentDeleteChecked ||
                    fileActions.state.showMixedDeleteExplanation,
            isPermanentDeleteToggleEnabled =
                fileActions.state.isPermanentDeleteToggleEnabled &&
                    !fileActions.state.showMixedDeleteExplanation,
            onConfirm = if (fileActions.state.showMixedDeleteExplanation) {
                ({})
            } else {
                fileActions.onConfirmDelete
            },
            onDismiss = fileActions.onDismissDelete,
            onTogglePermanentDelete = fileActions.onTogglePermanentDelete,
            decision = fileActions.state.deleteDecision,
            isShredChecked = fileActions.state.isShredChecked,
            onToggleShred = fileActions.onToggleShred
        )
    }
    if (fileActions.state.isPropertiesVisible) {
        PropertiesDialog(
            properties = fileActions.state.properties,
            isLoading = fileActions.state.isPropertiesLoading,
            onDismiss = {
                fileActions.onDismissProperties()
                onClearSelection()
            }
        )
    }
}
