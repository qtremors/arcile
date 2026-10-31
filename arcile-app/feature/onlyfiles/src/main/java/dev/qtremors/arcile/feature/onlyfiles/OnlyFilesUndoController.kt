package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** One-shot snackbar action, scoped to the feedback identity and unlocked mount. */
internal class OnlyFilesUndoController(
    private val files: VaultFileSystem,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val runBusy: ((suspend () -> Unit) -> Unit),
    private val reload: () -> Unit,
    private val onFoldersDeleted: suspend (VaultId, Set<DirectoryId>) -> Unit,
    private val navigate: (DirectoryId) -> Unit = {}
) {
    fun undo(feedback: OnlyFilesOperationFeedback) {
        val snapshot = state.value
        val action = feedback.undo ?: return
        val vault = snapshot.selectedVaultId ?: return
        if (!feedback.belongsTo(snapshot) || snapshot.operationFeedback?.id != feedback.id || snapshot.busy) return
        val owner = when (action) {
            is OnlyFilesUndoAction.Created -> action.expected.ref.vaultId
            is OnlyFilesUndoAction.Renamed -> action.entries.firstOrNull()?.expected?.ref?.vaultId
            is OnlyFilesUndoAction.Moved -> action.proofs.firstOrNull()?.vaultId
        }
        if (owner != vault) return
        state.update { if (it.operationFeedback?.id == feedback.id) it.copy(operationFeedback = null) else it }
        runBusy {
            val result = when (action) {
                is OnlyFilesUndoAction.Created -> files.undoCreated(action.expected)
                is OnlyFilesUndoAction.Renamed -> files.undoRenames(action.entries)
                is OnlyFilesUndoAction.Moved -> files.undoMoves(action.proofs)
            }
            if (state.value.mountGeneration != snapshot.mountGeneration || state.value.selectedVaultId != vault) return@runBusy
            result.fold(onSuccess = { undone ->
                if (undone.removedDirectoryIds.isNotEmpty()) onFoldersDeleted(vault, undone.removedDirectoryIds)
                val ids = undone.removedNodeIds.mapTo(hashSetOf()) { it.value }
                val replacements = undone.updated.associateBy { it.ref.vaultId to it.ref.nodeId }
                state.update { current ->
                    if (current.mountGeneration != snapshot.mountGeneration || current.selectedVaultId != vault) current
                    else current.copy(
                        viewer = current.viewer?.let { replacements[it.ref.vaultId to it.ref.nodeId] ?: it },
                        viewerQueue = current.viewerQueue.map { replacements[it.ref.vaultId to it.ref.nodeId] ?: it },
                        properties = current.properties.map { replacements[it.ref.vaultId to it.ref.nodeId] ?: it }
                    ).let { updated ->
                        updated.copy(
                            selectedNodeIds = updated.selectedNodeIds - ids,
                            viewer = updated.viewer?.takeUnless { it.ref.nodeId.value in ids },
                            viewerQueue = updated.viewerQueue.filterNot { it.ref.nodeId.value in ids },
                            properties = updated.properties.filterNot { it.ref.nodeId.value in ids },
                            message = null, operationFeedback = operationSuccess(R.string.onlyfiles_undo_complete, snapshot.mountGeneration)
                                .copy(undoResult = undone)
                        )
                    }
                }
                if (state.value.mountGeneration == snapshot.mountGeneration && state.value.selectedVaultId == vault) {
                    val stack = state.value.directoryStack
                    val removedAt = stack.indexOfFirst { it.id in undone.removedDirectoryIds }
                    if (removedAt >= 0) navigate(stack.getOrNull(removedAt - 1)?.id ?: DirectoryId.Root)
                    else reload()
                }
            }, onFailure = { error ->
                state.update { current ->
                    if (current.mountGeneration != snapshot.mountGeneration || current.selectedVaultId != vault) current
                    else current.copy(message = null, operationFeedback = vaultOperationFailure(error,
                        dev.qtremors.arcile.core.ui.R.string.file_operation_undo_failed, snapshot.mountGeneration))
                }
            })
        }
    }
}
