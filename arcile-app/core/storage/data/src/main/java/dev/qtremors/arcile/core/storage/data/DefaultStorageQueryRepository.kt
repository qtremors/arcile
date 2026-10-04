package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.manager.TrashManager
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.provider.RootStorageUsageProvider
import dev.qtremors.arcile.core.storage.data.source.StorageQueryClient
import dev.qtremors.arcile.core.storage.data.util.indexedVolumes
import dev.qtremors.arcile.core.storage.data.util.scopedVolumes
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import kotlinx.coroutines.withContext

class DefaultStorageQueryRepository(
    private val volumeProvider: VolumeProvider,
    private val storageQueryClient: StorageQueryClient,
    private val trashManager: TrashManager,
    private val recentFilesSnapshotStore: RecentFilesSnapshotStore,
    private val dispatchers: ArcileDispatchers,
    private val rootStorageUsageProvider: RootStorageUsageProvider
) : SearchRepository, StorageAnalyticsRepository {
    override suspend fun getCachedRecentFiles(scope: StorageScope, limit: Int, minTimestamp: Long): List<FileModel>? =
        recentFilesSnapshotStore.get(scope, limit, minTimestamp, allowPreviousWindow = true)

    override suspend fun getCachedCategoryStorageSizes(scope: StorageScope): List<CategoryStorage>? =
        storageQueryClient.getCachedCategoryStorageSizes(scope)
    override suspend fun getRecentFiles(
        scope: StorageScope,
        limit: Int,
        offset: Int,
        minTimestamp: Long
    ): Result<List<FileModel>> =
        storageQueryClient.getRecentFiles(scope, limit, offset, minTimestamp)
            .onSuccess { files ->
                if (offset == 0) {
                    recentFilesSnapshotStore.put(scope, limit, minTimestamp, files)
                }
            }

    override suspend fun getFilesByCategory(
        scope: StorageScope,
        categoryName: String
    ): Result<List<FileModel>> =
        storageQueryClient.getFilesByCategory(scope, categoryName)

    override suspend fun searchFiles(
        query: String,
        scope: StorageScope,
        filters: SearchFilters?
    ): Result<List<FileModel>> =
        storageQueryClient.searchFiles(query, scope, filters)

    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> =
        loadStorageInfo(scope, includeRootStorage = true)

    override suspend fun getMountedStorageInfo(scope: StorageScope): Result<StorageInfo> =
        loadStorageInfo(scope, includeRootStorage = false)

    private suspend fun loadStorageInfo(
        scope: StorageScope,
        includeRootStorage: Boolean
    ): Result<StorageInfo> =
        withContext(dispatchers.io) {
            volumeProvider.getStorageVolumes().map { volumes ->
                StorageInfo(
                    volumes = if (scope is StorageScope.AllStorage) {
                        indexedVolumes(volumes)
                    } else {
                        scopedVolumes(scope, volumes)
                    },
                    rootStorageUsage = if (includeRootStorage && scope is StorageScope.AllStorage) {
                        rootStorageUsageProvider.getRootStorageUsage()
                    } else {
                        null
                    }
                )
            }
        }

    override suspend fun getCategoryStorageSizes(
        scope: StorageScope
    ): Result<List<CategoryStorage>> =
        storageQueryClient.getCategoryStorageSizes(scope)

    override suspend fun getTrashStorageUsage(): Result<TrashStorageUsage> =
        trashManager.getTrashStorageUsage()

    override suspend fun invalidateAnalyticsCache() {
        storageQueryClient.invalidateCache()
    }
}
