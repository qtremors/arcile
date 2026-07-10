package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.runtime.R as RuntimeR
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class BrowserClipboardContext(
    val archiveContext: BrowserArchiveContext?,
    val currentPath: String,
    val clipboardState: ClipboardState?,
    val selectedPaths: Set<String>,
    val files: List<FileModel>,
    val folderStats: Map<String, FolderStats>
)

internal class BrowserClipboardController(
    private val scope: CoroutineScope,
    private val clipboardRepository: ClipboardRepository,
    private val clipboardController: ClipboardController,
    private val operationCoordinator: BulkFileOperationCoordinator,
    private val contextProvider: () -> BrowserClipboardContext,
    private val clearSelection: () -> Unit,
    private val onConflicts: (List<dev.qtremors.arcile.core.storage.domain.FileConflict>) -> Unit,
    private val onDismissConflicts: () -> Unit,
    private val onBusyChange: (Boolean) -> Unit,
    private val onError: (UiText?) -> Unit
) {
    fun copySelected() = storeSelection(ClipboardOperation.COPY)
    fun cutSelected() = storeSelection(ClipboardOperation.CUT)

    private fun storeSelection(operation: ClipboardOperation) {
        val context = contextProvider()
        if (context.archiveContext != null) return
        val selectedFiles = context.files
            .filter { it.absolutePath in context.selectedPaths }
            .map { file ->
                if (file.isDirectory) {
                    file.copy(size = context.folderStats[file.absolutePath]?.totalBytes ?: 0L)
                } else {
                    file
                }
            }
        if (clipboardController.store(operation, selectedFiles)) clearSelection()
    }

    fun cancel() {
        operationCoordinator.cancelActiveOperation()
        clipboardController.clear()
        onDismissConflicts()
    }

    fun remove(path: String) = clipboardController.remove(path)

    fun paste() {
        val context = contextProvider()
        if (context.archiveContext != null || context.currentPath.isEmpty()) return
        val clipboard = context.clipboardState ?: return
        scope.launch {
            onBusyChange(true)
            onError(null)
            val sources = clipboard.files.map(FileModel::absolutePath)
            clipboardRepository.detectCopyConflicts(sources, context.currentPath)
                .onSuccess { conflicts ->
                    if (conflicts.isEmpty()) {
                        executePaste(clipboard, context.currentPath, emptyMap())
                    } else {
                        onBusyChange(false)
                        onConflicts(conflicts)
                    }
                }
                .onFailure { error ->
                    onBusyChange(false)
                    onError(
                        error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_check_conflicts_failed)
                    )
                }
        }
    }

    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) {
        val context = contextProvider()
        val clipboard = context.clipboardState ?: return
        if (context.currentPath.isEmpty()) return
        onBusyChange(true)
        onDismissConflicts()
        executePaste(clipboard, context.currentPath, resolutions)
    }

    private fun executePaste(
        clipboard: ClipboardState,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>
    ) {
        val type = if (clipboard.operation == ClipboardOperation.CUT) {
            BulkFileOperationType.MOVE
        } else {
            BulkFileOperationType.COPY
        }
        val started = operationCoordinator.startOperation(
            type = type,
            sourcePaths = clipboard.files.map(FileModel::absolutePath),
            destinationPath = destinationPath,
            resolutions = resolutions
        )
        onBusyChange(false)
        if (!started) {
            onError(UiText.StringResource(RuntimeR.string.error_operation_already_running))
        }
    }
}
