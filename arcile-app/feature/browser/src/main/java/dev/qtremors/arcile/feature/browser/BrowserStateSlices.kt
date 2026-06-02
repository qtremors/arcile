package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.shared.presentation.PropertiesUiModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet

@Immutable
data class BrowserNavigationState(
    val currentPath: String = "",
    val currentVolumeId: String? = null,
    val isVolumeRootScreen: Boolean = false,
    val isCategoryScreen: Boolean = false,
    val activeCategoryName: String = "",
    val selectedFolderTabPath: String? = null,
    val storageVolumes: PersistentList<StorageVolume> = persistentListOf()
)

@Immutable
data class BrowserListingState(
    val files: PersistentList<FileModel> = persistentListOf(),
    val folderStatsByPath: PersistentMap<String, FolderStats> = persistentMapOf(),
    val folderStatsLoadingPaths: PersistentSet<String> = persistentSetOf(),
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val error: UiText? = null,
    val browserSortOption: FileSortOption = FileSortOption.NAME_ASC,
    val browserViewMode: BrowserViewMode = BrowserViewMode.LIST,
    val browserListZoom: Float = BrowserPresentationPreferences.DEFAULT_LIST_ZOOM,
    val browserGridMinCellSize: Float = BrowserPresentationPreferences.DEFAULT_GRID_MIN_CELL_SIZE,
    val browserShowThumbnails: Boolean = BrowserPresentationPreferences.DEFAULT_SHOW_THUMBNAILS,
    val displayState: BrowserDisplayState = BrowserDisplayState()
)

@Immutable
data class BrowserSelectionState(
    val selectedFiles: PersistentSet<String> = persistentSetOf(),
    val selectedFilesTotalSize: Long = 0L
)

@Immutable
data class BrowserSearchState(
    val searchResults: PersistentList<FileModel> = persistentListOf(),
    val isSearching: Boolean = false,
    val browserSearchQuery: String = "",
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearchFilterMenuVisible: Boolean = false
)

@Immutable
data class BrowserDialogState(
    val pasteConflicts: PersistentList<FileConflict> = persistentListOf(),
    val showConflictDialog: Boolean = false,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val pendingNativeAction: BrowserNativeAction? = null,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: PropertiesUiModel? = null
)

@Immutable
data class OperationUiState(
    val clipboardState: ClipboardState? = null,
    val activeFileOperation: BrowserFileOperationUiState? = null,
    val activeRecoveryOperation: BrowserOperationRecoveryUiState? = null,
    val fileOperationStatusMessage: UiText? = null,
    val pendingTrashUndoIds: PersistentList<String> = persistentListOf()
)

sealed interface BrowserNavigationEvent {
    data class StorageVolumesChanged(val volumes: List<StorageVolume>) : BrowserNavigationEvent
    data class OpenVolumeRoots(val volumes: List<FileModel>) : BrowserNavigationEvent
    data class OpenDirectory(val path: String, val volumeId: String?) : BrowserNavigationEvent
    data class OpenCategory(val categoryName: String, val volumeId: String?) : BrowserNavigationEvent
    data class SelectFolderTab(val path: String?) : BrowserNavigationEvent
}

sealed interface BrowserSelectionEvent {
    data class Toggle(val path: String, val files: List<FileModel>, val folderStats: Map<String, FolderStats>) : BrowserSelectionEvent
    data class SelectAll(val paths: List<String>, val files: List<FileModel>, val folderStats: Map<String, FolderStats>) : BrowserSelectionEvent
    data class Invert(val allPaths: List<String>, val files: List<FileModel>, val folderStats: Map<String, FolderStats>) : BrowserSelectionEvent
    data object Clear : BrowserSelectionEvent
}

sealed interface BrowserSearchEvent {
    data class QueryChanged(val query: String) : BrowserSearchEvent
    data class ResultsLoaded(val files: List<FileModel>) : BrowserSearchEvent
    data class SearchingChanged(val isSearching: Boolean) : BrowserSearchEvent
    data class FiltersChanged(val filters: SearchFilters) : BrowserSearchEvent
    data class FilterMenuChanged(val visible: Boolean) : BrowserSearchEvent
}

