package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal data class VaultConflictResponse(
    val decisions: Map<Int, VaultConflictDecision> = emptyMap(),
    val futureDecision: VaultConflictDecision? = null
)

internal fun VaultConflictDecision.canApplyTo(original: VaultConflict, candidate: VaultConflict): Boolean =
    when (this) {
        VaultConflictDecision.MERGE_DIRECTORIES -> candidate.canMerge
        VaultConflictDecision.REPLACE -> original.sourceIsDirectory == candidate.sourceIsDirectory &&
            original.destinationIsDirectory == candidate.destinationIsDirectory
        else -> true
    }

internal class OnlyFilesConflictReview(private val state: MutableStateFlow<OnlyFilesUiState>) {
    private val pending = AtomicReference<PendingReview?>()

    fun resolver(mount: Long, exporting: Boolean, isCancelled: () -> Boolean): VaultConflictResolver = object : VaultConflictResolver {
        private var futureDecision: Pair<VaultConflict, VaultConflictDecision>? = null

        override suspend fun review(conflicts: List<VaultConflict>): Map<VaultConflict, VaultConflictDecision> {
            val response = ask(conflicts, mount, exporting, allowFuture = false, isCancelled)
            return conflicts.mapIndexed { index, conflict -> conflict to response.decision(index, conflict) }.toMap()
        }

        override suspend fun decide(conflict: VaultConflict): VaultConflictDecision {
            if (!state.value.isMounted(mount)) return VaultConflictDecision.SKIP
            futureDecision?.let { (original, decision) ->
                if (decision.canApplyTo(original, conflict)) return decision
            }
            val response = ask(listOf(conflict), mount, exporting, allowFuture = true, isCancelled)
            response.futureDecision?.takeIf { it.isCompatible(conflict) }?.let { futureDecision = conflict to it }
            return response.decision(0, conflict)
        }
    }

    fun resolve(id: Long, response: VaultConflictResponse) {
        pending.get()?.takeIf { it.id == id }?.answer?.complete(response)
    }

    fun dismiss() {
        pending.get()?.answer?.complete(VaultConflictResponse())
    }

    private suspend fun ask(
        conflicts: List<VaultConflict>,
        mount: Long,
        exporting: Boolean,
        allowFuture: Boolean,
        isCancelled: () -> Boolean
    ): VaultConflictResponse {
        val current = state.value
        if (conflicts.isEmpty() || !current.isMounted(mount) || isCancelled()) {
            return VaultConflictResponse()
        }
        val request = PendingReview(ids.incrementAndGet(), CompletableDeferred())
        pending.set(request)
        state.update {
            if (it.isMounted(mount)) it.copy(
                pendingConflict = VaultConflictPrompt(request.id, conflicts, allowFuture, exporting)
            ) else it
        }
        if (!state.value.isMounted(mount) || isCancelled()) request.answer.complete(VaultConflictResponse())
        return try {
            request.answer.await()
        } finally {
            pending.compareAndSet(request, null)
            state.update { if (it.pendingConflict?.requestId == request.id) it.copy(pendingConflict = null) else it }
        }
    }

    private fun VaultConflictResponse.decision(index: Int, conflict: VaultConflict): VaultConflictDecision =
        decisions[index]?.takeIf { it.isCompatible(conflict) } ?: VaultConflictDecision.SKIP

    private fun VaultConflictDecision.isCompatible(conflict: VaultConflict): Boolean =
        this != VaultConflictDecision.MERGE_DIRECTORIES || conflict.canMerge

    private fun OnlyFilesUiState.isMounted(mount: Long) = mountGeneration == mount && selectedVaultId != null

    private data class PendingReview(val id: Long, val answer: CompletableDeferred<VaultConflictResponse>)

    private companion object { val ids = AtomicLong() }
}
