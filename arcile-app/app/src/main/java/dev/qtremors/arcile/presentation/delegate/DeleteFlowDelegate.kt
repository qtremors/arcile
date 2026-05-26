package dev.qtremors.arcile.presentation.delegate

import android.content.IntentSender
import dev.qtremors.arcile.R
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.DeleteDestination
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.evaluateDeletePolicy
import dev.qtremors.arcile.core.storage.domain.DeletePolicyResult
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

interface DeleteStateCallbacks {
    fun getSelectedFiles(): List<String>
    fun isPermanentDeleteChecked(): Boolean
    fun isPermanentDeleteToggleEnabled(): Boolean
    
    fun setLoading(isLoading: Boolean)
    fun showMixedDeleteExplanation()
    fun showPermanentDeleteConfirmation()
    fun showTrashConfirmation()
    fun togglePermanentDeleteChecked()
    fun dismissDeleteConfirmation()
    fun setError(error: String)
    fun setError(error: UiText) = setError(
        when (error) {
            is UiText.Dynamic -> error.value
            else -> "File operation failed"
        }
    )
    fun setDeleteDecision(decision: DeleteDecision) = Unit
    fun setPendingNativeAction()
    fun clearSelection()
}

class DeleteFlowDelegate(
    private val coroutineScope: CoroutineScope,
    private val repository: FileRepository,
    private val callbacks: DeleteStateCallbacks,
    private val startBulkDeleteOperation: (BulkFileOperationType, List<String>) -> Boolean,
    private val emitNativeRequest: suspend (IntentSender) -> Unit,
    private val onSuccess: () -> Unit,
    private val onFailure: () -> Unit = {}
) {
    fun requestDeleteSelected() {
        val selected = callbacks.getSelectedFiles()
        if (selected.isEmpty()) return

        coroutineScope.launch {
            callbacks.setLoading(true)
            val policyResult = evaluateDeletePolicy(selected, repository)
            val properties = repository.getSelectionProperties(selected).getOrNull()

            when (policyResult) {
                is DeletePolicyResult.MixedSelection -> {
                    callbacks.setDeleteDecision(
                        DeleteDecision(
                            destination = DeleteDestination.MixedBlocked,
                            selectedCount = selected.size,
                            totalBytes = properties?.totalBytes ?: 0L,
                            fileCount = properties?.fileCount ?: selected.size,
                            folderCount = properties?.folderCount ?: 0,
                            irreversible = true
                        )
                    )
                    callbacks.setLoading(false)
                    callbacks.showMixedDeleteExplanation()
                }
                is DeletePolicyResult.PermanentDelete -> {
                    callbacks.setDeleteDecision(
                        DeleteDecision(
                            destination = DeleteDestination.Permanent,
                            selectedCount = selected.size,
                            totalBytes = properties?.totalBytes ?: 0L,
                            fileCount = properties?.fileCount ?: selected.size,
                            folderCount = properties?.folderCount ?: 0,
                            irreversible = true
                        )
                    )
                    callbacks.setLoading(false)
                    callbacks.showPermanentDeleteConfirmation()
                }
                is DeletePolicyResult.Trash -> {
                    callbacks.setDeleteDecision(
                        DeleteDecision(
                            destination = DeleteDestination.Trash,
                            selectedCount = selected.size,
                            totalBytes = properties?.totalBytes ?: 0L,
                            fileCount = properties?.fileCount ?: selected.size,
                            folderCount = properties?.folderCount ?: 0,
                            irreversible = false
                        )
                    )
                    callbacks.setLoading(false)
                    callbacks.showTrashConfirmation()
                }
            }
        }
    }

    fun togglePermanentDelete() {
        if (callbacks.isPermanentDeleteToggleEnabled()) {
            callbacks.togglePermanentDeleteChecked()
        }
    }

    fun confirmDeleteSelected() {
        if (callbacks.isPermanentDeleteChecked()) {
            deleteSelectedPermanently()
        } else {
            moveSelectedToTrash()
        }
    }

    fun dismissDeleteConfirmation() {
        callbacks.dismissDeleteConfirmation()
    }

    fun moveSelectedToTrash() {
        val selected = callbacks.getSelectedFiles()
        if (selected.isEmpty()) return

        coroutineScope.launch {
            callbacks.setLoading(true)
            callbacks.dismissDeleteConfirmation()

            if (startBulkDeleteOperation(BulkFileOperationType.TRASH, selected)) {
                callbacks.setLoading(false)
                callbacks.clearSelection()
            } else {
                callbacks.setLoading(false)
                callbacks.setError(UiText.StringResource(R.string.error_operation_already_running))
                onFailure()
            }
        }
    }

    fun deleteSelectedPermanently() {
        val selected = callbacks.getSelectedFiles()
        if (selected.isEmpty()) return

        coroutineScope.launch {
            callbacks.setLoading(true)
            callbacks.dismissDeleteConfirmation()

            if (startBulkDeleteOperation(BulkFileOperationType.DELETE, selected)) {
                callbacks.setLoading(false)
                callbacks.clearSelection()
            } else {
                callbacks.setLoading(false)
                callbacks.setError(UiText.StringResource(R.string.error_operation_already_running))
                onFailure()
            }
        }
    }
}
