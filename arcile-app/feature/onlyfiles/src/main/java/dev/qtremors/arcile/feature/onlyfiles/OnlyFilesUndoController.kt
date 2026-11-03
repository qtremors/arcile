package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultCreatedUndo
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultUndoOrigin
import dev.qtremors.arcile.core.vault.domain.VaultUndoResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** One-shot snackbar action, scoped to the feedback identity and unlocked mount. */
internal class OnlyFilesUndoController(
    private val files: VaultFileSystem,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val runBusy: ((suspend () -> Unit) -> Unit),
    private val reload: () -> Unit,
    private val onFoldersDeleted: suspend (VaultId, Set<DirectoryId>) -> Unit,
    private val navigate: (DirectoryId) -> Unit = {},
    private val transfers: VaultTransferCoordinator? = null,
    private val boundary: VaultBoundaryTransferCoordinator? = null
) {
    /**
     * Undo that writes or deletes plaintext opens a confirmation first. Confirming runs the same feedback
     * once, even though opening the dialog dismissed its snackbar.
     */
    fun undo(feedback: OnlyFilesOperationFeedback, confirmed: Boolean = false) {
        val snapshot = state.value
        val action = feedback.undo ?: return
        val vault = snapshot.selectedVaultId ?: return
        if (!feedback.belongsTo(snapshot) || snapshot.busy) return
        val current = if (confirmed) snapshot.pendingUndoConfirmation?.id == feedback.id
            else snapshot.operationFeedback?.id == feedback.id
        if (!current || action.ownerVault != vault) return
        if (action.requiresConfirmation && !confirmed) {
            state.update {
                if (it.operationFeedback?.id == feedback.id) it.copy(operationFeedback = null, pendingUndoConfirmation = feedback) else it
            }
            return
        }
        state.update {
            if (it.operationFeedback?.id == feedback.id || it.pendingUndoConfirmation?.id == feedback.id) {
                it.copy(operationFeedback = null, pendingUndoConfirmation = null)
            } else it
        }
        runBusy {
            val result = runCatching { execute(action) }.fold({ it }, { Result.failure(it) })
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
                // Multi-step reversals may have restored some items before failing, so show the current state.
                if (action.isMultiStep && state.value.mountGeneration == snapshot.mountGeneration) reload()
            })
        }
    }

    fun cancelConfirmation(feedbackId: String) = state.update {
        if (it.pendingUndoConfirmation?.id == feedbackId) it.copy(pendingUndoConfirmation = null) else it
    }

    private suspend fun execute(action: OnlyFilesUndoAction): Result<VaultUndoResult> = when (action) {
        is OnlyFilesUndoAction.Created -> files.undoCreated(action.expected)
        is OnlyFilesUndoAction.Renamed -> files.undoRenames(action.entries)
        is OnlyFilesUndoAction.Moved -> files.undoMoves(action.proofs)
        is OnlyFilesUndoAction.Copied -> files.undoCreatedCopies(action.proofs)
        is OnlyFilesUndoAction.MovedAcrossVaults -> runCatching { moveBackAcrossVaults(action.proofs) }
        is OnlyFilesUndoAction.MovedIn -> runCatching { moveBackToLocal(action.proofs) }
        is OnlyFilesUndoAction.Plaintext -> runCatching {
            val coordinator = boundary ?: throw VaultFailure.Unavailable("Undo is unavailable")
            coordinator.undoPlaintext(action.proofs, NEVER_CANCELLED).requireCompleted()
            VaultUndoResult()
        }
    }

    /** Reuses verified cross-vault moves. The original vault must still be unlocked. */
    private suspend fun moveBackAcrossVaults(proofs: List<VaultCreatedUndo>): VaultUndoResult {
        val coordinator = transfers ?: throw VaultFailure.Unavailable("Undo is unavailable")
        files.verifyCreated(proofs).getOrThrow()
        val removedFolders = linkedSetOf<DirectoryId>()
        val groups = proofs.groupBy { proof ->
            val origin = proof.origin as VaultUndoOrigin.VaultFolder
            origin.vaultId to origin.parentId
        }
        for ((target, group) in groups) {
            val result = coordinator.transferAcrossVaults(
                group.map { it.expected.ref }, target.first, target.second, move = true, NO_REPLACEMENT, NEVER_CANCELLED
            )
            result.items.forEach { removedFolders += it.removedFolders }
            result.requireCompleted()
            // A Keep both name chosen on the way in is returned to the item's original name when it is free.
            for ((proof, item) in group.zip(result.items)) {
                val origin = proof.origin as VaultUndoOrigin.VaultFolder
                val restored = item.createdUndo?.expected ?: continue
                if (restored.name != origin.name) files.rename(restored.ref, origin.name)
            }
        }
        return VaultUndoResult(removedNodeIds = proofs.mapTo(linkedSetOf()) { it.expected.ref.nodeId }, removedDirectoryIds = removedFolders)
    }

    /** Decrypts back through the verified export path, which removes the vault copy only after publication. */
    private suspend fun moveBackToLocal(proofs: List<VaultCreatedUndo>): VaultUndoResult {
        val coordinator = boundary ?: throw VaultFailure.Unavailable("Undo is unavailable")
        files.verifyCreated(proofs).getOrThrow()
        val removedFolders = linkedSetOf<DirectoryId>()
        for ((parentPath, group) in proofs.groupBy { (it.origin as VaultUndoOrigin.LocalFolder).parentPath }) {
            val result = coordinator.prepareExport(group.map { it.expected.ref }).getOrThrow().use { reservation ->
                coordinator.exportToDestination(reservation, parentPath, move = true, NO_REPLACEMENT, NEVER_CANCELLED)
            }
            result.items.forEach { removedFolders += it.removedFolders }
            result.requireCompleted()
            // An import renamed for Keep both returns under its original local name when that name is free.
            val renames = group.mapNotNull { proof ->
                val origin = proof.origin as VaultUndoOrigin.LocalFolder
                (proof.expected.name to origin.name).takeIf { it.first != it.second }
            }
            if (renames.isNotEmpty()) withContext(Dispatchers.IO) {
                for ((exportedName, originalName) in renames) {
                    val original = File(parentPath, originalName)
                    if (!original.exists()) File(parentPath, exportedName).renameTo(original)
                }
            }
        }
        return VaultUndoResult(removedNodeIds = proofs.mapTo(linkedSetOf()) { it.expected.ref.nodeId }, removedDirectoryIds = removedFolders)
    }

    private val OnlyFilesUndoAction.isMultiStep: Boolean
        get() = this is OnlyFilesUndoAction.MovedAcrossVaults || this is OnlyFilesUndoAction.MovedIn ||
            this is OnlyFilesUndoAction.Plaintext

    private fun VaultBatchResult.requireCompleted() {
        val incomplete = items.firstOrNull { it.outcome != VaultItemOutcome.COMPLETED } ?: return
        throw incomplete.failure ?: if (incomplete.outcome == VaultItemOutcome.SKIPPED) {
            VaultFailure.NameConflict(incomplete.displayName)
        } else VaultFailure.Unavailable("Undo did not complete")
    }

    private companion object {
        val NEVER_CANCELLED = VaultCancellationSignal { false }
        /** Undo never overwrites, merges or renames into an occupied original location. */
        val NO_REPLACEMENT = VaultConflictResolver { VaultConflictDecision.SKIP }
    }
}
