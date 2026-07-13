package dev.qtremors.arcile.feature.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationRecoveryRecord

@Immutable
internal data class BrowserOperationRecoveryUiState(
    val operationId: String,
    val type: BulkFileOperationType,
    val sourcePaths: List<String>,
    val destinationPath: String?,
    val phase: String,
    val completedItems: Int,
    val totalItems: Int,
    val currentPath: String?,
    val error: String?
)

internal fun OperationRecoveryRecord.toBrowserRecoveryUiState(): BrowserOperationRecoveryUiState =
    BrowserOperationRecoveryUiState(
        operationId = request.operationId,
        type = request.type,
        sourcePaths = request.sourcePaths,
        destinationPath = request.destinationPath,
        phase = phase,
        completedItems = progress?.completedItems ?: 0,
        totalItems = progress?.totalItems ?: request.sourcePaths.size.coerceAtLeast(1),
        currentPath = progress?.currentPath ?: request.sourcePaths.firstOrNull(),
        error = error
    )
