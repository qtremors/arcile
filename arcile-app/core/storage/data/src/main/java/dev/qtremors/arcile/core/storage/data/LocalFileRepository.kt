package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.manager.TrashManager
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.MediaStoreClient
import dev.qtremors.arcile.core.storage.data.util.indexedVolumes
import dev.qtremors.arcile.core.storage.data.util.resolveVolumeForPath
import dev.qtremors.arcile.core.storage.data.util.scopedVolumes
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveManager
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.supportsTrash
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext
import java.io.File

class LocalFileRepository(
    private val volumeProvider: VolumeProvider,
    private val mediaStoreClient: MediaStoreClient,
    private val trashManager: TrashManager,
    private val fileSystemDataSource: FileSystemDataSource,
    private val folderStatsStore: FolderStatsStore,
    private val archiveManager: ArchiveManager = object : ArchiveManager {
        override suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>> =
            Result.failure(NotImplementedError("Archive support is not available"))

        override suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary> =
            Result.failure(NotImplementedError("Archive support is not available"))

        override suspend fun extractArchive(
            archivePath: String,
            destinationPath: String,
            entryPrefix: String?,
            password: String?,
            onProgress: ((BulkFileOperationProgress) -> Unit)?
        ): Result<Unit> = Result.failure(NotImplementedError("Archive support is not available"))

        override suspend fun createArchive(
            sourcePaths: List<String>,
            destinationArchivePath: String,
            format: ArchiveFormat,
            password: String?,
            onProgress: ((BulkFileOperationProgress) -> Unit)?
        ): Result<Unit> = Result.failure(NotImplementedError("Archive support is not available"))
    },
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : FileRepository {

    override fun observeStorageVolumes(): Flow<List<StorageVolume>> =
        volumeProvider.observeStorageVolumes()

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> =
        volumeProvider.getStorageVolumes()

    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> = withContext(dispatchers.io) {
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

    override fun listFilePages(path: String, pageSize: Int): Flow<ListingPage> =
        runCatching { StorageNodePath.of(path) }
            .map { fileSystemDataSource.list(it, pageSize) }
            .getOrElse { flowOf(ListingPage.failed(StorageNodePath.of(File("/").absolutePath), it)) }

    override suspend fun getCachedFolderStats(paths: Collection<String>): Map<String, FolderStats> =
        folderStatsStore.getCached(paths)

    override fun queueFolderStats(paths: List<String>) {
        folderStatsStore.queue(paths)
    }

    override fun observeFolderStatUpdates(): Flow<FolderStatUpdate> =
        folderStatsStore.observeUpdates()

    override suspend fun getSelectionProperties(paths: List<String>): Result<SelectionProperties> = withContext(dispatchers.io) {
        try {
            val selectedFiles = paths.distinct().map(::File)
            if (selectedFiles.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No items selected"))
            }

            val existingFiles = selectedFiles.filter(File::exists)
            if (existingFiles.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("Selected items are no longer available"))
            }

            val scansByPath = existingFiles.associate { file -> file.absolutePath to PropertiesScanner.scan(file) }

            val fileCount = scansByPath.values.sumOf { it.fileCount }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val folderCount = scansByPath.values.sumOf { it.folderCount }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val hiddenCount = scansByPath.values.sumOf { it.hiddenCount }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val totalBytes = scansByPath.values.sumOf { it.totalBytes }
            val newestModifiedAt = scansByPath.values.mapNotNull { it.newestModifiedAt }.maxOrNull()
            val oldestModifiedAt = scansByPath.values.mapNotNull { it.oldestModifiedAt }.minOrNull()
            val hasMissingSelection = existingFiles.size != selectedFiles.distinctBy { it.absolutePath }.size
            val hasUnavailableDirectory = scansByPath.values.any { it.selectedDirectoryUnavailable }
            val hasPartialDirectory = scansByPath.values.any { it.descendantReadFailed }
            val accessStatus = when {
                hasUnavailableDirectory -> PropertiesAccessStatus.Limited
                hasPartialDirectory || hasMissingSelection -> PropertiesAccessStatus.Partial
                else -> PropertiesAccessStatus.Full
            }

            val result = if (existingFiles.size == 1) {
                val file = existingFiles.first()
                val extension = file.extension.lowercase()
                val scan = scansByPath.getValue(file.absolutePath)
                SelectionProperties(
                    displayName = file.name,
                    pathSummary = file.absolutePath,
                    itemCount = 1,
                    fileCount = scan.fileCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    folderCount = scan.folderCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                    totalBytes = scan.totalBytes,
                    newestModifiedAt = scan.newestModifiedAt,
                    oldestModifiedAt = scan.oldestModifiedAt,
                    mimeTypeSummary = if (file.isFile) {
                        android.webkit.MimeTypeMap.getSingleton()
                            .getMimeTypeFromExtension(extension.ifEmpty { "" })
                            ?.takeIf { extension.isNotEmpty() }
                    } else {
                        null
                    },
                    extensionSummary = extension.ifEmpty { null },
                    hiddenCount = hiddenCount,
                    accessStatus = if (file.isDirectory || hasMissingSelection) accessStatus else PropertiesAccessStatus.Full,
                    folderStats = if (file.isDirectory) {
                        FolderStats(
                            fileCount = scan.fileCount,
                            totalBytes = scan.totalBytes,
                            cachedAt = System.currentTimeMillis(),
                            status = when (accessStatus) {
                                PropertiesAccessStatus.Full -> FolderStatsStatus.Ready
                                PropertiesAccessStatus.Partial -> FolderStatsStatus.Partial
                                PropertiesAccessStatus.Limited -> FolderStatsStatus.Unavailable
                            }
                        )
                    } else {
                        null
                    },
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

    override suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>> =
        archiveManager.listArchiveEntries(archivePath)

    override suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary> =
        archiveManager.getArchiveMetadata(archivePath)

    override suspend fun listArchiveEntries(archivePath: String, password: String?): Result<List<ArchiveEntryModel>> =
        archiveManager.listArchiveEntries(archivePath, password)

    override suspend fun getArchiveMetadata(archivePath: String, password: String?): Result<ArchiveSummary> =
        archiveManager.getArchiveMetadata(archivePath, password)

    override suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> =
        archiveManager.extractArchive(archivePath, destinationPath, entryPrefix, password, onProgress)

    override suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String?,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> =
        archiveManager.createArchive(sourcePaths, destinationArchivePath, format, password, onProgress)

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

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(dispatchers.io) {

        val volume = getVolumeForPath(path).getOrNull()
            ?: return@withContext Result.failure(IllegalArgumentException("Unable to resolve storage volume"))
        if (!volume.kind.supportsTrash) {
            return@withContext deletePermanently(listOf(path))
        }
        moveToTrash(listOf(path))
    }

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> =
        fileSystemDataSource.deletePermanently(paths)

    override suspend fun shred(paths: List<String>): Result<Unit> =
        fileSystemDataSource.shred(paths)

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> =
        fileSystemDataSource.renameFile(path, newName)

    override suspend fun getRecentFiles(
        scope: StorageScope,
        limit: Int,
        offset: Int,
        minTimestamp: Long
    ): Result<List<FileModel>> =
        mediaStoreClient.getRecentFiles(scope, limit, offset, minTimestamp)

    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> = withContext(dispatchers.io) {
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

    override suspend fun getTrashStorageUsage(): Result<TrashStorageUsage> =
        trashManager.getTrashStorageUsage()

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

    override suspend fun moveToTrash(
        paths: List<String>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> =
        trashManager.moveToTrash(paths, onProgress)

    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> =
        trashManager.restoreFromTrash(trashIds, destinationPath)

    override suspend fun emptyTrash(): Result<Unit> =
        trashManager.emptyTrash()

    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> =
        trashManager.getTrashFiles()

    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> =
        trashManager.deletePermanentlyFromTrash(trashIds)
}
