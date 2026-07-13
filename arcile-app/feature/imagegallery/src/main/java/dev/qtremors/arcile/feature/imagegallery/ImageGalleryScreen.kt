@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.qtremors.arcile.feature.imagegallery

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileDropdownMenuItem
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.SplitButtonGroup
import dev.qtremors.arcile.core.ui.ToolbarAction
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
    CloseAlbum,
    NavigateBack
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ImageGalleryScreen(
    state: ImageGalleryState,
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
    val onClearSearch = contentActions.clearSearch
    val onSelectAlbum = contentActions.selectAlbum
    val onClearError = contentActions.clearError
    val onFeedback = contentActions.feedback
    val onPresentationChange = presentationActions.photosChange
    val onAlbumPresentationChange = presentationActions.albumsChange
    val onShowFileDetailsChange = presentationActions.showFileDetailsChange
    val onAspectRatioChange = presentationActions.aspectRatioChange
    val onGroupingChange = presentationActions.groupingChange
    val onDefaultTabChange = presentationActions.defaultTabChange
    val onTogglePinnedAlbum = presentationActions.togglePinnedAlbum
    val onCopySelected = clipboardActions.copySelected
    val onCutSelected = clipboardActions.cutSelected
    val onPasteToAlbum = clipboardActions.pasteToAlbum
    val onCancelClipboard = clipboardActions.cancel
    val onRemoveFromClipboard = clipboardActions.remove
    val onClearActiveFileOperation = clipboardActions.clearActiveOperation
    val onResolvePasteConflicts = clipboardActions.resolveConflicts
    val onDismissPasteConflictDialog = clipboardActions.dismissConflictDialog
    val onRenameFile = fileActions.rename
    val onCreateZipFromSelection = fileActions.createZipFromSelection
    val onSetAlbumCover = fileActions.setAlbumCover
    val haptics = rememberArcileHaptics()
    val isSelectionMode = state.selectedFiles.isNotEmpty()
    var showSearchBar by rememberSaveable { mutableStateOf(state.searchQuery.isNotEmpty()) }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showClipboardContents by rememberSaveable { mutableStateOf(false) }
    var currentTab by rememberSaveable { mutableStateOf(GalleryTab.PHOTOS) }
    var defaultTabApplied by rememberSaveable { mutableStateOf(false) }
    val albumsGridState = rememberLazyGridState()
    val pagerState = rememberPagerState(
        initialPage = if (currentTab == GalleryTab.ALBUMS) 1 else 0,
        pageCount = { 2 }
    )
    val coroutineScope = rememberCoroutineScope()

    var activePhotosGridCellSize by remember(state.presentation.gridMinCellSize) {
        mutableStateOf(state.presentation.gridMinCellSize)
    }
    var activeAlbumsGridCellSize by remember(state.albumPresentation.gridMinCellSize) {
        mutableStateOf(state.albumPresentation.gridMinCellSize)
    }

    var isTopBarVisible by rememberSaveable { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -15f) {
                    isTopBarVisible = false
                } else if (available.y > 15f) {
                    isTopBarVisible = true
                }
                return Offset.Zero
            }
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { error ->
            haptics.error()
            onFeedback(ArcileFeedbackEvent(error, ArcileFeedbackSeverity.Error))
            onClearError()
        }
    }

    LaunchedEffect(state.preferencesLoaded, state.imageGalleryDefaultTab) {
        if (!defaultTabApplied && state.preferencesLoaded) {
            val targetTab = when (state.imageGalleryDefaultTab) {
                ImageGalleryDefaultTab.PHOTOS -> GalleryTab.PHOTOS
                ImageGalleryDefaultTab.ALBUMS -> GalleryTab.ALBUMS
            }
            currentTab = targetTab
            pagerState.scrollToPage(if (targetTab == GalleryTab.ALBUMS) 1 else 0)
            defaultTabApplied = true
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        currentTab = if (pagerState.currentPage == 1) GalleryTab.ALBUMS else GalleryTab.PHOTOS
        if (pagerState.currentPage == 0 && state.selectedAlbumPath != null) {
            onSelectAlbum(null)
        }
    }



    var backProgress by remember { mutableStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }
    var backActionAtStart by remember { mutableStateOf<GalleryBackAction?>(null) }

    PredictiveBackHandler(enabled = true) { progressFlow ->
        backActionAtStart = when {
            isSelectionMode -> GalleryBackAction.ClearSelection
            showSearchBar -> GalleryBackAction.CloseSearch
            currentTab == GalleryTab.ALBUMS && state.selectedAlbumPath != null -> GalleryBackAction.CloseAlbum
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
                GalleryBackAction.CloseAlbum -> onSelectAlbum(null)
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .nestedScroll(nestedScrollConnection)
    ) {
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 96.dp

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isBackPredicting && backActionAtStart == GalleryBackAction.NavigateBack) {
                        val scale = 1f - (backProgress * 0.08f)
                        scaleX = scale
                        scaleY = scale
                        translationX = backProgress * 100.dp.toPx()
                        alpha = 1f - (backProgress * 0.4f)
                    }
                }
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isSelectionMode
            ) { page ->
                if (page == 0) {
                    ImageGalleryContent(
                        state = state.copy(selectedAlbumPath = null).withResolvedDisplayedFiles(),
                        gridMinCellSize = activePhotosGridCellSize,
                        onPhotosGridCellSizeChange = { activePhotosGridCellSize = it },
                        onPhotosGridCellSizeFinalized = { size ->
                            onPresentationChange(state.presentation.copy(gridMinCellSize = size))
                        },
                        contentPadding = PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp,
                            bottom = bottomPadding
                        ),
                        onOpenFile = onOpenFileWithContext,
                        onToggleSelection = onToggleSelection,
                        onSelectMultiple = onSelectMultiple,
                        onSelectAlbum = onSelectAlbum,
                        onRefresh = onRefresh
                    )
                } else if (state.selectedAlbumPath == null) {
                    ImageGalleryAlbumsGrid(
                        state = state,
                        gridMinCellSize = activeAlbumsGridCellSize,
                        onAlbumsGridCellSizeChange = { activeAlbumsGridCellSize = it },
                        onAlbumsGridCellSizeFinalized = { size ->
                            onAlbumPresentationChange(state.albumPresentation.copy(gridMinCellSize = size))
                        },
                        contentPadding = PaddingValues(
                            top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp,
                            bottom = bottomPadding
                        ),
                        onSelectAlbum = onSelectAlbum,
                        onRefresh = onRefresh,
                        gridState = albumsGridState,
                        onPasteToAlbum = onPasteToAlbum,
                        onTogglePinnedAlbum = onTogglePinnedAlbum
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                if (isBackPredicting && backActionAtStart == GalleryBackAction.CloseAlbum) {
                                    translationX = backProgress * 120.dp.toPx()
                                    alpha = 1f - backProgress * 0.5f
                                }
                            }
                    ) {
                        ImageGalleryContent(
                            state = state,
                            gridMinCellSize = activePhotosGridCellSize,
                            onPhotosGridCellSizeChange = { activePhotosGridCellSize = it },
                            onPhotosGridCellSizeFinalized = { size ->
                                onPresentationChange(state.presentation.copy(gridMinCellSize = size))
                            },
                            contentPadding = PaddingValues(
                                top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 72.dp,
                                bottom = bottomPadding
                            ),
                            onOpenFile = onOpenFileWithContext,
                            onToggleSelection = onToggleSelection,
                            onSelectMultiple = onSelectMultiple,
                            onSelectAlbum = onSelectAlbum,
                            onRefresh = onRefresh
                        )
                    }
                }
            }
        }

        // Animated offsets & alpha for floating top bar components
        val topBarOffset by animateDpAsState(
            targetValue = if (isTopBarVisible || showSearchBar) 0.dp else (-120).dp,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "topBarOffset"
        )
        val topBarAlpha by animateFloatAsState(
            targetValue = if (isTopBarVisible || showSearchBar) 1f else 0f,
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "topBarAlpha"
        )

        // Floating top control row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = topBarOffset.toPx()
                    alpha = topBarAlpha
                    if (isBackPredicting) {
                        if (isSelectionMode) {
                            val scale = 1f - (backProgress * 0.15f)
                            scaleX = scale
                            scaleY = scale
                            alpha = topBarAlpha * (1f - backProgress)
                            translationY = topBarOffset.toPx() - (backProgress * 40.dp.toPx())
                        } else if (showSearchBar) {
                            alpha = topBarAlpha * (1f - backProgress)
                            translationY = topBarOffset.toPx() - (backProgress * 50.dp.toPx())
                        }
                    }
                }
        ) {
            if (isSelectionMode) {
                FloatingGallerySelectionTopBar(
                    selectedCount = state.selectedFiles.size,
                    selectedSize = formatFileSize(
                        state.files.filter { state.selectedFiles.contains(it.absolutePath) }.sumOf { it.size }
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
                    onSearchClick = { showSearchBar = true },
                    onSortClick = { showPresentationSheet = true },
                    onNavigateBack = {
                        if (currentTab == GalleryTab.ALBUMS && state.selectedAlbumPath != null) {
                            onSelectAlbum(null)
                        } else {
                            onNavigateBack()
                        }
                    },
                    onClearSearch = {
                        showSearchBar = false
                        onClearSearch()
                    },
                    onSearchQueryChange = onSearchQueryChange,
                    onShowFileDetailsChange = onShowFileDetailsChange,
                    onDefaultTabChange = onDefaultTabChange,
                    onSelectAll = onSelectAll,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        ImageGalleryBottomBar(
            state = state,
            currentTab = currentTab,
            isTopBarVisible = isTopBarVisible,
            isBackPredicting = isBackPredicting,
            backProgress = backProgress,
            selectionActions = selectionActions,
            deleteActions = deleteActions,
            clipboardActions = clipboardActions,
            fileActions = fileActions,
            onSelectPhotos = {
                currentTab = GalleryTab.PHOTOS
                onSelectAlbum(null)
                coroutineScope.launch { pagerState.animateScrollToPage(0) }
            },
            onSelectAlbums = {
                currentTab = GalleryTab.ALBUMS
                coroutineScope.launch { pagerState.animateScrollToPage(1) }
            },
            onShowRenameDialog = { showRenameDialog = true },
            onShowClipboardContents = { showClipboardContents = true }
        )
    }

    ImageGalleryDialogs(
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
