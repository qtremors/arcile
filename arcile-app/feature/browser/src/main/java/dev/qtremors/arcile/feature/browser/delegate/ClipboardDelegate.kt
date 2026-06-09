package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.runtime.R as RuntimeR
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.feature.browser.BrowserDialogEvent
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.feature.browser.reduce
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.ui.UiText
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClipboardDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val viewModelScope: CoroutineScope,
    private val clipboardRepository: ClipboardRepository,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    private val refreshAction: () -> Unit
) {
    fun copySelectedToClipboard() {
        if (state.value.archiveContext != null) return
        val selectedPaths = state.value.selectedFiles
        val selectedFiles = state.value.files.filter { it.absolutePath in selectedPaths }.map { file ->
            if (file.isDirectory) {
                file.copy(size = state.value.folderStatsByPath[file.absolutePath]?.totalBytes ?: 0L)
            } else file
        }
        
        if (selectedFiles.isNotEmpty()) {
            clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, selectedFiles))
            state.update {
                it.copy(
                    selectedFiles = persistentSetOf(),
                    selectedFilesTotalSize = 0L
                )
            }
        }
    }

    fun cutSelectedToClipboard() {
        if (state.value.archiveContext != null) return
        val selectedPaths = state.value.selectedFiles
        val selectedFiles = state.value.files.filter { it.absolutePath in selectedPaths }.map { file ->
            if (file.isDirectory) {
                file.copy(size = state.value.folderStatsByPath[file.absolutePath]?.totalBytes ?: 0L)
            } else file
        }
        
        if (selectedFiles.isNotEmpty()) {
            clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.CUT, selectedFiles))
            state.update {
                it.copy(
                    selectedFiles = persistentSetOf(),
                    selectedFilesTotalSize = 0L
                )
            }
        }
    }

    fun cancelClipboard() {
        bulkFileOperationCoordinator.cancelActiveOperation()
        clipboardRepository.clearClipboardState()
    }

    fun removeFromClipboard(path: String) {
        val clipboard = clipboardRepository.clipboardState.value ?: return
        val updatedFiles = clipboard.files.filter { it.absolutePath != path }
        if (updatedFiles.isEmpty()) {
            clipboardRepository.clearClipboardState()
        } else {
            clipboardRepository.setClipboardState(clipboard.copy(files = updatedFiles))
        }
    }

    fun pasteFromClipboard() {
        if (state.value.archiveContext != null) return
        val clipboard = state.value.clipboardState ?: return
        val currentPath = state.value.currentPath
        if (currentPath.isEmpty()) return

        viewModelScope.launch {
            state.update { it.copy(isLoading = true, error = null) }

            val sourcePaths = clipboard.files.map { it.absolutePath }
            clipboardRepository.detectCopyConflicts(sourcePaths, currentPath).onSuccess { conflicts ->
                if (conflicts.isNotEmpty()) {
                    state.update {
                        it.reduce(BrowserDialogEvent.ConflictDialogShown(conflicts)).copy(isLoading = false)
                    }
                } else {
                    executePaste(clipboard, currentPath, emptyMap())
                }
            }.onFailure { error ->
                state.update {
                    it.copy(
                        isLoading = false,
                        error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_check_conflicts_failed)
                    )
                }
            }
        }
    }

    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) {
        val clipboard = state.value.clipboardState ?: return
        val currentPath = state.value.currentPath
        if (currentPath.isEmpty()) return

        viewModelScope.launch {
            state.update { it.reduce(BrowserDialogEvent.ConflictDialogDismissed).copy(isLoading = true) }
            executePaste(clipboard, currentPath, resolutions)
        }
    }

    fun dismissConflictDialog() {
        state.update { it.reduce(BrowserDialogEvent.ConflictDialogDismissed) }
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
            sourcePaths = clipboard.files.map { it.absolutePath },
            destinationPath = destinationPath,
            resolutions = resolutions
        )

        if (started) {
            state.update { it.copy(isLoading = false) }
        } else {
            state.update { it.copy(isLoading = false, error = UiText.StringResource(RuntimeR.string.error_operation_already_running)) }
        }
    }
}
