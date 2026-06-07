package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.operation.OperationRecoveryRecord
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.shared.presentation.PropertiesUiModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import java.io.File

enum class BrowserNativeAction { TRASH }

enum class ArchiveExtractionTarget {
    NAMED_FOLDER,
    SAME_FOLDER,
    CUSTOM_FOLDER
}

@androidx.compose.runtime.Immutable
data class BrowserArchiveContext(
    val archivePath: String,
    val entryPrefix: String? = null,
    val password: String? = null,
    val nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
    val entries: List<ArchiveEntryModel> = emptyList(),
    val passwordRequired: Boolean = false,
    val pendingPasswordAction: ArchivePasswordAction = ArchivePasswordAction.OPEN
) {
    val archiveName: String get() = File(archivePath).name
    val parentPath: String get() = File(archivePath).parent.orEmpty()
}

enum class ArchivePasswordAction {
    OPEN,
    EXTRACT
}

@androidx.compose.runtime.Immutable
data class PendingArchiveExtraction(
    val archivePath: String,
    val destinationPath: String,
    val entryPrefix: String? = null,
    val entryPrefixes: List<String> = emptyList(),
    val password: String? = null,
    val nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
)

@androidx.compose.runtime.Immutable
data class BrowserFileOperationUiState(
    val type: BulkFileOperationType,
    val totalItems: Int,
    val completedItems: Int = 0,
    val currentPath: String? = null,
    val isCancelling: Boolean = false,
    val bytesCopied: Long? = null,
    val totalBytes: Long? = null,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val terminalStatus: OperationCompletionStatus? = null,
    val sourcePaths: List<String> = emptyList()
) {
    val isIndeterminate: Boolean
        get() = (totalBytes ?: 0L) <= 0L && totalItems <= 0
}

@androidx.compose.runtime.Immutable
data class BrowserOperationRecoveryUiState(
    val operationId: String,
    val type: BulkFileOperationType,
    val sourcePaths: List<String>,
    val destinationPath: String?,
    val phase: String,
    val completedItems: Int,
    val totalItems: Int,
    val currentPath: String?,
    val error: String?
)

sealed interface BrowserUndoAction {
    data class Trash(val trashIds: PersistentList<String>) : BrowserUndoAction
    data class Rename(val originalPath: String, val renamedPath: String) : BrowserUndoAction
    data class Created(val path: String) : BrowserUndoAction
    data class Moved(val entries: PersistentList<MoveUndoEntry>) : BrowserUndoAction
}

@androidx.compose.runtime.Immutable
data class MoveUndoEntry(
    val originalPath: String,
    val movedPath: String
)

@androidx.compose.runtime.Immutable
data class BrowserState(
    val currentPath: String = "",
    val currentVolumeId: String? = null,
    val isVolumeRootScreen: Boolean = false,
    val isCategoryScreen: Boolean = false,
    val activeCategoryName: String = "",
    val selectedFolderTabPath: String? = null,
    val files: PersistentList<FileModel> = persistentListOf(),
    val folderStatsByPath: PersistentMap<String, FolderStats> = persistentMapOf(),
    val folderStatsLoadingPaths: PersistentSet<String> = persistentSetOf(),
    val searchResults: PersistentList<FileModel> = persistentListOf(),
    val isSearching: Boolean = false,
    val browserSearchQuery: String = "",
    val browserSortOption: FileSortOption = FileSortOption.NAME_ASC,
    val browserViewMode: BrowserViewMode = BrowserViewMode.LIST,
    val browserListZoom: Float = BrowserPresentationPreferences.DEFAULT_LIST_ZOOM,
    val browserGridMinCellSize: Float = BrowserPresentationPreferences.DEFAULT_GRID_MIN_CELL_SIZE,
    val browserShowThumbnails: Boolean = BrowserPresentationPreferences.DEFAULT_SHOW_THUMBNAILS,
    val showHiddenFiles: Boolean = true,
    val selectedFiles: PersistentSet<String> = persistentSetOf(),
    val clipboardState: ClipboardState? = null,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearchFilterMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val error: UiText? = null,
    val pasteConflicts: PersistentList<FileConflict> = persistentListOf(),
    val showConflictDialog: Boolean = false,
    val storageVolumes: PersistentList<StorageVolume> = persistentListOf(),
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isShredChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val pendingNativeAction: BrowserNativeAction? = null,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: PropertiesUiModel? = null,
    val activeFileOperation: BrowserFileOperationUiState? = null,
    val activeRecoveryOperation: BrowserOperationRecoveryUiState? = null,
    val fileOperationStatusMessage: UiText? = null,
    val pendingTrashUndoIds: PersistentList<String> = persistentListOf(),
    val pendingUndoAction: BrowserUndoAction? = null,
    val selectedFilesTotalSize: Long = 0L,
    val archiveContext: BrowserArchiveContext? = null,
    val pendingArchiveExtraction: PendingArchiveExtraction? = null,
    val displayState: BrowserDisplayState = BrowserDisplayState()
)

private const val ALL_FILES_LABEL = "All files"

fun BrowserState.withUpdatedDisplayState(): BrowserState = copy(
    displayState = buildBrowserDisplayState(
        files = files,
        sortOption = browserSortOption,
        selectedFolderTabPath = selectedFolderTabPath,
        isCategoryScreen = isCategoryScreen,
        currentVolumeId = currentVolumeId,
        storageVolumes = storageVolumes,
        showHiddenFiles = showHiddenFiles,
        allFilesLabel = ALL_FILES_LABEL,
        folderStatsByPath = folderStatsByPath,
        browserListZoom = browserListZoom,
        browserGridMinCellSize = browserGridMinCellSize,
        previousDisplayState = displayState
    )
)

fun OperationRecoveryRecord.toBrowserRecoveryUiState(): BrowserOperationRecoveryUiState =
    BrowserOperationRecoveryUiState(
        operationId = request.operationId,
        type = request.type,
        sourcePaths = request.sourcePaths,
        destinationPath = request.destinationPath,
        phase = phase,
        completedItems = progress?.completedItems ?: 0,
        totalItems = progress?.totalItems ?: request.sourcePaths.size.coerceAtLeast(1),
        currentPath = progress?.currentPath ?: request.sourcePaths.firstOrNull(),
        error = error
    )
