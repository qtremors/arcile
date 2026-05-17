package dev.qtremors.arcile.presentation.delegate

import android.content.IntentSender
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.evaluateDeletePolicy
import dev.qtremors.arcile.domain.DeletePolicyResult
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
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

            when (policyResult) {
                is DeletePolicyResult.MixedSelection -> {
                    callbacks.setLoading(false)
                    callbacks.showMixedDeleteExplanation()
                }
                is DeletePolicyResult.PermanentDelete -> {
                    callbacks.setLoading(false)
                    callbacks.showPermanentDeleteConfirmation()
                }
                is DeletePolicyResult.Trash -> {
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
                callbacks.setError("Another file operation is already running")
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
                callbacks.setError("Another file operation is already running")
                onFailure()
            }
        }
    }
}
