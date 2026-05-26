package dev.qtremors.arcile.feature.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.R
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus
import android.content.IntentSender
import dev.qtremors.arcile.core.storage.domain.DestinationRequiredException
import dev.qtremors.arcile.core.storage.domain.NativeConfirmationRequiredException
import dev.qtremors.arcile.presentation.UiText
import dev.qtremors.arcile.presentation.utils.LocalSearchHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class NativeAction { RESTORE, RESTORE_TO_DESTINATION, EMPTY }

enum class TrashSortOption { DELETED_NEWEST, DELETED_OLDEST, NAME_ASC, NAME_DESC, SIZE_LARGEST, SIZE_SMALLEST, TYPE, ORIGINAL_FOLDER }

enum class TrashFilter { ALL, CAN_RESTORE, NEEDS_DESTINATION, RECOVERED }

data class TrashPropertiesUiModel(
    val title: String,
    val rows: List<Pair<String, String>>
)

data class TrashState(
    val trashFiles: List<TrashMetadata> = emptyList(),
    val visibleTrashFiles: List<TrashMetadata> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val snackbarMessage: UiText? = null,
    val showDestinationPicker: Boolean = false,
    val selectedTrashIdsForDestination: List<String> = emptyList(),
    val pendingNativeAction: NativeAction? = null,
    val pendingDestinationPath: String? = null,
    val pendingRestoreIds: List<String> = emptyList(),
    val availableVolumes: List<dev.qtremors.arcile.core.storage.domain.StorageVolume> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<TrashMetadata> = emptyList(),
    val isSearching: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val sortOption: TrashSortOption = TrashSortOption.DELETED_NEWEST,
    val filter: TrashFilter = TrashFilter.ALL,
    val isPropertiesVisible: Boolean = false,
    val properties: TrashPropertiesUiModel? = null
)


