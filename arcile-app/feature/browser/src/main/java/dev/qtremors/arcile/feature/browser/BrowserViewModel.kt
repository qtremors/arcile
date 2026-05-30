package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.shared.presentation.PropertiesUiModel
import dev.qtremors.arcile.feature.browser.delegate.ArchiveActionDelegate
import dev.qtremors.arcile.feature.browser.delegate.BrowserOperationDelegate
import dev.qtremors.arcile.feature.browser.delegate.ClipboardDelegate
import dev.qtremors.arcile.feature.browser.delegate.NavigationDelegate
import dev.qtremors.arcile.feature.browser.delegate.PropertiesDelegate
import dev.qtremors.arcile.feature.browser.delegate.SearchDelegate
import dev.qtremors.arcile.shared.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.shared.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.toArcileError
import dev.qtremors.arcile.core.storage.domain.userMessage
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import javax.inject.Inject

enum class BrowserNativeAction { TRASH }

@androidx.compose.runtime.Immutable
data class BrowserFileOperationUiState(
    val type: BulkFileOperationType,
    val totalItems: Int,
    val completedItems: Int = 0,
    val currentPath: String? = null,
    val isCancelling: Boolean = false,
    val bytesCopied: Long? = null,
    val totalBytes: Long? = null,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val terminalStatus: OperationCompletionStatus? = null
) {
    val isIndeterminate: Boolean
        get() = (totalBytes ?: 0L) <= 0L && totalItems <= 0
}

@androidx.compose.runtime.Immutable
data class BrowserState(
    val currentPath: String = "",
    val currentVolumeId: String? = null,
    val isVolumeRootScreen: Boolean = false,
    val isCategoryScreen: Boolean = false,
    val activeCategoryName: String = "",
    val selectedFolderTabPath: String? = null,
    val files: PersistentList<FileModel> = persistentListOf(),
    val folderStatsByPath: PersistentMap<String, FolderStats> = persistentMapOf(),
    val folderStatsLoadingPaths: PersistentSet<String> = persistentSetOf(),
    val searchResults: PersistentList<FileModel> = persistentListOf(),
    val isSearching: Boolean = false,
    val browserSearchQuery: String = "",
    val browserSortOption: FileSortOption = FileSortOption.NAME_ASC,
    val browserViewMode: BrowserViewMode = BrowserViewMode.LIST,
    val browserListZoom: Float = BrowserPresentationPreferences.DEFAULT_LIST_ZOOM,
    val browserGridMinCellSize: Float = BrowserPresentationPreferences.DEFAULT_GRID_MIN_CELL_SIZE,
    val browserShowThumbnails: Boolean = BrowserPresentationPreferences.DEFAULT_SHOW_THUMBNAILS,
    val selectedFiles: PersistentSet<String> = persistentSetOf(),
    val clipboardState: ClipboardState? = null,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearchFilterMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val error: UiText? = null,
    val pasteConflicts: PersistentList<FileConflict> = persistentListOf(),
    val showConflictDialog: Boolean = false,
    val storageVolumes: PersistentList<StorageVolume> = persistentListOf(),
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val pendingNativeAction: BrowserNativeAction? = null,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: PropertiesUiModel? = null,
    val activeFileOperation: BrowserFileOperationUiState? = null,
    val fileOperationStatusMessage: UiText? = null,
    val pendingTrashUndoIds: PersistentList<String> = persistentListOf(),
    val selectedFilesTotalSize: Long = 0L,
    val displayState: BrowserDisplayState = BrowserDisplayState()
)

private const val ALL_FILES_LABEL = "All files"

