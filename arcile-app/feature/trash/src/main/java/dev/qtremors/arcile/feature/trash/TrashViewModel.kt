package dev.qtremors.arcile.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus
import dev.qtremors.arcile.core.storage.domain.DestinationRequiredException
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationRequirement
import dev.qtremors.arcile.core.storage.domain.onAuthorizationRequired
import dev.qtremors.arcile.core.storage.domain.onFailure
import dev.qtremors.arcile.core.storage.domain.onSuccess
import dev.qtremors.arcile.core.storage.domain.joinStoragePath
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.presentation.SelectionReducer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
internal class TrashViewModel @Inject constructor(
    private val trashRepository: TrashRepository,
    private val volumeRepository: VolumeRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrashState())
    val state: StateFlow<TrashState> = _state.asStateFlow()

    private val searchController = TrashSearchController(viewModelScope) { _state.value.trashFiles }

    init {
        viewModelScope.launch {
            searchController.state.collectLatest { searchState ->
                _state.update { current ->
                    current.copy(
                        searchQuery = searchState.query,
                        searchResults = searchState.results,
                        isSearching = searchState.isSearching,
                        searchError = searchState.error
                    )
                }
            }
        }
        loadTrashFiles()
        viewModelScope.launch {
            volumeRepository.observeStorageVolumes().collect { volumes ->
                _state.update { it.copy(availableVolumes = volumes) }
            }
        }
    }

    fun loadTrashFiles() {
        _state.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            val result = trashRepository.getTrashFiles()
            result.onSuccess { trashItems ->
                _state.update { currentState -> 
                    currentState.copy(
                        isLoading = false, 
                        trashFiles = trashItems, 
                        visibleTrashFiles = applyTrashPresentation(trashItems, currentState.filter, currentState.sortOption),
                        error = null,
                        selectedFiles = SelectionReducer.retain(
                            currentState.selectedFiles,
                            trashItems.map(TrashMetadata::id)
                        ),
                        searchResults = if (currentState.searchQuery.isNotBlank()) {
                            applyTrashPresentation(
                                trashItems.filter { it.fileModel.name.contains(currentState.searchQuery, ignoreCase = true) },
                                currentState.filter,
                                currentState.sortOption
                            )
                        } else emptyList()


                    )
                }
                searchController.refresh()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_trash_failed)) }
            }
        }
    }

    fun toggleSelection(path: String) {
        _state.update { currentState ->
            currentState.copy(
                selectedFiles = SelectionReducer.toggle(currentState.selectedFiles, path)
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet()) }
    }

    fun restoreSelectedTrash() {
        clearPendingAuthorization()
        val selectedTrashIds = _state.value.selectedFiles.toList()
        if (selectedTrashIds.isEmpty()) return
        val selectedItems = _state.value.trashFiles.filter { it.id in selectedTrashIds }
        val undoPaths = selectedItems.mapNotNull { it.originalPath.takeIf(String::isNotBlank) }
        val hasDestinationRequiredItems = selectedItems.any {
            it.restoreStatus == TrashRestoreStatus.DESTINATION_REQUIRED ||
                it.restoreStatus == TrashRestoreStatus.RECOVERED_ITEM
        }
        if (hasDestinationRequiredItems) {
            _state.update {
                it.copy(
                    showDestinationPicker = true,
                    selectedTrashIdsForDestination = selectedTrashIds,
                    error = null
                )
            }
            return
        }
        val conflictCount = selectedItems.count { it.restoreStatus == TrashRestoreStatus.ORIGINAL_CONFLICT_RENAME }

        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            trashRepository.restoreFromTrash(selectedTrashIds).onSuccess {
                val message = restoreSummaryMessage(selectedTrashIds.size, conflictCount)
                clearSelection()
                _state.update { it.copy(snackbarMessage = message, pendingRestoreUndoPaths = undoPaths) }
                loadTrashFiles()
            }.onAuthorizationRequired { requirement ->
                requestNativeAuthorization(requirement, TrashAuthorizationAction.RESTORE)
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
                    else -> {
                        _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_restore_files_failed)) }
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
        clearPendingAuthorization()

        _state.update { it.copy(isLoading = true, error = null, showDestinationPicker = false) }
        viewModelScope.launch {
            trashRepository.restoreFromTrash(trashIds, destinationPath).onSuccess {
                val normalizedIds = trashIds.map { it.removePrefix("legacy:") }.toSet()
                val undoPaths = _state.value.trashFiles
                    .filter { it.id in normalizedIds }
                    .map { joinStoragePath(destinationPath, it.fileModel.name) }
                _state.update {
                    it.copy(
                        selectedFiles = it.selectedFiles - normalizedIds,
                        selectedTrashIdsForDestination = emptyList(),
                        pendingDestinationPath = null,
                        pendingRestoreIds = emptyList(),
                        pendingRestoreUndoPaths = undoPaths,
                        snackbarMessage = restoreSummaryMessage(trashIds.size, 0)
                    )
                }
                loadTrashFiles()
            }.onAuthorizationRequired { requirement ->
                requestNativeAuthorization(
                    requirement = requirement,
                    action = TrashAuthorizationAction.RESTORE_TO_DESTINATION,
                    destinationPath = destinationPath,
                    restoreIds = trashIds
                )
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_restore_files_failed)) }
                loadTrashFiles()
            }
        }
    }

    fun emptyTrash() {
        clearPendingAuthorization()
        _state.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            trashRepository.emptyTrash().onSuccess {
                clearSelection()
                loadTrashFiles()
            }.onAuthorizationRequired { requirement ->
                requestNativeAuthorization(requirement, TrashAuthorizationAction.EMPTY)
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_empty_trash_failed)) }
                loadTrashFiles()
            }
        }
    }

    fun selectAll() {
        val allIds = SelectionReducer.all(_state.value.trashFiles.map(TrashMetadata::id))
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
        clearPendingAuthorization()

        // Permanent trash deletion is intentionally repository-backed for now. If trash/delete
        // operations move into BulkFileOperationService later, this VM should observe coordinator events.
        _state.update { it.copy(isLoading = true, error = null, showPermanentDeleteConfirmation = false) }
        viewModelScope.launch {
            trashRepository.deletePermanentlyFromTrash(selectedIds).onSuccess {
                clearSelection()
                loadTrashFiles()
            }.onAuthorizationRequired { requirement ->
                requestNativeAuthorization(requirement, TrashAuthorizationAction.DELETE)
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_delete_files_permanently_failed)) }
                loadTrashFiles()
            }
        }
    }

    private fun requestNativeAuthorization(
        requirement: StorageAuthorizationRequirement,
        action: TrashAuthorizationAction,
        destinationPath: String? = null,
        restoreIds: List<String> = emptyList()
    ) {
        _state.update {
            it.copy(
                isLoading = false,
                pendingAuthorizationAction = action,
                pendingAuthorization = requirement,
                pendingDestinationPath = destinationPath,
                pendingRestoreIds = restoreIds
            )
        }
    }

    private fun clearPendingAuthorization() {
        _state.update {
            it.copy(
                pendingAuthorizationAction = null,
                pendingAuthorization = null,
                pendingDestinationPath = null,
                pendingRestoreIds = emptyList()
            )
        }
    }

    fun handleAuthorizationResult(requestId: String, confirmed: Boolean) {
        val current = _state.value
        if (current.pendingAuthorization?.requestId != requestId) return
        val action = current.pendingAuthorizationAction
        val destinationPath = current.pendingDestinationPath
        val restoreIds = current.pendingRestoreIds
        clearPendingAuthorization()
        if (!confirmed) {
            _state.update { it.copy(isLoading = false) }
            return
        }
        when (action) {
            TrashAuthorizationAction.RESTORE -> restoreSelectedTrash()
            TrashAuthorizationAction.RESTORE_TO_DESTINATION -> {
                if (destinationPath != null && restoreIds.isNotEmpty()) {
                    restoreToDestination(restoreIds, destinationPath)
                }
            }
            TrashAuthorizationAction.EMPTY -> emptyTrash()
            TrashAuthorizationAction.DELETE -> deletePermanentlySelected()
            null -> Unit
        }
    }

    fun handleAuthorizationUnavailable(requestId: String) {
        val current = _state.value
        if (current.pendingAuthorization?.requestId != requestId) return
        val error = when (current.pendingAuthorizationAction) {
            TrashAuthorizationAction.EMPTY -> R.string.error_empty_trash_failed
            TrashAuthorizationAction.DELETE -> R.string.error_delete_files_permanently_failed
            TrashAuthorizationAction.RESTORE,
            TrashAuthorizationAction.RESTORE_TO_DESTINATION,
            null -> R.string.error_restore_files_failed
        }
        clearPendingAuthorization()
        _state.update { it.copy(isLoading = false, error = UiText.StringResource(error)) }
    }

    fun clearError() {
        searchController.clearError()
        _state.update { it.copy(error = null, searchError = null) }
    }

    fun clearSnackbarMessage() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    fun undoLastRestore() {
        val paths = _state.value.pendingRestoreUndoPaths
        if (paths.isEmpty()) return
        _state.update { it.copy(pendingRestoreUndoPaths = emptyList(), isLoading = true, error = null) }
        viewModelScope.launch {
            trashRepository.moveToTrash(paths).onSuccess {
                loadTrashFiles()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_delete_files_permanently_failed)) }
                loadTrashFiles()
            }
        }
    }

    fun clearPendingRestoreUndo() {
        _state.update { it.copy(pendingRestoreUndoPaths = emptyList()) }
    }

    fun updateSearchQuery(query: String) {
        _state.update {
            it.copy(
                searchQuery = query,
                searchResults = if (query.isBlank()) emptyList() else it.searchResults,
                isSearching = if (query.isBlank()) false else it.isSearching
            )
        }
        searchController.updateQuery(query)
    }

    fun updateSortOption(sortOption: TrashSortOption) {
        _state.update {
            it.copy(
                sortOption = sortOption,
                visibleTrashFiles = applyTrashPresentation(it.trashFiles, it.filter, sortOption),
                searchResults = applyTrashPresentation(searchMatches(it.trashFiles, it.searchQuery), it.filter, sortOption)
            )
        }
        searchController.updatePresentation(_state.value.filter, sortOption)
    }

    fun updateFilter(filter: TrashFilter) {
        _state.update {
            val visibleItems = applyTrashPresentation(it.trashFiles, filter, it.sortOption)
            it.copy(
                filter = filter,
                visibleTrashFiles = visibleItems,
                searchResults = applyTrashPresentation(searchMatches(it.trashFiles, it.searchQuery), filter, it.sortOption),
                selectedFiles = it.selectedFiles.filter { id ->
                    visibleItems.any { item -> item.id == id }
                }.toSet()
            )
        }
        searchController.updatePresentation(filter, _state.value.sortOption)
    }

    fun openPropertiesForSelection() {
        val selectedItems = _state.value.trashFiles.filter { it.id in _state.value.selectedFiles }
        if (selectedItems.isEmpty()) return
        _state.update { it.copy(isPropertiesVisible = true, properties = selectedItems.toPropertiesModel()) }
    }

    fun dismissProperties() {
        _state.update { it.copy(isPropertiesVisible = false, properties = null) }
    }

    private fun restoreSummaryMessage(restoredCount: Int, conflictCount: Int): UiText {
        return if (conflictCount > 0) {
            UiText.PluralResource(
                R.plurals.trash_restored_items_with_conflicts,
                restoredCount,
                listOf(restoredCount, conflictCount)
            )
        } else {
            UiText.PluralResource(R.plurals.trash_restored_items, restoredCount, listOf(restoredCount))
        }
    }

}

