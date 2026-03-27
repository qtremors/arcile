package dev.qtremors.arcile.presentation.delegate

import android.content.IntentSender
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.NativeConfirmationRequiredException
import dev.qtremors.arcile.domain.evaluateDeletePolicy
import dev.qtremors.arcile.domain.DeletePolicyResult
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
    private val executeMoveToTrash: suspend (List<String>) -> Result<Unit>,
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
            
            executeMoveToTrash(selected).onSuccess {
                callbacks.clearSelection()
                onSuccess()
            }.onFailure { error ->
                if (error is NativeConfirmationRequiredException) {
                    callbacks.setLoading(false)
                    callbacks.setPendingNativeAction()
                    emitNativeRequest(error.intentSender)
                } else {
                    callbacks.setLoading(false)
                    callbacks.setError(error.message ?: "Failed to move files to Trash")
                    onFailure()
                }
            }
        }
    }

    fun deleteSelectedPermanently() {
        val selected = callbacks.getSelectedFiles()
        if (selected.isEmpty()) return

        coroutineScope.launch {
            callbacks.setLoading(true)
            callbacks.dismissDeleteConfirmation()
            
            repository.deletePermanently(selected).onSuccess {
                callbacks.clearSelection()
                onSuccess()
            }.onFailure { error ->
                callbacks.setLoading(false)
                callbacks.setError(error.message ?: "Failed to delete files")
                onFailure()
            }
        }
    }
}
