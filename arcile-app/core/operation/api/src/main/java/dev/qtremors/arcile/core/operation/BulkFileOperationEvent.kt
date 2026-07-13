package dev.qtremors.arcile.core.operation

import dev.qtremors.arcile.core.storage.domain.ArcileError

sealed interface BulkFileOperationEvent {
    data class Started(val request: BulkFileOperationRequest) : BulkFileOperationEvent
    data class Progress(
        val request: BulkFileOperationRequest,
        val progress: BulkFileOperationProgress
    ) : BulkFileOperationEvent
    data class Cancelling(val request: BulkFileOperationRequest) : BulkFileOperationEvent
    data class Completed(val request: BulkFileOperationRequest) : BulkFileOperationEvent
    data class Failed(
        val request: BulkFileOperationRequest,
        val message: String,
        val error: ArcileError? = null
    ) : BulkFileOperationEvent
    data class Cancelled(val request: BulkFileOperationRequest?) : BulkFileOperationEvent
    data class RecoveryAvailable(val record: OperationRecoveryRecord) : BulkFileOperationEvent
    data class RecoveryDismissed(val operationId: String) : BulkFileOperationEvent
    data class RecoveryCleanupCompleted(val operationId: String) : BulkFileOperationEvent
}
