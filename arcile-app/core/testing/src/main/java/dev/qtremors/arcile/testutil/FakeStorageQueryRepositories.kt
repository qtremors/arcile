package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

class FakeStorageAnalyticsRepository(
    volumes: List<StorageVolume> = emptyList(),
    initialRecentFilesByScope: Map<StorageScope, List<FileModel>> = emptyMap(),
    initialCategorySizesByScope: Map<StorageScope, List<CategoryStorage>> = emptyMap()
) : StorageAnalyticsRepository {
    var observedVolumes: List<StorageVolume> = volumes
    var recentFilesByScope: Map<StorageScope, List<FileModel>> = initialRecentFilesByScope
    var categorySizesByScope: Map<StorageScope, List<CategoryStorage>> = initialCategorySizesByScope
    var storageInfoResultProvider: (suspend (StorageScope) -> Result<StorageInfo>)? = null
    var recentFilesResultProvider: (suspend (StorageScope, Int, Int, Long) -> Result<List<FileModel>>)? = null
    var categoryStorageResultProvider: (suspend (StorageScope) -> Result<List<CategoryStorage>>)? = null
    var trashStorageUsageResult: Result<TrashStorageUsage> =
        Result.success(TrashStorageUsage(0L, emptyMap()))
    var invalidateAnalyticsCacheResult: Result<Unit> = Result.success(Unit)

    val requestedRecentScopes = mutableListOf<StorageScope>()
    val requestedStorageInfoScopes = mutableListOf<StorageScope>()
    val requestedCategoryScopes = mutableListOf<StorageScope>()
    var invalidateAnalyticsCacheCalls = 0

    override suspend fun getRecentFiles(
        scope: StorageScope,
        limit: Int,
        offset: Int,
        minTimestamp: Long
    ): Result<List<FileModel>> {
        requestedRecentScopes += scope
        return recentFilesResultProvider?.invoke(scope, limit, offset, minTimestamp)
            ?: Result.success(recentFilesByScope[scope].orEmpty())
    }

    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> {
        requestedStorageInfoScopes += scope
        return storageInfoResultProvider?.invoke(scope)
            ?: Result.success(storageInfoForScope(scope, observedVolumes))
    }

    override suspend fun getCategoryStorageSizes(
        scope: StorageScope
    ): Result<List<CategoryStorage>> {
        requestedCategoryScopes += scope
        return categoryStorageResultProvider?.invoke(scope)
            ?: Result.success(categorySizesByScope[scope].orEmpty())
    }

    override suspend fun getTrashStorageUsage(): Result<TrashStorageUsage> =
        trashStorageUsageResult

    override suspend fun invalidateAnalyticsCache() {
        invalidateAnalyticsCacheCalls += 1
        invalidateAnalyticsCacheResult.getOrThrow()
    }
}

class FakeVolumeRepository(
    volumes: List<StorageVolume> = emptyList()
) : VolumeRepository {
    private val observedVolumes = MutableSharedFlow<List<StorageVolume>>(replay = 1).apply {
        tryEmit(volumes)
    }

    var volumeLookupOverride: ((String) -> Result<StorageVolume>)? = null

    fun emitVolumes(volumes: List<StorageVolume>) {
        observedVolumes.tryEmit(volumes)
    }

    fun currentObservedVolumes(): List<StorageVolume> =
        observedVolumes.replayCache.lastOrNull().orEmpty()

    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = observedVolumes

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> =
        Result.success(currentObservedVolumes())

    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> {
        volumeLookupOverride?.let { return it(path) }
        val volume = currentObservedVolumes()
            .sortedByDescending { it.path.length }
            .firstOrNull {
                path == it.path ||
                    path.startsWith(it.path + "/") ||
                    path.startsWith(it.path + File.separator)
            }
        return volume?.let { Result.success(it) }
            ?: Result.failure(IllegalArgumentException("No volume for path"))
    }

    override fun getStandardFolders(): Map<String, String?> = emptyMap()
}

private fun storageInfoForScope(
    scope: StorageScope,
    volumes: List<StorageVolume>
): StorageInfo = when (scope) {
    StorageScope.AllStorage -> StorageInfo(volumes)
    is StorageScope.Volume -> StorageInfo(volumes.filter { it.id == scope.volumeId })
    is StorageScope.Path -> StorageInfo(volumes.filter { it.id == scope.volumeId })
    is StorageScope.Category -> StorageInfo(volumes.filter { it.id == scope.volumeId })
}
