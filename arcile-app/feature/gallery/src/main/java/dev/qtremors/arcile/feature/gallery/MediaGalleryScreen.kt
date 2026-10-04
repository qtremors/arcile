@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.gallery

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
import dev.qtremors.arcile.core.ui.category.CategoryLibraryShell
import dev.qtremors.arcile.core.ui.category.rememberCategoryLibraryShellState
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import dev.qtremors.arcile.core.ui.theme.menuGroupFirst
import dev.qtremors.arcile.core.ui.theme.menuGroupLast
import dev.qtremors.arcile.core.ui.theme.menuGroupMiddle
import dev.qtremors.arcile.core.ui.theme.menuGroupSingle
import dev.qtremors.arcile.core.presentation.formatFileSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class GalleryBackAction {
    ClearSelection,
    CloseSearch,
    CloseFolder,
    NavigateBack
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun MediaGalleryScreen(
    state: MediaGalleryState,
    navigationActions: GalleryNavigationActions,
    selectionActions: GallerySelectionActions,
    deleteActions: GalleryDeleteActions,
    contentActions: GalleryContentActions,
    presentationActions: GalleryPresentationActions,
    clipboardActions: GalleryClipboardActions,
    fileActions: GalleryFileActions
) {
    val onNavigateBack = navigationActions.navigateBack
    val onOpenFile = navigationActions.openFile
    val onOpenFileWithContext: (String, List<dev.qtremors.arcile.core.storage.domain.FileModel>) -> Unit =
        { path, files -> onOpenFile(path, files, state.selectedFiles) }
    val onToggleSelection = selectionActions.toggle
    val onClearSelection = selectionActions.clear
    val onSelectAll = selectionActions.selectAll
    val onInvertSelection = selectionActions.invert
    val onSelectMultiple = selectionActions.selectMultiple
    val onShareSelected = selectionActions.share
    val onOpenProperties = selectionActions.openProperties
    val onDismissProperties = selectionActions.dismissProperties
    val onRequestDeleteSelected = deleteActions.request
    val onConfirmDelete = deleteActions.confirm
    val onTogglePermanentDelete = deleteActions.togglePermanent
    val onToggleShred = deleteActions.toggleShred
    val onDismissDeleteConfirmation = deleteActions.dismiss
    val onRefresh = contentActions.refresh
    val onSearchQueryChange = contentActions.searchQueryChange
    val onSearchFiltersChange = contentActions.searchFiltersChange
    val onClearSearch = contentActions.clearSearch
    val onSelectFolder = contentActions.selectFolder
    val onClearError = contentActions.clearError
    val onFeedback = contentActions.feedback
    val onPresentationChange = presentationActions.itemsChange
    val onFolderPresentationChange = presentationActions.foldersChange
    val onShowFileDetailsChange = presentationActions.showFileDetailsChange
    val onAspectRatioChange = presentationActions.aspectRatioChange
    val onGroupingChange = presentationActions.groupingChange
    val onDefaultPageChange = presentationActions.defaultPageChange
    val onTogglePinnedFolder = presentationActions.togglePinnedFolder
    val onCopySelected = clipboardActions.copySelected
    val onCutSelected = clipboardActions.cutSelected
    val onPasteToFolder = clipboardActions.pasteToFolder
    val onCancelClipboard = clipboardActions.cancel
    val onRemoveFromClipboard = clipboardActions.remove
    val onResolvePasteConflicts = clipboardActions.resolveConflicts
    val onDismissPasteConflictDialog = clipboardActions.dismissConflictDialog
    val onRenameFile = fileActions.rename
    val onCreateZipFromSelection = fileActions.createZipFromSelection
    val onSetFolderCover = fileActions.setFolderCover
    val haptics = rememberArcileHaptics()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    var showSearchBar by rememberSaveable {
        mutableStateOf(state.searchQuery.isNotEmpty() || state.searchFilters.hasActiveFilters)
    }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showClipboardContents by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(CategoryLibraryPage.ITEMS) }
    var defaultPageApplied by rememberSaveable { mutableStateOf(false) }
    val foldersGridState = rememberLazyGridState()
    val pagerState = rememberPagerState(
        initialPage = if (currentTab == CategoryLibraryPage.FOLDERS) 1 else 0,
        pageCount = { 2 }
    )
    val coroutineScope = rememberCoroutineScope()

    var activeItemsGridCellSize by remember(state.presentation.gridMinCellSize) {
        mutableFloatStateOf(state.presentation.gridMinCellSize)
    }
    var activeFoldersGridCellSize by remember(state.folderPresentation.gridMinCellSize) {
        mutableFloatStateOf(state.folderPresentation.gridMinCellSize)
    }

    val shellState = rememberCategoryLibraryShellState()

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            haptics.error()
            onFeedback(ArcileFeedbackEvent(error, ArcileFeedbackSeverity.Error))
            onClearError()
        }
    }

    LaunchedEffect(state.preferencesLoaded, state.defaultPage) {
        if (!defaultPageApplied && state.preferencesLoaded) {
            val targetTab = when (state.defaultPage) {
                CategoryLibraryPage.ITEMS -> CategoryLibraryPage.ITEMS
                CategoryLibraryPage.FOLDERS -> CategoryLibraryPage.FOLDERS
            }
            currentTab = targetTab
            pagerState.scrollToPage(if (targetTab == CategoryLibraryPage.FOLDERS) 1 else 0)
            defaultPageApplied = true
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        currentTab = if (pagerState.currentPage == 1) CategoryLibraryPage.FOLDERS else CategoryLibraryPage.ITEMS
        if (pagerState.currentPage == 0 && state.selectedFolderPath != null) {
            onSelectFolder(null)
        }
    }



    var backProgress by remember { mutableFloatStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }
    var backActionAtStart by remember { mutableStateOf<GalleryBackAction?>(null) }

    PredictiveBackHandler(enabled = true) { progressFlow ->
        backActionAtStart = when {
            isSelectionMode -> GalleryBackAction.ClearSelection
            showSearchBar -> GalleryBackAction.CloseSearch
            currentTab == CategoryLibraryPage.FOLDERS && state.selectedFolderPath != null -> GalleryBackAction.CloseFolder
            else -> GalleryBackAction.NavigateBack
        }
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            when (backActionAtStart) {
                GalleryBackAction.ClearSelection -> onClearSelection()
                GalleryBackAction.CloseSearch -> {
                    showSearchBar = false
                    onClearSearch()
                }
                GalleryBackAction.CloseFolder -> onSelectFolder(null)
                GalleryBackAction.NavigateBack -> onNavigateBack()
                null -> Unit
            }
        } catch (e: Exception) {
            // Cancelled
        } finally {
            isBackPredicting = false
            backProgress = 0f
            backActionAtStart = null
        }
    }

    CategoryLibraryShell(
        state = shellState,
        selectionMode = isSelectionMode,
        searchVisible = showSearchBar,
        exitBackProgress = if (
            isBackPredicting && backActionAtStart == GalleryBackAction.NavigateBack
        ) {
            backProgress
        } else {
            0f
        },
        chromeBackProgress = if (
            isBackPredicting && (isSelectionMode || showSearchBar)
        ) {
            backProgress
        } else {
            0f
        },
        topChrome = {
            if (isSelectionMode) {
                val context = androidx.compose.ui.platform.LocalContext.current
                FloatingGallerySelectionTopBar(
                    selectedCount = state.selectedFiles.size,
                    selectedSize = formatFileSize(
                        context,
                        state.files.filter { state.selectedFiles.contains(it.reference) }
                            .sumOf { it.size }
                    ),
                    onClearSelection = onClearSelection,
                    onSelectAll = onSelectAll,
                    onInvertSelection = onInvertSelection,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                FloatingGalleryTopBar(
                    state = state,
                    showSearchBar = showSearchBar,
                    currentTab = currentTab,
                    onSearchClick = {
                        shellState.revealChrome()
                        showSearchBar = true
                    },
                    onSortClick = { showPresentationSheet = true },
                    onNavigateBack = {
                        if (
                            currentTab == CategoryLibraryPage.FOLDERS &&
                            state.selectedFolderPath != null
                        ) {
                            onSelectFolder(null)
                        } else {
                            onNavigateBack()
                        }
                    },
                    onClearSearch = {
                        showSearchBar = false
                        onClearSearch()
                    },
                    onSearchQueryChange = onSearchQueryChange,
                    onSearchFiltersChange = onSearchFiltersChange,
                    onShowFileDetailsChange = onShowFileDetailsChange,
                    onDefaultPageChange = onDefaultPageChange,
                    onSelectAll = onSelectAll,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        bottomChrome = { isChromeVisible ->
            MediaGalleryBottomBar(
                state = state,
                currentTab = currentTab,
                isTopBarVisible = isChromeVisible,
                isBackPredicting = isBackPredicting,
                backProgress = backProgress,
                selectionActions = selectionActions,
                deleteActions = deleteActions,
                clipboardActions = clipboardActions,
                fileActions = fileActions,
                onSelectItems = {
                    currentTab = CategoryLibraryPage.ITEMS
                    onSelectFolder(null)
                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                },
                onSelectFolders = {
                    currentTab = CategoryLibraryPage.FOLDERS
                    coroutineScope.launch { pagerState.animateScrollToPage(1) }
                },
                onShowRenameDialog = { showRenameDialog = true },
                onShowClipboardContents = { showClipboardContents = true }
            )
        }
    ) { contentPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isSelectionMode
            ) { page ->
                if (page == 0) {
                    MediaGalleryContent(
                        state = state.copy(selectedFolderPath = null).withResolvedDisplayedFiles(),
                        gridMinCellSize = activeItemsGridCellSize,
                        onItemsGridCellSizeChange = { activeItemsGridCellSize = it },
                        onItemsGridCellSizeFinalized = { size ->
                            onPresentationChange(state.presentation.copy(gridMinCellSize = size))
                        },
                        contentPadding = contentPadding,
                        onOpenFile = onOpenFileWithContext,
                        onToggleSelection = onToggleSelection,
                        onSelectMultiple = onSelectMultiple,
                        onSelectFolder = onSelectFolder,
                        onRefresh = onRefresh
                    )
                } else if (state.selectedFolderPath == null) {
                    MediaGalleryFoldersGrid(
                        state = state,
                        gridMinCellSize = activeFoldersGridCellSize,
                        onFoldersGridCellSizeChange = { activeFoldersGridCellSize = it },
                        onFoldersGridCellSizeFinalized = { size ->
                            onFolderPresentationChange(state.folderPresentation.copy(gridMinCellSize = size))
                        },
                        contentPadding = contentPadding,
                        onSelectFolder = onSelectFolder,
                        onRefresh = onRefresh,
                        gridState = foldersGridState,
                        onPasteToFolder = onPasteToFolder,
                        onTogglePinnedFolder = onTogglePinnedFolder
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (isBackPredicting && backActionAtStart == GalleryBackAction.CloseFolder) {
                                    translationX = backProgress * 120.dp.toPx()
                                    alpha = 1f - backProgress * 0.5f
                                }
                            }
                    ) {
                        MediaGalleryContent(
                            state = state,
                            gridMinCellSize = activeItemsGridCellSize,
                            onItemsGridCellSizeChange = { activeItemsGridCellSize = it },
                            onItemsGridCellSizeFinalized = { size ->
                                onPresentationChange(state.presentation.copy(gridMinCellSize = size))
                            },
                            contentPadding = contentPadding,
                            onOpenFile = onOpenFileWithContext,
                            onToggleSelection = onToggleSelection,
                            onSelectMultiple = onSelectMultiple,
                            onSelectFolder = onSelectFolder,
                            onRefresh = onRefresh
                        )
                    }
                }
            }
    }

    MediaGalleryDialogs(
        state = state,
        currentTab = currentTab,
        showRenameDialog = showRenameDialog,
        showClipboardContents = showClipboardContents,
        showPresentationSheet = showPresentationSheet,
        selectionActions = selectionActions,
        deleteActions = deleteActions,
        clipboardActions = clipboardActions,
        fileActions = fileActions,
        presentationActions = presentationActions,
        onDismissRenameDialog = { showRenameDialog = false },
        onDismissClipboardContents = { showClipboardContents = false },
        onDismissPresentationSheet = { showPresentationSheet = false }
    )
}
