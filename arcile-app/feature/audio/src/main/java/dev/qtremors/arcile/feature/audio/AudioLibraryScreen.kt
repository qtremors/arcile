package dev.qtremors.arcile.feature.audio

import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.storage.domain.AudioTrack
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.PasteConflictDialog
import dev.qtremors.arcile.core.ui.category.CategoryLibraryShell
import dev.qtremors.arcile.core.ui.category.rememberCategoryLibraryShellState
import dev.qtremors.arcile.core.ui.dialogs.ClipboardContentsDialog
import dev.qtremors.arcile.core.ui.dialogs.DeleteConfirmationDialog
import dev.qtremors.arcile.core.ui.dialogs.PropertiesDialog
import dev.qtremors.arcile.core.ui.dialogs.RenameDialog
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private enum class AudioBackAction {
    CLEAR_SELECTION,
    CLOSE_SEARCH,
    CLOSE_FOLDER,
    NAVIGATE_BACK
}

@Composable
internal fun AudioLibraryScreen(
    state: AudioLibraryState,
    playback: AudioPlaybackState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearchFiltersChange: (SearchFilters) -> Unit,
    onSelectTab: (CategoryLibraryPage) -> Unit,
    onSelectFolder: (AudioFolder) -> Unit,
    onClearFolderFilter: () -> Unit,
    onPresentationChange: (CategoryLibraryPage, FileListingPreferences) -> Unit,
    onGroupingChange: (CategoryGrouping) -> Unit,
    onShowFileDetailsChange: (Boolean) -> Unit,
    onDefaultPageChange: (CategoryLibraryPage) -> Unit,
    onToggleFavoriteSelection: () -> Unit,
    onTogglePinnedFolder: (AudioFolder) -> Unit,
    onUpdateFolderCover: (AudioFolder, String?) -> Unit,
    onToggleSelection: (String) -> Unit,
    onSelectPaths: (Collection<String>) -> Unit,
    onTogglePaths: (Collection<String>) -> Unit,
    onSelectAll: () -> Unit,
    onInvertSelection: () -> Unit,
    onClearSelection: () -> Unit,
    onCopySelection: () -> Unit,
    onCutSelection: () -> Unit,
    onRenameSelection: (String) -> Unit,
    onDeleteSelection: () -> Unit,
    onConfirmDelete: () -> Unit,
    onDismissDelete: () -> Unit,
    onTogglePermanentDelete: () -> Unit,
    onToggleShred: () -> Unit,
    onOpenProperties: () -> Unit,
    onDismissProperties: () -> Unit,
    onCreateZip: () -> Unit,
    onPaste: () -> Unit,
    onPasteToFolder: (String) -> Unit,
    onCancelClipboard: () -> Unit,
    onRemoveFromClipboard: (String) -> Unit,
    onResolvePasteConflicts: (Map<String, ConflictResolution>) -> Unit,
    onDismissPasteConflictDialog: () -> Unit,
    onClearActiveFileOperation: () -> Unit,
    onPlay: (String) -> Unit,
    onPlaySelection: (Collection<String>) -> Unit,
    onShareSelected: (List<AudioTrack>, () -> Unit) -> Unit,
    onOpenWith: (AudioTrack) -> Unit,
    onClearError: () -> Unit,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    val haptics = rememberArcileHaptics()
    val selectedTracks = remember(state.tracks, state.selectedPaths) {
        state.tracks.filter { it.file.absolutePath in state.selectedPaths }
    }
    val isSelectionMode = selectedTracks.isNotEmpty()
    var showSearchBar by rememberSaveable { mutableStateOf(state.query.isNotEmpty()) }
    var showPresentationSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showClipboardContents by rememberSaveable { mutableStateOf(false) }
    var coverFolder by remember { mutableStateOf<AudioFolder?>(null) }
    val shellState = rememberCategoryLibraryShellState()
    var backProgress by remember { mutableFloatStateOf(0f) }
    var backAction by remember { mutableStateOf<AudioBackAction?>(null) }
    val pagerState = rememberPagerState(
        initialPage = if (state.defaultPage == CategoryLibraryPage.FOLDERS) 1 else 0,
        pageCount = { 2 }
    )
    val coroutineScope = rememberCoroutineScope()
    var activeAudioGridSize by remember(state.audioPresentation.gridMinCellSize) {
        mutableFloatStateOf(state.audioPresentation.gridMinCellSize)
    }
    var activeFolderGridSize by remember(state.folderPresentation.gridMinCellSize) {
        mutableFloatStateOf(state.folderPresentation.gridMinCellSize)
    }

    LaunchedEffect(pagerState.currentPage) {
        val tab =
            if (pagerState.currentPage == 0) CategoryLibraryPage.ITEMS else CategoryLibraryPage.FOLDERS
        if (state.tab != tab) onSelectTab(tab)
    }
    LaunchedEffect(state.tab) {
        val page = if (state.tab == CategoryLibraryPage.ITEMS) 0 else 1
        if (pagerState.currentPage != page) pagerState.animateScrollToPage(page)
    }
    LaunchedEffect(state.error, playback.error) {
        val error = state.error ?: if (playback.error) {
            UiText.StringResource(R.string.audio_playback_failed)
        } else {
            null
        }
        error?.let {
            haptics.error()
            onFeedback(ArcileFeedbackEvent(it, ArcileFeedbackSeverity.Error))
            onClearError()
        }
    }

    PredictiveBackHandler { progress ->
        backAction = when {
            isSelectionMode -> AudioBackAction.CLEAR_SELECTION
            showSearchBar -> AudioBackAction.CLOSE_SEARCH
            state.folderFilter != null -> AudioBackAction.CLOSE_FOLDER
            else -> AudioBackAction.NAVIGATE_BACK
        }
        try {
            progress.collect { event -> backProgress = event.progress }
            when (requireNotNull(backAction)) {
                AudioBackAction.CLEAR_SELECTION -> onClearSelection()
                AudioBackAction.CLOSE_SEARCH -> {
                    showSearchBar = false
                    onQueryChange("")
                }
                AudioBackAction.CLOSE_FOLDER -> onClearFolderFilter()
                AudioBackAction.NAVIGATE_BACK -> onNavigateBack()
            }
        } catch (_: CancellationException) {
            // Keep the current category state when a predictive gesture is cancelled.
        } finally {
            backProgress = 0f
            backAction = null
        }
    }

    CategoryLibraryShell(
            state = shellState,
            selectionMode = isSelectionMode,
            searchVisible = showSearchBar,
            exitBackProgress = if (backAction == AudioBackAction.NAVIGATE_BACK) {
                backProgress
            } else {
                0f
            },
            chromeBackProgress = if (
                backAction == AudioBackAction.CLEAR_SELECTION ||
                backAction == AudioBackAction.CLOSE_SEARCH
            ) {
                backProgress
            } else {
                0f
            },
            extraBottomContentPadding = 8.dp,
            topChrome = {
                if (isSelectionMode) {
                    AudioSelectionTopBar(
                        selectedCount = selectedTracks.size,
                        selectedSize = selectedTracks.sumOf { it.file.size },
                        onClearSelection = onClearSelection,
                        onSelectAll = onSelectAll,
                        onInvertSelection = onInvertSelection,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    AudioLibraryFloatingTopBar(
                        state = state,
                        currentTab = if (pagerState.currentPage == 0) {
                            CategoryLibraryPage.ITEMS
                        } else {
                            CategoryLibraryPage.FOLDERS
                        },
                        showSearchBar = showSearchBar,
                        onSearchClick = {
                            shellState.revealChrome()
                            showSearchBar = true
                        },
                        onCloseSearch = {
                            onQueryChange("")
                            showSearchBar = false
                        },
                        onQueryChange = onQueryChange,
                        onSearchFiltersChange = onSearchFiltersChange,
                        onViewSort = { showPresentationSheet = true },
                        onDefaultPageChange = onDefaultPageChange,
                        onSelectAll = onSelectAll,
                        onNavigateBack = {
                            if (state.folderFilter != null) {
                                onClearFolderFilter()
                            } else {
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            bottomChrome = { isChromeVisible ->
                AudioLibraryBottomBar(
                    state = state,
                    currentTab = if (pagerState.currentPage == 0) {
                        CategoryLibraryPage.ITEMS
                    } else {
                        CategoryLibraryPage.FOLDERS
                    },
                    selectedTracks = selectedTracks,
                    isChromeVisible = isChromeVisible,
                    selectionBackProgress = if (
                        backAction == AudioBackAction.CLEAR_SELECTION
                    ) {
                        backProgress
                    } else {
                        0f
                    },
                    onSelectTab = { tab ->
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(
                                if (tab == CategoryLibraryPage.ITEMS) 0 else 1
                            )
                        }
                    },
                    onPlaySelected = {
                        onPlaySelection(selectedTracks.map { it.file.absolutePath })
                        onClearSelection()
                    },
                    onCopySelected = onCopySelection,
                    onCutSelected = onCutSelection,
                    onRenameSelected = { showRenameDialog = true },
                    onDeleteSelected = onDeleteSelection,
                    onShareSelected = {
                        onShareSelected(selectedTracks, onClearSelection)
                    },
                    onOpenProperties = onOpenProperties,
                    onCreateZip = onCreateZip,
                    onOpenWith = {
                        selectedTracks.singleOrNull()?.let(onOpenWith)
                        onClearSelection()
                    },
                    onToggleFavorite = {
                        onToggleFavoriteSelection()
                        onClearSelection()
                    },
                    onPaste = onPaste,
                    onCancelClipboard = onCancelClipboard,
                    onShowClipboardContents = { showClipboardContents = true },
                    onClearActiveFileOperation = onClearActiveFileOperation,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        ) { contentPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = !isSelectionMode
            ) { page ->
                val tab =
                    if (page == 0) CategoryLibraryPage.ITEMS else CategoryLibraryPage.FOLDERS
                val showingFolderContents =
                    tab == CategoryLibraryPage.FOLDERS && state.folderFilter != null
                val presentationPage = if (showingFolderContents) {
                    CategoryLibraryPage.ITEMS
                } else {
                    tab
                }
                AudioLibraryPage(
                    state = state,
                    tab = tab,
                    activeGridSize = if (presentationPage == CategoryLibraryPage.ITEMS) {
                        activeAudioGridSize
                    } else {
                        activeFolderGridSize
                    },
                    contentPadding = contentPadding,
                    currentMediaId = playback.currentMediaId,
                    onGridSizeChange = { size ->
                        if (presentationPage == CategoryLibraryPage.ITEMS) {
                            activeAudioGridSize = size
                        } else {
                            activeFolderGridSize = size
                        }
                    },
                    onGridSizeFinalized = { size ->
                        val current = if (presentationPage == CategoryLibraryPage.ITEMS) {
                            state.audioPresentation
                        } else {
                            state.folderPresentation
                        }
                        onPresentationChange(
                            presentationPage,
                            current.copy(gridMinCellSize = size)
                        )
                    },
                    onRefresh = onRefresh,
                    onPlay = onPlay,
                    onSelectFolder = onSelectFolder,
                    onToggleSelection = onToggleSelection,
                    onSelectPaths = onSelectPaths,
                    onTogglePaths = onTogglePaths,
                    onPasteToFolder = onPasteToFolder,
                    onTogglePinnedFolder = onTogglePinnedFolder,
                    onChooseFolderCover = { coverFolder = it },
                    onResetFolderCover = { onUpdateFolderCover(it, null) },
                    modifier = Modifier.graphicsLayer {
                        if (
                            backAction == AudioBackAction.CLOSE_FOLDER &&
                            showingFolderContents
                        ) {
                            translationX = backProgress * 120.dp.toPx()
                            alpha = 1f - backProgress * 0.5f
                        }
                    }
                )
            }
        }

    if (showPresentationSheet) {
        val selectedTab =
            if (pagerState.currentPage == 0) CategoryLibraryPage.ITEMS else CategoryLibraryPage.FOLDERS
        val presentationPage = if (
            selectedTab == CategoryLibraryPage.FOLDERS &&
            state.folderFilter != null
        ) {
            CategoryLibraryPage.ITEMS
        } else {
            selectedTab
        }
        AudioViewOptionsDialog(
            tab = presentationPage,
            presentation = if (presentationPage == CategoryLibraryPage.ITEMS) {
                state.audioPresentation
            } else {
                state.folderPresentation
            },
            grouping = state.grouping,
            showFileDetails = state.showFileDetails,
            onApply = { presentation, grouping, showDetails ->
                onPresentationChange(presentationPage, presentation)
                onGroupingChange(grouping)
                onShowFileDetailsChange(showDetails)
            },
            onDismiss = { showPresentationSheet = false }
        )
    }
    coverFolder?.let { folder ->
        AudioFolderCoverDialog(
            folder = folder,
            onSelect = { trackPath ->
                onUpdateFolderCover(folder, trackPath)
                coverFolder = null
            },
            onDismiss = { coverFolder = null }
        )
    }
    if (showRenameDialog && selectedTracks.size == 1) {
        RenameDialog(
            currentName = selectedTracks.single().file.name,
            onDismiss = {
                showRenameDialog = false
                onClearSelection()
            },
            onConfirm = { newName ->
                onRenameSelection(newName)
                showRenameDialog = false
            }
        )
    }
    if (state.showPasteConflictDialog && state.pasteConflicts.isNotEmpty()) {
        PasteConflictDialog(
            conflicts = state.pasteConflicts,
            onResolve = onResolvePasteConflicts,
            onDismiss = onDismissPasteConflictDialog
        )
    }
    if (
        state.showTrashConfirmation ||
        state.showPermanentDeleteConfirmation ||
        state.showMixedDeleteExplanation
    ) {
        DeleteConfirmationDialog(
            selectedCount = state.selectedPaths.size,
            isPermanentDeleteChecked =
                state.isPermanentDeleteChecked || state.showMixedDeleteExplanation,
            isPermanentDeleteToggleEnabled =
                state.isPermanentDeleteToggleEnabled && !state.showMixedDeleteExplanation,
            onConfirm = if (state.showMixedDeleteExplanation) ({}) else onConfirmDelete,
            onDismiss = onDismissDelete,
            onTogglePermanentDelete = onTogglePermanentDelete,
            decision = state.deleteDecision,
            isShredChecked = state.isShredChecked,
            onToggleShred = onToggleShred
        )
    }
    state.clipboardState?.let { clipboard ->
        if (showClipboardContents) {
            ClipboardContentsDialog(
                state = clipboard,
                onRemoveItem = onRemoveFromClipboard,
                onDismiss = { showClipboardContents = false }
            )
        }
    }
    if (state.isPropertiesVisible) {
        PropertiesDialog(
            properties = state.properties,
            isLoading = state.isPropertiesLoading,
            onDismiss = {
                onDismissProperties()
                onClearSelection()
            }
        )
    }
}
