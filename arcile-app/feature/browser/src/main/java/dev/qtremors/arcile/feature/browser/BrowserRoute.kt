package dev.qtremors.arcile.feature.browser

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.feature.browser.ui.BrowserScreen
import dev.qtremors.arcile.shared.ui.ArcileFeedbackEvent
import kotlinx.coroutines.launch

sealed interface BrowserEntry {
    data class Root(val restorePersistentLocation: Boolean) : BrowserEntry
    data class Path(
        val path: String,
        val seedInitialPathHistory: Boolean = true
    ) : BrowserEntry
    data class Category(
        val name: String,
        val volumeId: String? = null
    ) : BrowserEntry
    data class Archive(val path: String) : BrowserEntry
}

data class BrowserEntryRequest(
    val id: Long,
    val entry: BrowserEntry,
    val focusPath: String? = null
)

data class BrowserRouteStatus(
    val hasActiveLocation: Boolean = false,
    val isCategoryScreen: Boolean = false
)

sealed interface BrowserDestination {
    data object ExitToHome : BrowserDestination
    data object ExitToPreviousRoute : BrowserDestination
    data class OpenFile(
        val path: String,
        val surroundingFiles: List<FileModel>
    ) : BrowserDestination
}

@Composable
fun BrowserRoute(
    entryRequest: BrowserEntryRequest?,
    isVisible: Boolean,
    hasPreviousRoute: Boolean,
    onStatusChange: (BrowserRouteStatus) -> Unit,
    onDestination: (BrowserDestination) -> Unit,
    onShareSelected: suspend (List<String>, List<FileModel>) -> Boolean,
    onFeedback: (ArcileFeedbackEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel = hiltViewModel<BrowserViewModel>()
    val pinViewModel = hiltViewModel<BrowserQuickAccessViewModel>()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val state = uiState.source
    val scope = rememberCoroutineScope()
    val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    val scrollPositionKey = state.scrollPositionKey()
    val hasActiveLocation =
        uiState.location.currentPath.isNotBlank() ||
            uiState.location.isVolumeRootScreen ||
            uiState.location.isCategoryScreen ||
            state.archiveContext != null
    var revealedFocusPath by rememberSaveable(entryRequest?.focusPath) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(hasActiveLocation, uiState.location.isCategoryScreen) {
        onStatusChange(
            BrowserRouteStatus(
                hasActiveLocation = hasActiveLocation,
                isCategoryScreen = uiState.location.isCategoryScreen
            )
        )
    }

    LaunchedEffect(entryRequest?.id) {
        when (val entry = entryRequest?.entry) {
            is BrowserEntry.Archive -> viewModel.openArchive(entry.path)
            is BrowserEntry.Category -> viewModel.navigateToCategory(entry.name, entry.volumeId)
            is BrowserEntry.Path -> viewModel.navigateToSpecificFolder(
                entry.path,
                seedInitialPathHistory = entry.seedInitialPathHistory
            )
            is BrowserEntry.Root -> viewModel.openFileBrowser(entry.restorePersistentLocation)
            null -> Unit
        }
    }

    LaunchedEffect(isVisible, entryRequest?.id, hasActiveLocation) {
        if (isVisible && entryRequest == null && !hasActiveLocation) {
            viewModel.openFileBrowser(restorePersistentLocation = true)
        }
    }

    LaunchedEffect(
        entryRequest?.focusPath,
        isVisible,
        state.displayState.visibleFiles
    ) {
        val focusPath = entryRequest?.focusPath
        if (focusPath != null && focusPath != revealedFocusPath && isVisible) {
            val index = state.displayState.visibleFiles.indexOfFirst {
                it.absolutePath == focusPath
            }
            if (index >= 0) {
                listState.scrollToItem(index)
                gridState.scrollToItem(index)
                revealedFocusPath = focusPath
            }
        }
    }

    val navigateBack: () -> Unit = {
        if (!viewModel.navigateBack(allowVolumeRootFallback = !hasPreviousRoute)) {
            onDestination(
                when (browserBackFallback(hasPreviousRoute)) {
                    BrowserBackFallback.PopAppBackStack ->
                        BrowserDestination.ExitToPreviousRoute
                    BrowserBackFallback.ShowHomePager ->
                        BrowserDestination.ExitToHome
                }
            )
        }
    }
    val isAtRoot = !hasActiveLocation
    var backProgress by remember { mutableFloatStateOf(0f) }
    var isBackPredicting by remember { mutableStateOf(false) }

    BackHandler(enabled = isVisible && !isAtRoot, onBack = navigateBack)
    PredictiveBackHandler(enabled = isVisible && isAtRoot) { progressFlow ->
        isBackPredicting = true
        try {
            progressFlow.collect { backEvent -> backProgress = backEvent.progress }
            navigateBack()
        } finally {
            isBackPredicting = false
            backProgress = 0f
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                if (isBackPredicting && isVisible) {
                    val scale = 1f - (backProgress * 0.08f)
                    scaleX = scale
                    scaleY = scale
                    translationX = backProgress * size.width
                    alpha = 1f - (backProgress * 0.4f)
                }
            }
    ) {
        BrowserScreen(
            state = state,
            onNavigateBack = navigateBack,
            onNavigateTo = viewModel::navigateToFolder,
            onOpenFile = { path ->
                viewModel.saveVisibleScrollPosition(scrollPositionKey, listState, gridState)
                if (ArchiveFormat.isSupported(path)) {
                    viewModel.openArchive(path)
                } else {
                    viewModel.requestOpenedFileReveal(path)
                    onDestination(
                        BrowserDestination.OpenFile(
                            path = path,
                            surroundingFiles = if (state.browserSearchQuery.isNotBlank()) {
                                state.searchResults
                            } else {
                                state.displayState.visibleFiles
                            }
                        )
                    )
                }
            },
            onToggleSelection = viewModel::toggleSelection,
            onSelectMultiple = viewModel::selectMultiple,
            onClearSelection = viewModel::clearSelection,
            onCreateFolder = viewModel::createFolder,
            onCreateFile = viewModel::createFile,
            onCreateFakeFile = viewModel::createFakeFile,
            onRequestDeleteSelected = viewModel::requestDeleteSelected,
            onConfirmDelete = viewModel::confirmDeleteSelected,
            onTogglePermanentDelete = viewModel::togglePermanentDelete,
            onToggleShred = viewModel::toggleShred,
            onDismissDeleteConfirmation = viewModel::dismissDeleteConfirmation,
            onRenameFile = viewModel::renameFile,
            onSearchQueryChange = viewModel::updateBrowserSearchQuery,
            onClearSearch = { viewModel.updateBrowserSearchQuery("") },
            onPresentationChange = viewModel::updateBrowserPresentation,
            onClearError = viewModel::clearError,
            onCopySelected = viewModel::copySelectedToClipboard,
            onCutSelected = viewModel::cutSelectedToClipboard,
            onPasteFromClipboard = viewModel::pasteFromClipboard,
            onCancelClipboard = viewModel::cancelClipboard,
            onShareSelected = {
                scope.launch {
                    val visibleFiles = if (state.browserSearchQuery.isNotBlank()) {
                        state.searchResults
                    } else {
                        state.displayState.visibleFiles
                    }
                    if (onShareSelected(state.selectedFiles.toList(), visibleFiles)) {
                        viewModel.clearSelection()
                    }
                }
            },
            onClearFileOperationStatusMessage = viewModel::clearFileOperationStatusMessage,
            onOpenProperties = viewModel::openPropertiesForSelection,
            onDismissProperties = viewModel::dismissProperties,
            onClearActiveFileOperation = viewModel::clearActiveFileOperation,
            isRefreshing = state.isPullToRefreshing,
            onRefresh = { viewModel.refresh(pullToRefresh = true) },
            onSearchFiltersChange = viewModel::updateSearchFilters,
            onToggleSearchFilterMenu = viewModel::toggleSearchFilterMenu,
            onResolvingConflicts = viewModel::resolveConflicts,
            onDismissConflictDialog = viewModel::dismissConflictDialog,
            onPinToQuickAccess = pinViewModel::addCustomFolder,
            onNativeRequestResult = viewModel::handleNativeActionResult,
            onSelectAll = viewModel::selectAll,
            onInvertSelection = viewModel::invertSelection,
            onRemoveFromClipboard = viewModel::removeFromClipboard,
            onSelectFolderTab = viewModel::selectFolderTab,
            onExtractArchive = viewModel::extractArchive,
            onExtractSelectedArchiveEntries = viewModel::extractSelectedArchiveEntries,
            onExtractCurrentArchiveFolder = viewModel::extractCurrentArchiveFolder,
            onCreateZipFromSelection = viewModel::createZipFromSelection,
            onCreateArchiveFromSelection = viewModel::createArchiveFromSelection,
            onSubmitArchivePassword = { password ->
                if (state.archiveContext?.pendingPasswordAction == ArchivePasswordAction.EXTRACT) {
                    viewModel.submitArchiveExtractionPassword(password)
                } else {
                    viewModel.submitArchivePassword(password)
                }
            },
            onDismissArchivePassword = viewModel::dismissArchivePasswordPrompt,
            onUndoLastTrashMove = viewModel::undoLastTrashMove,
            onClearPendingTrashUndo = viewModel::clearPendingTrashUndo,
            onUndoLastOperation = viewModel::undoLastOperation,
            onClearPendingUndo = viewModel::clearPendingUndo,
            onRetryRecoveredOperation = viewModel::retryRecoveredOperation,
            onCleanupRecoveredOperation = viewModel::cleanupRecoveredOperation,
            onDismissRecoveredOperation = viewModel::dismissRecoveredOperation,
            onFeedback = onFeedback,
            nativeRequestFlow = viewModel.nativeRequestFlow,
            listState = listState,
            gridState = gridState,
            scrollPositionKey = scrollPositionKey,
            savedScrollPosition = viewModel.savedScrollPosition(scrollPositionKey),
            savedScrollPositionProvider = viewModel::savedScrollPosition,
            onSaveScrollPosition = viewModel::saveScrollPosition,
            onClearScrollPosition = viewModel::clearScrollPosition,
            pendingRevealFilePath = state.pendingRevealFilePath,
            pendingRevealReady = state.pendingRevealReady,
            onArmPendingReveal = viewModel::armOpenedFileReveal,
            onConsumePendingReveal = viewModel::consumeOpenedFileReveal
        )
    }
}

private fun BrowserViewModel.saveVisibleScrollPosition(
    key: String,
    listState: LazyListState,
    gridState: LazyGridState
) {
    saveScrollPosition(
        key,
        BrowserScrollPosition(
            listIndex = listState.firstVisibleItemIndex,
            listOffset = listState.firstVisibleItemScrollOffset,
            gridIndex = gridState.firstVisibleItemIndex,
            gridOffset = gridState.firstVisibleItemScrollOffset
        )
    )
}