fun BrowserState.withUpdatedDisplayState(): BrowserState = copy(
    displayState = buildBrowserDisplayState(
        files = files,
        sortOption = browserSortOption,
        selectedFolderTabPath = selectedFolderTabPath,
        isCategoryScreen = isCategoryScreen,
        currentVolumeId = currentVolumeId,
        storageVolumes = storageVolumes,
        allFilesLabel = ALL_FILES_LABEL
    )
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val searchRepository: SearchRepository,
    private val clipboardRepository: ClipboardRepository,
    private val trashRepository: TrashRepository,
    private val archiveRepository: ArchiveRepository,
    private val volumeRepository: VolumeRepository,
    private val browserPreferencesRepository: BrowserPreferencesStore,
    private val savedStateHandle: SavedStateHandle,
    private val getStorageVolumesUseCase: GetStorageVolumesUseCase,
    private val bulkFileCoordinator: BulkFileOperationCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()
    val navigationState: StateFlow<BrowserNavigationState> = _state
        .map { it.navigationState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.navigationState())
    val listingState: StateFlow<BrowserListingState> = _state
        .map { it.listingState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.listingState())
    val selectionState: StateFlow<BrowserSelectionState> = _state
        .map { it.selectionState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.selectionState())
    val searchState: StateFlow<BrowserSearchState> = _state
        .map { it.searchState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.searchState())
    val dialogState: StateFlow<BrowserDialogState> = _state
        .map { it.dialogState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.dialogState())
    val operationUiState: StateFlow<OperationUiState> = _state
        .map { it.operationUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.operationUiState())

    private val _nativeRequestFlow = MutableSharedFlow<android.content.IntentSender>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val nativeRequestFlow: SharedFlow<android.content.IntentSender> = _nativeRequestFlow.asSharedFlow()

    private val searchDelegate = SearchDelegate(_state, viewModelScope, searchRepository)
    private val navigationDelegate = NavigationDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        fileBrowserRepository = fileBrowserRepository,
        searchRepository = searchRepository,
        browserPreferencesRepository = browserPreferencesRepository,
        savedStateHandle = savedStateHandle,
        onClearSearch = { searchDelegate.updateBrowserSearchQuery("") }
    )
    private val clipboardDelegate = ClipboardDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        clipboardRepository = clipboardRepository,
        bulkFileOperationCoordinator = bulkFileCoordinator,
        refreshAction = { navigationDelegate.refresh() }
    )
    private val operationDelegate = BrowserOperationDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        trashRepository = trashRepository,
        bulkFileOperationCoordinator = bulkFileCoordinator,
        refreshAction = { navigationDelegate.refresh() }
    )
    private val archiveActionDelegate = ArchiveActionDelegate(
        state = _state,
        bulkFileOperationCoordinator = bulkFileCoordinator,
        clearSelection = { clearSelection() }
    )
    private val propertiesDelegate = PropertiesDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        fileBrowserRepository = fileBrowserRepository,
        archiveRepository = archiveRepository
    )
    private val deleteFlowDelegate = DeleteFlowDelegate(
        coroutineScope = viewModelScope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = object : DeleteStateCallbacks {
            override fun getSelectedFiles(): List<String> = _state.value.selectedFiles.toList()
            override fun isPermanentDeleteChecked(): Boolean = _state.value.isPermanentDeleteChecked
            override fun isPermanentDeleteToggleEnabled(): Boolean = _state.value.isPermanentDeleteToggleEnabled
            override fun setLoading(isLoading: Boolean) {
                _state.update { it.copy(isLoading = isLoading) }
            }
            override fun showMixedDeleteExplanation() {
                _state.update { it.copy(showMixedDeleteExplanation = true) }
            }
            override fun showPermanentDeleteConfirmation() {
                _state.update {
                    it.copy(
                        showPermanentDeleteConfirmation = true,
                        isPermanentDeleteChecked = true,
                        isPermanentDeleteToggleEnabled = false
                    )
                }
            }
            override fun showTrashConfirmation() {
                _state.update {
                    it.copy(
                        showTrashConfirmation = true,
                        isPermanentDeleteChecked = false,
                        isPermanentDeleteToggleEnabled = true
                    )
                }
            }
            override fun togglePermanentDeleteChecked() {
                _state.update { it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked) }
            }
            override fun dismissDeleteConfirmation() {
                _state.update {
                    it.copy(
                        showTrashConfirmation = false,
                        showPermanentDeleteConfirmation = false,
                        showMixedDeleteExplanation = false,
                        deleteDecision = null
                    )
                }
            }
            override fun setError(error: String) {
                _state.update { it.copy(error = UiText.Dynamic(error)) }
            }
            override fun setError(error: UiText) {
                _state.update { it.copy(error = error) }
            }
            override fun setDeleteDecision(decision: DeleteDecision) {
                _state.update { it.copy(deleteDecision = decision) }
            }
            override fun setPendingNativeAction() {
                _state.update { it.copy(pendingNativeAction = BrowserNativeAction.TRASH) }
            }
            override fun clearSelection() {
                _state.update { it.copy(selectedFiles = persistentSetOf()) }
            }
        },
        startBulkDeleteOperation = { type, selected ->
            bulkFileCoordinator.startOperation(
                type = type,
                sourcePaths = selected,
                destinationPath = null,
                resolutions = emptyMap<String, ConflictResolution>()
            )
        },
        emitNativeRequest = { sender -> _nativeRequestFlow.emit(sender) },
        onSuccess = { navigationDelegate.refresh() }
    )

    private var isInitialized = false

    init {
        operationDelegate.hydrateActiveOperation()

        viewModelScope.launch {
            getStorageVolumesUseCase().collectLatest { volumes ->
                _state.update { it.copy(storageVolumes = volumes.toPersistentList()).withUpdatedDisplayState() }

                if (!isInitialized) {
                    isInitialized = true
                    when (val location = navigationDelegate.restoreLocationFromState()) {
                        StorageBrowserLocation.Roots -> navigationDelegate.openVolumeRoots()
                        is StorageBrowserLocation.Directory -> {
                            _state.update {
                                it.copy(
                                    currentPath = location.pathScope.absolutePath,
                                    currentVolumeId = location.pathScope.volumeId,
                                    isVolumeRootScreen = false,
                                    isCategoryScreen = false,
                                    activeCategoryName = ""
                                )
                            }
                            navigationDelegate.refresh()
                        }
                        is StorageBrowserLocation.Category -> {
                            _state.update {
                                it.copy(
                                    currentPath = "",
                                    currentVolumeId = location.categoryScope.volumeId,
                                    isVolumeRootScreen = false,
                                    isCategoryScreen = true,
                                    activeCategoryName = location.categoryScope.categoryName
                                )
                            }
                            navigationDelegate.refresh()
                        }
                        null -> navigationDelegate.initializeFromArgs()
                    }
                } else {
                    val currentVolumeId = _state.value.currentVolumeId
                    if (currentVolumeId != null && volumes.none { it.id == currentVolumeId }) {
                        navigationDelegate.openVolumeRoots(UiText.StringResource(R.string.error_selected_storage_removed))
                    } else if (_state.value.isVolumeRootScreen) {
                        _state.update { it.copy(files = navigationDelegate.volumeFiles().toPersistentList()).withUpdatedDisplayState() }
                    }
                }
            }
        }

        viewModelScope.launch {
            fileBrowserRepository.observeFolderStatUpdates().collectLatest { update ->
                _state.update { currentState ->
                    if (currentState.isVolumeRootScreen || currentState.isCategoryScreen) {
                        return@update currentState
                    }

                    val visiblePaths = currentState.files
                        .asSequence()
                        .filter { it.isDirectory }
                        .map { it.absolutePath }
                        .toSet()
                    if (update.path !in visiblePaths) {
                        return@update currentState
                    }

                    currentState.copy(
                        folderStatsByPath = (currentState.folderStatsByPath + (update.path to update.stats)).toPersistentMap(),
                        folderStatsLoadingPaths = (currentState.folderStatsLoadingPaths - update.path).toPersistentSet()
                    )
                }
            }
        }

        viewModelScope.launch {
            browserPreferencesRepository.preferencesFlow.collectLatest { prefs ->
                _state.update { currentState ->
                    val pathPresentation = if (currentState.isCategoryScreen) {
                        prefs.getPresentationForCategory(currentState.activeCategoryName)
                    } else if (currentState.currentPath.isNotEmpty()) {
                        prefs.getPresentationForPath(currentState.currentPath)
                    } else {
                        prefs.globalPresentation
                    }
                    currentState.copy(
                        browserSortOption = pathPresentation.sortOption,
                        browserViewMode = pathPresentation.viewMode,
                        browserListZoom = pathPresentation.listZoom,
                        browserGridMinCellSize = pathPresentation.gridMinCellSize,
                        browserShowThumbnails = pathPresentation.showThumbnails
                    ).withUpdatedDisplayState()
                }
            }
        }

        operationDelegate.observeOperationEvents()
    }

    fun openFileBrowser(restorePersistentLocation: Boolean = false, errorMessage: String? = null) =
        navigationDelegate.openFileBrowser(restorePersistentLocation, errorMessage?.let(UiText::Dynamic))
    fun navigateToSpecificFolder(path: String, seedInitialPathHistory: Boolean = true) =
        navigationDelegate.navigateToSpecificFolder(path, seedInitialPathHistory)
    fun navigateToCategory(categoryName: String, volumeId: String? = null) = navigationDelegate.navigateToCategory(categoryName, volumeId)
    fun navigateToFolder(path: String) = navigationDelegate.navigateToFolder(path)
    fun navigateBack(): Boolean = navigationDelegate.navigateBack()
    fun refresh(pullToRefresh: Boolean = false) = navigationDelegate.refresh(pullToRefresh)

    fun toggleSelection(path: String) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            currentState.reduce(
                BrowserSelectionEvent.Toggle(
                    path = path,
                    files = currentState.files,
                    folderStats = currentState.folderStatsByPath
                )
            )
        }
    }

    fun selectAll(paths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            currentState.reduce(
                BrowserSelectionEvent.SelectAll(
                    paths = paths,
                    files = currentState.files,
                    folderStats = currentState.folderStatsByPath
                )
            )
        }
    }

    fun invertSelection(allPaths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            currentState.reduce(
                BrowserSelectionEvent.Invert(
                    allPaths = allPaths,
                    files = currentState.files,
                    folderStats = currentState.folderStatsByPath
                )
            )
        }
    }

    private fun calculateSelectionSize(selectedPaths: Set<String>, currentFiles: List<FileModel>, folderStats: Map<String, FolderStats>): Long {
        return calculateBrowserSelectionSize(selectedPaths, currentFiles, folderStats)
    }

    fun selectMultiple(paths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            val updatedSelection = currentState.selectedFiles + paths
            currentState.copy(
                selectedFiles = updatedSelection.toPersistentSet(),
                selectedFilesTotalSize = calculateSelectionSize(updatedSelection, currentState.files, currentState.folderStatsByPath),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun clearSelection() {
        _state.update { it.reduce(BrowserSelectionEvent.Clear) }
    }

    fun selectFolderTab(path: String?) {
        _state.update { currentState -> currentState.reduce(BrowserNavigationEvent.SelectFolderTab(path)) }
    }

    fun updateBrowserSearchQuery(query: String) = searchDelegate.updateBrowserSearchQuery(query)
    fun updateSearchFilters(filters: SearchFilters) = searchDelegate.updateSearchFilters(filters)
    fun toggleSearchFilterMenu(visible: Boolean) = searchDelegate.toggleSearchFilterMenu(visible)

    fun updateBrowserPresentation(
        presentation: BrowserPresentationPreferences,
        applyToSubfolders: Boolean
    ) {
        if (_state.value.isVolumeRootScreen) return
        val normalized = presentation.normalized()
        _state.update {
            it.copy(
                browserSortOption = normalized.sortOption,
                browserViewMode = normalized.viewMode,
                browserListZoom = normalized.listZoom,
                browserGridMinCellSize = normalized.gridMinCellSize,
                browserShowThumbnails = normalized.showThumbnails
            ).withUpdatedDisplayState()
        }
        viewModelScope.launch {
            if (_state.value.isCategoryScreen) {
                browserPreferencesRepository.updatePathPresentation(
                    path = "category_${_state.value.activeCategoryName}",
                    presentation = normalized,
                    applyToSubfolders = false
                )
            } else {
                val path = _state.value.currentPath
                if (path.isNotEmpty()) {
                    browserPreferencesRepository.updatePathPresentation(path, normalized, applyToSubfolders)
                } else if (applyToSubfolders) {
                    browserPreferencesRepository.updateGlobalPresentation(normalized)
                }
            }
        }

    }

    fun createFolder(name: String) {
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty() || _state.value.isVolumeRootScreen) return

        viewModelScope.launch {
            fileMutationRepository.createDirectory(currentPath, name).onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_create_folder_failed)) }
            }
        }
    }

    fun createFile(name: String) {
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty() || _state.value.isVolumeRootScreen) return

        viewModelScope.launch {
            fileMutationRepository.createFile(currentPath, name).onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_create_file_failed)) }
            }
        }
    }

    fun createFakeFile(name: String, size: Long) {
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty() || _state.value.isVolumeRootScreen) return

        bulkFileCoordinator.startOperation(
            type = BulkFileOperationType.CREATE_FAKE,
            sourcePaths = listOf(name),
            destinationPath = currentPath,
            resolutions = emptyMap<String, ConflictResolution>(),
            fakeFileSize = size
        )
    }

    fun extractSelectedArchiveHere(password: String? = null) = archiveActionDelegate.extractSelectedArchiveHere(password)
    fun extractSelectedArchiveToFolder(password: String? = null) = archiveActionDelegate.extractSelectedArchiveToFolder(password)
    fun createArchiveFromSelection(
        archiveName: String,
        format: ArchiveFormat,
        password: String? = null
    ) = archiveActionDelegate.createArchiveFromSelection(archiveName, format, password)
    fun createZipFromSelection() = archiveActionDelegate.createZipFromSelection()

    fun requestDeleteSelected() = deleteFlowDelegate.requestDeleteSelected()
    fun togglePermanentDelete() = deleteFlowDelegate.togglePermanentDelete()
    fun confirmDeleteSelected() = deleteFlowDelegate.confirmDeleteSelected()
    fun dismissDeleteConfirmation() = deleteFlowDelegate.dismissDeleteConfirmation()
    fun moveSelectedToTrash() = deleteFlowDelegate.moveSelectedToTrash()
    fun deleteSelectedPermanently() = deleteFlowDelegate.deleteSelectedPermanently()

    fun handleNativeActionResult(confirmed: Boolean) {
        val pendingAction = _state.value.pendingNativeAction ?: return
        _state.update { it.copy(pendingNativeAction = null) }
        if (!confirmed) return

        when (pendingAction) {
            BrowserNativeAction.TRASH -> confirmDeleteSelected()
        }
    }

    fun renameFile(path: String, newName: String) {
        val invalidChars = listOf('/', '\\', '\u0000')
        if (newName.isBlank() || invalidChars.any { newName.contains(it) } || newName.contains("..")) {
            _state.update { it.copy(error = UiText.StringResource(R.string.error_invalid_name)) }
            return
        }

        viewModelScope.launch {
            fileMutationRepository.renameFile(path, newName).onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_rename_file_failed)) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearFileOperationStatusMessage() = operationDelegate.clearStatusMessage()

    fun undoLastTrashMove() {
        val trashIds = _state.value.pendingTrashUndoIds
        if (trashIds.isEmpty()) return
        _state.update { it.copy(pendingTrashUndoIds = persistentListOf()) }
        viewModelScope.launch {
            trashRepository.restoreFromTrash(trashIds).onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.toArcileError().userMessage) }
            }
        }
    }

    fun clearPendingTrashUndo() {
        _state.update { it.copy(pendingTrashUndoIds = persistentListOf()) }
    }

    fun clearActiveFileOperation() = operationDelegate.clearActiveOperation()

    fun openPropertiesForSelection() = propertiesDelegate.openPropertiesForSelection()
    fun dismissProperties() = propertiesDelegate.dismissProperties()

    fun copySelectedToClipboard() = clipboardDelegate.copySelectedToClipboard()
    fun cutSelectedToClipboard() = clipboardDelegate.cutSelectedToClipboard()
    fun cancelClipboard() = clipboardDelegate.cancelClipboard()
    fun pasteFromClipboard() = clipboardDelegate.pasteFromClipboard()
    fun removeFromClipboard(path: String) = clipboardDelegate.removeFromClipboard(path)
    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) = clipboardDelegate.resolveConflicts(resolutions)
    fun dismissConflictDialog() = clipboardDelegate.dismissConflictDialog()

}