@HiltViewModel
class TrashViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(TrashState())
    val state: StateFlow<TrashState> = _state.asStateFlow()

    private val _nativeRequestFlow = kotlinx.coroutines.flow.MutableSharedFlow<android.content.IntentSender>()
    val nativeRequestFlow: kotlinx.coroutines.flow.SharedFlow<android.content.IntentSender> = _nativeRequestFlow.asSharedFlow()

    private val localSearchHelper = LocalSearchHelper(
        scope = viewModelScope,
        source = { _state.value.trashFiles },
        matches = { item: TrashMetadata, query: String -> item.fileModel.name.contains(query, ignoreCase = true) },
        onQueryChanged = { query -> _state.update { it.copy(searchQuery = query) } },
        onSearchingChanged = { isSearching -> _state.update { it.copy(isSearching = isSearching) } },
        onResultsChanged = { results ->
            _state.update { state -> state.copy(searchResults = applyTrashPresentation(results, state.filter, state.sortOption)) }
        }
    )

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
                        visibleTrashFiles = applyTrashPresentation(trashItems, currentState.filter, currentState.sortOption),
                        error = null,
                        selectedFiles = currentState.selectedFiles.filter { path -> trashItems.any { it.id == path } }.toSet(),
                        searchResults = if (currentState.searchQuery.isNotBlank()) {
                            applyTrashPresentation(
                                trashItems.filter { it.fileModel.name.contains(currentState.searchQuery, ignoreCase = true) },
                                currentState.filter,
                                currentState.sortOption
                            )
                        } else emptyList()


                    )
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_trash_failed)) }
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
        val selectedItems = _state.value.trashFiles.filter { it.id in selectedTrashIds }
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
            repository.restoreFromTrash(selectedTrashIds).onSuccess {
                val message = restoreSummaryMessage(selectedTrashIds.size, conflictCount)
                clearSelection()
                _state.update { it.copy(snackbarMessage = message) }
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

        _state.update { it.copy(isLoading = true, error = null, showDestinationPicker = false) }
        viewModelScope.launch {
            repository.restoreFromTrash(trashIds, destinationPath).onSuccess {
                val normalizedIds = trashIds.map { it.removePrefix("legacy:") }.toSet()
                _state.update {
                    it.copy(
                        selectedFiles = it.selectedFiles - normalizedIds,
                        selectedTrashIdsForDestination = emptyList(),
                        pendingDestinationPath = null,
                        pendingRestoreIds = emptyList(),
                        snackbarMessage = restoreSummaryMessage(trashIds.size, 0)
                    )
                }
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
                    _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_restore_files_failed)) }
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
                    _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_empty_trash_failed)) }
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

        // Permanent trash deletion is intentionally repository-backed for now. If trash/delete
        // operations move into BulkFileOperationService later, this VM should observe coordinator events.
        _state.update { it.copy(isLoading = true, error = null, showPermanentDeleteConfirmation = false) }
        viewModelScope.launch {
            repository.deletePermanentlyFromTrash(selectedIds).onSuccess {
                clearSelection()
                loadTrashFiles()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_delete_files_permanently_failed)) }
                loadTrashFiles()
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun clearSnackbarMessage() {
        _state.update { it.copy(snackbarMessage = null) }
    }

    fun updateSearchQuery(query: String) {
        localSearchHelper.updateQuery(query)
    }

    fun updateSortOption(sortOption: TrashSortOption) {
        _state.update {
            it.copy(
                sortOption = sortOption,
                visibleTrashFiles = applyTrashPresentation(it.trashFiles, it.filter, sortOption),
                searchResults = applyTrashPresentation(searchMatches(it.trashFiles, it.searchQuery), it.filter, sortOption)
            )
        }
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

private fun searchMatches(items: List<TrashMetadata>, query: String): List<TrashMetadata> {
    val normalizedQuery = query.trim()
    return if (normalizedQuery.isBlank()) {
        emptyList()
    } else {
        items.filter { it.fileModel.name.contains(normalizedQuery, ignoreCase = true) }
    }
}

private fun applyTrashPresentation(
    items: List<TrashMetadata>,
    filter: TrashFilter,
    sortOption: TrashSortOption
): List<TrashMetadata> {
    val filtered = when (filter) {
        TrashFilter.ALL -> items
        TrashFilter.CAN_RESTORE -> items.filter {
            it.restoreStatus == TrashRestoreStatus.ORIGINAL_AVAILABLE ||
                it.restoreStatus == TrashRestoreStatus.ORIGINAL_CONFLICT_RENAME
        }
        TrashFilter.NEEDS_DESTINATION -> items.filter { it.restoreStatus == TrashRestoreStatus.DESTINATION_REQUIRED }
        TrashFilter.RECOVERED -> items.filter { it.restoreStatus == TrashRestoreStatus.RECOVERED_ITEM }
    }
    val comparator = when (sortOption) {
        TrashSortOption.DELETED_NEWEST -> compareByDescending<TrashMetadata> { it.deletionTime }
        TrashSortOption.DELETED_OLDEST -> compareBy<TrashMetadata> { it.deletionTime }
        TrashSortOption.NAME_ASC -> compareBy { it.fileModel.name.lowercase() }
        TrashSortOption.NAME_DESC -> compareByDescending { it.fileModel.name.lowercase() }
        TrashSortOption.SIZE_LARGEST -> compareByDescending { it.fileModel.size }
        TrashSortOption.SIZE_SMALLEST -> compareBy { it.fileModel.size }
        TrashSortOption.TYPE -> compareBy<TrashMetadata> { !it.fileModel.isDirectory }
            .thenBy { it.fileModel.extension.lowercase() }
            .thenBy { it.fileModel.name.lowercase() }
        TrashSortOption.ORIGINAL_FOLDER -> compareBy<TrashMetadata> { it.originalPath.substringBeforeLast("/", "") }
            .thenBy { it.fileModel.name.lowercase() }
    }
    return filtered.sortedWith(comparator)
}

private fun List<TrashMetadata>.toPropertiesModel(): TrashPropertiesUiModel {
    val fileCount = count { !it.fileModel.isDirectory }
    val folderCount = count { it.fileModel.isDirectory }
    val totalBytes = sumOf { it.fileModel.size }
    val single = singleOrNull()
    val rows = mutableListOf<Pair<String, String>>()
    rows += "Items" to size.toString()
    rows += "Files" to fileCount.toString()
    rows += "Folders" to folderCount.toString()
    rows += "Size" to dev.qtremors.arcile.utils.formatFileSize(totalBytes)
    if (single != null) {
        rows += "Original path" to single.originalPath.ifBlank { "Unavailable" }
        rows += "Trash payload" to single.fileModel.absolutePath
        rows += "Restore status" to single.restoreStatus.name.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        rows += "Source volume" to single.sourceVolumeId
    } else {
        rows += "Recovered items" to count { it.restoreStatus == TrashRestoreStatus.RECOVERED_ITEM }.toString()
        rows += "Need destination" to count {
            it.restoreStatus == TrashRestoreStatus.DESTINATION_REQUIRED ||
                it.restoreStatus == TrashRestoreStatus.RECOVERED_ITEM
        }.toString()
    }
    return TrashPropertiesUiModel(
        title = single?.fileModel?.name ?: "$size selected",
        rows = rows
    )
}

