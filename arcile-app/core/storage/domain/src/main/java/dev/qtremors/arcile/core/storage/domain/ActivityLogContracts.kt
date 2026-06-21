package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.serialization.Serializable

@Serializable
sealed interface ActivityLogEntry {
    val id: String
    val timestampMillis: Long

    @Serializable
    data class FolderOpened(
        override val id: String,
        override val timestampMillis: Long,
        val path: String,
        val volumeId: String?
    ) : ActivityLogEntry

    @Serializable
    data class FileOperation(
        override val id: String,
        override val timestampMillis: Long,
        val operationId: String,
        val operationType: String,
        val status: ActivityLogOperationStatus,
        val sourceCount: Int,
        val destinationPath: String? = null,
        val errorMessage: String? = null
    ) : ActivityLogEntry
}

@Serializable
enum class ActivityLogOperationStatus {
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED
}

interface ActivityLogStore {
    val entries: Flow<List<ActivityLogEntry>>

    suspend fun recordFolderOpened(path: String, volumeId: String?)
    suspend fun upsertFileOperation(entry: ActivityLogEntry.FileOperation)
    suspend fun clear()
}

object NoOpActivityLogStore : ActivityLogStore {
    override val entries: Flow<List<ActivityLogEntry>> = flowOf(emptyList())

    override suspend fun recordFolderOpened(path: String, volumeId: String?) = Unit
    override suspend fun upsertFileOperation(entry: ActivityLogEntry.FileOperation) = Unit
    override suspend fun clear() = Unit
}
