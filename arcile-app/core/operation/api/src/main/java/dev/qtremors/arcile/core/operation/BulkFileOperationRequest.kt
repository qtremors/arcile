package dev.qtremors.arcile.core.operation

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import kotlinx.serialization.Serializable

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
    val sourceRefs: List<StorageNodeRef>
        get() = sourcePaths.mapNotNull { runCatching { StorageNodeRef.local(it) }.getOrNull() }
    val destinationRef: StorageNodeRef?
        get() = destinationPath?.let { runCatching { StorageNodeRef.local(it) }.getOrNull() }
}
