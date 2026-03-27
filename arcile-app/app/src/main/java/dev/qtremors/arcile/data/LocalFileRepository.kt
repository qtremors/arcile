package dev.qtremors.arcile.data

import dev.qtremors.arcile.data.manager.TrashManager
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.FileSystemDataSource
import dev.qtremors.arcile.data.source.MediaStoreClient
import dev.qtremors.arcile.data.util.indexedVolumes
import dev.qtremors.arcile.data.util.resolveVolumeForPath
import dev.qtremors.arcile.data.util.scopedVolumes
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.supportsTrash
import dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class LocalFileRepository(
    private val volumeProvider: VolumeProvider,
    private val mediaStoreClient: MediaStoreClient,
    private val trashManager: TrashManager,
    private val fileSystemDataSource: FileSystemDataSource
) : FileRepository {

    override fun observeStorageVolumes(): Flow<List<StorageVolume>> =
        volumeProvider.observeStorageVolumes()

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> =
        volumeProvider.getStorageVolumes()

    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> = withContext(Dispatchers.IO) {
        try {
            val volumes = volumeProvider.currentVolumes()
            if (volumes.isEmpty()) {
                return@withContext Result.failure(Exception("Could not fetch volumes"))
            }
            val volume = resolveVolumeForPath(path, volumes)
            if (volume != null) {
                Result.success(volume)
            } else {
                Result.failure(Exception("No volume found for path"))
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override fun getStandardFolders(): Map<String, String?> =
        fileSystemDataSource.getStandardFolders()

    override suspend fun listFiles(path: String): Result<List<FileModel>> =
        fileSystemDataSource.listFiles(path)

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> =
        fileSystemDataSource.createDirectory(parentPath, name)

    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> =
        fileSystemDataSource.createFile(parentPath, name)

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        val volume = getVolumeForPath(path).getOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Unable to resolve storage volume"))
        if (!volume.kind.supportsTrash) {
            return@withContext deletePermanently(listOf(path))
        }
        moveToTrash(listOf(path))
    }

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> =
        fileSystemDataSource.deletePermanently(paths)

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> =
        fileSystemDataSource.renameFile(path, newName)

    override suspend fun getRecentFiles(
        scope: StorageScope,
        limit: Int,
        offset: Int,
        minTimestamp: Long
    ): Result<List<FileModel>> =
        mediaStoreClient.getRecentFiles(scope, limit, offset, minTimestamp)

    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> = withContext(Dispatchers.IO) {
        getStorageVolumes().map { allVolumes ->
            val queryVolumes = if (scope is StorageScope.AllStorage) {
                indexedVolumes(allVolumes)
            } else {
                scopedVolumes(scope, allVolumes)
            }
            StorageInfo(queryVolumes)
        }
    }

    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> =
        mediaStoreClient.getCategoryStorageSizes(scope)

    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> =
        mediaStoreClient.getFilesByCategory(scope, categoryName)

    override suspend fun searchFiles(
        query: String,
        scope: StorageScope,
        filters: SearchFilters?
    ): Result<List<FileModel>> =
        mediaStoreClient.searchFiles(query, scope, filters)

    override suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>> =
        fileSystemDataSource.detectCopyConflicts(sourcePaths, destinationPath)

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> =
        fileSystemDataSource.copyFiles(sourcePaths, destinationPath, resolutions, onProgress)

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> =
        fileSystemDataSource.moveFiles(sourcePaths, destinationPath, resolutions, onProgress)

    override suspend fun moveToTrash(paths: List<String>): Result<Unit> =
        trashManager.moveToTrash(paths)

    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> =
        trashManager.restoreFromTrash(trashIds, destinationPath)

    override suspend fun emptyTrash(): Result<Unit> =
        trashManager.emptyTrash()

    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> =
        trashManager.getTrashFiles()

    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> =
        trashManager.deletePermanentlyFromTrash(trashIds)
}
