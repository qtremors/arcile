package dev.qtremors.arcile.core.operation

import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArcileError
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import kotlinx.serialization.Serializable

@Serializable
enum class BulkFileOperationType {
    COPY,
    MOVE,
    TRASH,
    DELETE,
    SHRED,
    CREATE_FAKE,
    EXTRACT_ARCHIVE,
    CREATE_ARCHIVE,
    SAVE_TO_ARCILE_IMPORT
}

@Serializable
data class SaveToArcileImportItem(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long? = null,
    val requiresCountedStream: Boolean = false
)

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
    val archivePassword: String? = null,
    val archiveNameEncoding: ArchiveNameEncoding? = null,
    val archiveCompressionLevel: ArchiveCompressionLevel? = null,
    val importItems: List<SaveToArcileImportItem> = emptyList()
) {
    val sourceRefs: List<StorageNodeRef> get() = sourcePaths.mapNotNull { runCatching { StorageNodeRef.local(it) }.getOrNull() }
    val destinationRef: StorageNodeRef? get() = destinationPath?.let { runCatching { StorageNodeRef.local(it) }.getOrNull() }
}

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
    data class RecoveryAvailable(val record: OperationRecoveryRecord) : BulkFileOperationEvent
    data class RecoveryDismissed(val operationId: String) : BulkFileOperationEvent
    data class RecoveryCleanupCompleted(val operationId: String) : BulkFileOperationEvent
}
