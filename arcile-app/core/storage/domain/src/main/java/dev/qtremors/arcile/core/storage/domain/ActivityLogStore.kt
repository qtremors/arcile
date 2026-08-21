package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface ActivityLogStore {
    val entries: Flow<List<ActivityLogEntry>>
    val recordingEnabled: Flow<Boolean>
        get() = flowOf(true)

    suspend fun recordFolderOpened(path: String, volumeId: String?)
    suspend fun recordFileOpened(path: String) = Unit
    suspend fun upsertFileOperation(entry: ActivityLogEntry.FileOperation)
    suspend fun setRecordingEnabled(enabled: Boolean) = Unit
    suspend fun clear()
}

object NoOpActivityLogStore : ActivityLogStore {
    override val entries: Flow<List<ActivityLogEntry>> = flowOf(emptyList())
    override val recordingEnabled: Flow<Boolean> = flowOf(false)

    override suspend fun recordFolderOpened(path: String, volumeId: String?) = Unit
    override suspend fun recordFileOpened(path: String) = Unit
    override suspend fun upsertFileOperation(entry: ActivityLogEntry.FileOperation) = Unit
    override suspend fun setRecordingEnabled(enabled: Boolean) = Unit
    override suspend fun clear() = Unit
}
