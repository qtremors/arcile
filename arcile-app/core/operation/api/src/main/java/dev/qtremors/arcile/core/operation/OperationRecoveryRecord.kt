package dev.qtremors.arcile.core.operation

import kotlinx.serialization.Serializable

@Serializable
data class OperationRecoveryRecord(
    val request: BulkFileOperationRequest,
    val phase: String,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val progress: BulkFileOperationProgress? = null,
    val stagedPaths: List<String> = emptyList(),
    val finalizedPaths: List<String> = emptyList(),
    val rollbackHints: List<String> = emptyList(),
    val trashResultIds: List<String> = emptyList(),
    val error: String? = null
)
