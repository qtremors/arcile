package dev.qtremors.arcile.presentation.operations

import dev.qtremors.arcile.domain.ConflictResolution
import kotlinx.serialization.Serializable

@Serializable
enum class BulkFileOperationType {
    COPY,
    MOVE
}

@Serializable
data class BulkFileOperationRequest(
    val operationId: String,
    val type: BulkFileOperationType,
    val sourcePaths: List<String>,
    val destinationPath: String,
    val resolutions: Map<String, ConflictResolution> = emptyMap()
)

@Serializable
data class BulkFileOperationProgress(
    val completedItems: Int,
    val totalItems: Int,
    val currentPath: String? = null
)

sealed interface BulkFileOperationEvent {
    data class Started(val request: BulkFileOperationRequest) : BulkFileOperationEvent
    data class Progress(val request: BulkFileOperationRequest, val progress: BulkFileOperationProgress) : BulkFileOperationEvent
    data class Cancelling(val request: BulkFileOperationRequest) : BulkFileOperationEvent
    data class Completed(val request: BulkFileOperationRequest) : BulkFileOperationEvent
    data class Failed(val request: BulkFileOperationRequest, val message: String) : BulkFileOperationEvent
    data class Cancelled(val request: BulkFileOperationRequest?) : BulkFileOperationEvent
}
