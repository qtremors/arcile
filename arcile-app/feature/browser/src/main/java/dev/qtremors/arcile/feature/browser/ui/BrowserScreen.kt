@file:Suppress("LocalContextGetResourceValueCall")

package dev.qtremors.arcile.feature.browser.ui

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.shared.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.shared.ui.rememberArcileHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.feature.browser.ArchiveExtractionTarget
import dev.qtremors.arcile.feature.browser.BrowserScrollPosition
import dev.qtremors.arcile.feature.browser.scrollPositionKey
import dev.qtremors.arcile.feature.browser.ui.BrowserContent
import dev.qtremors.arcile.feature.browser.ui.BrowserCreateFab
import dev.qtremors.arcile.feature.browser.ui.BrowserDialogs
import dev.qtremors.arcile.feature.browser.ui.BrowserFloatingSurfaces
import dev.qtremors.arcile.feature.browser.ui.BrowserTopBars
import dev.qtremors.arcile.feature.browser.ui.BrowserUiActions
import dev.qtremors.arcile.feature.browser.ui.rememberBrowserDialogVisibility
import dev.qtremors.arcile.ui.theme.spacing

/**
 * Full-featured file browser screen.
 *
 * Supports list and grid views, multi-select with range selection, inline search with filters,
 * file creation, rename, delete (via trash), copy/cut/paste clipboard, share, and pull-to-refresh.
 */
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
    onToggleShred: () -> Unit = {},
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
    onSearchFiltersChange: (dev.qtremors.arcile.core.storage.domain.SearchFilters) -> Unit = {},
    onToggleSearchFilterMenu: (Boolean) -> Unit = {},
    onResolvingConflicts: (Map<String, dev.qtremors.arcile.core.storage.domain.ConflictResolution>) -> Unit = {},
    onPinToQuickAccess: (String, String) -> Unit = { _, _ -> },
    onNativeRequestResult: (Boolean) -> Unit = {},
    onInvertSelection: (List<String>) -> Unit = {},
    onSelectAll: (List<String>) -> Unit = {},
    onRemoveFromClipboard: (String) -> Unit = {},
    onSelectFolderTab: (String?) -> Unit = {},
    onExtractArchive: (ArchiveExtractionTarget, String?) -> Unit = { _, _ -> },
    onExtractSelectedArchiveEntries: (ArchiveExtractionTarget, String?) -> Unit = { _, _ -> },
    onExtractCurrentArchiveFolder: (ArchiveExtractionTarget, String?) -> Unit = { _, _ -> },
    onCreateZipFromSelection: () -> Unit = {},
    onCreateArchiveFromSelection: (String, ArchiveFormat, ArchiveCompressionLevel, String?) -> Unit = { _, _, _, _ -> },
    onSubmitArchivePassword: (String) -> Unit = {},
    onDismissArchivePassword: () -> Unit = {},
    onUndoLastTrashMove: () -> Unit = {},
    onClearPendingTrashUndo: () -> Unit = {},
    onUndoLastOperation: () -> Unit = onUndoLastTrashMove,
    onClearPendingUndo: () -> Unit = onClearPendingTrashUndo,
    onRetryRecoveredOperation: (String) -> Unit = {},
    onCleanupRecoveredOperation: (String) -> Unit = {},
    onDismissRecoveredOperation: (String) -> Unit = {},
    onFeedback: (ArcileFeedbackEvent) -> Unit = {},
    nativeRequestFlow: kotlinx.coroutines.flow.SharedFlow<android.content.IntentSender>? = null,
    listState: LazyListState = androidx.compose.foundation.lazy.rememberLazyListState(),
    gridState: LazyGridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState(),
    scrollPositionKey: String = state.scrollPositionKey(),
    savedScrollPosition: BrowserScrollPosition? = null,
    savedScrollPositionProvider: (String) -> BrowserScrollPosition? = { savedScrollPosition },
    onSaveScrollPosition: (String, BrowserScrollPosition) -> Unit = { _, _ -> },
    onClearScrollPosition: (String) -> Unit = {},
    pendingRevealFilePath: String? = state.pendingRevealFilePath,
    pendingRevealReady: Boolean = state.pendingRevealReady,
    onArmPendingReveal: () -> Unit = {},
    onConsumePendingReveal: (String) -> Unit = {}
) {
    val haptics = rememberArcileHaptics()
    val dialogVisibility = rememberBrowserDialogVisibility()
    val lifecycleOwner = LocalLifecycleOwner.current
    var isBrowserLifecycleResumed by remember {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    var resumeRestoreTick by remember { mutableStateOf(0) }
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
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    isBrowserLifecycleResumed = false
                    onArmPendingReveal()
                }
                Lifecycle.Event.ON_RESUME -> {
                    isBrowserLifecycleResumed = true
                    resumeRestoreTick += 1
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

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

    val displayedFiles = state.displayState.visibleFiles
    val pendingRevealIndex = remember(
        pendingRevealFilePath,
        pendingRevealReady,
        displayedFiles
    ) {
        pendingRevealFilePath
            ?.takeIf { pendingRevealReady }
            ?.let { revealPath -> displayedFiles.indexOfFirst { it.absolutePath == revealPath } }
            ?.takeIf { it >= 0 }
    }
    if (pendingRevealReady && pendingRevealIndex != null) {
        if (state.browserViewMode == BrowserViewMode.GRID) {
            gridState.requestScrollToItem(pendingRevealIndex)
        } else {
            listState.requestScrollToItem(pendingRevealIndex)
        }
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
            showThumbnails = state.browserShowThumbnails && state.archiveContext == null
        )
    }
    val actions = BrowserUiActions(
        onNavigateBack = onNavigateBack,
        onNavigateTo = onNavigateTo,
        onOpenFile = onOpenFile,
        onToggleSelection = onToggleSelection,
        onSelectMultiple = onSelectMultiple,
        onClearSelection = onClearSelection,
        onCreateFolder = onCreateFolder,
        onCreateFile = onCreateFile,
        onCreateFakeFile = onCreateFakeFile,
        onRequestDeleteSelected = onRequestDeleteSelected,
        onConfirmDelete = onConfirmDelete,
        onTogglePermanentDelete = onTogglePermanentDelete,
        onToggleShred = onToggleShred,
        onDismissDeleteConfirmation = onDismissDeleteConfirmation,
        onRenameFile = onRenameFile,
        onSearchQueryChange = onSearchQueryChange,
        onClearSearch = onClearSearch,
        onPresentationChange = onPresentationChange,
        onClearError = onClearError,
        onCopySelected = onCopySelected,
        onCutSelected = onCutSelected,
        onPasteFromClipboard = onPasteFromClipboard,
        onCancelClipboard = onCancelClipboard,
        onShareSelected = onShareSelected,
        onClearFileOperationStatusMessage = onClearFileOperationStatusMessage,
        onOpenProperties = onOpenProperties,
        onDismissProperties = onDismissProperties,
        onClearActiveFileOperation = onClearActiveFileOperation,
        onDismissConflictDialog = onDismissConflictDialog,
        onRefresh = onRefresh,
        onSearchFiltersChange = onSearchFiltersChange,
        onToggleSearchFilterMenu = onToggleSearchFilterMenu,
        onResolvingConflicts = onResolvingConflicts,
        onPinToQuickAccess = onPinToQuickAccess,
        onNativeRequestResult = onNativeRequestResult,
        onInvertSelection = onInvertSelection,
        onSelectAll = onSelectAll,
        onRemoveFromClipboard = onRemoveFromClipboard,
        onSelectFolderTab = onSelectFolderTab,
        onExtractArchive = onExtractArchive,
        onExtractSelectedArchiveEntries = onExtractSelectedArchiveEntries,
        onExtractCurrentArchiveFolder = onExtractCurrentArchiveFolder,
        onCreateZipFromSelection = onCreateZipFromSelection,
        onCreateArchiveFromSelection = onCreateArchiveFromSelection,
        onSubmitArchivePassword = onSubmitArchivePassword,
        onDismissArchivePassword = onDismissArchivePassword,
        onUndoLastTrashMove = onUndoLastTrashMove,
        onClearPendingTrashUndo = onClearPendingTrashUndo,
        onRetryRecoveredOperation = onRetryRecoveredOperation,
        onCleanupRecoveredOperation = onCleanupRecoveredOperation,
        onDismissRecoveredOperation = onDismissRecoveredOperation
    )
    val categoryFolderTabs = state.displayState.categoryFolderTabs
    val selectedCategoryFolderTabIndex = state.displayState.selectedCategoryFolderTabIndex
    val switchCategoryFolderTab: (Int) -> Unit = { direction ->
        if (categoryFolderTabs.size > 1) {
            val nextIndex = (selectedCategoryFolderTabIndex + direction)
                .coerceIn(0, categoryFolderTabs.lastIndex)
            if (nextIndex != selectedCategoryFolderTabIndex) {
                onSelectFolderTab(categoryFolderTabs[nextIndex].path)
            }
        }
    }

    val scrollResetKey = scrollPositionKey
    var restoredScrollKey by remember { mutableStateOf<String?>(null) }
    var lastScrollResetKey by rememberSaveable { mutableStateOf(scrollResetKey) }
    var pendingScrollReset by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(scrollResetKey) {
        if (scrollResetKey != lastScrollResetKey) {
            pendingScrollReset = true
            lastScrollResetKey = scrollResetKey
        }
    }
    LaunchedEffect(scrollResetKey, displayedFiles.size) {
        if (pendingScrollReset && displayedFiles.isNotEmpty()) {
            listState.scrollToItem(0)
            gridState.scrollToItem(0)
            onClearScrollPosition(scrollPositionKey)
            restoredScrollKey = scrollPositionKey
            pendingScrollReset = false
        }
    }
    LaunchedEffect(scrollPositionKey, resumeRestoreTick, displayedFiles.size, state.displayState.visibleListRows.size, state.displayState.visibleGridRows.size, pendingScrollReset) {
        if (!pendingScrollReset && displayedFiles.isNotEmpty()) {
            val shouldRestore = restoredScrollKey != scrollPositionKey || resumeRestoreTick > 0
            if (shouldRestore) savedScrollPositionProvider(scrollPositionKey)?.let { position ->
                val listIndex = position.listIndex.coerceAtMost(state.displayState.visibleListRows.lastIndex.coerceAtLeast(0))
                val gridIndex = position.gridIndex.coerceAtMost(state.displayState.visibleGridRows.lastIndex.coerceAtLeast(0))
                if (state.browserViewMode == BrowserViewMode.GRID) {
                    gridState.scrollToItem(gridIndex, position.gridOffset)
                } else {
                    listState.scrollToItem(listIndex, position.listOffset)
                }
            }
            restoredScrollKey = scrollPositionKey
        }
    }
    LaunchedEffect(pendingRevealFilePath, pendingRevealReady, displayedFiles.size, state.browserViewMode) {
        val revealPath = pendingRevealFilePath
        val index = pendingRevealIndex
        if (pendingRevealReady && !revealPath.isNullOrBlank() && index != null) {
                if (state.browserViewMode == BrowserViewMode.GRID) {
                    gridState.scrollToItem(index)
                } else {
                    listState.scrollToItem(index)
                }
                onSaveScrollPosition(
                    scrollPositionKey,
                    BrowserScrollPosition(
                        listIndex = index,
                        listOffset = 0,
                        gridIndex = index,
                        gridOffset = 0
                    )
                )
                onConsumePendingReveal(revealPath)
                restoredScrollKey = scrollPositionKey
        }
    }
    LaunchedEffect(scrollPositionKey, state.displayState.visibleListRows.size, state.displayState.visibleGridRows.size, restoredScrollKey, listState, gridState) {
        snapshotFlow {
            BrowserScrollCapture(
                hasRows = state.displayState.visibleListRows.isNotEmpty() || state.displayState.visibleGridRows.isNotEmpty(),
                isScrollInProgress = listState.isScrollInProgress || gridState.isScrollInProgress,
                position = BrowserScrollPosition(
                    listIndex = listState.firstVisibleItemIndex,
                    listOffset = listState.firstVisibleItemScrollOffset,
                    gridIndex = gridState.firstVisibleItemIndex,
                    gridOffset = gridState.firstVisibleItemScrollOffset
                )
            )
        }
            .distinctUntilChanged()
            .collect { capture ->
                if (capture.hasRows && restoredScrollKey == scrollPositionKey) {
                    if (
                        (!capture.isScrollInProgress && !capture.position.isAtTop()) ||
                        (capture.isScrollInProgress && capture.position.isAtTop())
                    ) {
                        onSaveScrollPosition(scrollPositionKey, capture.position)
                    } else {
                        savedScrollPositionProvider(scrollPositionKey)
                            ?.takeUnless { it.isAtTop() }
                            ?.let { position ->
                                val listIndex = position.listIndex.coerceAtMost(state.displayState.visibleListRows.lastIndex.coerceAtLeast(0))
                                val gridIndex = position.gridIndex.coerceAtMost(state.displayState.visibleGridRows.lastIndex.coerceAtLeast(0))
                                if (state.browserViewMode == BrowserViewMode.GRID) {
                                    gridState.scrollToItem(gridIndex, position.gridOffset)
                                } else {
                                    listState.scrollToItem(listIndex, position.listOffset)
                                }
                            }
                    }
                }
            }
    }

    LaunchedEffect(state.browserSearchQuery) {
        if (state.browserSearchQuery.isNotEmpty()) {
            showSearchBar = true
        }
    }

    val hasModal = dialogVisibility.hasVisibleDialog ||
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
                dialogVisibility.showCreateFolderDialog -> dialogVisibility.showCreateFolderDialog = false
                dialogVisibility.showCreateFileDialog -> dialogVisibility.showCreateFileDialog = false
                dialogVisibility.showCreateFakeFileDialog -> dialogVisibility.showCreateFakeFileDialog = false
                dialogVisibility.showCreateArchiveDialog -> dialogVisibility.showCreateArchiveDialog = false
                dialogVisibility.showExtractArchiveDialog -> dialogVisibility.showExtractArchiveDialog = false
                dialogVisibility.showRenameDialog -> dialogVisibility.showRenameDialog = false
                dialogVisibility.showSortDialog -> dialogVisibility.showSortDialog = false
                dialogVisibility.showClipboardContents -> dialogVisibility.showClipboardContents = false
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

    var backProgress by remember { mutableStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }
    var backActionAtStart by remember { mutableStateOf<BrowserBackAction?>(null) }

    val backAction = resolveBrowserBackAction(backState)
    PredictiveBackHandler(enabled = backAction != BrowserBackAction.ExitApp) { progressFlow ->
        backActionAtStart = backAction
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent ->
                backProgress = backEvent.progress
            }
            when (backActionAtStart) {
                BrowserBackAction.CloseModal -> handleBrowserBack()
                BrowserBackAction.CloseSheet -> onToggleSearchFilterMenu(false)
                BrowserBackAction.CloseSearch -> {
                    showSearchBar = false
                    onClearSearch()
                }
                BrowserBackAction.ClearSelection -> onClearSelection()
                BrowserBackAction.NavigateFolderUp,
                BrowserBackAction.PopRoute,
                BrowserBackAction.ExitApp -> onNavigateBack()
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

    LaunchedEffect(state.clipboardState) {
        state.clipboardState?.let { clipboard ->
            val action = if (clipboard.operation == ClipboardOperation.COPY) context.getString(R.string.clipboard_copied) else context.getString(R.string.clipboard_cut)
            val count = clipboard.files.size
            onFeedback(
                ArcileFeedbackEvent(
                    message = UiText.Dynamic(context.getString(R.string.clipboard_feedback, count, action)),
                    severity = ArcileFeedbackSeverity.Info
                )
            )
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { errorMsg ->
            onClearError()
            haptics.error()
            onFeedback(ArcileFeedbackEvent(message = errorMsg, severity = ArcileFeedbackSeverity.Error))
        }
    }
    LaunchedEffect(state.fileOperationStatusMessage) {
        state.fileOperationStatusMessage?.let { message ->
            onClearFileOperationStatusMessage()
            onFeedback(
                ArcileFeedbackEvent(
                    message = message,
                    severity = ArcileFeedbackSeverity.Success,
                    actionLabel = state.pendingUndoAction?.let { UiText.StringResource(R.string.undo) },
                    onAction = state.pendingUndoAction?.let { { onUndoLastOperation() } },
                    onDismiss = state.pendingUndoAction?.let { { onClearPendingUndo() } }
                )
            )
        }
    }

    val scrollBehavior = androidx.compose.material3.TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val isSelectionMode = state.selectedFiles.isNotEmpty()
    val isClipboardActive = state.clipboardState != null
    val isRecoveryVisible = state.activeRecoveryOperation != null
    val bottomContentPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
        (if (isSelectionMode || isClipboardActive || isRecoveryVisible) MaterialTheme.spacing.toolbarBottomGap else MaterialTheme.spacing.screenGutter)
    val layoutDirection = LocalLayoutDirection.current

    Scaffold(
        modifier = Modifier
            .nestedScroll(scrollBehavior.nestedScrollConnection)
            .graphicsLayer {
                if (isBackPredicting && backActionAtStart == BrowserBackAction.PopRoute) {
                    val scale = 1f - (backProgress * 0.08f)
                    scaleX = scale
                    scaleY = scale
                    translationX = backProgress * 100.dp.toPx()
                    alpha = 1f - (backProgress * 0.4f)
                }
            },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {},
        topBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        if (isBackPredicting && (backActionAtStart == BrowserBackAction.CloseSearch || backActionAtStart == BrowserBackAction.ClearSelection)) {
                            translationY = -backProgress * size.height.toFloat()
                            alpha = 1f - backProgress
                        }
                    }
            ) {
                BrowserTopBars(
                    state = state,
                    displayedFiles = displayedFiles,
                    showSearchBar = showSearchBar,
                    onShowSearchBarChange = { showSearchBar = it },
                    scrollBehavior = scrollBehavior,
                    dialogVisibility = dialogVisibility,
                    actions = actions,
                    onBackClick = handleBrowserBack,
                    onSelectionChanged = { haptics.selectionChanged() },
                    onShowPinnedSnackbar = { label ->
                        onFeedback(
                            ArcileFeedbackEvent(
                                message = UiText.StringResource(R.string.quick_access_pinned, listOf(label)),
                                severity = ArcileFeedbackSeverity.Success
                            )
                        )
                    }
                )
            }
        },
        bottomBar = {},
        floatingActionButton = {
            BrowserCreateFab(
                state = state,
                showSearchBar = showSearchBar,
                isFabExpanded = isFabExpanded,
                fabIconRotation = fabIconRotation,
                onFabExpandedChange = { isFabExpanded = it },
                dialogVisibility = dialogVisibility
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isBackPredicting && backActionAtStart == BrowserBackAction.NavigateFolderUp) {
                            translationX = backProgress * 120.dp.toPx()
                            alpha = 1f - backProgress * 0.5f
                        }
                    }
            ) {
                BrowserContent(
                    state = state,
                    displayedFiles = displayedFiles,
                    currentPresentation = currentPresentation,
                    showSearchBar = showSearchBar,
                    showLoading = showLoading,
                    isRefreshing = isRefreshing,
                    bottomContentPadding = bottomContentPadding,
                    scaffoldPadding = padding,
                    layoutDirection = layoutDirection,
                    listState = listState,
                    gridState = gridState,
                    actions = actions,
                    onShowSearchBarChange = { showSearchBar = it },
                    onSwitchCategoryFolderTab = switchCategoryFolderTab
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isBackPredicting && backActionAtStart == BrowserBackAction.ClearSelection) {
                            translationY = backProgress * 150.dp.toPx()
                            alpha = 1f - backProgress
                        }
                    }
            ) {
                BrowserFloatingSurfaces(
                    state = state,
                    scaffoldPadding = padding,
                    isFabExpanded = isFabExpanded,
                    onFabExpandedChange = { isFabExpanded = it },
                    dialogVisibility = dialogVisibility,
                    actions = actions,
                    onOperationSucceeded = { haptics.success() },
                    onOperationFailed = { haptics.error() }
                )
            }
        }
    }

    BrowserDialogs(
        state = state,
        currentPresentation = currentPresentation,
        dialogVisibility = dialogVisibility,
        actions = actions
    )
}

private data class BrowserScrollCapture(
    val hasRows: Boolean,
    val isScrollInProgress: Boolean,
    val position: BrowserScrollPosition
)

private fun BrowserScrollPosition.isAtTop(): Boolean =
    listIndex == 0 && listOffset == 0 && gridIndex == 0 && gridOffset == 0
