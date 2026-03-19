package dev.qtremors.arcile.presentation.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.TrashMetadata
import android.content.IntentSender
import dev.qtremors.arcile.domain.DestinationRequiredException
import dev.qtremors.arcile.domain.NativeConfirmationRequiredException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NativeAction { RESTORE, RESTORE_TO_DESTINATION, EMPTY }

data class TrashState(
    val trashFiles: List<TrashMetadata> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDestinationPicker: Boolean = false,
    val selectedTrashIdsForDestination: List<String> = emptyList(),
    val nativeRequest: IntentSender? = null,
    val pendingNativeAction: NativeAction? = null,
    val pendingDestinationPath: String? = null,
    val pendingRestoreIds: List<String> = emptyList(),
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
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
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

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.restoreFromTrash(selectedTrashIds).onSuccess {
                clearSelection()
                loadTrashFiles()
            }.onFailure { error ->
                when (error) {
                    is DestinationRequiredException -> {
                        _state.update { 
                            it.copy(
                                isLoading = false, 
                                showDestinationPicker = true,
                                selectedTrashIdsForDestination = error.trashIds
                            )
                        }
                    }
                    is NativeConfirmationRequiredException -> {
                        _state.update { it.copy(isLoading = false, nativeRequest = error.intentSender, pendingNativeAction = NativeAction.RESTORE) }
                    }
                    else -> {
                        _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to restore files") }
                        loadTrashFiles()
                    }
                }
            }
        }
    }

    fun dismissDestinationPicker() {
        _state.update { it.copy(showDestinationPicker = false, selectedTrashIdsForDestination = emptyList()) }
    }

    fun restoreToDestination(trashIds: List<String>, destinationPath: String) {
        if (trashIds.isEmpty()) return

        _state.update { it.copy(isLoading = true, error = null, showDestinationPicker = false) }
        viewModelScope.launch {
            repository.restoreFromTrash(trashIds, destinationPath).onSuccess {
                _state.update { it.copy(selectedFiles = it.selectedFiles - trashIds.toSet(), selectedTrashIdsForDestination = emptyList(), pendingDestinationPath = null, pendingRestoreIds = emptyList()) }
                loadTrashFiles()
            }.onFailure { error ->
                if (error is NativeConfirmationRequiredException) {
                    _state.update { 
                        it.copy(
                            isLoading = false, 
                            nativeRequest = error.intentSender, 
                            pendingNativeAction = NativeAction.RESTORE_TO_DESTINATION,
                            pendingDestinationPath = destinationPath,
                            pendingRestoreIds = trashIds
                        ) 
                    }
                } else {
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to restore files") }
                    loadTrashFiles()
                }
            }
        }
    }

    fun clearNativeRequest() {
        _state.update { it.copy(nativeRequest = null, pendingNativeAction = null, pendingDestinationPath = null, pendingRestoreIds = emptyList()) }
    }

    fun emptyTrash() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.emptyTrash().onSuccess {
                clearSelection()
                loadTrashFiles()
            }.onFailure { error ->
                if (error is NativeConfirmationRequiredException) {
                    _state.update { it.copy(isLoading = false, nativeRequest = error.intentSender, pendingNativeAction = NativeAction.EMPTY) }
                } else {
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to empty Trash Bin") }
                    loadTrashFiles()
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }
}
