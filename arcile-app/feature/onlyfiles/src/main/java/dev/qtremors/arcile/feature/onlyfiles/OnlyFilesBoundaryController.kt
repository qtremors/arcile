package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnlyFilesBoundaryController(
    private val coordinator: VaultBoundaryTransferCoordinator,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val reload: () -> Unit
) {
    private var cancellation: AtomicBoolean? = null
    private var answer: CompletableDeferred<Pair<VaultConflictDecision, Boolean>>? = null
    private var requestId = 100_000L

    fun start(nodes: List<VaultNodeMetadata>, destinationTreeUri: String, move: Boolean) {
        val sources = nodes.map(VaultNodeMetadata::ref).distinctBy { it.backendIdentity }
        if (sources.isEmpty() || state.value.busy) return
        val cancelled = AtomicBoolean(false)
        cancellation = cancelled
        scope.launch {
            state.update { it.copy(busy = true, selectedNodeIds = emptySet(), message = null) }
            var applyAll: VaultConflictDecision? = null
            val conflicts = VaultConflictResolver { conflict ->
                applyAll ?: awaitDecision(conflict).also { if (it.second) applyAll = it.first }.first
            }
            val result = try {
                coordinator.exportToDocumentTree(sources, destinationTreeUri, move, conflicts, VaultCancellationSignal(cancelled::get))
            } finally {
                cancellation = null
                answer?.cancel(); answer = null
                state.update { it.copy(busy = false, pendingConflict = null, transferProgress = null) }
            }
            val completed = result.items.count { it.outcome == VaultItemOutcome.COMPLETED }
            val skipped = result.items.count { it.outcome == VaultItemOutcome.SKIPPED }
            val failed = result.items.size - completed - skipped
            state.update { it.copy(message = "$completed exported, $skipped skipped, $failed failed") }
            if (move && completed > 0) reload()
        }
    }

    fun cancel() {
        cancellation?.set(true)
        answer?.complete(VaultConflictDecision.SKIP to true)
    }

    fun resolve(decision: VaultConflictDecision, applyToAll: Boolean) {
        answer?.complete(decision to applyToAll)
    }

    fun clear() {
        cancellation?.set(true)
        answer?.cancel(); answer = null
    }

    private suspend fun awaitDecision(conflict: VaultConflict): Pair<VaultConflictDecision, Boolean> {
        val pending = CompletableDeferred<Pair<VaultConflictDecision, Boolean>>()
        answer = pending
        state.update { it.copy(pendingConflict = VaultConflictPrompt(++requestId, conflict)) }
        return pending.await().also {
            answer = null
            state.update { current -> current.copy(pendingConflict = null) }
        }
    }
}
