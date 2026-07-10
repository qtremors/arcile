package dev.qtremors.arcile.feature.browser.delegate

import android.content.IntentSender
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.runtime.R as RuntimeR
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R as UiR
import dev.qtremors.arcile.feature.browser.BrowserNativeAction
import dev.qtremors.arcile.feature.browser.BrowserUndoAction
import dev.qtremors.arcile.core.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.core.presentation.delegate.DeleteStateCallbacks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class BrowserMutationContext(
    val currentPath: String,
    val isVolumeRootScreen: Boolean,
    val isArchive: Boolean,
    val selectedPaths: List<String>
)

internal data class BrowserDeleteWorkflowState(
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isShredChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val pendingNativeAction: BrowserNativeAction? = null
)

internal class BrowserMutationController(
    initialState: BrowserDeleteWorkflowState,
    private val scope: CoroutineScope,
    private val fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    volumeRepository: VolumeRepository,
    private val operationCoordinator: BulkFileOperationCoordinator,
    private val contextProvider: () -> BrowserMutationContext,
    private val clearSelection: () -> Unit,
    private val emitNativeRequest: suspend (IntentSender) -> Unit,
    private val onStateChange: (BrowserDeleteWorkflowState) -> Unit,
    private val onBusyChange: (Boolean) -> Unit,
    private val onError: (UiText) -> Unit,
    private val onMutationCompleted: (UiText, BrowserUndoAction) -> Unit
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<BrowserDeleteWorkflowState> = _state.asStateFlow()

    private val deleteFlow = DeleteFlowDelegate(
        coroutineScope = scope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = object : DeleteStateCallbacks {
            override fun getSelectedFiles() = contextProvider().selectedPaths
            override fun isPermanentDeleteChecked() = state.value.isPermanentDeleteChecked
            override fun isPermanentDeleteToggleEnabled() = state.value.isPermanentDeleteToggleEnabled
            override fun setLoading(isLoading: Boolean) = onBusyChange(isLoading)
            override fun showMixedDeleteExplanation() = update {
                it.copy(showMixedDeleteExplanation = true)
            }
            override fun showPermanentDeleteConfirmation() = update {
                it.copy(
                    showPermanentDeleteConfirmation = true,
                    isPermanentDeleteChecked = true,
                    isPermanentDeleteToggleEnabled = false
                )
            }
            override fun showTrashConfirmation() = update {
                it.copy(
                    showTrashConfirmation = true,
                    isPermanentDeleteChecked = false,
                    isPermanentDeleteToggleEnabled = true
                )
            }
            override fun togglePermanentDeleteChecked() = update {
                it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked)
            }
            override fun isShredChecked() = state.value.isShredChecked
            override fun toggleShredChecked() = update {
                it.copy(isShredChecked = !it.isShredChecked)
            }
            override fun dismissDeleteConfirmation() =
                this@BrowserMutationController.dismissDeleteConfirmation()
            override fun setError(error: String) = onError(UiText.Dynamic(error))
            override fun setError(error: UiText) = onError(error)
            override fun setDeleteDecision(decision: DeleteDecision) = update {
                it.copy(deleteDecision = decision)
            }
            override fun setPendingNativeAction() = update {
                it.copy(pendingNativeAction = BrowserNativeAction.TRASH)
            }
            override fun clearSelection() = this@BrowserMutationController.clearSelection()
        },
        startBulkDeleteOperation = { type, selected ->
            operationCoordinator.startOperation(
                type = type,
                sourcePaths = selected,
                destinationPath = null,
                resolutions = emptyMap<String, ConflictResolution>()
            )
        },
        emitNativeRequest = emitNativeRequest,
        onSuccess = {}
    )

    fun createFolder(name: String) = createEntry(name, isDirectory = true)

    fun createFile(name: String) = createEntry(name, isDirectory = false)

    fun createFakeFile(name: String, size: Long) {
        val context = actionableContext() ?: return
        val started = operationCoordinator.startOperation(
            type = BulkFileOperationType.CREATE_FAKE,
            sourcePaths = listOf(name),
            destinationPath = context.currentPath,
            resolutions = emptyMap<String, ConflictResolution>(),
            fakeFileSize = size
        )
        if (!started) {
            onError(UiText.StringResource(RuntimeR.string.error_operation_already_running))
        }
    }

    fun requestDeleteSelected() {
        if (!contextProvider().isArchive) deleteFlow.requestDeleteSelected()
    }

    fun togglePermanentDelete() = deleteFlow.togglePermanentDelete()

    fun toggleShred() = deleteFlow.toggleShred()

    fun confirmDeleteSelected() {
        if (!contextProvider().isArchive) deleteFlow.confirmDeleteSelected()
    }

    fun dismissDeleteConfirmation() {
        update {
            BrowserDeleteWorkflowState(
                pendingNativeAction = it.pendingNativeAction
            )
        }
    }

    fun moveSelectedToTrash() {
        if (!contextProvider().isArchive) deleteFlow.moveSelectedToTrash()
    }

    fun deleteSelectedPermanently() {
        if (!contextProvider().isArchive) deleteFlow.deleteSelectedPermanently()
    }

    fun handleNativeActionResult(confirmed: Boolean) {
        val pendingAction = state.value.pendingNativeAction ?: return
        update { it.copy(pendingNativeAction = null) }
        if (confirmed && pendingAction == BrowserNativeAction.TRASH) confirmDeleteSelected()
    }

    fun rename(path: String, newName: String) {
        if (contextProvider().isArchive) return
        if (!isValidFileName(newName)) {
            onError(UiText.StringResource(UiR.string.error_invalid_name))
            return
        }
        scope.launch {
            fileMutationRepository.renameFile(path, newName).onSuccess { renamed ->
                clearSelection()
                onMutationCompleted(
                    UiText.StringResource(UiR.string.file_operation_renamed),
                    BrowserUndoAction.Rename(path, renamed.absolutePath)
                )
            }.onFailure { error ->
                onError(
                    error.message?.let(UiText::Dynamic)
                        ?: UiText.StringResource(UiR.string.error_rename_file_failed)
                )
            }
        }
    }

    private fun createEntry(name: String, isDirectory: Boolean) {
        val context = actionableContext() ?: return
        if (!isValidFileName(name)) {
            onError(UiText.StringResource(UiR.string.error_invalid_name))
            return
        }
        scope.launch {
            val result = if (isDirectory) {
                fileMutationRepository.createDirectory(context.currentPath, name)
            } else {
                fileMutationRepository.createFile(context.currentPath, name)
            }
            result.onSuccess { created ->
                onMutationCompleted(
                    UiText.StringResource(
                        if (isDirectory) UiR.string.file_operation_folder_created
                        else UiR.string.file_operation_file_created
                    ),
                    BrowserUndoAction.Created(created.absolutePath)
                )
            }.onFailure { error ->
                onError(
                    error.message?.let(UiText::Dynamic)
                        ?: UiText.StringResource(
                            if (isDirectory) UiR.string.error_create_folder_failed
                            else UiR.string.error_create_file_failed
                        )
                )
            }
        }
    }

    private fun actionableContext(): BrowserMutationContext? =
        contextProvider().takeIf {
            !it.isArchive && !it.isVolumeRootScreen && it.currentPath.isNotBlank()
        }

    private fun isValidFileName(name: String): Boolean =
        name.isNotBlank() &&
            name != "." &&
            name != ".." &&
            '/' !in name &&
            '\\' !in name &&
            '\u0000' !in name

    private inline fun update(
        transform: (BrowserDeleteWorkflowState) -> BrowserDeleteWorkflowState
    ) {
        _state.update(transform)
        onStateChange(_state.value)
    }
}
