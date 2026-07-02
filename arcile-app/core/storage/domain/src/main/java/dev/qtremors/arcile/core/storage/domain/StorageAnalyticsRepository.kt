package dev.qtremors.arcile.core.storage.domain

interface StorageAnalyticsRepository {
    suspend fun getRecentFiles(
        scope: StorageScope = StorageScope.AllStorage,
        limit: Int = 10,
        offset: Int = 0,
        minTimestamp: Long = 0L
    ): Result<List<FileModel>>
    suspend fun getStorageInfo(
        scope: StorageScope = StorageScope.AllStorage
    ): Result<StorageInfo>
    suspend fun getCategoryStorageSizes(
        scope: StorageScope = StorageScope.AllStorage
    ): Result<List<CategoryStorage>>
    suspend fun getTrashStorageUsage(): Result<TrashStorageUsage>
    suspend fun invalidateAnalyticsCache() = Unit
}
