package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import kotlinx.serialization.Serializable

interface TrashManager {
    suspend fun moveToTrash(
        paths: List<String>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit>

    suspend fun moveToTrashTargets(
        targets: List<TrashTarget>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit>

    suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit>
    suspend fun emptyTrash(): Result<Unit>
    suspend fun getTrashFiles(): Result<List<TrashMetadata>>
    suspend fun getTrashStorageUsage(): Result<TrashStorageUsage>
    suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit>
}

data class TrashTarget(
    val path: String,
    val nodeRef: StorageNodeRef? = null
) {
    val contentUri: String?
        get() = nodeRef?.contentUri?.takeIf { it.isNotBlank() }
}

@Serializable
data class TrashMetadataEntity(
    val schemaVersion: Int = 1,
    val id: String,
    val originalPath: String,
    val deletionTime: Long,
    val sourceVolumeId: String? = null,
    val sourceStorageKind: String? = null
)
