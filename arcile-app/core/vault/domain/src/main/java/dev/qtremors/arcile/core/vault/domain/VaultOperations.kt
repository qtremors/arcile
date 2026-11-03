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
    suspend fun prepareBiometricEnrollment(vaultId: VaultId, password: CharArray): Result<VaultBiometricChallenge>
    suspend fun prepareBiometricUnlock(vaultId: VaultId): Result<VaultBiometricChallenge>
    suspend fun hasBiometricEnrollment(vaultId: VaultId): Boolean
    suspend fun removeBiometric(vaultId: VaultId): Result<Unit>
}

enum class VaultBiometricPurpose { ENROLL, UNLOCK }

/**
 * One-shot, in-memory bridge to the platform BIOMETRIC_STRONG prompt. The platform object is an
 * android.hardware.biometrics.BiometricPrompt.CryptoObject on Android and must never be persisted.
 */
interface VaultBiometricChallenge : Closeable {
    val vaultId: VaultId
    val purpose: VaultBiometricPurpose
    val platformCryptoObject: Any
    val isClosed: Boolean
    suspend fun completeAfterAuthentication(): Result<Unit>
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

    /** Review known conflicts before writing. Unreviewed or changed conflicts still use [decide]. */
    suspend fun review(conflicts: List<VaultConflict>): Map<VaultConflict, VaultConflictDecision> = emptyMap()
}

data class VaultConflict(
    val sourceName: String,
    val destinationName: String,
    val sourceIsDirectory: Boolean,
    val destinationIsDirectory: Boolean,
    val sourceIdentity: String? = null,
    val destinationIdentity: String? = null,
    val sourceSize: Long = 0,
    val destinationSize: Long = 0,
    val sourceModifiedAt: Long = 0,
    val destinationModifiedAt: Long = 0
) {
    val canMerge: Boolean get() = sourceIsDirectory && destinationIsDirectory
}

enum class VaultTransferAction { COPY, MOVE, IMPORT, EXPORT, SHARE, OPEN_WITH }
enum class VaultItemOutcome { COMPLETED, PARTIAL, SKIPPED, FAILED, ROLLED_BACK }

/** An in-memory continuation of a folder merge. Both vaults must still be unlocked to use it. */
interface VaultMergeRetry {
    val source: VaultNodeRef
    val destinationVault: VaultId
    val destinationDirectory: DirectoryId
    val move: Boolean
    val remainingCount: Int
}

/** A one-session continuation for a folder already published outside the vault. */
interface VaultExportRetry {
    val source: VaultNodeRef
    val displayName: String
    val move: Boolean
    val remainingCount: Int
}

data class VaultItemResult(
    val sourceIdentity: String,
    val displayName: String,
    val outcome: VaultItemOutcome,
    val failure: VaultFailure? = null,
    val removedFolders: Set<DirectoryId> = emptySet(),
    val mergeRetry: VaultMergeRetry? = null,
    val exportRetry: VaultExportRetry? = null,
    val moveUndo: VaultMoveUndo? = null,
    val createdUndo: VaultCreatedUndo? = null,
    val plaintextUndo: VaultPlaintextUndo? = null
)

data class VaultBatchResult(val items: List<VaultItemResult>) {
    val completed: List<VaultItemResult> get() = items.filter { it.outcome == VaultItemOutcome.COMPLETED }
    val partial: List<VaultItemResult> get() = items.filter { it.outcome == VaultItemOutcome.PARTIAL }
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
    val totalBytes: Long?,
    val startedAtMillis: Long? = null
)

fun interface VaultCancellationSignal {
    fun isCancelled(): Boolean
}

data class VaultImportProgress(
    val completedItems: Int,
    val totalItems: Int,
    val bytesProcessed: Long,
    val totalBytes: Long?,
    val currentName: String?
)

data class VaultImportState(
    val vaultId: VaultId,
    val progress: VaultImportProgress? = null,
    val isCancelling: Boolean = false
)

sealed interface VaultImportEvent {
    data class Started(val vaultId: VaultId, val recoveredDestination: DirectoryId? = null) : VaultImportEvent
    data class Progress(val vaultId: VaultId, val value: VaultImportProgress) : VaultImportEvent
    data class Completed(val vaultId: VaultId, val result: VaultBatchResult? = null) : VaultImportEvent
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

    suspend fun resumeMerge(
        retry: VaultMergeRetry,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultItemResult = throw VaultFailure.Unavailable("Resuming this vault merge is unavailable")
}

interface VaultBoundaryTransferCoordinator {
    val progress: Flow<VaultTransferProgress>
    fun prepareExport(sources: List<VaultNodeRef>): Result<VaultBoundaryTransferReservation>
    suspend fun exportToDestination(
        reservation: VaultBoundaryTransferReservation,
        destinationPath: String,
        move: Boolean,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult

    suspend fun moveToMainTrash(
        reservation: VaultBoundaryTransferReservation,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult = throw VaultFailure.Unavailable("Main Trash is unavailable")

    suspend fun resumeExport(
        retry: VaultExportRetry,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultItemResult = throw VaultFailure.Unavailable("Resuming this export is unavailable")

    /**
     * Reverses exports and Trash moves. Unchanged plaintext is re-encrypted into its [VaultPlaintextUndo.origin]
     * when one exists, and removed only after that publication succeeds. Each proof is reversed independently.
     */
    suspend fun undoPlaintext(
        proofs: List<VaultPlaintextUndo>,
        cancellation: VaultCancellationSignal
    ): VaultBatchResult = throw VaultFailure.Unavailable("Undo is unavailable")
}

interface VaultBoundaryTransferReservation : Closeable {
    val sources: List<VaultNodeRef>
    val isClosed: Boolean
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

data class VaultExternalGrant(
    val token: String,
    val contentUri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val expiresAtMillis: Long,
    val vaultId: VaultId? = null
)

data class VaultGrantedContent(
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val reader: VaultSeekableReader
)

interface VaultExternalAccessManager {
    fun issue(ref: VaultNodeRef, lifetimeMillis: Long = 10 * 60 * 1000L): Result<VaultExternalGrant>
    suspend fun issuePlaintextFallback(
        ref: VaultNodeRef,
        lifetimeMillis: Long = 10 * 60 * 1000L
    ): Result<VaultExternalGrant>
    fun activeGrants(): List<VaultExternalGrant>
    fun activeGrants(vaultId: VaultId): List<VaultExternalGrant> = activeGrants().filter { it.vaultId == vaultId }
    fun revoke(token: String): Boolean
    fun revokeAll()
    fun describe(token: String): Result<VaultExternalGrant>
    fun openGrantedContent(token: String, consumerUid: Int): Result<VaultGrantedContent>
}
