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
import kotlinx.coroutines.flow.asSharedFlow
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
    val pendingNativeAction: NativeAction? = null,
    val pendingDestinationPath: String? = null,
    val pendingRestoreIds: List<String> = emptyList(),
    val availableVolumes: List<dev.qtremors.arcile.domain.StorageVolume> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<TrashMetadata> = emptyList(),
    val isSearching: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false
)


@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrashState())
    val state: StateFlow<TrashState> = _state.asStateFlow()

    private val _nativeRequestFlow = kotlinx.coroutines.flow.MutableSharedFlow<android.content.IntentSender>()
    val nativeRequestFlow: kotlinx.coroutines.flow.SharedFlow<android.content.IntentSender> = _nativeRequestFlow.asSharedFlow()

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
                        selectedFiles = currentState.selectedFiles.filter { path -> trashItems.any { it.id == path } }.toSet(),
                        searchResults = if (currentState.searchQuery.isNotBlank()) {
                            trashItems.filter { it.fileModel.name.contains(currentState.searchQuery, ignoreCase = true) }
                        } else emptyList()


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
                        _state.update { it.copy(isLoading = false, pendingNativeAction = NativeAction.RESTORE) }
                        viewModelScope.launch { _nativeRequestFlow.emit(error.intentSender) }
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
                            pendingNativeAction = NativeAction.RESTORE_TO_DESTINATION,
                            pendingDestinationPath = destinationPath,
                            pendingRestoreIds = trashIds
                        ) 
                    }
                    viewModelScope.launch { _nativeRequestFlow.emit(error.intentSender) }
                } else {
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to restore files") }
                    loadTrashFiles()
                }
            }
        }
    }

    fun emptyTrash() {
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            repository.emptyTrash().onSuccess {
                clearSelection()
                loadTrashFiles()
            }.onFailure { error ->
                if (error is NativeConfirmationRequiredException) {
                    _state.update { it.copy(isLoading = false, pendingNativeAction = NativeAction.EMPTY) }
                    viewModelScope.launch { _nativeRequestFlow.emit(error.intentSender) }
                } else {
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to empty Trash Bin") }
                    loadTrashFiles()
                }
            }
        }
    }

    fun selectAll() {
        val allIds = _state.value.trashFiles.map { it.id }.toSet()
        _state.update { it.copy(selectedFiles = allIds) }
    }

    fun requestDeletePermanentlySelected() {
        if (_state.value.selectedFiles.isNotEmpty()) {
            _state.update { it.copy(showPermanentDeleteConfirmation = true) }
        }
    }

    fun dismissPermanentDeleteConfirmation() {
        _state.update { it.copy(showPermanentDeleteConfirmation = false) }
    }

    fun deletePermanentlySelected() {
        val selectedIds = _state.value.selectedFiles.toList()
        if (selectedIds.isEmpty()) return

        _state.update { it.copy(isLoading = true, error = null, showPermanentDeleteConfirmation = false) }
        viewModelScope.launch {
            repository.deletePermanentlyFromTrash(selectedIds).onSuccess {
                clearSelection()
                loadTrashFiles()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to delete files permanently") }
                loadTrashFiles()
            }
        }
    }

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
            val filtered = _state.value.trashFiles.filter { it.fileModel.name.contains(query, ignoreCase = true) }
            _state.update { it.copy(isSearching = false, searchResults = filtered) }

        }
    }
}

