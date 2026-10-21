package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultTransferCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnlyFilesTransferController(
    private val coordinator: VaultTransferCoordinator,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val reload: () -> Unit,
    private val onFoldersDeleted: suspend (VaultId, Set<DirectoryId>) -> Unit = { _, _ -> }
) {
    private var cancelled: AtomicBoolean? = null
    val conflictReview = OnlyFilesConflictReview(state)

    fun setClipboard(action: VaultClipboardAction, nodes: List<VaultNodeMetadata>) {
        val refs = nodes.map(VaultNodeMetadata::ref).distinctBy(VaultNodeRef::nodeId)
        if (refs.isEmpty()) return
        state.update {
            it.copy(
                clipboard = VaultClipboard(action, refs, nodes.distinctBy { node -> node.ref.nodeId }),
                selectedNodeIds = emptySet(),
                message = "${refs.size} item(s) ready to ${action.name.lowercase()}"
            )
        }
    }

    fun paste() {
        val snapshot = state.value
        val clipboard = snapshot.clipboard ?: return
        val destinationVault = snapshot.selectedVaultId ?: return
        val destination = snapshot.currentDirectory?.id ?: return
        if (snapshot.busy || cancelled != null || clipboard.sources.isEmpty()) return
        val signal = AtomicBoolean(false)
        val retries = clipboard.retriesAt(destinationVault, destination)
        val retryIds = retries.mapTo(mutableSetOf()) { it.source.backendIdentity }
        val freshSources = clipboard.sources.filterNot { it.backendIdentity in retryIds }
        cancelled = signal
        scope.launch {
            state.update { current ->
                current.copy(
                    busy = true, message = null, transferMountGeneration = snapshot.mountGeneration,
                    pasteQueueNames = clipboard.displayNodes.map(VaultNodeMetadata::name)
                        .takeIf { retries.isEmpty() && current.mountGeneration == snapshot.mountGeneration }.orEmpty()
                )
            }
            val conflicts = conflictReview.resolver(snapshot.mountGeneration, exporting = false, signal::get)
            val cancellation = VaultCancellationSignal(signal::get)
            val move = clipboard.action == VaultClipboardAction.MOVE
            try {
                val fresh = when {
                    freshSources.isEmpty() -> emptyList()
                    freshSources.all { it.vaultId == destinationVault } -> {
                        if (move) coordinator.moveWithinVault(freshSources, destination, conflicts, cancellation).items
                        else coordinator.copyWithinVault(freshSources, destination, conflicts, cancellation).items
                    }
                    else -> coordinator.transferAcrossVaults(freshSources, destinationVault, destination, move, conflicts, cancellation).items
                }
                val resumed = retries.map { retry ->
                    try {
                        coordinator.resumeMerge(retry, conflicts, cancellation)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        VaultItemResult(
                            retry.source.backendIdentity, "", VaultItemOutcome.PARTIAL,
                            error as? VaultFailure ?: VaultFailure.Unavailable("The vault merge could not be resumed", error),
                            mergeRetry = retry
                        )
                    }
                }
                complete(VaultBatchResult(fresh + resumed), clipboard, snapshot.mountGeneration)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    if (it.mountGeneration == snapshot.mountGeneration) {
                        it.copy(message = error.message ?: "Vault paste failed")
                    } else it
                }
            } finally {
                cancelled = null
                conflictReview.dismiss()
                state.update {
                    it.copy(
                        busy = false, pendingConflict = null, transferProgress = null,
                        transferMountGeneration = null, pasteQueueNames = emptyList()
                    )
                }
            }
        }
    }

    fun cancel() {
        cancelled?.set(true)
        conflictReview.dismiss()
    }

    fun clear() {
        cancelled?.set(true)
        conflictReview.dismiss()
    }

    private suspend fun complete(result: VaultBatchResult, submittedClipboard: VaultClipboard, mountGeneration: Long) {
        val sourceVaults = submittedClipboard.sources.associate { it.backendIdentity to it.vaultId }
        val removedByVault = mutableMapOf<VaultId, MutableSet<DirectoryId>>()
        for (item in result.items) {
            if (item.removedFolders.isEmpty()) continue
            val vault = sourceVaults[item.sourceIdentity] ?: continue
            removedByVault.getOrPut(vault) { mutableSetOf() }.addAll(item.removedFolders)
        }
        for ((vault, folders) in removedByVault) onFoldersDeleted(vault, folders)
        val completedIds = result.completed.mapTo(mutableSetOf()) { it.sourceIdentity }
        val completed = completedIds.size
        val partial = result.items.count { it.outcome == VaultItemOutcome.PARTIAL }
        val skipped = result.items.count { it.outcome == VaultItemOutcome.SKIPPED }
        val failed = result.items.count { it.outcome == VaultItemOutcome.FAILED || it.outcome == VaultItemOutcome.ROLLED_BACK }
        val retryFailure = result.partial.firstNotNullOfOrNull { it.failure?.message }
        state.update {
            if (it.mountGeneration != mountGeneration) return@update it
            val remainingClipboard = if (it.clipboard === submittedClipboard) {
                val retries = submittedClipboard.mergeRetries.toMutableMap()
                for (item in result.items) {
                    val retry = item.mergeRetry
                    if (retry != null) retries[item.sourceIdentity] = retry
                    else if (item.outcome == VaultItemOutcome.COMPLETED || item.outcome == VaultItemOutcome.PARTIAL) {
                        retries.remove(item.sourceIdentity)
                    }
                }
                submittedClipboard.copy(
                    sources = submittedClipboard.sources.filterNot { ref -> ref.backendIdentity in completedIds },
                    displayNodes = submittedClipboard.displayNodes.filterNot { node -> node.ref.backendIdentity in completedIds },
                    mergeRetries = retries
                ).takeIf { clipboard -> clipboard.sources.isNotEmpty() }
            } else it.clipboard
            it.copy(
                clipboard = remainingClipboard,
                message = if (partial > 0) {
                    val summary = "$completed completed, $partial partially completed, $skipped skipped, $failed failed."
                    "$summary ${retryFailure ?: "Skipped files remain at the source."}"
                } else {
                    "$completed completed, $skipped skipped, $failed failed"
                }
            )
        }
        if (state.value.mountGeneration == mountGeneration) reload()
    }
}
