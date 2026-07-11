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
import dev.qtremors.arcile.feature.browser.BrowserUiState
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.ui.rememberArcileHaptics
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.ui.asString
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.feature.browser.ArchiveExtractionTarget
import dev.qtremors.arcile.feature.browser.BrowserScrollPosition
import dev.qtremors.arcile.feature.browser.scrollPositionKey
import dev.qtremors.arcile.feature.browser.ui.BrowserContent
import dev.qtremors.arcile.feature.browser.ui.BrowserCreateFab
import dev.qtremors.arcile.feature.browser.ui.BrowserDialogs
import dev.qtremors.arcile.feature.browser.ui.BrowserFloatingSurfaces
import dev.qtremors.arcile.feature.browser.ui.BrowserTopBars
import dev.qtremors.arcile.feature.browser.ui.rememberBrowserDialogVisibility
import dev.qtremors.arcile.core.ui.theme.spacing

/**
 * Full-featured file browser screen.
 *
 * Supports list and grid views, multi-select with range selection, inline search with filters,
 * file creation, rename, delete (via trash), copy/cut/paste clipboard, share, and pull-to-refresh.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalSharedTransitionApi::class)
@Composable
internal fun BrowserScreen(
    state: BrowserUiState,
    intents: BrowserIntents,
    scroll: BrowserScrollBindings,
    onFeedback: (ArcileFeedbackEvent) -> Unit
) {
    val listState = scroll.listState
    val gridState = scroll.gridState
    val onArmPendingReveal = scroll.onArmPendingReveal
    val onSelectFolderTab = intents.navigation.onSelectFolderTab
    val onDismissConflictDialog = intents.clipboard.onDismissConflictDialog
    val onDismissProperties = intents.selection.onDismissProperties
    val onDismissDeleteConfirmation = intents.mutation.onDismissDeleteConfirmation
    val onToggleSearchFilterMenu = intents.search.onToggleSearchFilterMenu
    val onClearSearch = intents.search.onClearSearch
    val onClearSelection = intents.selection.onClearSelection
    val onNavigateBack = intents.navigation.onNavigateBack
    val onClearError = intents.search.onClearError
    val onClearFileOperationStatusMessage = intents.operation.onClearFileOperationStatusMessage
    val onUndoLastOperation = intents.operation.onUndoLastOperation
    val onClearPendingUndo = intents.operation.onClearPendingUndo
    val isRefreshing = state.isPullToRefreshing
    val haptics = rememberArcileHaptics()
    val dialogVisibility = rememberBrowserDialogVisibility()
    val lifecycleOwner = LocalLifecycleOwner.current
    var resumeRestoreTick by remember { mutableStateOf(0) }
    var showSearchBar by rememberSaveable { mutableStateOf(state.browserSearchQuery.isNotEmpty()) }
    
    var isFabExpanded by rememberSaveable { mutableStateOf(false) }
    val fabIconRotation by animateFloatAsState(
        targetValue = if (isFabExpanded) 45f else 0f,
        label = "fabRotation"
    )

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP -> {
                    onArmPendingReveal()
                }
                Lifecycle.Event.ON_RESUME -> {
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
    val currentPresentation = remember(
        state.browserSortOption,
        state.browserViewMode,
        state.browserListZoom,
        state.browserGridMinCellSize,
        state.browserShowThumbnails
    ) {
        FileListingPreferences(
            sortOption = state.browserSortOption,
            viewMode = state.browserViewMode,
            listZoom = state.browserListZoom,
            gridMinCellSize = state.browserGridMinCellSize,
            showThumbnails = state.browserShowThumbnails && state.archiveContext == null
        )
    }
    BrowserScrollEffects(state, scroll, resumeRestoreTick)
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
                else -> Unit
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
                    searchIntents = intents.search,
                    selectionIntents = intents.selection,
                    mutationIntents = intents.mutation,
                    clipboardIntents = intents.clipboard,
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
                    navigationIntents = intents.navigation,
                    selectionIntents = intents.selection,
                    searchIntents = intents.search,
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
                    selectionIntents = intents.selection,
                    mutationIntents = intents.mutation,
                    clipboardIntents = intents.clipboard,
                    operationIntents = intents.operation,
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
        selectionIntents = intents.selection,
        mutationIntents = intents.mutation,
        searchIntents = intents.search,
        clipboardIntents = intents.clipboard,
        archiveIntents = intents.archive
    )
}
