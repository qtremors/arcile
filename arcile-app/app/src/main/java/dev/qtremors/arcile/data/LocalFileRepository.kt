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
import dev.qtremors.arcile.domain.FolderStatUpdate
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.FolderStatsStatus
import dev.qtremors.arcile.domain.PropertiesAccessStatus
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.SelectionProperties
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.supportsTrash
import dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

class LocalFileRepository(
    private val volumeProvider: VolumeProvider,
    private val mediaStoreClient: MediaStoreClient,
    private val trashManager: TrashManager,
    private val fileSystemDataSource: FileSystemDataSource,
    private val folderStatsStore: FolderStatsStore
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

    override suspend fun getCachedFolderStats(paths: Collection<String>): Map<String, FolderStats> =
        folderStatsStore.getCached(paths)

    override fun queueFolderStats(paths: List<String>) {
        folderStatsStore.queue(paths)
    }

    override fun observeFolderStatUpdates(): Flow<FolderStatUpdate> =
        folderStatsStore.observeUpdates()

    override suspend fun getSelectionProperties(paths: List<String>): Result<SelectionProperties> = withContext(Dispatchers.IO) {
        try {
            val selectedFiles = paths.distinct().map(::File)
            if (selectedFiles.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No items selected"))
            }

            val existingFiles = selectedFiles.filter(File::exists)
            if (existingFiles.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Selected items are no longer available"))
            }

            val statsByPath = existingFiles
                .filter(File::isDirectory)
                .associate { file -> file.absolutePath to FolderStatsCalculator.calculate(file) }

            val fileCount = existingFiles.count { it.isFile }
            val folderCount = existingFiles.count { it.isDirectory }
            val hiddenCount = existingFiles.count { it.name.startsWith(".") }
            val totalBytes = existingFiles.sumOf { file ->
                if (file.isFile) {
                    file.length()
                } else {
                    statsByPath[file.absolutePath]?.totalBytes ?: 0L
                }
            }
            val newestModifiedAt = existingFiles.maxOfOrNull(File::lastModified)
            val oldestModifiedAt = existingFiles.minOfOrNull(File::lastModified)
            val hasUnavailableDirectory = statsByPath.values.any { it.status == FolderStatsStatus.Unavailable }
            val hasPartialDirectory = statsByPath.values.any { it.status == FolderStatsStatus.Partial }
            val accessStatus = when {
                hasUnavailableDirectory -> PropertiesAccessStatus.Limited
                hasPartialDirectory -> PropertiesAccessStatus.Partial
                else -> PropertiesAccessStatus.Full
            }

            val result = if (existingFiles.size == 1) {
                val file = existingFiles.first()
                val extension = file.extension.lowercase()
                SelectionProperties(
                    displayName = file.name,
                    pathSummary = file.absolutePath,
                    itemCount = 1,
                    fileCount = if (file.isFile) 1 else 0,
                    folderCount = if (file.isDirectory) 1 else 0,
                    totalBytes = if (file.isFile) file.length() else statsByPath[file.absolutePath]?.totalBytes ?: 0L,
                    newestModifiedAt = file.lastModified(),
                    oldestModifiedAt = file.lastModified(),
                    mimeTypeSummary = if (file.isFile) {
                        android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(extension.ifEmpty { "" })
                            ?.takeIf { extension.isNotEmpty() }
                    } else {
                        null
                    },
                    extensionSummary = extension.ifEmpty { null },
                    hiddenCount = hiddenCount,
                    accessStatus = if (file.isDirectory) accessStatus else PropertiesAccessStatus.Full,
                    folderStats = statsByPath[file.absolutePath],
                    isSingleItem = true,
                    isDirectory = file.isDirectory
                )
            } else {
                val commonParent = existingFiles
                    .mapNotNull { it.parent }
                    .distinct()
                    .singleOrNull()
                    ?: existingFiles.first().parent.orEmpty()
                SelectionProperties(
                    displayName = "${existingFiles.size} items",
                    pathSummary = commonParent,
                    itemCount = existingFiles.size,
                    fileCount = fileCount,
                    folderCount = folderCount,
                    totalBytes = totalBytes,
                    newestModifiedAt = newestModifiedAt,
                    oldestModifiedAt = oldestModifiedAt,
                    mimeTypeSummary = null,
                    extensionSummary = null,
                    hiddenCount = hiddenCount,
                    accessStatus = accessStatus,
                    folderStats = null,
                    isSingleItem = false,
                    isDirectory = null
                )
            }

            Result.success(result)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> =
        fileSystemDataSource.createDirectory(parentPath, name)

    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> =
        fileSystemDataSource.createFile(parentPath, name)

    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> =
        fileSystemDataSource.createFakeFile(parentPath, name, size, onProgress)

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
