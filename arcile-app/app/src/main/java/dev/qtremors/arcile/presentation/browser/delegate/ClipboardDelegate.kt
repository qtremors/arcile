package dev.qtremors.arcile.presentation.browser.delegate

import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.usecase.PasteFilesUseCase
import dev.qtremors.arcile.presentation.ClipboardOperation
import dev.qtremors.arcile.presentation.ClipboardState
import dev.qtremors.arcile.presentation.browser.BrowserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ClipboardDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val viewModelScope: CoroutineScope,
    private val repository: FileRepository,
    private val pasteFilesUseCase: PasteFilesUseCase,
    private val refreshAction: () -> Unit
) {
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
        val isMove = clipboard.operation == ClipboardOperation.CUT
        val result = pasteFilesUseCase(clipboard.sourcePaths, destinationPath, isMove, resolutions)

        result.onSuccess {
            state.update { it.copy(clipboardState = null) }
            refreshAction()
        }.onFailure { error ->
            state.update { it.copy(isLoading = false, error = error.message ?: "Failed to paste files") }
        }
    }
}