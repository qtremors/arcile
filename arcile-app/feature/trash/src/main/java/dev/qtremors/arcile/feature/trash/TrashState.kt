package dev.qtremors.arcile.feature.trash

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationRequirement
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus

internal enum class NativeAction { RESTORE, RESTORE_TO_DESTINATION, EMPTY, DELETE }

internal enum class TrashSortOption {
    DELETED_NEWEST,
    DELETED_OLDEST,
    NAME_ASC,
    NAME_DESC,
    SIZE_LARGEST,
    SIZE_SMALLEST,
    TYPE,
    ORIGINAL_FOLDER
}

internal enum class TrashFilter { ALL, CAN_RESTORE, NEEDS_DESTINATION, RECOVERED }

internal data class TrashPropertiesUiModel(
    val title: String,
    val rows: List<Pair<String, String>>
)

internal data class TrashState(
    val trashFiles: List<TrashMetadata> = emptyList(),
    val visibleTrashFiles: List<TrashMetadata> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val isLoading: Boolean = true,
    val error: UiText? = null,
    val snackbarMessage: UiText? = null,
    val showDestinationPicker: Boolean = false,
    val selectedTrashIdsForDestination: List<String> = emptyList(),
    val pendingNativeAction: NativeAction? = null,
    val pendingAuthorization: StorageAuthorizationRequirement? = null,
    val pendingDestinationPath: String? = null,
    val pendingRestoreIds: List<String> = emptyList(),
    val pendingRestoreUndoPaths: List<String> = emptyList(),
    val availableVolumes: List<StorageVolume> = emptyList(),
    val searchQuery: String = "",
    val searchResults: List<TrashMetadata> = emptyList(),
    val isSearching: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val sortOption: TrashSortOption = TrashSortOption.DELETED_NEWEST,
    val filter: TrashFilter = TrashFilter.ALL,
    val isPropertiesVisible: Boolean = false,
    val properties: TrashPropertiesUiModel? = null
)

internal fun searchMatches(
    items: List<TrashMetadata>,
    query: String
): List<TrashMetadata> {
    val normalizedQuery = query.trim()
    return if (normalizedQuery.isBlank()) {
        emptyList()
    } else {
        items.filter { it.fileModel.name.contains(normalizedQuery, ignoreCase = true) }
    }
}

internal fun applyTrashPresentation(
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
        TrashFilter.NEEDS_DESTINATION ->
            items.filter { it.restoreStatus == TrashRestoreStatus.DESTINATION_REQUIRED }
        TrashFilter.RECOVERED ->
            items.filter { it.restoreStatus == TrashRestoreStatus.RECOVERED_ITEM }
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
        TrashSortOption.ORIGINAL_FOLDER ->
            compareBy<TrashMetadata> { it.originalPath.substringBeforeLast("/", "") }
                .thenBy { it.fileModel.name.lowercase() }
    }
    return filtered.sortedWith(comparator)
}

internal fun List<TrashMetadata>.toPropertiesModel(): TrashPropertiesUiModel {
    val single = singleOrNull()
    val rows = mutableListOf<Pair<String, String>>()
    rows += "Items" to size.toString()
    rows += "Files" to count { !it.fileModel.isDirectory }.toString()
    rows += "Folders" to count { it.fileModel.isDirectory }.toString()
    rows += "Size" to dev.qtremors.arcile.core.presentation.formatFileSize(sumOf { it.fileModel.size })
    if (single != null) {
        rows += "Original path" to single.originalPath.ifBlank { "Unavailable" }
        rows += "Trash payload" to single.fileModel.absolutePath
        rows += "Restore status" to single.restoreStatus.name
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar(Char::uppercase)
        rows += "Source volume" to single.sourceVolumeId
    } else {
        rows += "Recovered items" to count {
            it.restoreStatus == TrashRestoreStatus.RECOVERED_ITEM
        }.toString()
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
