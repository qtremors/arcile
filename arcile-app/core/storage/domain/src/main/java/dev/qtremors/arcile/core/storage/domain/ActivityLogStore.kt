package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

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
