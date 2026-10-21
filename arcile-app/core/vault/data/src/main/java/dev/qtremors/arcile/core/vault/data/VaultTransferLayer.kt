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
        return runTransfer(VaultTransferAction.COPY, sources, listOf(vaultId), vaultId, destination, conflicts, cancellation) {
                sessionsById, source, resolver ->
            transferEngine.copyOne(
                requireNotNull(sessionsById[source.vaultId]),
                source,
                requireNotNull(sessionsById[vaultId]),
                destination,
                resolver,
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
        return runTransfer(VaultTransferAction.MOVE, sources, listOf(vaultId), vaultId, destination, conflicts, cancellation) {
                sessionsById, source, resolver ->
            transferEngine.moveOneWithinVault(
                requireNotNull(sessionsById[vaultId]),
                source,
                destination,
                resolver,
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
            destinationVault,
            destination,
            conflicts,
            cancellation
        ) { sessionsById, source, resolver ->
            transferEngine.copyOne(
                requireNotNull(sessionsById[source.vaultId]),
                source,
                requireNotNull(sessionsById[destinationVault]),
                destination,
                resolver,
                cancellation,
                moveSource = move
            )
        }
    }

    override suspend fun resumeMerge(
        retry: VaultMergeRetry,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultItemResult {
        val pending = retry as? PendingVaultMerge
            ?: throw VaultFailure.InvalidPath("This vault merge cannot be resumed")
        return withTransferSessions(listOf(retry.source.vaultId, retry.destinationVault)) { active ->
            val source = active.getValue(retry.source.vaultId)
            val destination = active.getValue(retry.destinationVault)
            val action = if (retry.move) VaultTransferAction.MOVE else VaultTransferAction.COPY
            transferEngine.resumeMerge(pending, source, destination, conflicts, cancellation) { count, name ->
                transferProgress.emit(VaultTransferProgress(action, name, count, retry.remainingCount, 0L, null))
            }
        }
    }

    private suspend fun runTransfer(
        action: VaultTransferAction,
        sources: List<VaultNodeRef>,
        vaultIds: List<VaultId>,
        destinationVault: VaultId,
        destination: DirectoryId,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal,
        operation: suspend (Map<VaultId, VaultSessionRecord>, VaultNodeRef, VaultConflictResolver) -> VaultItemResult
    ): VaultBatchResult = withTransferSessions(vaultIds) { operationSessions ->
        val knownConflicts = if (cancellation.isCancelled()) emptyList() else {
            try {
                transferEngine.topLevelConflicts(
                    operationSessions, sources, destinationVault, destination,
                    move = action == VaultTransferAction.MOVE, cancellation
                )
            } catch (_: VaultFailure) {
                // Individual transfers report an unreadable source or destination.
                emptyList()
            }
        }
        val resolver = conflicts.reviewed(knownConflicts, cancellation)
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
                operation(operationSessions, source, resolver)
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
    }

    private suspend fun <T> withTransferSessions(
        vaultIds: List<VaultId>,
        action: suspend (Map<VaultId, VaultSessionRecord>) -> T
    ): T = withContext(dispatchers.io) {
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
                action(operationSessions)
            } finally {
                locked.asReversed().forEach(Mutex::unlock)
                operationSessions.values.forEach(VaultSessionRecord::destroy)
            }
        }
    }
}
