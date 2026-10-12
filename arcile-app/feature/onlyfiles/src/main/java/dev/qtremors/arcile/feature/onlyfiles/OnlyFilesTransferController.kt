package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultTransferCoordinator
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnlyFilesTransferController(
    private val coordinator: VaultTransferCoordinator,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val reload: () -> Unit
) {
    private var cancelled: AtomicBoolean? = null
    private var conflictRequestId = 0L
    private var conflictAnswer: CompletableDeferred<Pair<VaultConflictDecision, Boolean>>? = null

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
        cancelled = signal
        scope.launch {
            state.update { current ->
                current.copy(
                    busy = true, message = null, transferMountGeneration = snapshot.mountGeneration,
                    pasteQueueNames = clipboard.displayNodes.map(VaultNodeMetadata::name)
                        .takeIf { current.mountGeneration == snapshot.mountGeneration }.orEmpty()
                )
            }
            var applyDecision: VaultConflictDecision? = null
            val conflicts = VaultConflictResolver { conflict ->
                    applyDecision ?: awaitConflict(conflict, snapshot.mountGeneration).also { answer ->
                    if (answer.second) applyDecision = answer.first
                }.first
            }
            val cancellation = VaultCancellationSignal(signal::get)
            val move = clipboard.action == VaultClipboardAction.MOVE
            val result = try {
                if (clipboard.sources.all { it.vaultId == destinationVault }) {
                    if (move) coordinator.moveWithinVault(clipboard.sources, destination, conflicts, cancellation)
                    else coordinator.copyWithinVault(clipboard.sources, destination, conflicts, cancellation)
                } else {
                    coordinator.transferAcrossVaults(clipboard.sources, destinationVault, destination, move, conflicts, cancellation)
                }
            } finally {
                cancelled = null
                conflictAnswer?.cancel()
                conflictAnswer = null
                state.update {
                    it.copy(
                        busy = false, pendingConflict = null, transferProgress = null,
                        transferMountGeneration = null, pasteQueueNames = emptyList()
                    )
                }
            }
            complete(result, clipboard, snapshot.mountGeneration)
        }
    }

    fun cancel() {
        cancelled?.set(true)
        conflictAnswer?.complete(VaultConflictDecision.SKIP to true)
    }

    fun resolveConflict(decision: VaultConflictDecision, applyToAll: Boolean) {
        conflictAnswer?.complete(decision to applyToAll)
    }

    fun clear() {
        cancelled?.set(true)
        conflictAnswer?.cancel()
        conflictAnswer = null
    }

    private suspend fun awaitConflict(conflict: VaultConflict, mountGeneration: Long): Pair<VaultConflictDecision, Boolean> {
        if (state.value.mountGeneration != mountGeneration) return VaultConflictDecision.SKIP to true
        val answer = CompletableDeferred<Pair<VaultConflictDecision, Boolean>>()
        conflictAnswer = answer
        state.update { it.copy(pendingConflict = VaultConflictPrompt(++conflictRequestId, conflict)) }
        return answer.await().also {
            conflictAnswer = null
            state.update { current -> current.copy(pendingConflict = null) }
        }
    }

    private fun complete(result: VaultBatchResult, submittedClipboard: VaultClipboard, mountGeneration: Long) {
        val completedIds = result.completed.mapTo(mutableSetOf()) { it.sourceIdentity }
        val completed = completedIds.size
        val partial = result.items.count { it.outcome == VaultItemOutcome.PARTIAL }
        val skipped = result.items.count { it.outcome == VaultItemOutcome.SKIPPED }
        val failed = result.items.count { it.outcome == VaultItemOutcome.FAILED || it.outcome == VaultItemOutcome.ROLLED_BACK }
        state.update {
            if (it.mountGeneration != mountGeneration) return@update it
            val remainingClipboard = if (it.clipboard === submittedClipboard) {
                submittedClipboard.copy(
                    sources = submittedClipboard.sources.filterNot { ref -> ref.backendIdentity in completedIds },
                    displayNodes = submittedClipboard.displayNodes.filterNot { node -> node.ref.backendIdentity in completedIds }
                ).takeIf { clipboard -> clipboard.sources.isNotEmpty() }
            } else it.clipboard
            it.copy(
                clipboard = remainingClipboard,
                message = if (partial > 0) {
                    "$completed completed, $partial partially completed, $skipped skipped, $failed failed. Skipped files remain at the source."
                } else {
                    "$completed completed, $skipped skipped, $failed failed"
                }
            )
        }
        if (state.value.mountGeneration == mountGeneration) reload()
    }
}
