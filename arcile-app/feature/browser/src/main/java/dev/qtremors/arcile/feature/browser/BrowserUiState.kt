package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationRequirement
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.presentation.OperationUiState
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import dev.qtremors.arcile.feature.browser.delegate.BrowserTransientState

@Immutable
internal data class BrowserSelectionState(
    val selectedFiles: PersistentSet<String> = persistentSetOf(),
    val selectedFilesTotalSize: Long = 0L
)

@Immutable
internal data class BrowserSearchState(
    val searchResults: PersistentList<FileModel> = persistentListOf(),
    val isSearching: Boolean = false,
    val error: UiText? = null,
    val browserSearchQuery: String = "",
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearchFilterMenuVisible: Boolean = false
)

@Immutable
internal data class BrowserDialogState(
    val pasteConflicts: PersistentList<FileConflict> = persistentListOf(),
    val showConflictDialog: Boolean = false,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isShredChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true
)

@Immutable
internal data class BrowserOperationState(
    val clipboardState: ClipboardState? = null,
    val activeFileOperation: OperationUiState? = null,
    val activeRecoveryOperation: BrowserOperationRecoveryUiState? = null,
    val fileOperationStatusMessage: UiText? = null,
    val pendingTrashUndoIds: PersistentList<String> = persistentListOf(),
    val pendingUndoAction: BrowserUndoAction? = null,
    val pendingAuthorization: StorageAuthorizationRequirement? = null
)

@Immutable
internal data class BrowserUiState(
    val location: BrowserLocationState = BrowserLocationState(),
    val listing: BrowserListingState = BrowserListingState(),
    val selection: BrowserSelectionState = BrowserSelectionState(),
    val search: BrowserSearchState = BrowserSearchState(),
    val dialogs: BrowserDialogState = BrowserDialogState(),
    val propertiesState: BrowserPropertiesState = BrowserPropertiesState(),
    val operation: BrowserOperationState = BrowserOperationState(),
    val transient: BrowserTransientState = BrowserTransientState(),
    val pendingArchiveExtraction: PendingArchiveExtraction? = null,
    val pendingRevealFilePath: String? = null,
    val pendingRevealReady: Boolean = false
) {
    val currentPath get() = location.currentPath
    val currentVolumeId get() = location.currentVolumeId
    val isVolumeRootScreen get() = location.isVolumeRootScreen
    val isCategoryScreen get() = location.isCategoryScreen
    val activeCategoryName get() = location.activeCategoryName
    val selectedFolderTabPath get() = location.selectedFolderTabPath
    val storageVolumes get() = location.storageVolumes
    val archiveContext = location.archiveContext
    val files get() = listing.files
    val folderStatsByPath get() = listing.folderStatsByPath
    val folderStatsLoadingPaths get() = listing.folderStatsLoadingPaths
    val isLoading get() = listing.isLoading || transient.isBusy
    val isPullToRefreshing get() = listing.isPullToRefreshing
    val error get() = search.error ?: transient.error ?: listing.error
    val browserSortOption get() = listing.browserSortOption
    val browserViewMode get() = listing.browserViewMode
    val browserListZoom get() = listing.browserListZoom
    val browserGridMinCellSize get() = listing.browserGridMinCellSize
    val browserShowThumbnails get() = listing.browserShowThumbnails
    val browserScrollbarEnabled get() = listing.browserScrollbarEnabled
    val showHiddenFiles get() = listing.showHiddenFiles
    val displayState get() = listing.displayState
    val selectedFiles get() = selection.selectedFiles
    val selectedFilesTotalSize get() = selection.selectedFilesTotalSize
    val searchResults get() = search.searchResults
    val isSearching get() = search.isSearching
    val browserSearchQuery get() = search.browserSearchQuery
    val activeSearchFilters get() = search.activeSearchFilters
    val isSearchFilterMenuVisible get() = search.isSearchFilterMenuVisible
    val pasteConflicts get() = dialogs.pasteConflicts
    val showConflictDialog get() = dialogs.showConflictDialog
    val showTrashConfirmation get() = dialogs.showTrashConfirmation
    val showPermanentDeleteConfirmation get() = dialogs.showPermanentDeleteConfirmation
    val showMixedDeleteExplanation get() = dialogs.showMixedDeleteExplanation
    val deleteDecision get() = dialogs.deleteDecision
    val isPermanentDeleteChecked get() = dialogs.isPermanentDeleteChecked
    val isShredChecked get() = dialogs.isShredChecked
    val isPermanentDeleteToggleEnabled get() = dialogs.isPermanentDeleteToggleEnabled
    val isPropertiesVisible get() = propertiesState.isVisible
    val isPropertiesLoading get() = propertiesState.isLoading
    val properties get() = propertiesState.properties
    val clipboardState = operation.clipboardState
    val activeFileOperation = operation.activeFileOperation
    val activeRecoveryOperation get() = operation.activeRecoveryOperation
    val fileOperationStatusMessage get() = operation.fileOperationStatusMessage
    val pendingTrashUndoIds get() = operation.pendingTrashUndoIds
    val pendingUndoAction get() = operation.pendingUndoAction
}

internal sealed interface BrowserSearchEvent {
    data class QueryChanged(val query: String) : BrowserSearchEvent
    data class ResultsLoaded(val files: List<FileModel>) : BrowserSearchEvent
    data class SearchingChanged(val isSearching: Boolean) : BrowserSearchEvent
    data class ErrorChanged(val error: UiText?) : BrowserSearchEvent
    data class FiltersChanged(val filters: SearchFilters) : BrowserSearchEvent
    data class FilterMenuChanged(val visible: Boolean) : BrowserSearchEvent
}

internal sealed interface BrowserDialogEvent {
    data class ConflictDialogShown(val conflicts: List<FileConflict>) : BrowserDialogEvent
    data object ConflictDialogDismissed : BrowserDialogEvent
    data class DeleteDecisionChanged(val decision: DeleteDecision?) : BrowserDialogEvent
    data object TrashConfirmationShown : BrowserDialogEvent
    data object PermanentDeleteConfirmationShown : BrowserDialogEvent
    data object MixedDeleteExplanationShown : BrowserDialogEvent
    data object DeleteConfirmationDismissed : BrowserDialogEvent
    data object PermanentDeleteToggled : BrowserDialogEvent
}

internal fun BrowserSearchState.reduce(event: BrowserSearchEvent): BrowserSearchState = when (event) {
    is BrowserSearchEvent.QueryChanged -> copy(browserSearchQuery = event.query)
    is BrowserSearchEvent.ResultsLoaded -> copy(
        isSearching = false,
        searchResults = event.files.toPersistentList()
    )
    is BrowserSearchEvent.SearchingChanged -> copy(isSearching = event.isSearching)
    is BrowserSearchEvent.ErrorChanged -> copy(error = event.error)
    is BrowserSearchEvent.FiltersChanged -> copy(activeSearchFilters = event.filters)
    is BrowserSearchEvent.FilterMenuChanged -> copy(isSearchFilterMenuVisible = event.visible)
}

internal fun BrowserDialogState.reduce(event: BrowserDialogEvent): BrowserDialogState = when (event) {
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
}

internal fun calculateBrowserSelectionSize(
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
