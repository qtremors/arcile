package dev.qtremors.arcile.presentation.operations

import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArcileError
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import kotlinx.serialization.Serializable

@Serializable
enum class BulkFileOperationType {
    COPY,
    MOVE,
    TRASH,
    DELETE,
    CREATE_FAKE,
    EXTRACT_ARCHIVE,
    CREATE_ARCHIVE
}

@Serializable
data class BulkFileOperationRequest(
    val operationId: String,
    val type: BulkFileOperationType,
    val sourcePaths: List<String>,
    val destinationPath: String? = null,
    val resolutions: Map<String, ConflictResolution> = emptyMap(),
    val fakeFileSize: Long? = null,
    val archiveFormat: ArchiveFormat? = null,
    val archiveEntryPrefix: String? = null,
    val archivePassword: String? = null
) {
    val sourceRefs: List<StorageNodeRef> get() = sourcePaths.mapNotNull { runCatching { StorageNodeRef.local(it) }.getOrNull() }
    val destinationRef: StorageNodeRef? get() = destinationPath?.let { runCatching { StorageNodeRef.local(it) }.getOrNull() }
}

@Serializable
data class BulkFileOperationProgress(
    val completedItems: Int,
    val totalItems: Int,
    val currentPath: String? = null,
    val bytesCopied: Long? = null,
    val totalBytes: Long? = null
)

sealed interface BulkFileOperationEvent {
    data class Started(val request: BulkFileOperationRequest) : BulkFileOperationEvent
    data class Progress(val request: BulkFileOperationRequest, val progress: BulkFileOperationProgress) : BulkFileOperationEvent
    data class Cancelling(val request: BulkFileOperationRequest) : BulkFileOperationEvent
    data class Completed(val request: BulkFileOperationRequest) : BulkFileOperationEvent
    data class Failed(
        val request: BulkFileOperationRequest,
        val message: String,
        val error: ArcileError? = null
    ) : BulkFileOperationEvent
    data class Cancelled(val request: BulkFileOperationRequest?) : BulkFileOperationEvent
}
