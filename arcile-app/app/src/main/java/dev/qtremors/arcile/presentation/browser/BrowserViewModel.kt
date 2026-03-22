package dev.qtremors.arcile.presentation.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.data.BrowserPreferencesStore
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.domain.usecase.MoveToTrashUseCase
import dev.qtremors.arcile.domain.usecase.PasteFilesUseCase
import dev.qtremors.arcile.presentation.ClipboardState
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.presentation.browser.delegate.ClipboardDelegate
import dev.qtremors.arcile.presentation.browser.delegate.NavigationDelegate
import dev.qtremors.arcile.presentation.browser.delegate.SearchDelegate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    val searchResults: List<FileModel> = emptyList(),
    val isSearching: Boolean = false,
    val browserSearchQuery: String = "",
    val browserSortOption: FileSortOption = FileSortOption.NAME_ASC,
    val isGridView: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val clipboardState: ClipboardState? = null,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearchFilterMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val error: String? = null,
    val pasteConflicts: List<FileConflict> = emptyList(),
    val showConflictDialog: Boolean = false,
    val storageVolumes: List<dev.qtremors.arcile.domain.StorageVolume> = emptyList(),
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
    private val pasteFilesUseCase: PasteFilesUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()
    
    private val _nativeRequestFlow = kotlinx.coroutines.flow.MutableSharedFlow<android.content.IntentSender>()
    val nativeRequestFlow: kotlinx.coroutines.flow.SharedFlow<android.content.IntentSender> = _nativeRequestFlow.asSharedFlow()

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
        pasteFilesUseCase = pasteFilesUseCase,
        refreshAction = { navigationDelegate.refresh() }
    )

    private var isInitialized = false

    init {
        viewModelScope.launch {
            getStorageVolumesUseCase().collectLatest { volumes ->
                _state.update { it.copy(storageVolumes = volumes) }
                
                if (!isInitialized) {
                    isInitialized = true
                    when (val location = navigationDelegate.restoreLocationFromState()) {
                        dev.qtremors.arcile.domain.StorageBrowserLocation.Roots -> navigationDelegate.openFileBrowser()
                        is dev.qtremors.arcile.domain.StorageBrowserLocation.Directory -> {
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
                        is dev.qtremors.arcile.domain.StorageBrowserLocation.Category -> {
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

    fun updateBrowserSortOption(sortOption: FileSortOption, applyToSubfolders: Boolean) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { it.copy(browserSortOption = sortOption) }
        viewModelScope.launch {
            if (_state.value.isCategoryScreen) {
                browserPreferencesRepository.updatePathSortOption("category_${_state.value.activeCategoryName}", sortOption, applyToSubfolders = false)
            } else {
                val path = _state.value.currentPath
                if (path.isNotEmpty()) {
                    browserPreferencesRepository.updatePathSortOption(path, sortOption, applyToSubfolders)
                } else if (applyToSubfolders) {
                    browserPreferencesRepository.updateGlobalSortOption(sortOption)
                }
            }
        }
    }

    fun setGridView(enabled: Boolean) {
        _state.update { it.copy(isGridView = enabled) }
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

    fun requestDeleteSelected() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            val policyResult = dev.qtremors.arcile.domain.evaluateDeletePolicy(selectedFiles, repository)

            when (policyResult) {
                is dev.qtremors.arcile.domain.DeletePolicyResult.MixedSelection -> {
                    _state.update { it.copy(isLoading = false, showMixedDeleteExplanation = true) }
                }
                is dev.qtremors.arcile.domain.DeletePolicyResult.PermanentDelete -> {
                    _state.update { it.copy(
                        isLoading = false, 
                        showPermanentDeleteConfirmation = true,
                        isPermanentDeleteChecked = true,
                        isPermanentDeleteToggleEnabled = false
                    ) }
                }
                is dev.qtremors.arcile.domain.DeletePolicyResult.Trash -> {
                    _state.update { it.copy(
                        isLoading = false, 
                        showTrashConfirmation = true,
                        isPermanentDeleteChecked = false,
                        isPermanentDeleteToggleEnabled = true
                    ) }
                }
            }
        }
    }

    fun togglePermanentDelete() {
        if (_state.value.isPermanentDeleteToggleEnabled) {
            _state.update { it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked) }
        }
    }

    fun confirmDeleteSelected() {
        if (_state.value.isPermanentDeleteChecked) {
            deleteSelectedPermanently()
        } else {
            moveSelectedToTrash()
        }
    }

    fun dismissDeleteConfirmation() {
        _state.update { it.copy(showTrashConfirmation = false, showPermanentDeleteConfirmation = false, showMixedDeleteExplanation = false) }
    }

    fun moveSelectedToTrash() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showTrashConfirmation = false, showPermanentDeleteConfirmation = false) }
            moveToTrashUseCase(selectedFiles).onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                if (error is dev.qtremors.arcile.domain.NativeConfirmationRequiredException) {
                    _state.update { it.copy(isLoading = false, pendingNativeAction = BrowserNativeAction.TRASH) }
                    viewModelScope.launch { _nativeRequestFlow.emit(error.intentSender) }
                } else {
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to move files to Trash") }
                }
            }
        }
    }

    fun deleteSelectedPermanently() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showTrashConfirmation = false, showPermanentDeleteConfirmation = false) }
            repository.deletePermanently(selectedFiles).onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to delete files") }
            }
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
