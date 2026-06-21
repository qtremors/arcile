package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import dev.qtremors.arcile.core.storage.domain.ActivityLogStore
import kotlinx.coroutines.flow.MutableStateFlow

class FakeActivityLogStore : ActivityLogStore {
    private val _entries = MutableStateFlow<List<ActivityLogEntry>>(emptyList())
    override val entries = _entries

    val folderOpenRequests = mutableListOf<Pair<String, String?>>()
    val fileOperationRequests = mutableListOf<ActivityLogEntry.FileOperation>()
    var clearCalled = false

    override suspend fun recordFolderOpened(path: String, volumeId: String?) {
        folderOpenRequests += path to volumeId
        _entries.value = listOf(
            ActivityLogEntry.FolderOpened(
                id = "folder-${folderOpenRequests.size}",
                timestampMillis = System.currentTimeMillis(),
                path = path,
                volumeId = volumeId
            )
        ) + _entries.value
    }

    override suspend fun upsertFileOperation(entry: ActivityLogEntry.FileOperation) {
        fileOperationRequests += entry
        _entries.value = listOf(entry) + _entries.value.filterNot {
            it is ActivityLogEntry.FileOperation && it.operationId == entry.operationId
        }
    }

    override suspend fun clear() {
        clearCalled = true
        _entries.value = emptyList()
    }
}
