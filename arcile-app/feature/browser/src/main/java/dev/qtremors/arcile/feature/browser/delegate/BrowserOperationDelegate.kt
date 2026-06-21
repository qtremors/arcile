package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.feature.browser.BrowserFileOperationUiState
import dev.qtremors.arcile.feature.browser.MoveUndoEntry
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.feature.browser.BrowserUndoAction
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.userMessage
import dev.qtremors.arcile.feature.browser.toBrowserRecoveryUiState
import java.io.File
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class BrowserOperationDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val viewModelScope: CoroutineScope,
    private val trashRepository: TrashRepository,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    private val refreshAction: () -> Unit
) {
    fun hydrateActiveOperation() {
        bulkFileOperationCoordinator.activeRequest.value?.let { activeReq ->
            state.update {
                it.copy(
                    activeFileOperation = BrowserFileOperationUiState(
                        type = activeReq.type,
                        totalItems = activeReq.sourcePaths.size,
                        currentPath = activeReq.sourcePaths.firstOrNull(),
                        sourcePaths = activeReq.sourcePaths
                    )
                )
            }
        }
    }

    fun observeRecoveryRecords() {
        state.update {
            it.copy(activeRecoveryOperation = bulkFileOperationCoordinator.recoveryRecords.value.firstOrNull()?.toBrowserRecoveryUiState())
        }
        viewModelScope.launch {
            bulkFileOperationCoordinator.recoveryRecords.collectLatest { records ->
                state.update { current ->
                    current.copy(activeRecoveryOperation = records.firstOrNull()?.toBrowserRecoveryUiState())
                }
            }
        }
    }

    fun observeOperationEvents() {
        viewModelScope.launch {
            bulkFileOperationCoordinator.events.collectLatest { event ->
                handleEvent(event)
            }
        }
    }

    fun clearStatusMessage() {
        state.update { it.copy(fileOperationStatusMessage = null) }
    }

    fun clearActiveOperation() {
        state.update { it.copy(activeFileOperation = null) }
    }

    fun retryRecoveredOperation(operationId: String) {
        bulkFileOperationCoordinator.retryRecoveredOperation(operationId)
    }

    fun cleanupRecoveredOperation(operationId: String) {
        bulkFileOperationCoordinator.cleanupRecoveredOperation(operationId)
    }

    fun dismissRecoveredOperation(operationId: String) {
        bulkFileOperationCoordinator.dismissRecoveredOperation(operationId)
    }

    private suspend fun handleEvent(event: BulkFileOperationEvent) {
        when (event) {
            is BulkFileOperationEvent.Started -> {
                state.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        activeFileOperation = BrowserFileOperationUiState(
                            type = event.request.type,
                            totalItems = event.request.operationItemCount(),
                            currentPath = event.request.operationCurrentPath(),
                            sourcePaths = event.request.sourcePaths
                        ),
                        fileOperationStatusMessage = null
                    )
                }
            }
            is BulkFileOperationEvent.Progress -> {
                state.update {
                    it.copy(
                        isLoading = true,
                        activeFileOperation = BrowserFileOperationUiState(
                            type = event.request.type,
                            totalItems = event.progress.totalItems,
                            completedItems = event.progress.completedItems,
                            currentPath = event.progress.currentPath,
                            isCancelling = false,
                            bytesCopied = event.progress.bytesCopied,
                            totalBytes = event.progress.totalBytes,
                            startTimeMillis = it.activeFileOperation?.startTimeMillis
                                ?: System.currentTimeMillis(),
                            sourcePaths = event.request.sourcePaths
                        )
                    )
                }
            }
            is BulkFileOperationEvent.Cancelling -> {
                state.update { currentState ->
                    currentState.copy(
                        isLoading = true,
                        activeFileOperation = currentState.activeFileOperation?.copy(isCancelling = true)
                            ?: BrowserFileOperationUiState(
                                type = event.request.type,
                                totalItems = event.request.operationItemCount(),
                                isCancelling = true,
                                sourcePaths = event.request.sourcePaths
                            )
                    )
                }
            }
            is BulkFileOperationEvent.Completed -> {
                val undoIds = if (event.request.type == BulkFileOperationType.TRASH) {
                    trashUndoIdsFor(event.request.sourcePaths)
                } else {
                    persistentListOf()
                }
                val undoAction = when (event.request.type) {
                    BulkFileOperationType.TRASH -> undoIds.takeIf { ids -> ids.isNotEmpty() }
                        ?.let { ids -> BrowserUndoAction.Trash(ids.toPersistentList()) }
                    BulkFileOperationType.MOVE -> moveUndoActionFor(
                        sourcePaths = event.request.sourcePaths,
                        destinationPath = event.request.destinationPath,
                        hasConflictResolutions = event.request.resolutions.isNotEmpty()
                    )
                    BulkFileOperationType.CREATE_FAKE -> createdUndoActionFor(
                        name = event.request.sourcePaths.singleOrNull(),
                        destinationPath = event.request.destinationPath
                    )
                    else -> null
                }
                state.update {
                    it.copy(
                        isLoading = false,
                        activeFileOperation = it.activeFileOperation?.copy(
                            terminalStatus = OperationCompletionStatus.SUCCESS
                        ),
                        clipboardState = null,
                        fileOperationStatusMessage = formatOperationCompletedMessage(
                            type = event.request.type,
                            itemCount = event.request.operationItemCount()
                        ),
                        pendingTrashUndoIds = undoIds.toPersistentList(),
                        pendingUndoAction = undoAction
                    )
                }
                refreshAction()
            }
            is BulkFileOperationEvent.Failed -> {
                state.update {
                    it.copy(
                        isLoading = false,
                        activeFileOperation = it.activeFileOperation?.copy(
                            terminalStatus = OperationCompletionStatus.FAILED
                        ),
                        clipboardState = null,
                        fileOperationStatusMessage = event.error?.userMessage
                            ?: UiText.StringResource(R.string.error_file_operation_failed)
                    )
                }
            }
            is BulkFileOperationEvent.Cancelled -> {
                state.update {
                    it.copy(
                        isLoading = false,
                        activeFileOperation = it.activeFileOperation?.copy(
                            terminalStatus = OperationCompletionStatus.CANCELLED
                        ),
                        clipboardState = null,
                        fileOperationStatusMessage = UiText.StringResource(R.string.file_operation_cancelled)
                    )
                }
            }
            is BulkFileOperationEvent.RecoveryAvailable -> {
                state.update { it.copy(activeRecoveryOperation = event.record.toBrowserRecoveryUiState()) }
            }
            is BulkFileOperationEvent.RecoveryDismissed -> {
                state.update { current ->
                    if (current.activeRecoveryOperation?.operationId == event.operationId) {
                        current.copy(activeRecoveryOperation = null)
                    } else {
                        current
                    }
                }
            }
            is BulkFileOperationEvent.RecoveryCleanupCompleted -> {
                state.update { current ->
                    val updated = if (current.activeRecoveryOperation?.operationId == event.operationId) {
                        current.copy(
                            activeRecoveryOperation = null,
                            fileOperationStatusMessage = UiText.StringResource(R.string.file_operation_recovery_cleanup_complete)
                        )
                    } else {
                        current.copy(fileOperationStatusMessage = UiText.StringResource(R.string.file_operation_recovery_cleanup_complete))
                    }
                    updated
                }
                refreshAction()
            }
        }
    }

    private fun formatOperationCompletedMessage(
        type: BulkFileOperationType,
        itemCount: Int
    ): UiText {
        val pluralRes = when (type) {
            BulkFileOperationType.COPY -> R.plurals.file_operation_copied_items
            BulkFileOperationType.MOVE -> R.plurals.file_operation_moved_items
            BulkFileOperationType.TRASH -> R.plurals.file_operation_trashed_items
            BulkFileOperationType.DELETE -> R.plurals.file_operation_deleted_items
            BulkFileOperationType.SHRED -> R.plurals.file_operation_shredded_items
            BulkFileOperationType.CREATE_FAKE -> R.plurals.file_operation_created_items
            BulkFileOperationType.EXTRACT_ARCHIVE -> R.plurals.file_operation_extracted_items
            BulkFileOperationType.CREATE_ARCHIVE -> R.plurals.file_operation_archived_items
            BulkFileOperationType.SAVE_TO_ARCILE_IMPORT -> R.plurals.file_operation_copied_items
        }
        return UiText.PluralResource(pluralRes, itemCount, listOf(itemCount))
    }

    private suspend fun trashUndoIdsFor(sourcePaths: List<String>): List<String> {
        val sourceSet = sourcePaths.toSet()
        return trashRepository.getTrashFiles().getOrNull()
            ?.filter { it.originalPath in sourceSet }
            ?.sortedByDescending { it.deletionTime }
            ?.map { it.id }
            ?.take(sourcePaths.size)
            .orEmpty()
    }

    private fun moveUndoActionFor(
        sourcePaths: List<String>,
        destinationPath: String?,
        hasConflictResolutions: Boolean
    ): BrowserUndoAction.Moved? {
        if (destinationPath.isNullOrBlank() || sourcePaths.isEmpty() || hasConflictResolutions) return null
        val entries = sourcePaths.map { sourcePath ->
            MoveUndoEntry(
                originalPath = sourcePath,
                movedPath = joinStoragePath(destinationPath, File(sourcePath).name)
            )
        }
        return BrowserUndoAction.Moved(entries.toPersistentList())
    }

    private fun createdUndoActionFor(
        name: String?,
        destinationPath: String?
    ): BrowserUndoAction.Created? {
        if (name.isNullOrBlank() || destinationPath.isNullOrBlank()) return null
        return BrowserUndoAction.Created(joinStoragePath(destinationPath, name))
    }

    private fun joinStoragePath(parent: String, name: String): String =
        parent.trimEnd('/', '\\') + "/" + name
}

private fun dev.qtremors.arcile.core.operation.BulkFileOperationRequest.operationItemCount(): Int =
    if (type == BulkFileOperationType.SAVE_TO_ARCILE_IMPORT) importItems.size else sourcePaths.size

private fun dev.qtremors.arcile.core.operation.BulkFileOperationRequest.operationCurrentPath(): String? =
    if (type == BulkFileOperationType.SAVE_TO_ARCILE_IMPORT) {
        importItems.firstOrNull()?.displayName
    } else {
        sourcePaths.firstOrNull()
    }
