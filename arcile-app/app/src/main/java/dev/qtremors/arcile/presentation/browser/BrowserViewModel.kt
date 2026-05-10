package dev.qtremors.arcile.presentation.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.data.BrowserPreferencesStore
import dev.qtremors.arcile.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.domain.BrowserViewMode
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageBrowserLocation
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.presentation.ClipboardState
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.presentation.browser.delegate.ClipboardDelegate
import dev.qtremors.arcile.presentation.browser.delegate.NavigationDelegate
import dev.qtremors.arcile.presentation.browser.delegate.SearchDelegate
import dev.qtremors.arcile.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.presentation.operations.BulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.operations.BulkFileOperationEvent
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    val totalBytes: Long? = null
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
    val files: List<FileModel> = emptyList(),
    val folderStatsByPath: Map<String, FolderStats> = emptyMap(),
    val folderStatsLoadingPaths: Set<String> = emptySet(),
    val searchResults: List<FileModel> = emptyList(),
    val isSearching: Boolean = false,
    val browserSearchQuery: String = "",
    val browserSortOption: FileSortOption = FileSortOption.NAME_ASC,
    val browserViewMode: BrowserViewMode = BrowserViewMode.LIST,
    val browserListZoom: Float = BrowserPresentationPreferences.DEFAULT_LIST_ZOOM,
    val browserGridMinCellSize: Float = BrowserPresentationPreferences.DEFAULT_GRID_MIN_CELL_SIZE,
    val browserShowThumbnails: Boolean = BrowserPresentationPreferences.DEFAULT_SHOW_THUMBNAILS,
    val selectedFiles: Set<String> = emptySet(),
    val clipboardState: ClipboardState? = null,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearchFilterMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val error: String? = null,
    val pasteConflicts: List<FileConflict> = emptyList(),
    val showConflictDialog: Boolean = false,
    val storageVolumes: List<StorageVolume> = emptyList(),
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val pendingNativeAction: BrowserNativeAction? = null,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: PropertiesUiModel? = null,
    val activeFileOperation: BrowserFileOperationUiState? = null,
    val fileOperationStatusMessage: String? = null,
    val selectedFilesTotalSize: Long = 0L
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: FileRepository,
    private val browserPreferencesRepository: BrowserPreferencesStore,
    private val savedStateHandle: SavedStateHandle,
    private val getStorageVolumesUseCase: GetStorageVolumesUseCase,
    bulkFileOperationCoordinator: BulkFileOperationCoordinator
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val _nativeRequestFlow = MutableSharedFlow<android.content.IntentSender>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val nativeRequestFlow: SharedFlow<android.content.IntentSender> = _nativeRequestFlow.asSharedFlow()

    private val searchDelegate = SearchDelegate(_state, viewModelScope, repository)
    private val navigationDelegate = NavigationDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        repository = repository,
        browserPreferencesRepository = browserPreferencesRepository,
        savedStateHandle = savedStateHandle,
        onClearSearch = { searchDelegate.updateBrowserSearchQuery("") }
    )
    private val clipboardDelegate = ClipboardDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        repository = repository,
        bulkFileOperationCoordinator = bulkFileOperationCoordinator,
        refreshAction = { navigationDelegate.refresh() }
    )
    private val deleteFlowDelegate = DeleteFlowDelegate(
        coroutineScope = viewModelScope,
        repository = repository,
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
                        showMixedDeleteExplanation = false
                    )
                }
            }
            override fun setError(error: String) {
                _state.update { it.copy(error = error) }
            }
            override fun setPendingNativeAction() {
                _state.update { it.copy(pendingNativeAction = BrowserNativeAction.TRASH) }
            }
            override fun clearSelection() {
                _state.update { it.copy(selectedFiles = emptySet()) }
            }
        },
        startBulkDeleteOperation = { type, selected ->
            bulkFileOperationCoordinator.startOperation(
                type = type,
                sourcePaths = selected,
                destinationPath = null,
                resolutions = emptyMap()
            )
        },
        emitNativeRequest = { sender -> _nativeRequestFlow.emit(sender) },
        onSuccess = { navigationDelegate.refresh() }
    )

    private var isInitialized = false

    init {
        bulkFileOperationCoordinator.activeRequest.value?.let { activeReq ->
            _state.update {
                it.copy(
                    activeFileOperation = BrowserFileOperationUiState(
                        type = activeReq.type,
                        totalItems = activeReq.sourcePaths.size,
                        currentPath = activeReq.sourcePaths.firstOrNull()
                    )
                )
            }
        }

        viewModelScope.launch {
            getStorageVolumesUseCase().collectLatest { volumes ->
                _state.update { it.copy(storageVolumes = volumes) }

                if (!isInitialized) {
                    isInitialized = true
                    when (val location = navigationDelegate.restoreLocationFromState()) {
                        StorageBrowserLocation.Roots -> navigationDelegate.openFileBrowser()
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
                        navigationDelegate.openVolumeRoots("Selected storage was removed")
                    } else if (_state.value.isVolumeRootScreen) {
                        _state.update { it.copy(files = navigationDelegate.volumeFiles()) }
                    }
                }
            }
        }

        viewModelScope.launch {
            repository.observeFolderStatUpdates().collectLatest { update ->
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
                        folderStatsByPath = currentState.folderStatsByPath + (update.path to update.stats),
                        folderStatsLoadingPaths = currentState.folderStatsLoadingPaths - update.path
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
                    )
                }
            }
        }

        viewModelScope.launch {
            bulkFileOperationCoordinator.events.collectLatest { event ->
                when (event) {
                    is BulkFileOperationEvent.Started -> {
                        _state.update {
                            it.copy(
                                isLoading = true,
                                error = null,
                                activeFileOperation = BrowserFileOperationUiState(
                                    type = event.request.type,
                                    totalItems = event.request.sourcePaths.size,
                                    currentPath = event.request.sourcePaths.firstOrNull()
                                ),
                                fileOperationStatusMessage = null
                            )
                        }
                    }
                    is BulkFileOperationEvent.Progress -> {
                        _state.update {
                            it.copy(
                                isLoading = true,
                                activeFileOperation = BrowserFileOperationUiState(
                                    type = event.request.type,
                                    totalItems = event.progress.totalItems,
                                    completedItems = event.progress.completedItems,
                                    currentPath = event.progress.currentPath,
                                    isCancelling = false,
                                    bytesCopied = event.progress.bytesCopied,
                                    totalBytes = event.progress.totalBytes
                                )
                            )
                        }
                    }
                    is BulkFileOperationEvent.Cancelling -> {
                        _state.update { currentState ->
                            currentState.copy(
                                isLoading = true,
                                activeFileOperation = currentState.activeFileOperation?.copy(isCancelling = true)
                                    ?: BrowserFileOperationUiState(
                                        type = event.request.type,
                                        totalItems = event.request.sourcePaths.size,
                                        isCancelling = true
                                    )
                            )
                        }
                    }
                    is BulkFileOperationEvent.Completed -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                activeFileOperation = null,
                                clipboardState = null,
                                fileOperationStatusMessage = formatOperationCompletedMessage(
                                    type = event.request.type,
                                    itemCount = event.request.sourcePaths.size
                                )
                            )
                        }
                    }
                    is BulkFileOperationEvent.Failed -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                activeFileOperation = null,
                                clipboardState = null,
                                fileOperationStatusMessage = event.message
                            )
                        }
                    }
                    is BulkFileOperationEvent.Cancelled -> {
                        _state.update {
                            it.copy(
                                isLoading = false,
                                activeFileOperation = null,
                                clipboardState = null,
                                fileOperationStatusMessage = "File operation cancelled"
                            )
                        }
                    }
                }
            }
        }
    }

    fun openFileBrowser(errorMessage: String? = null) = navigationDelegate.openFileBrowser(errorMessage)
    fun navigateToSpecificFolder(path: String) = navigationDelegate.navigateToSpecificFolder(path)
    fun navigateToCategory(categoryName: String, volumeId: String? = null) = navigationDelegate.navigateToCategory(categoryName, volumeId)
    fun navigateToFolder(path: String) = navigationDelegate.navigateToFolder(path)
    fun navigateBack(): Boolean = navigationDelegate.navigateBack()
    fun refresh(pullToRefresh: Boolean = false) = navigationDelegate.refresh(pullToRefresh)

    fun toggleSelection(path: String) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            val updatedSelection = if (currentState.selectedFiles.contains(path)) {
                currentState.selectedFiles - path
            } else {
                currentState.selectedFiles + path
            }
            currentState.copy(
                selectedFiles = updatedSelection,
                selectedFilesTotalSize = calculateSelectionSize(updatedSelection, currentState.files, currentState.folderStatsByPath),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun selectAll(paths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            val updatedSelection = paths.toSet()
            currentState.copy(
                selectedFiles = updatedSelection,
                selectedFilesTotalSize = calculateSelectionSize(updatedSelection, currentState.files, currentState.folderStatsByPath),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun invertSelection(allPaths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            val currentlySelected = currentState.selectedFiles
            val updatedSelection = allPaths.filter { it !in currentlySelected }.toSet()
            currentState.copy(
                selectedFiles = updatedSelection,
                selectedFilesTotalSize = calculateSelectionSize(updatedSelection, currentState.files, currentState.folderStatsByPath),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    private fun calculateSelectionSize(selectedPaths: Set<String>, currentFiles: List<FileModel>, folderStats: Map<String, FolderStats>): Long {
        var total = 0L
        selectedPaths.forEach { path ->
            val file = currentFiles.find { it.absolutePath == path }
            if (file != null) {
                if (file.isDirectory) {
                    total += folderStats[path]?.totalBytes ?: 0L
                } else {
                    total += file.size
                }
            }
        }
        return total
    }

    fun selectMultiple(paths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            val updatedSelection = currentState.selectedFiles + paths
            currentState.copy(
                selectedFiles = updatedSelection,
                selectedFilesTotalSize = calculateSelectionSize(updatedSelection, currentState.files, currentState.folderStatsByPath),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun clearSelection() {
        _state.update {
            it.copy(
                selectedFiles = emptySet(),
                selectedFilesTotalSize = 0L,
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
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
            )
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
            repository.createDirectory(currentPath, name).onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to create folder") }
            }
        }
    }

    fun createFile(name: String) {
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty() || _state.value.isVolumeRootScreen) return

        viewModelScope.launch {
            repository.createFile(currentPath, name).onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to create file") }
            }
        }
    }

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
            _state.update { it.copy(error = "Invalid name: must not be blank or contain /, \\, or ..") }
            return
        }

        viewModelScope.launch {
            repository.renameFile(path, newName).onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to rename file") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearFileOperationStatusMessage() {
        _state.update { it.copy(fileOperationStatusMessage = null) }
    }

    fun openPropertiesForSelection() {
        val selectedPaths = _state.value.selectedFiles.toList()
        if (selectedPaths.isEmpty()) return

        _state.update {
            it.copy(
                isPropertiesVisible = true,
                isPropertiesLoading = true,
                properties = null
            )
        }

        viewModelScope.launch {
            repository.getSelectionProperties(selectedPaths).onSuccess { properties ->
                _state.update {
                    it.copy(
                        isPropertiesVisible = true,
                        isPropertiesLoading = false,
                        properties = properties.toUiModel()
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isPropertiesVisible = false,
                        isPropertiesLoading = false,
                        properties = null,
                        error = error.message ?: "Failed to load properties"
                    )
                }
            }
        }
    }

    fun dismissProperties() {
        _state.update {
            it.copy(
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun copySelectedToClipboard() = clipboardDelegate.copySelectedToClipboard()
    fun cutSelectedToClipboard() = clipboardDelegate.cutSelectedToClipboard()
    fun cancelClipboard() = clipboardDelegate.cancelClipboard()
    fun pasteFromClipboard() = clipboardDelegate.pasteFromClipboard()
    fun removeFromClipboard(path: String) = clipboardDelegate.removeFromClipboard(path)
    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) = clipboardDelegate.resolveConflicts(resolutions)
    fun dismissConflictDialog() = clipboardDelegate.dismissConflictDialog()

    private fun formatOperationCompletedMessage(
        type: BulkFileOperationType,
        itemCount: Int
    ): String {
        val verb = when (type) {
            BulkFileOperationType.COPY -> "Copied"
            BulkFileOperationType.MOVE -> "Moved"
            BulkFileOperationType.TRASH -> "Moved to Trash"
            BulkFileOperationType.DELETE -> "Deleted"
        }
        return "$verb $itemCount item(s)"
    }
}
