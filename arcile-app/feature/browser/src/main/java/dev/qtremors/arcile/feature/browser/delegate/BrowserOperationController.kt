package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.core.presentation.OperationPresentationMapper
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.StorageMutationResult
import dev.qtremors.arcile.core.storage.domain.toArcileError
import dev.qtremors.arcile.core.storage.domain.joinStoragePath
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.storage.domain.storagePathName
import dev.qtremors.arcile.core.presentation.userMessage
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.BrowserOperationState
import dev.qtremors.arcile.feature.browser.BrowserUndoAction
import dev.qtremors.arcile.feature.browser.MoveUndoEntry
import dev.qtremors.arcile.feature.browser.toBrowserRecoveryUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class BrowserOperationController(
    initialState: BrowserOperationState,
    private val scope: CoroutineScope,
    private val trashRepository: TrashRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val clipboardRepository: ClipboardRepository,
    private val clipboardController: ClipboardController,
    private val coordinator: BulkFileOperationCoordinator,
    private val onBusyChange: (Boolean) -> Unit,
    private val onError: (UiText?) -> Unit,
    private val refreshAction: () -> Unit
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<BrowserOperationState> = _state.asStateFlow()
    private val observationJobs = mutableListOf<Job>()

    fun startObserving() {
        stopObserving()
        hydrateActiveOperation()
        update {
            it.copy(
                activeRecoveryOperation = coordinator.recoveryRecords.value
                    .firstOrNull()
                    ?.toBrowserRecoveryUiState()
            )
        }
        observationJobs += scope.launch {
            clipboardRepository.clipboardState.collectLatest { clipboard ->
                update { it.copy(clipboardState = clipboard) }
            }
        }
        observationJobs += scope.launch {
            coordinator.recoveryRecords.collectLatest { records ->
                update {
                    it.copy(activeRecoveryOperation = records.firstOrNull()?.toBrowserRecoveryUiState())
                }
            }
        }
        observationJobs += scope.launch {
            coordinator.events.collectLatest(::handleEvent)
        }
    }

    fun stopObserving() {
        observationJobs.forEach(Job::cancel)
        observationJobs.clear()
    }

    fun clearStatusMessage() {
        update { it.copy(fileOperationStatusMessage = null) }
    }

    fun clearActiveOperation() {
        update { it.copy(activeFileOperation = null) }
    }

    fun retryRecoveredOperation(operationId: String) {
        coordinator.retryRecoveredOperation(operationId)
    }

    fun cleanupRecoveredOperation(operationId: String) {
        coordinator.cleanupRecoveredOperation(operationId)
    }

    fun dismissRecoveredOperation(operationId: String) {
        coordinator.dismissRecoveredOperation(operationId)
    }

    fun undoLastTrashMove() {
        val trashIds = state.value.pendingTrashUndoIds
        if (trashIds.isEmpty()) return
        update {
            it.copy(
                pendingTrashUndoIds = persistentListOf(),
                pendingUndoAction = null
            )
        }
        scope.launch {
            when (val result = trashRepository.restoreFromTrash(trashIds)) {
                StorageMutationResult.Completed -> refreshAction()
                is StorageMutationResult.AuthorizationRequired -> {
                    update {
                        it.copy(
                            pendingTrashUndoIds = trashIds,
                            pendingAuthorization = result.requirement
                        )
                    }
                }
                is StorageMutationResult.Failed -> reportStorageError(result.error)
            }
        }
    }

    fun handleAuthorizationResult(requestId: String, confirmed: Boolean): Boolean {
        if (state.value.pendingAuthorization?.requestId != requestId) return false
        update { it.copy(pendingAuthorization = null) }
        if (confirmed) undoLastTrashMove()
        return true
    }

    fun handleAuthorizationUnavailable(requestId: String): Boolean {
        if (state.value.pendingAuthorization?.requestId != requestId) return false
        update { it.copy(pendingAuthorization = null) }
        reportStorageError(IllegalStateException("Native authorization request expired"))
        return true
    }

    fun clearPendingTrashUndo() {
        update {
            it.copy(
                pendingTrashUndoIds = persistentListOf(),
                pendingUndoAction = null
            )
        }
    }

    fun undoLastOperation() {
        when (val undo = state.value.pendingUndoAction) {
            is BrowserUndoAction.Trash -> undoLastTrashMove()
            is BrowserUndoAction.Rename -> undoRename(undo)
            is BrowserUndoAction.Created -> undoCreated(undo)
            is BrowserUndoAction.Moved -> undoMove(undo)
            null -> Unit
        }
    }

    fun clearPendingUndo() {
        clearPendingTrashUndo()
    }

    fun recordLocalMutation(status: UiText, undoAction: BrowserUndoAction) {
        update {
            it.copy(
                fileOperationStatusMessage = status,
                pendingUndoAction = undoAction
            )
        }
    }

    private fun hydrateActiveOperation() {
        coordinator.activeRequest.value?.let { request ->
            update {
                it.copy(
                    activeFileOperation = OperationPresentationMapper.map(request)
                )
            }
        }
    }

    private suspend fun handleEvent(event: BulkFileOperationEvent) {
        when (event) {
            is BulkFileOperationEvent.Started -> {
                onBusyChange(true)
                onError(null)
                update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(event.request),
                        fileOperationStatusMessage = null
                    )
                }
            }
            is BulkFileOperationEvent.Progress -> {
                onBusyChange(true)
                update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(
                            request = event.request,
                            progress = event.progress,
                            previous = it.activeFileOperation
                        )
                    )
                }
            }
            is BulkFileOperationEvent.Cancelling -> {
                onBusyChange(true)
                update {
                    it.copy(
                        activeFileOperation = it.activeFileOperation?.copy(isCancelling = true)
                            ?: OperationPresentationMapper.map(
                                request = event.request,
                                isCancelling = true
                            )
                    )
                }
            }
            is BulkFileOperationEvent.Completed -> completeOperation(event)
            is BulkFileOperationEvent.Failed -> {
                finishClipboardOperation()
                update {
                    it.copy(
                        activeFileOperation = it.activeFileOperation?.copy(
                            terminalStatus = OperationCompletionStatus.FAILED
                        ),
                        fileOperationStatusMessage = event.error?.userMessage
                            ?: UiText.StringResource(R.string.error_file_operation_failed)
                    )
                }
            }
            is BulkFileOperationEvent.Cancelled -> {
                finishClipboardOperation()
                update {
                    it.copy(
                        activeFileOperation = it.activeFileOperation?.copy(
                            terminalStatus = OperationCompletionStatus.CANCELLED
                        ),
                        fileOperationStatusMessage = UiText.StringResource(
                            R.string.file_operation_cancelled
                        )
                    )
                }
            }
            is BulkFileOperationEvent.RecoveryAvailable -> {
                update {
                    it.copy(activeRecoveryOperation = event.record.toBrowserRecoveryUiState())
                }
            }
            is BulkFileOperationEvent.RecoveryDismissed -> {
                update {
                    if (it.activeRecoveryOperation?.operationId == event.operationId) {
                        it.copy(activeRecoveryOperation = null)
                    } else {
                        it
                    }
                }
            }
            is BulkFileOperationEvent.RecoveryCleanupCompleted -> {
                update {
                    it.copy(
                        activeRecoveryOperation = it.activeRecoveryOperation
                            ?.takeUnless { recovery -> recovery.operationId == event.operationId },
                        fileOperationStatusMessage = UiText.StringResource(
                            R.string.file_operation_recovery_cleanup_complete
                        )
                    )
                }
                refreshAction()
            }
        }
    }

    private suspend fun completeOperation(event: BulkFileOperationEvent.Completed) {
        val undoIds = if (event.request.type == BulkFileOperationType.TRASH) {
            trashUndoIdsFor(event.request.sourcePaths)
        } else {
            emptyList()
        }
        val undoAction = when (event.request.type) {
            BulkFileOperationType.TRASH -> undoIds.takeIf(List<String>::isNotEmpty)
                ?.let { BrowserUndoAction.Trash(it.toPersistentList()) }
            BulkFileOperationType.MOVE -> moveUndoActionFor(
                event.request.sourcePaths,
                event.request.destinationPath,
                event.request.resolutions.isNotEmpty()
            )
            BulkFileOperationType.CREATE_FAKE -> createdUndoActionFor(
                event.request.sourcePaths.singleOrNull(),
                event.request.destinationPath
            )
            else -> null
        }
        finishClipboardOperation()
        update {
            it.copy(
                activeFileOperation = it.activeFileOperation?.copy(
                    terminalStatus = OperationCompletionStatus.SUCCESS
                ),
                fileOperationStatusMessage = formatCompletedMessage(
                    event.request.type,
                    OperationPresentationMapper.itemCount(event.request)
                ),
                pendingTrashUndoIds = undoIds.toPersistentList(),
                pendingUndoAction = undoAction
            )
        }
        refreshAction()
    }

    private fun finishClipboardOperation() {
        onBusyChange(false)
        clipboardController.clear()
        update { it.copy(clipboardState = null) }
    }

    private fun undoRename(undo: BrowserUndoAction.Rename) {
        update { it.copy(pendingUndoAction = null) }
        scope.launch {
            fileMutationRepository.renameFile(
                undo.renamedPath,
                storagePathName(undo.originalPath)
            ).onSuccess {
                refreshAction()
            }.onFailure(::reportStorageError)
        }
    }

    private fun undoCreated(undo: BrowserUndoAction.Created) {
        update { it.copy(pendingUndoAction = null) }
        scope.launch {
            fileMutationRepository.deletePermanently(listOf(undo.path)).onSuccess {
                refreshAction()
            }.onFailure(::reportStorageError)
        }
    }

    private fun undoMove(undo: BrowserUndoAction.Moved) {
        update { it.copy(pendingUndoAction = null) }
        scope.launch {
            undo.entries.groupBy { storageParentPath(it.originalPath).orEmpty() }
                .forEach { (originalParent, entries) ->
                    if (originalParent.isBlank()) {
                        onError(UiText.StringResource(R.string.file_operation_undo_failed))
                        return@launch
                    }
                    clipboardRepository.moveFiles(
                        entries.map(MoveUndoEntry::movedPath),
                        originalParent
                    ).onFailure {
                        reportStorageError(it)
                        return@launch
                    }
                }
            refreshAction()
        }
    }

    private fun reportStorageError(error: Throwable) {
        onError(error.toArcileError().userMessage)
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
        if (destinationPath.isNullOrBlank() || sourcePaths.isEmpty() || hasConflictResolutions) {
            return null
        }
        return BrowserUndoAction.Moved(
            sourcePaths.map {
                MoveUndoEntry(it, joinStoragePath(destinationPath, storagePathName(it)))
            }.toPersistentList()
        )
    }

    private fun createdUndoActionFor(
        name: String?,
        destinationPath: String?
    ): BrowserUndoAction.Created? =
        if (name.isNullOrBlank() || destinationPath.isNullOrBlank()) {
            null
        } else {
            BrowserUndoAction.Created(joinStoragePath(destinationPath, name))
        }

    private fun formatCompletedMessage(type: BulkFileOperationType, count: Int): UiText {
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
        return UiText.PluralResource(pluralRes, count, listOf(count))
    }

    private inline fun update(
        transform: (BrowserOperationState) -> BrowserOperationState
    ) {
        _state.update(transform)
    }
}