sealed interface BrowserDialogEvent {
    data class ConflictDialogShown(val conflicts: List<FileConflict>) : BrowserDialogEvent
    data object ConflictDialogDismissed : BrowserDialogEvent
    data class DeleteDecisionChanged(val decision: DeleteDecision?) : BrowserDialogEvent
    data object TrashConfirmationShown : BrowserDialogEvent
    data object PermanentDeleteConfirmationShown : BrowserDialogEvent
    data object MixedDeleteExplanationShown : BrowserDialogEvent
    data object DeleteConfirmationDismissed : BrowserDialogEvent
    data object PermanentDeleteToggled : BrowserDialogEvent
    data class NativeActionChanged(val action: BrowserNativeAction?) : BrowserDialogEvent
    data class PropertiesLoading(val visible: Boolean) : BrowserDialogEvent
    data class PropertiesLoaded(val properties: PropertiesUiModel?) : BrowserDialogEvent
    data object PropertiesDismissed : BrowserDialogEvent
}

sealed interface BrowserOperationEvent {
    data class ClipboardChanged(val clipboardState: ClipboardState?) : BrowserOperationEvent
    data class ActiveOperationChanged(val operation: BrowserFileOperationUiState?) : BrowserOperationEvent
    data class StatusMessageChanged(val message: UiText?) : BrowserOperationEvent
    data class PendingTrashUndoChanged(val ids: List<String>) : BrowserOperationEvent
}

fun BrowserState.navigationState() = BrowserNavigationState(
    currentPath = currentPath,
    currentVolumeId = currentVolumeId,
    isVolumeRootScreen = isVolumeRootScreen,
    isCategoryScreen = isCategoryScreen,
    activeCategoryName = activeCategoryName,
    selectedFolderTabPath = selectedFolderTabPath,
    storageVolumes = storageVolumes
)

fun BrowserState.listingState() = BrowserListingState(
    files = files,
    folderStatsByPath = folderStatsByPath,
    folderStatsLoadingPaths = folderStatsLoadingPaths,
    isLoading = isLoading,
    isPullToRefreshing = isPullToRefreshing,
    error = error,
    browserSortOption = browserSortOption,
    browserViewMode = browserViewMode,
    browserListZoom = browserListZoom,
    browserGridMinCellSize = browserGridMinCellSize,
    browserShowThumbnails = browserShowThumbnails,
    displayState = displayState
)

fun BrowserState.selectionState() = BrowserSelectionState(
    selectedFiles = selectedFiles,
    selectedFilesTotalSize = selectedFilesTotalSize
)

fun BrowserState.searchState() = BrowserSearchState(
    searchResults = searchResults,
    isSearching = isSearching,
    browserSearchQuery = browserSearchQuery,
    activeSearchFilters = activeSearchFilters,
    isSearchFilterMenuVisible = isSearchFilterMenuVisible
)

fun BrowserState.dialogState() = BrowserDialogState(
    pasteConflicts = pasteConflicts,
    showConflictDialog = showConflictDialog,
    showTrashConfirmation = showTrashConfirmation,
    showPermanentDeleteConfirmation = showPermanentDeleteConfirmation,
    showMixedDeleteExplanation = showMixedDeleteExplanation,
    deleteDecision = deleteDecision,
    isPermanentDeleteChecked = isPermanentDeleteChecked,
    isPermanentDeleteToggleEnabled = isPermanentDeleteToggleEnabled,
    pendingNativeAction = pendingNativeAction,
    isPropertiesVisible = isPropertiesVisible,
    isPropertiesLoading = isPropertiesLoading,
    properties = properties
)

fun BrowserState.operationUiState() = OperationUiState(
    clipboardState = clipboardState,
    activeFileOperation = activeFileOperation,
    activeRecoveryOperation = activeRecoveryOperation,
    fileOperationStatusMessage = fileOperationStatusMessage,
    pendingTrashUndoIds = pendingTrashUndoIds
)

