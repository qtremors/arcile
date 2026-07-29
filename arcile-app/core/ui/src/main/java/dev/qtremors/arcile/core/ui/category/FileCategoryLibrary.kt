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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.coroutines.delay
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
fun FileCategoryLibrary(
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
    var searchVisible by rememberSaveable { mutableStateOf(false) }
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
                            onShowContents = { showClipboardContents = true },
                            onClearCompleted = fileActions.onClearActiveOperation
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

        SnackbarHost(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPullRefreshPage(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable BoxScope.() -> Unit
) {
    val pullRefreshState = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = pullRefreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            ArcilePullRefreshIndicator(
                isRefreshing = isRefreshing,
                state = pullRefreshState
            )
        },
        content = content
    )
}

private val CategoryLibraryPage.page: Int
    get() = ordinal

@Composable
private fun CategoryFilesContent(
    files: List<FileModel>,
    selectedPaths: Set<String>,
    presentation: FileListingPreferences,
    grouping: CategoryGrouping,
    scrollbarEnabled: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onOpenFile: (FileModel) -> Unit,
    fileItem: @Composable (
        FileModel,
        Boolean,
        Boolean,
        () -> Unit,
        () -> Unit,
        Modifier
    ) -> Unit
) {
    val selectionMode = selectedPaths.isNotEmpty()
    val groups = remember(files, grouping) { groupCategoryFiles(files, grouping) }
    val flatFiles = remember(files, groups, grouping) {
        if (grouping == CategoryGrouping.NONE) files else groups.values.flatten()
    }
    var lastInteractedIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    val haptics = rememberArcileHaptics()
    LaunchedEffect(selectedPaths.isEmpty()) {
        if (selectedPaths.isEmpty()) lastInteractedIndex = null
    }
    fun click(file: FileModel) {
        if (selectionMode) {
            lastInteractedIndex = flatFiles.indexOf(file)
            onToggleSelection(file.absolutePath)
            haptics.selectionChanged()
        } else {
            onOpenFile(file)
        }
    }
    fun longClick(file: FileModel) {
        val index = flatFiles.indexOf(file)
        val previous = lastInteractedIndex
        if (selectionMode && previous != null && previous != index) {
            val start = minOf(previous, index)
            val end = maxOf(previous, index)
            onSelectPaths(flatFiles.subList(start, end + 1).map(FileModel::absolutePath))
            haptics.selectionChanged()
        } else {
            onToggleSelection(file.absolutePath)
            if (selectedPaths.isEmpty()) haptics.selectionStart() else haptics.selectionChanged()
        }
        lastInteractedIndex = index
    }
    val gridContentPadding = PaddingValues(
        start = 12.dp,
        top = topPadding + 8.dp,
        end = 12.dp,
        bottom = bottomPadding
    )
    val listContentPadding = PaddingValues(top = topPadding, bottom = bottomPadding)
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val scrollbarState: ScrollbarState =
        if (presentation.viewMode == FileViewMode.GRID) {
            LazyGridScrollbarState(gridState)
        } else {
            LazyListScrollbarState(listState)
        }
    Box(Modifier.fillMaxSize()) {
        if (presentation.viewMode == FileViewMode.GRID) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(presentation.gridMinCellSize.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = gridContentPadding,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (grouping == CategoryGrouping.NONE) {
                    items(files, key = FileModel::absolutePath) { file ->
                        fileItem(
                            file,
                            file.absolutePath in selectedPaths,
                            selectionMode,
                            { click(file) },
                            { longClick(file) },
                            Modifier.animateItem()
                        )
                    }
                } else {
                    groups.forEach { (group, groupFiles) ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CategorySectionHeader(group.label)
                        }
                        items(groupFiles, key = FileModel::absolutePath) { file ->
                            fileItem(
                                file,
                                file.absolutePath in selectedPaths,
                                selectionMode,
                                { click(file) },
                                { longClick(file) },
                                Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = listContentPadding
            ) {
                if (grouping == CategoryGrouping.NONE) {
                    items(files, key = FileModel::absolutePath) { file ->
                        fileItem(
                            file,
                            file.absolutePath in selectedPaths,
                            selectionMode,
                            { click(file) },
                            { longClick(file) },
                            Modifier.animateItem()
                        )
                    }
                } else {
                    groups.forEach { (group, groupFiles) ->
                        item { CategorySectionHeader(group.label) }
                        items(groupFiles, key = FileModel::absolutePath) { file ->
                            fileItem(
                                file,
                                file.absolutePath in selectedPaths,
                                selectionMode,
                                { click(file) },
                                { longClick(file) },
                                Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
        ArcileFastScrollbar(
            scrollbarState = scrollbarState,
            labelForIndex = { index ->
                categoryFileForLazyIndex(index, files, grouping, groups)?.name.orEmpty()
            },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            contentPadding = if (presentation.viewMode == FileViewMode.GRID) {
                gridContentPadding
            } else {
                listContentPadding
            },
            enabled = scrollbarEnabled
        )
    }
}

@Composable
private fun CategoryFoldersContent(
    folders: List<CategoryFolderSummary>,
    presentation: FileListingPreferences,
    scrollbarEnabled: Boolean,
    clipboardAvailable: Boolean,
    topPadding: androidx.compose.ui.unit.Dp,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onOpenFolder: (CategoryFolderSummary) -> Unit,
    onPasteToFolder: (String) -> Unit,
    folderItem: @Composable (CategoryFolderSummary, () -> Unit, Modifier) -> Unit
) {
    val contentPadding = PaddingValues(
        start = 16.dp,
        top = topPadding + 8.dp,
        end = 16.dp,
        bottom = bottomPadding
    )
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val scrollbarState: ScrollbarState = LazyGridScrollbarState(gridState)
    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(presentation.gridMinCellSize.dp),
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(folders, key = CategoryFolderSummary::path) { folder ->
                CategoryFolderWithPasteAction(
                    folder = folder,
                    clipboardAvailable = clipboardAvailable,
                    onOpenFolder = onOpenFolder,
                    onPasteToFolder = onPasteToFolder,
                    folderItem = folderItem,
                    modifier = Modifier.animateItem()
                )
            }
        }
        ArcileFastScrollbar(
            scrollbarState = scrollbarState,
            labelForIndex = { index -> folders.getOrNull(index)?.label.orEmpty() },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight(),
            contentPadding = contentPadding,
            enabled = scrollbarEnabled
        )
    }
}

@Composable
private fun CategoryFolderWithPasteAction(
    folder: CategoryFolderSummary,
    clipboardAvailable: Boolean,
    onOpenFolder: (CategoryFolderSummary) -> Unit,
    onPasteToFolder: (String) -> Unit,
    folderItem: @Composable (CategoryFolderSummary, () -> Unit, Modifier) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        folderItem(folder, { onOpenFolder(folder) }, Modifier.fillMaxWidth())
        if (clipboardAvailable) {
            Surface(
                onClick = { onPasteToFolder(folder.path) },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = stringResource(R.string.action_paste_here),
                    modifier = Modifier.padding(9.dp).size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun CategoryClipboardToolbar(
    state: CategoryFileActionState,
    canPaste: Boolean,
    onPaste: () -> Unit,
    onCancel: () -> Unit,
    onShowContents: () -> Unit,
    onClearCompleted: () -> Unit
) {
    val clipboard = state.clipboardState
    val operation = state.activeOperation
    LaunchedEffect(operation?.terminalStatus) {
        if (operation?.terminalStatus != null) {
            delay(800L)
            onClearCompleted()
        }
    }
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val rawProgress = operation?.let { active ->
            active.totalBytes
                ?.takeIf { it > 0L }
                ?.let { total ->
                    ((active.bytesCopied ?: 0L).toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
                ?: active.totalItems.takeIf { it > 0 }?.let { total ->
                    (active.completedItems.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                }
        } ?: 0f
        val displayedProgress = if (operation?.terminalStatus != null) 1f else rawProgress
        val progressColor = when (operation?.terminalStatus) {
            OperationCompletionStatus.SUCCESS -> androidx.compose.ui.graphics.Color(0xFF4CAF50)
                .copy(alpha = 0.25f)
            OperationCompletionStatus.FAILED,
            OperationCompletionStatus.CANCELLED -> MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
            null -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        }
        Surface(
            onClick = {
                if (operation == null && clipboard != null) onShowContents()
            },
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            modifier = Modifier
                .height(56.dp)
                .padding(end = 8.dp)
                .width(192.dp)
                .animateContentSize()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (operation != null) {
                            Modifier.drawBehind {
                                drawRect(
                                    color = progressColor,
                                    size = androidx.compose.ui.geometry.Size(
                                        size.width * displayedProgress,
                                        size.height
                                    )
                                )
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when {
                        operation?.type == BulkFileOperationType.MOVE ||
                            clipboard?.operation ==
                            dev.qtremors.arcile.core.storage.domain.ClipboardOperation.CUT ->
                            Icons.Default.ContentCut
                        operation?.type == BulkFileOperationType.CREATE_ARCHIVE ->
                            Icons.Default.FolderZip
                        else -> Icons.Default.ContentCopy
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    val itemCount = operation?.totalItems ?: clipboard?.files?.size ?: 0
                    Text(
                        text = androidx.compose.ui.res.pluralStringResource(
                            R.plurals.clipboard_item_count,
                            itemCount,
                            itemCount
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = operation?.totalBytes
                            ?.takeIf { it > 0L }
                            ?.let { total ->
                                formatFileSize(
                                    (total - (operation.bytesCopied ?: 0L)).coerceAtLeast(0L)
                                )
                            }
                            ?: if (operation != null) {
                                "${operation.completedItems}/${operation.totalItems}"
                            } else {
                                clipboard?.let { formatFileSize(it.totalSize) }.orEmpty()
                            },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        SplitButtonGroup(
            actions = when {
                operation != null && operation.terminalStatus == null -> listOf(
                    ToolbarAction(
                        icon = Icons.Default.Close,
                        contentDescription = stringResource(R.string.action_cancel_transfer),
                        containerColor = MaterialTheme.colorScheme.error,
                        tint = MaterialTheme.colorScheme.onError,
                        onClick = onCancel
                    )
                )
                operation == null && clipboard != null -> buildList {
                    if (canPaste) {
                        add(
                            ToolbarAction(
                                icon = Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.action_paste_here),
                                onClick = onPaste
                            )
                        )
                    }
                    add(
                        ToolbarAction(
                            icon = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_cancel_transfer),
                            containerColor = MaterialTheme.colorScheme.error,
                            tint = MaterialTheme.colorScheme.onError,
                            onClick = onCancel
                        )
                    )
                }
                else -> emptyList()
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            height = 48.dp,
            minWidth = 48.dp,
            iconSize = 24.dp
        )
    }
}

@Composable
private fun CategoryLibrarySelectionActions(
    canOpenWith: Boolean,
    canRename: Boolean,
    actions: CategoryLibraryFileActionCallbacks,
    onShowRename: () -> Unit,
    onShare: () -> Unit,
    onOpenWith: () -> Unit
) {
    var showMore by rememberSaveable { mutableStateOf(false) }
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SplitButtonGroup(
                actions = buildList {
                    add(
                        ToolbarAction(
                            icon = Icons.Default.ContentCopy,
                            contentDescription = stringResource(R.string.action_copy),
                            onClick = actions.onCopy
                        )
                    )
                    add(
                        ToolbarAction(
                            icon = Icons.Default.ContentCut,
                            contentDescription = stringResource(R.string.action_cut),
                            onClick = actions.onCut
                        )
                    )
                    add(
                        ToolbarAction(
                            icon = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            onClick = actions.onDelete
                        )
                    )
                    add(
                        ToolbarAction(
                            icon = Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            onClick = onShare
                        )
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Box {
                androidx.compose.material3.IconButton(onClick = { showMore = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.action_more_options))
                }
                ArcileDropdownMenu(
                    expanded = showMore,
                    onDismissRequest = { showMore = false },
                    items = buildList {
                        if (canRename) {
                            add {
                                ArcileDropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_rename)) },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMore = false
                                        onShowRename()
                                    }
                                )
                            }
                        }
                        add {
                            ArcileDropdownMenuItem(
                                text = { Text(stringResource(R.string.archive_compress_zip)) },
                                leadingIcon = { Icon(Icons.Default.FolderZip, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    actions.onCreateZip()
                                }
                            )
                        }
                        if (canOpenWith) {
                            add {
                                ArcileDropdownMenuItem(
                                    text = { Text(stringResource(R.string.image_gallery_open_with)) },
                                    leadingIcon = {
                                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                    },
                                    onClick = {
                                        showMore = false
                                        onOpenWith()
                                    }
                                )
                            }
                        }
                        add {
                            ArcileDropdownMenuItem(
                                text = { Text(stringResource(R.string.action_properties)) },
                                leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    actions.onOpenProperties()
                                }
                            )
                        }
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryLibraryViewOptionsSheet(
    title: String,
    presentation: FileListingPreferences,
    grouping: CategoryGrouping,
    showFileDetails: Boolean,
    isFolderPage: Boolean,
    onApply: (FileListingPreferences, CategoryGrouping, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val haptics = rememberArcileHaptics()
    var draft by remember(presentation, isFolderPage) {
        mutableStateOf(
            presentation.normalized().let {
                if (isFolderPage) it.copy(viewMode = FileViewMode.GRID) else it
            }
        )
    }
    var draftGrouping by remember(grouping) { mutableStateOf(grouping) }
    var draftShowFileDetails by remember(showFileDetails) {
        mutableStateOf(showFileDetails)
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (!isFolderPage) {
                    CategoryViewModeSection(
                        selected = draft.viewMode,
                        onSelected = { draft = draft.copy(viewMode = it) }
                    )
                }
                CategorySizeSection(
                    preferences = draft,
                    availableWidth = this@BoxWithConstraints.maxWidth,
                    onPreferencesChange = { draft = it }
                )
                CategorySortSection(
                    preferences = draft,
                    onSortChange = { draft = draft.copy(sortOption = it) }
                )
                if (!isFolderPage) {
                    CategoryGroupingSection(
                        selected = draftGrouping,
                        onSelected = { draftGrouping = it }
                    )
                    CategoryThumbnailSection(
                        showThumbnails = draft.showThumbnails,
                        onShowThumbnailsChange = { draft = draft.copy(showThumbnails = it) }
                    )
                    CategoryDetailsSection(
                        showFileDetails = draftShowFileDetails,
                        onShowFileDetailsChange = { draftShowFileDetails = it }
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            haptics.selectionChanged()
                            onDismiss()
                        },
                        shape = ExpressiveShapes.medium
                    ) {
                        Text(stringResource(R.string.cancel))
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = {
                            haptics.selectionChanged()
                            onApply(
                                draft.normalized().let {
                                    if (isFolderPage) {
                                        it.copy(viewMode = FileViewMode.GRID)
                                    } else {
                                        it
                                    }
                                },
                                draftGrouping,
                                draftShowFileDetails
                            )
                        },
                        shape = ExpressiveShapes.medium
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryGroupingSection(
    selected: CategoryGrouping,
    onSelected: (CategoryGrouping) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategorySectionTitle(stringResource(R.string.image_gallery_grouping))
        CategoryGrouping.entries.chunked(2).forEach { options ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { grouping ->
                    ExpressiveFilterChip(
                        selected = selected == grouping,
                        onClick = { onSelected(grouping) },
                        label = {
                            Text(
                                stringResource(
                                    when (grouping) {
                                        CategoryGrouping.NONE -> R.string.category_group_none
                                        CategoryGrouping.DAY -> R.string.category_group_day
                                        CategoryGrouping.WEEK -> R.string.category_group_week
                                        CategoryGrouping.MONTH -> R.string.category_group_month
                                    }
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryViewModeSection(
    selected: FileViewMode,
    onSelected: (FileViewMode) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategorySectionTitle(stringResource(R.string.browser_layout_view_mode))
        ExpressiveSegmentedRow(
            options = FileViewMode.entries,
            selectedOption = selected,
            onOptionSelected = onSelected,
            modifier = Modifier.fillMaxWidth()
        ) { mode ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (mode == FileViewMode.LIST) {
                        Icons.AutoMirrored.Filled.ViewList
                    } else {
                        Icons.Default.GridView
                    },
                    contentDescription = null
                )
                Text(
                    stringResource(
                        if (mode == FileViewMode.LIST) R.string.list_view else R.string.grid_view
                    )
                )
            }
        }
    }
}

@Composable
private fun CategorySizeSection(
    preferences: FileListingPreferences,
    availableWidth: androidx.compose.ui.unit.Dp,
    onPreferencesChange: (FileListingPreferences) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    if (preferences.viewMode == FileViewMode.LIST) {
                        R.string.browser_layout_list_zoom
                    } else {
                        R.string.browser_layout_grid_size
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            val value = if (preferences.viewMode == FileViewMode.LIST) {
                stringResource(
                    R.string.browser_layout_list_zoom_value,
                    (preferences.listZoom * 100).roundToInt()
                )
            } else {
                val columns = max(
                    1,
                    floor(
                        ((availableWidth.value - 32f) / preferences.gridMinCellSize).toDouble()
                    ).toInt()
                )
                stringResource(R.string.browser_layout_grid_columns_value, columns)
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = if (preferences.viewMode == FileViewMode.LIST) {
                preferences.listZoom
            } else {
                preferences.gridMinCellSize
            },
            onValueChange = {
                onPreferencesChange(
                    if (preferences.viewMode == FileViewMode.LIST) {
                        preferences.copy(listZoom = it)
                    } else {
                        preferences.copy(gridMinCellSize = it)
                    }
                )
            },
            valueRange = if (preferences.viewMode == FileViewMode.LIST) {
                FileListingPreferences.MIN_LIST_ZOOM..FileListingPreferences.MAX_LIST_ZOOM
            } else {
                FileListingPreferences.MIN_GRID_MIN_CELL_SIZE..
                    FileListingPreferences.MAX_GRID_MIN_CELL_SIZE
            },
            steps = if (preferences.viewMode == FileViewMode.LIST) 7 else 1
        )
    }
}

@Composable
private fun CategorySortSection(
    preferences: FileListingPreferences,
    onSortChange: (FileSortOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CategorySectionTitle(stringResource(R.string.action_sort))
        FileSortOption.entries.chunked(2).forEach { options ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { option ->
                    ExpressiveFilterChip(
                        selected = preferences.sortOption == option,
                        onClick = { onSortChange(option) },
                        label = {
                            Text(
                                text = stringResource(sortLabel(option)),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryThumbnailSection(
    showThumbnails: Boolean,
    onShowThumbnailsChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.settings_show_thumbnails),
            style = MaterialTheme.typography.bodyMedium
        )
        Switch(
            checked = showThumbnails,
            onCheckedChange = onShowThumbnailsChange
        )
    }
}

@Composable
private fun CategoryDetailsSection(
    showFileDetails: Boolean,
    onShowFileDetailsChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.category_show_file_details),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = stringResource(R.string.category_show_file_details_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = showFileDetails,
            onCheckedChange = onShowFileDetailsChange
        )
    }
}

@Composable
private fun CategorySectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
}

private fun sortLabel(option: FileSortOption): Int = when (option) {
    FileSortOption.NAME_ASC -> R.string.sort_name_asc
    FileSortOption.NAME_DESC -> R.string.sort_name_desc
    FileSortOption.DATE_NEWEST -> R.string.sort_date_newest
    FileSortOption.DATE_OLDEST -> R.string.sort_date_oldest
    FileSortOption.SIZE_LARGEST -> R.string.sort_size_largest
    FileSortOption.SIZE_SMALLEST -> R.string.sort_size_smallest
}
