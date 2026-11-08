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
        return runTransfer(sources, vaultId, destination, move = false, conflicts, cancellation)
    }

    override suspend fun moveWithinVault(
        sources: List<VaultNodeRef>,
        destination: DirectoryId,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult {
        val vaultId = sources.firstOrNull()?.vaultId ?: return VaultBatchResult(emptyList())
        require(sources.all { it.vaultId == vaultId })
        return runTransfer(sources, vaultId, destination, move = true, conflicts, cancellation)
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
        return runTransfer(sources, destinationVault, destination, move, conflicts, cancellation)
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
            val origin = pending.origin
            if (origin != null) {
                if (origin.ownerMount !== destination.mutationMutex || pending.move && origin.sourceMount !== source.mutationMutex) {
                    throw VaultFailure.Unavailable("The original folder retry expired. Reopen interrupted transfers to resume as a copy.")
                }
                operationJournal.resume(destination, origin.queueId, VaultRecoveryKind.PASTE).use { queue ->
                    val saved = queue.pendingRetries().find { it.first == origin.index }?.second?.mergeRetry as? PendingVaultMerge
                        ?: throw VaultFailure.Unavailable("This folder retry is no longer available")
                    val current = saved.copy(move = pending.move, cleanupPaths = pending.cleanupPaths)
                    val progress = queue.retryProgress(origin.index)
                    val result = transferEngine.resumeMerge(current, source, destination, conflicts, cancellation,
                        onPublication = { child, receipt -> progress.prepare(VaultRetryPublication.merge(child, receipt)) },
                        onPublished = progress::published) { count, name ->
                        transferProgress.emit(VaultTransferProgress(action, name, count, current.remainingCount, 0L, null))
                    }
                    return@withTransferSessions queue.bindRetry(origin.index, queue.finishRetry(origin.index, result), source.mutationMutex)
                        .also { queue.finishQueue() }
                }
            }
            transferEngine.resumeMerge(pending, source, destination, conflicts, cancellation) { count, name ->
                transferProgress.emit(VaultTransferProgress(action, name, count, retry.remainingCount, 0L, null))
            }
        }
    }

    internal suspend fun resumeInterruptedPaste(
        queue: VaultRecoveryQueue,
        plan: VaultQueuePlan,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult = runTransfer(
        plan.nodes.map(QueuedVaultNode::ref), queue.vaultId, DirectoryId.of(plan.destination),
        move = false, conflicts, cancellation, recoveryId = queue.id
    )

    private suspend fun runTransfer(
        sources: List<VaultNodeRef>,
        destinationVault: VaultId,
        destination: DirectoryId,
        move: Boolean,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal,
        recoveryId: String? = null
    ): VaultBatchResult {
        if (sources.isEmpty()) return VaultBatchResult(emptyList())
        val vaults = sources.map(VaultNodeRef::vaultId) + destinationVault
        return withTransferSessions(vaults) { active ->
            val target = active.getValue(destinationVault)
            val queue = if (recoveryId != null) {
                operationJournal.resume(target, recoveryId, VaultRecoveryKind.PASTE)
            } else {
                operationJournal.start(target, VaultQueuePlan(
                    VaultRecoveryKind.PASTE, destination.value, move, nodes = sources.map(QueuedVaultNode::from), publicationReceipts = true
                ))
            }
            queue.use {
                val result = transferItems(active, target, destination, move, conflicts, cancellation, queue)
                queue.finishQueue()
                result
            }
        }
    }

    private suspend fun transferItems(
        operationSessions: Map<VaultId, VaultSessionRecord>,
        target: VaultSessionRecord,
        destination: DirectoryId,
        move: Boolean,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal,
        queue: VaultQueueRun
    ): VaultBatchResult {
        val action = if (move) VaultTransferAction.MOVE else VaultTransferAction.COPY
        val start = queue.startIndex
        val sources = queue.plan.nodes.drop(start).map(QueuedVaultNode::ref)
        val knownConflicts = if (cancellation.isCancelled()) emptyList() else {
            try {
                transferEngine.topLevelConflicts(
                    operationSessions, sources, target.id, destination, move, cancellation
                )
            } catch (_: VaultFailure) {
                // Individual transfers report an unreadable source or destination.
                emptyList()
            }
        }
        val resolver = conflicts.reviewed(knownConflicts, cancellation)
        val results = mutableListOf<VaultItemResult>()
        for ((index, saved) in queue.pendingRetries()) {
            cancellation.throwIfCancelled()
            val retry = saved.mergeRetry as? PendingVaultMerge
                ?: throw VaultFailure.InvalidPath("This checkpoint does not describe a vault paste")
            val progress = queue.retryProgress(index)
            val result = transferEngine.resumeMerge(
                retry, operationSessions.getValue(retry.source.vaultId), target, conflicts, cancellation,
                onPublication = { child, receipt -> progress.prepare(VaultRetryPublication.merge(child, receipt)) },
                onPublished = progress::published
            ) { count, name ->
                transferProgress.emit(VaultTransferProgress(VaultTransferAction.COPY, name, count, retry.remainingCount, 0L, null))
            }
            results += queue.finishRetry(index, result)
        }
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
            queue.beginItem(start + index)
            val result = try {
                val origin = operationSessions.getValue(source.vaultId)
                if (move && origin.id == target.id) {
                    transferEngine.moveOneWithinVault(origin, source, destination, resolver, cancellation) {
                        queue.preparePublication(start + index, it)
                    }
                } else {
                    transferEngine.copyOne(origin, source, target, destination, resolver, cancellation, moveSource = move) {
                        queue.preparePublication(start + index, it)
                    }
                }
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
            queue.finishItem(start + index, result)
            results += queue.bindRetry(start + index, result, operationSessions.getValue(source.vaultId).mutationMutex)
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
        return VaultBatchResult(results)
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
