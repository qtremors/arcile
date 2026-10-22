package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultOperationRecovery
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryKind
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryQueue
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

@Singleton
internal class DefaultVaultOperationRecovery @Inject constructor(
    private val repository: DefaultVaultRepository,
    private val boundary: DefaultVaultBoundaryTransferCoordinator,
    private val imports: DefaultVaultImportCoordinator
) : VaultOperationRecovery {
    override suspend fun list(vault: VaultId): Result<List<VaultRecoveryQueue>> =
        repository.listInterruptedOperations(vault)

    override suspend fun discard(queue: VaultRecoveryQueue): Result<Unit> =
        repository.discardInterruptedOperation(queue)

    override suspend fun resume(
        queue: VaultRecoveryQueue,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): Result<VaultRecoveryResult> = try {
        cancellation.throwIfCancelled()
        val plan = repository.interruptedPlan(queue).getOrThrow()
        cancellation.throwIfCancelled()
        val result = when (plan.kind) {
            VaultRecoveryKind.IMPORT -> {
                if (!imports.startRecoveredImport(queue.vaultId, queue.id)) throw VaultFailure.OperationInProgress()
                VaultRecoveryResult.ImportStarted
            }
            VaultRecoveryKind.EXPORT -> VaultRecoveryResult.Transferred(
                boundary.resumeInterruptedExport(queue, plan, conflicts, cancellation)
            )
            VaultRecoveryKind.PASTE -> VaultRecoveryResult.Transferred(
                repository.resumeInterruptedPaste(queue, plan, conflicts, cancellation)
            )
        }
        Result.success(result)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }
}
