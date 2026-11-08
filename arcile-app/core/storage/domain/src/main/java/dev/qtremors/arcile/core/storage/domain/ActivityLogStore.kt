package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface ActivityLogStore {
    val entries: Flow<List<ActivityLogEntry>>
    val recordingEnabled: Flow<Boolean>
        get() = flowOf(true)

    suspend fun recordFolderOpened(path: String, volumeId: String?)
    suspend fun recordFileOpened(path: String) = Unit
    suspend fun recordPageVisited(page: ActivityLogPage, detail: String? = null) = Unit
    suspend fun upsertFileOperation(entry: ActivityLogEntry.FileOperation)
    suspend fun setRecordingEnabled(enabled: Boolean) = Unit
    suspend fun clear()
}