fun BrowserState.reduce(event: BrowserNavigationEvent): BrowserState = when (event) {
    is BrowserNavigationEvent.StorageVolumesChanged -> copy(
        storageVolumes = event.volumes.toPersistentList()
    ).withUpdatedDisplayState()
    is BrowserNavigationEvent.OpenVolumeRoots -> copy(
        archiveContext = null,
        pendingArchiveExtraction = null,
        currentPath = "",
        currentVolumeId = null,
        isVolumeRootScreen = true,
        isCategoryScreen = false,
        activeCategoryName = "",
        selectedFolderTabPath = null,
        files = event.volumes.toPersistentList(),
        folderStatsByPath = persistentMapOf(),
        folderStatsLoadingPaths = persistentSetOf(),
        selectedFiles = persistentSetOf(),
        selectedFilesTotalSize = 0L,
        isLoading = false,
        isPullToRefreshing = false,
        isPropertiesVisible = false,
        isPropertiesLoading = false,
        properties = null
    ).withUpdatedDisplayState()
    is BrowserNavigationEvent.OpenDirectory -> copy(
        archiveContext = null,
        pendingArchiveExtraction = null,
        currentPath = event.path,
        currentVolumeId = event.volumeId,
        isVolumeRootScreen = false,
        isCategoryScreen = false,
        activeCategoryName = "",
        selectedFolderTabPath = null,
        files = persistentListOf(),
        folderStatsByPath = persistentMapOf(),
        folderStatsLoadingPaths = persistentSetOf(),
        selectedFiles = persistentSetOf(),
        selectedFilesTotalSize = 0L,
        isPropertiesVisible = false,
        isPropertiesLoading = false,
        properties = null
    ).withUpdatedDisplayState()
    is BrowserNavigationEvent.OpenCategory -> copy(
        archiveContext = null,
        pendingArchiveExtraction = null,
        currentPath = "",
        currentVolumeId = event.volumeId,
        isVolumeRootScreen = false,
        isCategoryScreen = true,
        activeCategoryName = event.categoryName,
        selectedFolderTabPath = null,
        files = persistentListOf(),
        folderStatsByPath = persistentMapOf(),
        folderStatsLoadingPaths = persistentSetOf(),
        selectedFiles = persistentSetOf(),
        selectedFilesTotalSize = 0L,
        isPropertiesVisible = false,
        isPropertiesLoading = false,
        properties = null
    ).withUpdatedDisplayState()
    is BrowserNavigationEvent.SelectFolderTab -> copy(
        selectedFolderTabPath = event.path,
        selectedFiles = persistentSetOf(),
        selectedFilesTotalSize = 0L,
        isPropertiesVisible = false,
        isPropertiesLoading = false,
        properties = null
    ).withUpdatedDisplayState()
}

fun BrowserState.reduce(event: BrowserSelectionEvent): BrowserState {
    val nextSelected = when (event) {
        is BrowserSelectionEvent.Toggle -> {
            if (selectedFiles.contains(event.path)) selectedFiles - event.path else selectedFiles + event.path
        }
        is BrowserSelectionEvent.SelectAll -> event.paths.toSet()
        is BrowserSelectionEvent.Invert -> event.allPaths.filter { it !in selectedFiles }.toSet()
        BrowserSelectionEvent.Clear -> emptySet()
    }
    val files = when (event) {
        is BrowserSelectionEvent.Toggle -> event.files
        is BrowserSelectionEvent.SelectAll -> event.files
        is BrowserSelectionEvent.Invert -> event.files
        BrowserSelectionEvent.Clear -> emptyList()
    }
    val folderStats = when (event) {
        is BrowserSelectionEvent.Toggle -> event.folderStats
        is BrowserSelectionEvent.SelectAll -> event.folderStats
        is BrowserSelectionEvent.Invert -> event.folderStats
        BrowserSelectionEvent.Clear -> emptyMap()
    }
    return copy(
        selectedFiles = nextSelected.toPersistentSet(),
        selectedFilesTotalSize = calculateBrowserSelectionSize(nextSelected, files, folderStats),
        isPropertiesVisible = false,
        isPropertiesLoading = false,
        properties = null
    )
}

