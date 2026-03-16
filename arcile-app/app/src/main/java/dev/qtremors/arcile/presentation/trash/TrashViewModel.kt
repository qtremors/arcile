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
    val error: String? = null,
    val showDestinationPicker: Boolean = false,
    val availableVolumes: List<dev.qtremors.arcile.domain.StorageVolume> = emptyList()
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrashState())
    val state: StateFlow<TrashState> = _state.asStateFlow()

    init {
        loadTrashFiles()
        viewModelScope.launch {
            repository.observeStorageVolumes().collect { volumes ->
                _state.update { it.copy(availableVolumes = volumes) }
            }
        }
    }

    fun loadTrashFiles() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.getTrashFiles()
            result.onSuccess { trashItems ->
                _state.update { currentState -> 
                    currentState.copy(
                        isLoading = false, 
                        trashFiles = trashItems, 
                        error = null,
                        selectedFiles = currentState.selectedFiles.filter { path -> trashItems.any { it.id == path } }.toSet()
                    )
                }
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
                val msg = error.message ?: ""
                if (msg.startsWith("DESTINATION_REQUIRED")) {
                    _state.update { it.copy(isLoading = false, showDestinationPicker = true) }
                } else {
                    _state.update { it.copy(isLoading = false, error = msg.ifEmpty { "Failed to restore files" }) }
                    loadTrashFiles()
                }
            }
        }
    }

    fun dismissDestinationPicker() {
        _state.update { it.copy(showDestinationPicker = false) }
    }

    fun restoreToDestination(destinationPath: String) {
        val selectedTrashIds = _state.value.selectedFiles.toList()
        if (selectedTrashIds.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showDestinationPicker = false) }
            val result = repository.restoreFromTrash(selectedTrashIds, destinationPath)
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
