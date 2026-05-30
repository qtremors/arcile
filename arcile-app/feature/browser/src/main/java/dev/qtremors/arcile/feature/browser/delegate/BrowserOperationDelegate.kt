package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.feature.browser.BrowserFileOperationUiState
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.userMessage
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
                        currentPath = activeReq.sourcePaths.firstOrNull()
                    )
                )
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

    private suspend fun handleEvent(event: BulkFileOperationEvent) {
        when (event) {
            is BulkFileOperationEvent.Started -> {
                state.update {
                    it.copy(
                        isLoading = true,
                        error = null,
                        activeFileOperation = BrowserFileOperationUiState(
                            type = event.request.type,
                            totalItems = event.request.sourcePaths.size,
                            currentPath = event.request.sourcePaths.firstOrNull()
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
                                ?: System.currentTimeMillis()
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
                                totalItems = event.request.sourcePaths.size,
                                isCancelling = true
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
                state.update {
                    it.copy(
                        isLoading = false,
                        activeFileOperation = it.activeFileOperation?.copy(
                            terminalStatus = OperationCompletionStatus.SUCCESS
                        ),
                        clipboardState = null,
                        fileOperationStatusMessage = formatOperationCompletedMessage(
                            type = event.request.type,
                            itemCount = event.request.sourcePaths.size
                        ),
                        pendingTrashUndoIds = undoIds.toPersistentList()
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
            BulkFileOperationType.CREATE_FAKE -> R.plurals.file_operation_created_items
            BulkFileOperationType.EXTRACT_ARCHIVE -> R.plurals.file_operation_extracted_items
            BulkFileOperationType.CREATE_ARCHIVE -> R.plurals.file_operation_archived_items
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
}
