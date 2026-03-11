package dev.qtremors.arcile.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.TrashMetadata
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrashState(
    val trashFiles: List<TrashMetadata> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrashState())
    val state: StateFlow<TrashState> = _state.asStateFlow()

    init {
        loadTrashFiles()
    }

    fun loadTrashFiles() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, selectedFiles = emptySet()) }
            val result = repository.getTrashFiles()
            result.onSuccess { trashItems ->
                _state.update { it.copy(isLoading = false, trashFiles = trashItems) }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load Trash Bin") }
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

    fun restoreSelectedTrash() {
        val selectedTrashIds = _state.value.selectedFiles.toList()
        if (selectedTrashIds.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.restoreFromTrash(selectedTrashIds)
            result.onSuccess {
                clearSelection()
                loadTrashFiles()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to restore files") }
                loadTrashFiles()
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.emptyTrash()
            result.onSuccess {
                clearSelection()
                loadTrashFiles()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to empty Trash Bin") }
                loadTrashFiles()
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
