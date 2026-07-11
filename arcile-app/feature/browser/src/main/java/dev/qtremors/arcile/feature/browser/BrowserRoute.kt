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
import dev.qtremors.arcile.feature.browser.ui.BrowserArchiveIntents
import dev.qtremors.arcile.feature.browser.ui.BrowserClipboardIntents
import dev.qtremors.arcile.feature.browser.ui.BrowserIntents
import dev.qtremors.arcile.feature.browser.ui.BrowserInitializationSurface
import dev.qtremors.arcile.feature.browser.ui.BrowserMutationIntents
import dev.qtremors.arcile.feature.browser.ui.BrowserNavigationIntents
import dev.qtremors.arcile.feature.browser.ui.BrowserOperationIntents
import dev.qtremors.arcile.feature.browser.ui.BrowserScrollBindings
import dev.qtremors.arcile.feature.browser.ui.BrowserSearchIntents
import dev.qtremors.arcile.feature.browser.ui.BrowserSelectionIntents
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.NativeStorageAuthorizationEffect
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
    val initializationState by viewModel.initializationState.collectAsStateWithLifecycle()
    val state = uiState
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

    NativeStorageAuthorizationEffect(
        requirement = state.operation.pendingAuthorization,
        onResult = viewModel::handleAuthorizationResult,
        onUnavailable = viewModel::handleAuthorizationUnavailable
    )

    LaunchedEffect(hasActiveLocation, uiState.location.isCategoryScreen) {
        onStatusChange(
            BrowserRouteStatus(
                hasActiveLocation = hasActiveLocation,
                isCategoryScreen = uiState.location.isCategoryScreen
            )
        )
    }

    LaunchedEffect(isVisible, entryRequest?.id) {
        if (isVisible) viewModel.initialize(entryRequest)
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

    val screenIntents = BrowserIntents(
        navigation = BrowserNavigationIntents(
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
            onRefresh = { viewModel.refresh(pullToRefresh = true) },
            onSelectFolderTab = viewModel::selectFolderTab
        ),
        selection = BrowserSelectionIntents(
            onToggleSelection = viewModel::toggleSelection,
            onSelectMultiple = viewModel::selectMultiple,
            onClearSelection = viewModel::clearSelection,
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
            onOpenProperties = viewModel::openPropertiesForSelection,
            onDismissProperties = viewModel::dismissProperties,
            onInvertSelection = viewModel::invertSelection,
            onSelectAll = viewModel::selectAll,
            onPinToQuickAccess = pinViewModel::addCustomFolder
        ),
        mutation = BrowserMutationIntents(
            onCreateFolder = viewModel::createFolder,
            onCreateFile = viewModel::createFile,
            onCreateFakeFile = viewModel::createFakeFile,
            onRequestDeleteSelected = viewModel::requestDeleteSelected,
            onConfirmDelete = viewModel::confirmDeleteSelected,
            onTogglePermanentDelete = viewModel::togglePermanentDelete,
            onToggleShred = viewModel::toggleShred,
            onDismissDeleteConfirmation = viewModel::dismissDeleteConfirmation,
            onRenameFile = viewModel::renameFile
        ),
        search = BrowserSearchIntents(
            onSearchQueryChange = viewModel::updateBrowserSearchQuery,
            onClearSearch = { viewModel.updateBrowserSearchQuery("") },
            onPresentationChange = viewModel::updateBrowserPresentation,
            onClearError = viewModel::clearError,
            onSearchFiltersChange = viewModel::updateSearchFilters,
            onToggleSearchFilterMenu = viewModel::toggleSearchFilterMenu
        ),
        clipboard = BrowserClipboardIntents(
            onCopySelected = viewModel::copySelectedToClipboard,
            onCutSelected = viewModel::cutSelectedToClipboard,
            onPasteFromClipboard = viewModel::pasteFromClipboard,
            onCancelClipboard = viewModel::cancelClipboard,
            onRemoveFromClipboard = viewModel::removeFromClipboard,
            onResolvingConflicts = viewModel::resolveConflicts,
            onDismissConflictDialog = viewModel::dismissConflictDialog
        ),
        archive = BrowserArchiveIntents(
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
            onDismissArchivePassword = viewModel::dismissArchivePasswordPrompt
        ),
        operation = BrowserOperationIntents(
            onClearFileOperationStatusMessage = viewModel::clearFileOperationStatusMessage,
            onClearActiveFileOperation = viewModel::clearActiveFileOperation,
            onUndoLastTrashMove = viewModel::undoLastTrashMove,
            onClearPendingTrashUndo = viewModel::clearPendingTrashUndo,
            onUndoLastOperation = viewModel::undoLastOperation,
            onClearPendingUndo = viewModel::clearPendingUndo,
            onRetryRecoveredOperation = viewModel::retryRecoveredOperation,
            onCleanupRecoveredOperation = viewModel::cleanupRecoveredOperation,
            onDismissRecoveredOperation = viewModel::dismissRecoveredOperation
        )
    )

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
        if (initializationState == BrowserInitializationState.Ready) {
            BrowserScreen(
                state = state,
                intents = screenIntents,
                scroll = BrowserScrollBindings(
                    listState = listState,
                    gridState = gridState,
                    positionKey = scrollPositionKey,
                    savedPosition = viewModel.savedScrollPosition(scrollPositionKey),
                    savedPositionProvider = viewModel::savedScrollPosition,
                    onSavePosition = viewModel::saveScrollPosition,
                    onClearPosition = viewModel::clearScrollPosition,
                    pendingRevealFilePath = state.pendingRevealFilePath,
                    pendingRevealReady = state.pendingRevealReady,
                    onArmPendingReveal = viewModel::armOpenedFileReveal,
                    onConsumePendingReveal = viewModel::consumeOpenedFileReveal
                ),
                onFeedback = onFeedback
            )
        } else {
            BrowserInitializationSurface(
                state = initializationState,
                onRetry = viewModel::retryInitialization,
                modifier = Modifier.fillMaxSize()
            )
        }
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
