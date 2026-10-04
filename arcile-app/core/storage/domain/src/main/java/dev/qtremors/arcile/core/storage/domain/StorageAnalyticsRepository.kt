package dev.qtremors.arcile.core.storage.domain

interface StorageAnalyticsRepository {
    /** Last known values for display while a live query runs; never a freshness guarantee. */
    suspend fun getCachedRecentFiles(
        scope: StorageScope = StorageScope.AllStorage,
        limit: Int = 10,
        minTimestamp: Long = 0L
    ): List<FileModel>? = null

    suspend fun getCachedCategoryStorageSizes(
        scope: StorageScope = StorageScope.AllStorage
    ): List<CategoryStorage>? = null

    suspend fun getRecentFiles(
        scope: StorageScope = StorageScope.AllStorage,
        limit: Int = 10,
        offset: Int = 0,
        minTimestamp: Long = 0L
    ): Result<List<FileModel>>
    suspend fun getStorageInfo(
        scope: StorageScope = StorageScope.AllStorage
    ): Result<StorageInfo>
    suspend fun getMountedStorageInfo(
        scope: StorageScope = StorageScope.AllStorage
    ): Result<StorageInfo> = getStorageInfo(scope).map { info ->
        info.copy(rootStorageUsage = null)
    }
    suspend fun getCategoryStorageSizes(
        scope: StorageScope = StorageScope.AllStorage
    ): Result<List<CategoryStorage>>
    suspend fun getTrashStorageUsage(): Result<TrashStorageUsage>
    suspend fun invalidateAnalyticsCache() = Unit
}
