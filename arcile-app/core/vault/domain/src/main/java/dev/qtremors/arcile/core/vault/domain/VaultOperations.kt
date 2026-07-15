package dev.qtremors.arcile.core.vault.domain

import kotlinx.coroutines.flow.Flow
import java.io.Closeable

enum class VaultLeasePurpose { INTERACTIVE, TRANSFER, EXTERNAL_ACCESS }

interface VaultKeyLease : Closeable {
    val vaultId: VaultId
    val purpose: VaultLeasePurpose
    val isClosed: Boolean
}

data class VaultUnlockOptions(
    val password: CharArray? = null,
    val allowBiometric: Boolean = false
)

interface VaultSessionManager {
    suspend fun unlock(vaultId: VaultId, options: VaultUnlockOptions): Result<Unit>
    suspend fun lockInteractive(vaultId: VaultId)
    suspend fun lockAllInteractive()
    fun acquireLease(vaultId: VaultId, purpose: VaultLeasePurpose): Result<VaultKeyLease>
    suspend fun changePassword(
        vaultId: VaultId,
        currentPassword: CharArray,
        newPassword: CharArray,
        weakPasswordConfirmed: Boolean
    ): Result<Unit>
    suspend fun enrollBiometric(vaultId: VaultId, password: CharArray): Result<Unit>
    suspend fun removeBiometric(vaultId: VaultId): Result<Unit>
}

interface VaultSeekableReader : Closeable {
    val sizeBytes: Long
    fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int
}

interface VaultStreamingWriter : Closeable {
    val bytesWritten: Long
    fun write(source: ByteArray, offset: Int = 0, length: Int = source.size)
    fun commit(): Result<VaultNodeMetadata>
    fun cancel()
}

enum class VaultConflictDecision { KEEP_BOTH, REPLACE, SKIP, MERGE_DIRECTORIES }

fun interface VaultConflictResolver {
    suspend fun decide(conflict: VaultConflict): VaultConflictDecision
}

data class VaultConflict(
    val sourceName: String,
    val destinationName: String,
    val sourceIsDirectory: Boolean,
    val destinationIsDirectory: Boolean
)

enum class VaultTransferAction { COPY, MOVE, IMPORT, EXPORT, SHARE, OPEN_WITH }
enum class VaultItemOutcome { COMPLETED, SKIPPED, FAILED, ROLLED_BACK }

data class VaultItemResult(
    val sourceIdentity: String,
    val displayName: String,
    val outcome: VaultItemOutcome,
    val failure: VaultFailure? = null
)

data class VaultBatchResult(val items: List<VaultItemResult>) {
    val completed: List<VaultItemResult> get() = items.filter { it.outcome == VaultItemOutcome.COMPLETED }
    val skipped: List<VaultItemResult> get() = items.filter { it.outcome == VaultItemOutcome.SKIPPED }
    val failed: List<VaultItemResult> get() = items.filter { it.outcome == VaultItemOutcome.FAILED }
    val rolledBack: List<VaultItemResult> get() = items.filter { it.outcome == VaultItemOutcome.ROLLED_BACK }
}

data class VaultTransferProgress(
    val action: VaultTransferAction,
    val currentName: String?,
    val completedTopLevelItems: Int,
    val totalTopLevelItems: Int,
    val bytesTransferred: Long,
    val totalBytes: Long?
)

fun interface VaultCancellationSignal {
    fun isCancelled(): Boolean
}

data class VaultImportProgress(
    val completedItems: Int,
    val totalItems: Int,
    val bytesCopied: Long,
    val totalBytes: Long?,
    val currentName: String?
)

data class VaultImportState(
    val vaultId: VaultId,
    val progress: VaultImportProgress? = null,
    val isCancelling: Boolean = false
)

sealed interface VaultImportEvent {
    data class Started(val vaultId: VaultId) : VaultImportEvent
    data class Progress(val vaultId: VaultId, val value: VaultImportProgress) : VaultImportEvent
    data class Completed(val vaultId: VaultId) : VaultImportEvent
    data class Partial(val vaultId: VaultId, val result: VaultBatchResult) : VaultImportEvent
    data class Failed(val vaultId: VaultId, val message: String) : VaultImportEvent
    data class Cancelled(val vaultId: VaultId) : VaultImportEvent
}

interface VaultTransferCoordinator {
    val progress: Flow<VaultTransferProgress>
    suspend fun copyWithinVault(
        sources: List<VaultNodeRef>,
        destination: DirectoryId,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult
    suspend fun moveWithinVault(
        sources: List<VaultNodeRef>,
        destination: DirectoryId,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult
    suspend fun transferAcrossVaults(
        sources: List<VaultNodeRef>,
        destinationVault: VaultId,
        destination: DirectoryId,
        move: Boolean,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult
}

enum class VaultHealthMode { QUICK, FULL }
enum class VaultHealthSeverity { INFO, WARNING, ERROR }

data class VaultHealthIssue(
    val severity: VaultHealthSeverity,
    val code: String,
    val objectIdentity: String? = null,
    val message: String
)

data class VaultHealthReport(
    val vaultId: VaultId,
    val mode: VaultHealthMode,
    val generation: Long,
    val checkedObjects: Long,
    val checkedChunks: Long,
    val orphanObjectIds: Set<VaultObjectId>,
    val issues: List<VaultHealthIssue>
) {
    val isHealthy: Boolean get() = issues.none { it.severity == VaultHealthSeverity.ERROR }
}

interface VaultHealthService {
    suspend fun verify(vaultId: VaultId, mode: VaultHealthMode): Result<VaultHealthReport>
    suspend fun cleanupOrphans(vaultId: VaultId, confirmedObjectIds: Set<VaultObjectId>): Result<Int>
    suspend fun recoverTransactions(vaultId: VaultId): Result<Unit>
}
