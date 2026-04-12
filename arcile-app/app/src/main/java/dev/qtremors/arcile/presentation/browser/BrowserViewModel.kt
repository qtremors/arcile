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
import dev.qtremors.arcile.domain.usecase.MoveToTrashUseCase
import dev.qtremors.arcile.presentation.ClipboardState
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.presentation.browser.delegate.ClipboardDelegate
import dev.qtremors.arcile.presentation.browser.delegate.NavigationDelegate
import dev.qtremors.arcile.presentation.browser.delegate.SearchDelegate
import dev.qtremors.arcile.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.presentation.operations.BulkFileOperationCoordinator
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
    val pendingNativeAction: BrowserNativeAction? = null
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: FileRepository,
    private val browserPreferencesRepository: BrowserPreferencesStore,
    private val savedStateHandle: SavedStateHandle,
    private val getStorageVolumesUseCase: GetStorageVolumesUseCase,
    private val moveToTrashUseCase: MoveToTrashUseCase,
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
        executeMoveToTrash = { selected -> moveToTrashUseCase(selected) },
        emitNativeRequest = { sender -> _nativeRequestFlow.emit(sender) },
        onSuccess = { navigationDelegate.refresh() }
    )

    private var isInitialized = false

    init {
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
            currentState.copy(selectedFiles = updatedSelection)
        }
    }

    fun selectMultiple(paths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            currentState.copy(selectedFiles = currentState.selectedFiles + paths)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet()) }
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
                browserGridMinCellSize = normalized.gridMinCellSize
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

    fun copySelectedToClipboard() = clipboardDelegate.copySelectedToClipboard()
    fun cutSelectedToClipboard() = clipboardDelegate.cutSelectedToClipboard()
    fun cancelClipboard() = clipboardDelegate.cancelClipboard()
    fun pasteFromClipboard() = clipboardDelegate.pasteFromClipboard()
    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) = clipboardDelegate.resolveConflicts(resolutions)
    fun dismissConflictDialog() = clipboardDelegate.dismissConflictDialog()
}
