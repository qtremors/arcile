package dev.qtremors.arcile.presentation.browser.delegate

import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.presentation.ClipboardOperation
import dev.qtremors.arcile.presentation.ClipboardState
import dev.qtremors.arcile.presentation.browser.BrowserState
import dev.qtremors.arcile.presentation.operations.BulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.operations.BulkFileOperationEvent
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClipboardDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val viewModelScope: CoroutineScope,
    private val repository: FileRepository,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    private val refreshAction: () -> Unit
) {
    init {
        viewModelScope.launch {
            bulkFileOperationCoordinator.activeRequest.collect { activeRequest ->
                if (activeRequest == null && state.value.isLoading && state.value.clipboardState == null) {
                    state.update { it.copy(isLoading = false) }
                }
            }
        }

        viewModelScope.launch {
            bulkFileOperationCoordinator.events.collect { event ->
                when (event) {
                    is BulkFileOperationEvent.Started -> {
                        state.update { it.copy(isLoading = true, error = null) }
                    }
                    is BulkFileOperationEvent.Completed -> {
                        state.update { it.copy(isLoading = false, clipboardState = null) }
                        refreshAction()
                    }
                    is BulkFileOperationEvent.Failed -> {
                        state.update { it.copy(isLoading = false, error = event.message) }
                    }
                    is BulkFileOperationEvent.Cancelled -> {
                        state.update { it.copy(isLoading = false) }
                    }
                }
            }
        }
    }

    fun copySelectedToClipboard() {
        val selected = state.value.selectedFiles.toList()
        if (selected.isNotEmpty()) {
            state.update {
                it.copy(
                    clipboardState = ClipboardState(ClipboardOperation.COPY, selected),
                    selectedFiles = emptySet()
                )
            }
        }
    }

    fun cutSelectedToClipboard() {
        val selected = state.value.selectedFiles.toList()
        if (selected.isNotEmpty()) {
            state.update {
                it.copy(
                    clipboardState = ClipboardState(ClipboardOperation.CUT, selected),
                    selectedFiles = emptySet()
                )
            }
        }
    }

    fun cancelClipboard() {
        bulkFileOperationCoordinator.cancelActiveOperation()
        state.update { it.copy(clipboardState = null) }
    }

    fun pasteFromClipboard() {
        val clipboard = state.value.clipboardState ?: return
        val currentPath = state.value.currentPath
        if (currentPath.isEmpty()) return

        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            repository.detectCopyConflicts(clipboard.sourcePaths, currentPath).onSuccess { conflicts ->
                if (conflicts.isNotEmpty()) {
                    state.update {
                        it.copy(
                            isLoading = false,
                            pasteConflicts = conflicts,
                            showConflictDialog = true
                        )
                    }
                } else {
                    executePaste(clipboard, currentPath, emptyMap())
                }
            }.onFailure { error ->
                state.update { it.copy(isLoading = false, error = error.message ?: "Failed to check for conflicts") }
            }
        }
    }

    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) {
        val clipboard = state.value.clipboardState ?: return
        val currentPath = state.value.currentPath
        if (currentPath.isEmpty()) return

        viewModelScope.launch {
            state.update { it.copy(showConflictDialog = false, pasteConflicts = emptyList(), isLoading = true) }
            executePaste(clipboard, currentPath, resolutions)
        }
    }

    fun dismissConflictDialog() {
        state.update { it.copy(showConflictDialog = false, pasteConflicts = emptyList()) }
    }

    private suspend fun executePaste(
        clipboard: ClipboardState,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>
    ) {
        val operationType = if (clipboard.operation == ClipboardOperation.CUT) {
            BulkFileOperationType.MOVE
        } else {
            BulkFileOperationType.COPY
        }
        val started = bulkFileOperationCoordinator.startOperation(
            type = operationType,
            sourcePaths = clipboard.sourcePaths,
            destinationPath = destinationPath,
            resolutions = resolutions
        )

        if (!started) {
            state.update { it.copy(isLoading = false, error = "Another file operation is already running") }
        }
    }
}
