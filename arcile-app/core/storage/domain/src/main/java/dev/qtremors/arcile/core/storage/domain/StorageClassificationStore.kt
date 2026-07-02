package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

interface StorageClassificationStore {
    fun observeClassifications(): Flow<Map<String, StorageClassification>>
    suspend fun getClassification(storageKey: String): StorageClassification?
    suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String? = null,
        lastSeenPath: String? = null
    )
    suspend fun resetClassification(storageKey: String)
}
