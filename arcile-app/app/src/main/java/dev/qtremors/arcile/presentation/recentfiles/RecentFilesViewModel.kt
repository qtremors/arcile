package dev.qtremors.arcile.presentation.recentfiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.presentation.delegate.DeleteStateCallbacks
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
    val yesterdayStart: Long = 0L
)

@HiltViewModel
class RecentFilesViewModel @Inject constructor(
    private val repository: FileRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(RecentFilesState())
    val state: StateFlow<RecentFilesState> = _state.asStateFlow()

    private val _nativeRequestFlow = MutableSharedFlow<android.content.IntentSender>()
    val nativeRequestFlow: SharedFlow<android.content.IntentSender> = _nativeRequestFlow.asSharedFlow()

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
        executeMoveToTrash = { selected -> repository.moveToTrash(selected) },
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
        val capturedState = _state.value
        if (loadMore && (capturedState.isLoadingMore || !capturedState.hasMore)) return

        val offset = if (loadMore) capturedState.currentOffset + 50 else 0

        _state.update {
            if (loadMore) {
                it.copy(isLoadingMore = true, error = null)
            } else {
                it.copy(isLoading = !pullToRefresh, isPullToRefreshing = pullToRefresh, error = null, currentOffset = 0, hasMore = true)
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
            currentState.copy(selectedFiles = updatedSelection)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet()) }
    }

    fun requestDeleteSelected() = deleteFlowDelegate.requestDeleteSelected()
    fun togglePermanentDelete() = deleteFlowDelegate.togglePermanentDelete()
    fun confirmDeleteSelected() = deleteFlowDelegate.confirmDeleteSelected()
    fun dismissDeleteConfirmation() = deleteFlowDelegate.dismissDeleteConfirmation()
    fun moveSelectedToTrash() = deleteFlowDelegate.moveSelectedToTrash()
    fun deleteSelectedPermanently() = deleteFlowDelegate.deleteSelectedPermanently()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            searchJob?.cancel()
        } else {
            debouncedSearch(query)
        }
    }

    private fun debouncedSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(300)
            _state.update { it.copy(isSearching = true) }
            val filtered = _state.value.recentFiles.filter { it.name.contains(query, ignoreCase = true) }
            _state.update { it.copy(isSearching = false, searchResults = filtered) }
        }
    }
}

