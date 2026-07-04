package dev.qtremors.arcile.core.presentation

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus

@Immutable
data class OperationUiState(
    val type: BulkFileOperationType,
    val totalItems: Int,
    val completedItems: Int = 0,
    val currentPath: String? = null,
    val isCancelling: Boolean = false,
    val bytesCopied: Long? = null,
    val totalBytes: Long? = null,
    val startTimeMillis: Long = System.currentTimeMillis(),
    val terminalStatus: OperationCompletionStatus? = null,
    val sourcePaths: List<String> = emptyList()
) {
    val isIndeterminate: Boolean
        get() = (totalBytes ?: 0L) <= 0L && totalItems <= 0
}

object OperationPresentationMapper {
    fun map(
        request: BulkFileOperationRequest,
        progress: BulkFileOperationProgress? = null,
        previous: OperationUiState? = null,
        terminalStatus: OperationCompletionStatus? = null,
        isCancelling: Boolean = false,
        completedItems: Int? = progress?.completedItems
    ): OperationUiState = OperationUiState(
        type = request.type,
        totalItems = progress?.totalItems ?: itemCount(request),
        completedItems = completedItems
            ?: if (terminalStatus == OperationCompletionStatus.SUCCESS) itemCount(request) else 0,
        currentPath = progress?.currentPath ?: currentPath(request),
        isCancelling = isCancelling,
        bytesCopied = progress?.bytesCopied,
        totalBytes = progress?.totalBytes,
        startTimeMillis = previous?.startTimeMillis ?: System.currentTimeMillis(),
        terminalStatus = terminalStatus,
        sourcePaths = request.sourcePaths
    )

    fun itemCount(request: BulkFileOperationRequest): Int = when {
        request.importItems.isNotEmpty() -> request.importItems.size
        request.sourcePaths.isNotEmpty() -> request.sourcePaths.size
        else -> 1
    }

    fun currentPath(request: BulkFileOperationRequest): String? =
        request.archiveEntryPrefix
            ?: request.sourcePaths.firstOrNull()
            ?: request.importItems.firstOrNull()?.displayName
}
