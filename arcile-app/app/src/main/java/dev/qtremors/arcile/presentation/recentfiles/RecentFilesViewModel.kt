package dev.qtremors.arcile.presentation.recentfiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.supportsTrash
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes

enum class RecentNativeAction { TRASH }

data class RecentFilesState(
    val currentVolumeId: String? = null,
    val recentFiles: List<FileModel> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val isPullToRefreshing: Boolean = false,
    val error: String? = null,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val nativeRequest: android.content.IntentSender? = null,
    val pendingNativeAction: RecentNativeAction? = null
)



@HiltViewModel
class RecentFilesViewModel @Inject constructor(
    private val repository: FileRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(RecentFilesState())
    val state: StateFlow<RecentFilesState> = _state.asStateFlow()

    init {
        val volumeId: String? = try {
            savedStateHandle.toRoute<AppRoutes.RecentFiles>().volumeId
        } catch (e: Exception) {
            savedStateHandle.get<String>("volumeId")
        }
        _state.update { it.copy(currentVolumeId = volumeId?.takeIf { it.isNotBlank() }) }
        viewModelScope.launch {
            repository.observeStorageVolumes().collectLatest {
                loadRecentFiles(false)
            }
        }
        loadRecentFiles(false)
    }

    fun loadRecentFiles(pullToRefresh: Boolean = false) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = !pullToRefresh, isPullToRefreshing = pullToRefresh, error = null) }
            val scope = _state.value.currentVolumeId?.let { StorageScope.Volume(it) } ?: StorageScope.AllStorage
            val result = repository.getRecentFiles(scope = scope, limit = 500)
            result.onSuccess { files ->
                _state.update { it.copy(isLoading = false, isPullToRefreshing = false, recentFiles = files) }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, isPullToRefreshing = false, error = error.message ?: "Failed to load recent files") }
            }
        }
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
                    _state.update { it.copy(isLoading = false, showPermanentDeleteConfirmation = true) }
                }
                is dev.qtremors.arcile.domain.DeletePolicyResult.Trash -> {
                    _state.update { it.copy(isLoading = false, showTrashConfirmation = true) }
                }
            }
        }
    }

    fun dismissDeleteConfirmation() {
        _state.update { it.copy(showTrashConfirmation = false, showPermanentDeleteConfirmation = false, showMixedDeleteExplanation = false) }
    }

    fun moveSelectedToTrash() {
        val selected = _state.value.selectedFiles.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showTrashConfirmation = false) }
            val result = repository.moveToTrash(selected)
            result.onSuccess {
                clearSelection()
                loadRecentFiles(false)
            }.onFailure { error ->
                if (error is dev.qtremors.arcile.domain.NativeConfirmationRequiredException) {
                    _state.update { it.copy(isLoading = false, nativeRequest = error.intentSender, pendingNativeAction = RecentNativeAction.TRASH) }
                } else {
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to move files to Trash") }
                    loadRecentFiles(false)
                }
            }
        }
    }

    fun clearNativeRequest() {
        _state.update { it.copy(nativeRequest = null) }
    }

    fun deleteSelectedPermanently() {
        val selected = _state.value.selectedFiles.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showPermanentDeleteConfirmation = false) }
            val result = repository.deletePermanently(selected)
            result.onSuccess {
                clearSelection()
                loadRecentFiles(false)
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to delete files") }
                loadRecentFiles(false)
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

}