fun BrowserState.reduce(event: BrowserSearchEvent): BrowserState = when (event) {
    is BrowserSearchEvent.QueryChanged -> copy(
        browserSearchQuery = event.query,
        selectedFolderTabPath = if (event.query.isBlank()) null else selectedFolderTabPath
    ).withUpdatedDisplayState()
    is BrowserSearchEvent.ResultsLoaded -> copy(
        isSearching = false,
        searchResults = event.files.toPersistentList()
    )
    is BrowserSearchEvent.SearchingChanged -> copy(isSearching = event.isSearching)
    is BrowserSearchEvent.FiltersChanged -> copy(activeSearchFilters = event.filters)
    is BrowserSearchEvent.FilterMenuChanged -> copy(isSearchFilterMenuVisible = event.visible)
}

fun BrowserState.reduce(event: BrowserDialogEvent): BrowserState = when (event) {
    is BrowserDialogEvent.ConflictDialogShown -> copy(
        pasteConflicts = event.conflicts.toPersistentList(),
        showConflictDialog = true
    )
    BrowserDialogEvent.ConflictDialogDismissed -> copy(
        pasteConflicts = persistentListOf(),
        showConflictDialog = false
    )
    is BrowserDialogEvent.DeleteDecisionChanged -> copy(deleteDecision = event.decision)
    BrowserDialogEvent.TrashConfirmationShown -> copy(
        showTrashConfirmation = true,
        isPermanentDeleteChecked = false,
        isPermanentDeleteToggleEnabled = true
    )
    BrowserDialogEvent.PermanentDeleteConfirmationShown -> copy(
        showPermanentDeleteConfirmation = true,
        isPermanentDeleteChecked = true,
        isPermanentDeleteToggleEnabled = false
    )
    BrowserDialogEvent.MixedDeleteExplanationShown -> copy(showMixedDeleteExplanation = true)
    BrowserDialogEvent.DeleteConfirmationDismissed -> copy(
        showTrashConfirmation = false,
        showPermanentDeleteConfirmation = false,
        showMixedDeleteExplanation = false,
        deleteDecision = null
    )
    BrowserDialogEvent.PermanentDeleteToggled -> copy(isPermanentDeleteChecked = !isPermanentDeleteChecked)
    is BrowserDialogEvent.NativeActionChanged -> copy(pendingNativeAction = event.action)
    is BrowserDialogEvent.PropertiesLoading -> copy(
        isPropertiesVisible = event.visible,
        isPropertiesLoading = event.visible,
        properties = null
    )
    is BrowserDialogEvent.PropertiesLoaded -> copy(
        isPropertiesVisible = event.properties != null,
        isPropertiesLoading = false,
        properties = event.properties
    )
    BrowserDialogEvent.PropertiesDismissed -> copy(
        isPropertiesVisible = false,
        isPropertiesLoading = false,
        properties = null
    )
}

fun BrowserState.reduce(event: BrowserOperationEvent): BrowserState = when (event) {
    is BrowserOperationEvent.ClipboardChanged -> copy(clipboardState = event.clipboardState)
    is BrowserOperationEvent.ActiveOperationChanged -> copy(activeFileOperation = event.operation)
    is BrowserOperationEvent.StatusMessageChanged -> copy(fileOperationStatusMessage = event.message)
    is BrowserOperationEvent.PendingTrashUndoChanged -> copy(pendingTrashUndoIds = event.ids.toPersistentList())
}

fun calculateBrowserSelectionSize(
    selectedPaths: Set<String>,
    currentFiles: List<FileModel>,
    folderStats: Map<String, FolderStats>
): Long {
    var total = 0L
    selectedPaths.forEach { path ->
        val file = currentFiles.find { it.absolutePath == path }
        if (file != null) {
            total += if (file.isDirectory) {
                folderStats[path]?.totalBytes ?: 0L
            } else {
                file.size
            }
        }
    }
    return total
}
