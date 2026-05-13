package dev.qtremors.arcile.presentation.recentfiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.presentation.filterFilesByFolderTab
import dev.qtremors.arcile.presentation.operations.BulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.utils.LocalSearchHelper
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.qtremors.arcile.presentation.browser.toUiModel

enum class RecentNativeAction { TRASH }

data class RecentFilesState(
    val currentVolumeId: String? = null,
    val recentFiles: List<FileModel> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentOffset: Int = 0,
    val selectedFolderTabPath: String? = null,
    val selectedFileType: String? = null,
    val error: String? = null,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val pendingNativeAction: RecentNativeAction? = null,
    val searchQuery: String = "",
    val searchResults: List<FileModel> = emptyList(),
    val isSearching: Boolean = false,
    val todayStart: Long = 0L,
    val yesterdayStart: Long = 0L,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: dev.qtremors.arcile.presentation.browser.PropertiesUiModel? = null
)

@HiltViewModel
class RecentFilesViewModel @Inject constructor(
    private val repository: FileRepository,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(RecentFilesState())
    val state: StateFlow<RecentFilesState> = _state.asStateFlow()

    private val _nativeRequestFlow = MutableSharedFlow<android.content.IntentSender>()
    val nativeRequestFlow: SharedFlow<android.content.IntentSender> = _nativeRequestFlow.asSharedFlow()

    private val localSearchHelper = LocalSearchHelper(
        scope = viewModelScope,
        source = { _state.value.recentFiles },
        matches = { file: FileModel, query: String -> file.name.contains(query, ignoreCase = true) },
        onQueryChanged = { query -> _state.update { it.copy(searchQuery = query) } },
        onSearchingChanged = { isSearching -> _state.update { it.copy(isSearching = isSearching) } },
        onResultsChanged = { results -> _state.update { it.copy(searchResults = results) } }
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
                _state.update { it.copy(pendingNativeAction = RecentNativeAction.TRASH) }
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
        onSuccess = { loadRecentFiles(false) },
        onFailure = { loadRecentFiles(false) }
    )

    init {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val tStart = cal.timeInMillis

        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yStart = cal.timeInMillis

        val volumeId: String? = try {
            savedStateHandle.toRoute<AppRoutes.RecentFiles>().volumeId
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            savedStateHandle.get<String>("volumeId")
        }
        _state.update { it.copy(currentVolumeId = volumeId?.takeIf { value -> value.isNotBlank() }, todayStart = tStart, yesterdayStart = yStart) }
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            repository.observeStorageVolumes()
                .debounce(1000L)
                .distinctUntilChanged()
                .collectLatest {
                    loadRecentFiles(false)
                }
        }
    }

    fun loadRecentFiles(pullToRefresh: Boolean = false, loadMore: Boolean = false) {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val newTodayStart = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val newYesterdayStart = cal.timeInMillis

        val capturedState = _state.value
        if (loadMore && (capturedState.isLoadingMore || !capturedState.hasMore)) return

        val offset = if (loadMore) capturedState.currentOffset + 50 else 0

        _state.update {
            if (loadMore) {
                it.copy(isLoadingMore = true, error = null, todayStart = newTodayStart, yesterdayStart = newYesterdayStart)
            } else {
                it.copy(
                    isLoading = !pullToRefresh,
                    isPullToRefreshing = pullToRefresh,
                    error = null,
                    currentOffset = 0,
                    hasMore = true,
                    selectedFolderTabPath = null,
                    selectedFileType = null,
                    todayStart = newTodayStart,
                    yesterdayStart = newYesterdayStart
                )
            }
        }
        viewModelScope.launch {
            val scope = capturedState.currentVolumeId?.let { StorageScope.Volume(it) } ?: StorageScope.AllStorage
            val result = repository.getRecentFiles(scope = scope, limit = 50, offset = offset)
            result.onSuccess { files ->
                _state.update {
                    if (loadMore && it.currentOffset != capturedState.currentOffset) return@update it
                    val newFiles = if (loadMore) it.recentFiles + files else files
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        isLoadingMore = false,
                        recentFiles = newFiles,
                        currentOffset = offset,
                        hasMore = files.size == 50,
                        searchResults = if (it.searchQuery.isNotBlank()) {
                            newFiles.filter { file -> file.name.contains(it.searchQuery, ignoreCase = true) }
                        } else emptyList()
                    )
                }
            }.onFailure { error ->
                _state.update {
                    if (loadMore && it.currentOffset != capturedState.currentOffset) return@update it
                    it.copy(isLoading = false, isPullToRefreshing = false, isLoadingMore = false, error = error.message ?: "Failed to load recent files")
                }
            }
        }
    }

    fun loadMore() {
        loadRecentFiles(loadMore = true)
    }

    fun toggleSelection(path: String) {
        _state.update { currentState ->
            val updatedSelection = if (currentState.selectedFiles.contains(path)) {
                currentState.selectedFiles - path
            } else {
                currentState.selectedFiles + path
            }
            currentState.copy(
                selectedFiles = updatedSelection,
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
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun requestDeleteSelected() = deleteFlowDelegate.requestDeleteSelected()
    fun togglePermanentDelete() = deleteFlowDelegate.togglePermanentDelete()
    fun confirmDeleteSelected() = deleteFlowDelegate.confirmDeleteSelected()
    fun dismissDeleteConfirmation() = deleteFlowDelegate.dismissDeleteConfirmation()
    fun moveSelectedToTrash() = deleteFlowDelegate.moveSelectedToTrash()
    fun deleteSelectedPermanently() = deleteFlowDelegate.deleteSelectedPermanently()

    fun selectAll() {
        _state.update { currentState ->
            val allPaths = if (currentState.searchQuery.isNotBlank()) {
                currentState.searchResults.map { it.absolutePath }
            } else {
                currentState.visibleRecentFiles().map { it.absolutePath }
            }
            currentState.copy(selectedFiles = allPaths.toSet())
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun updateSearchQuery(query: String) {
        if (query.isBlank()) {
            _state.update { it.copy(selectedFolderTabPath = null) }
        }
        localSearchHelper.updateQuery(query)
    }

    fun selectFolderTab(path: String?) {
        _state.update { currentState ->
            currentState.copy(
                selectedFolderTabPath = path,
                selectedFiles = emptySet(),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun selectFileType(categoryName: String?) {
        _state.update { currentState ->
            currentState.copy(
                selectedFileType = categoryName,
                selectedFiles = emptySet(),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
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
}

fun RecentFilesState.visibleRecentFiles(): List<FileModel> {
    val folderFiltered = filterFilesByFolderTab(recentFiles, selectedFolderTabPath)
    val category = selectedFileType?.let { selected -> FileCategories.all.find { it.name == selected } }
    return if (category == null) {
        folderFiltered
    } else {
        folderFiltered.filter { file ->
            FileCategories.getCategoryForFile(file.extension, file.mimeType)?.name == category.name
        }
    }
}
