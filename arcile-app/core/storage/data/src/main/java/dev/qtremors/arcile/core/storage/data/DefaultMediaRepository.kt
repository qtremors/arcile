package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.manager.TrashManager
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.provider.RootStorageUsageProvider
import dev.qtremors.arcile.core.storage.data.source.MediaStoreClient
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.data.util.indexedVolumes
import dev.qtremors.arcile.core.storage.data.util.scopedVolumes
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageNodeSearchLimits
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Provider

class DefaultMediaRepository(
    private val volumeProvider: VolumeProvider,
    private val mediaStoreClient: MediaStoreClient,
    private val trashManager: TrashManager,
    private val recentFilesSnapshotStore: RecentFilesSnapshotStore,
    private val dispatchers: ArcileDispatchers,
    private val rootStorageUsageProvider: RootStorageUsageProvider,
    private val fileSystemDataSource: Provider<FileSystemDataSource>
) : SearchRepository, StorageAnalyticsRepository {
    override suspend fun getRecentFiles(
        scope: StorageScope,
        limit: Int,
        offset: Int,
        minTimestamp: Long
    ): Result<List<FileModel>> =
        mediaStoreClient.getRecentFiles(scope, limit, offset, minTimestamp)
            .onSuccess { files ->
                if (offset == 0) {
                    recentFilesSnapshotStore.put(scope, limit, minTimestamp, files)
                }
            }

    override suspend fun getFilesByCategory(
        scope: StorageScope,
        categoryName: String
    ): Result<List<FileModel>> =
        mediaStoreClient.getFilesByCategory(scope, categoryName)

    override suspend fun searchFiles(
        query: String,
        scope: StorageScope,
        filters: SearchFilters?
    ): Result<List<FileModel>> =
        mediaStoreClient.searchFiles(query, scope, filters)

    override suspend fun searchNode(
        query: String,
        root: StorageNodeRef,
        filters: SearchFilters?,
        limits: StorageNodeSearchLimits
    ): Result<List<FileModel>> = withContext(dispatchers.storage) {
        StorageNodeSearchEngine(fileSystemDataSource.get()).search(query, root, filters, limits)
    }

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
        mediaStoreClient.getCategoryStorageSizes(scope)

    override suspend fun getTrashStorageUsage(): Result<TrashStorageUsage> =
        trashManager.getTrashStorageUsage()

    override suspend fun invalidateAnalyticsCache() {
        mediaStoreClient.invalidateCache()
        recentFilesSnapshotStore.clear()
    }
}
