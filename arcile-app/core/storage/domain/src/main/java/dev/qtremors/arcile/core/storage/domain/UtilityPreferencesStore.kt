package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface UtilityPreferencesStore {
    val homeUtilityIds: Flow<List<String>>
    val batchRenameHistory: Flow<List<String>> get() = flowOf(emptyList())

    suspend fun setHomeUtilityIds(ids: List<String>)
    suspend fun addBatchRenameHistory(query: String) {}
    suspend fun removeBatchRenameHistory(query: String) {}
    suspend fun clearBatchRenameHistory() {}
}

object NoOpUtilityPreferencesStore : UtilityPreferencesStore {
    override val homeUtilityIds: Flow<List<String>> = flowOf(listOf("trash", "cleaner"))
    override val batchRenameHistory: Flow<List<String>> = flowOf(emptyList())

    override suspend fun setHomeUtilityIds(ids: List<String>) = Unit
    override suspend fun addBatchRenameHistory(query: String) = Unit
    override suspend fun removeBatchRenameHistory(query: String) = Unit
    override suspend fun clearBatchRenameHistory() = Unit
}
