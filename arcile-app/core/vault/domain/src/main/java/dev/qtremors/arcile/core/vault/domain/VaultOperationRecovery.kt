package dev.qtremors.arcile.core.vault.domain

enum class VaultRecoveryKind { IMPORT, EXPORT, PASTE }

data class VaultRecoveryQueue(
    val id: String,
    val vaultId: VaultId,
    val kind: VaultRecoveryKind?,
    val createdAtMillis: Long,
    val settledItems: Int,
    val remainingItems: Int,
    val hasUncertainItem: Boolean,
    val wasMove: Boolean,
    val lockedSourceVaults: Set<VaultId>
)

sealed interface VaultRecoveryResult {
    data object ImportStarted : VaultRecoveryResult
    data class Transferred(val batch: VaultBatchResult) : VaultRecoveryResult
}

interface VaultOperationRecovery {
    suspend fun list(vault: VaultId): Result<List<VaultRecoveryQueue>>
    suspend fun discard(queue: VaultRecoveryQueue): Result<Unit>
    suspend fun resume(
        queue: VaultRecoveryQueue,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): Result<VaultRecoveryResult>
}
