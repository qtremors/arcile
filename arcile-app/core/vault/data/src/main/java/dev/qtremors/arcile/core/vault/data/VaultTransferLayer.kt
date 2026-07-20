package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.domain.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal abstract class VaultTransferLayer(
    context: Context,
    dispatchers: ArcileDispatchers,
    applicationScope: CoroutineScope,
    portableLocationResolver: VaultPortableLocationResolver
) : VaultSessionLayer(context, dispatchers, applicationScope, portableLocationResolver),
    VaultTransferCoordinator {
    override val progress: Flow<VaultTransferProgress> = transferProgress

    override suspend fun copyWithinVault(
        sources: List<VaultNodeRef>,
        destination: DirectoryId,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult {
        val vaultId = sources.firstOrNull()?.vaultId ?: return VaultBatchResult(emptyList())
        require(sources.all { it.vaultId == vaultId })
        return runTransfer(VaultTransferAction.COPY, sources, listOf(vaultId), cancellation) {
                sessionsById, source ->
            transferEngine.copyOne(
                requireNotNull(sessionsById[source.vaultId]),
                source,
                requireNotNull(sessionsById[vaultId]),
                destination,
                conflicts,
                cancellation
            )
        }
    }

    override suspend fun moveWithinVault(
        sources: List<VaultNodeRef>,
        destination: DirectoryId,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult {
        val vaultId = sources.firstOrNull()?.vaultId ?: return VaultBatchResult(emptyList())
        require(sources.all { it.vaultId == vaultId })
        return runTransfer(VaultTransferAction.MOVE, sources, listOf(vaultId), cancellation) {
                sessionsById, source ->
            transferEngine.moveOneWithinVault(
                requireNotNull(sessionsById[vaultId]),
                source,
                destination,
                conflicts,
                cancellation
            )
        }
    }

    override suspend fun transferAcrossVaults(
        sources: List<VaultNodeRef>,
        destinationVault: VaultId,
        destination: DirectoryId,
        move: Boolean,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult {
        require(sources.all { it.vaultId != destinationVault })
        val vaultIds = (sources.map(VaultNodeRef::vaultId) + destinationVault).distinct()
        return runTransfer(
            if (move) VaultTransferAction.MOVE else VaultTransferAction.COPY,
            sources,
            vaultIds,
            cancellation
        ) { sessionsById, source ->
            val copied = transferEngine.copyOne(
                requireNotNull(sessionsById[source.vaultId]),
                source,
                requireNotNull(sessionsById[destinationVault]),
                destination,
                conflicts,
                cancellation
            )
            if (move && copied.outcome == VaultItemOutcome.COMPLETED) {
                transferEngine.deleteOne(requireNotNull(sessionsById[source.vaultId]), source)
            }
            copied
        }
    }

    private suspend fun runTransfer(
        action: VaultTransferAction,
        sources: List<VaultNodeRef>,
        vaultIds: List<VaultId>,
        cancellation: VaultCancellationSignal,
        operation: suspend (Map<VaultId, VaultSessionRecord>, VaultNodeRef) -> VaultItemResult
    ): VaultBatchResult = withContext(dispatchers.io) {
        transferMutex.withLock {
            val operationSessions = mutableMapOf<VaultId, VaultSessionRecord>()
            val locked = mutableListOf<Mutex>()
            try {
                vaultIds.distinct().forEach { id ->
                    val interactive = sessions[id.value] ?: throw VaultFailure.Locked(id)
                    operationSessions[id] = interactive.copyForOperation()
                }
                operationSessions.entries.sortedBy { it.key.value }.forEach { (_, session) ->
                    session.mutationMutex.lock()
                    locked += session.mutationMutex
                }
                val results = mutableListOf<VaultItemResult>()
                for ((index, source) in sources.withIndex()) {
                    if (cancellation.isCancelled()) {
                        results += sources.drop(index).map {
                            VaultItemResult(
                                "${it.vaultId.value}:${it.nodeId.value}",
                                "",
                                VaultItemOutcome.ROLLED_BACK,
                                VaultFailure.Cancelled()
                            )
                        }
                        break
                    }
                    transferProgress.emit(
                        VaultTransferProgress(action, null, index, sources.size, 0L, null)
                    )
                    val result = try {
                        operation(operationSessions, source)
                    } catch (error: Throwable) {
                        if (error is CancellationException) throw error
                        VaultItemResult(
                            "${source.vaultId.value}:${source.nodeId.value}",
                            "",
                            if (error is VaultFailure.Cancelled) {
                                VaultItemOutcome.ROLLED_BACK
                            } else {
                                VaultItemOutcome.FAILED
                            },
                            error as? VaultFailure
                                ?: VaultFailure.Unavailable("Vault transfer failed", error)
                        )
                    }
                    results += result
                    transferProgress.emit(
                        VaultTransferProgress(
                            action,
                            result.displayName,
                            index + 1,
                            sources.size,
                            0L,
                            null
                        )
                    )
                    if (result.failure is VaultFailure.Cancelled) break
                }
                VaultBatchResult(results)
            } finally {
                locked.asReversed().forEach(Mutex::unlock)
                operationSessions.values.forEach(VaultSessionRecord::destroy)
            }
        }
    }
}
