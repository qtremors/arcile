package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import java.io.File

class FakeFileBrowserRepository(
    initialFilesByPath: Map<String, List<FileModel>> = emptyMap()
) : FileBrowserRepository {
    private val folderStatUpdates = MutableSharedFlow<FolderStatUpdate>(replay = 1, extraBufferCapacity = 16)

    var filesByPath: Map<String, List<FileModel>> = initialFilesByPath
    var cachedFolderStats: Map<String, FolderStats> = emptyMap()
    var listFilesResultProvider: (suspend (String) -> Result<List<FileModel>>)? = null
    var selectionPropertiesResultProvider: (suspend (List<String>) -> Result<SelectionProperties>)? = null

    val queuedFolderStatsRequests = mutableListOf<List<String>>()

    fun emitFolderStatUpdate(update: FolderStatUpdate) {
        folderStatUpdates.tryEmit(update)
    }

    override suspend fun listFiles(path: String): Result<List<FileModel>> =
        listFilesResultProvider?.invoke(path) ?: Result.success(filesByPath[path].orEmpty())

    override fun listFilePages(path: String, pageSize: Int): Flow<ListingPage> = flow {
        val nodePath = StorageNodePath.of(path)
        listFiles(path).fold(
            onSuccess = { files -> emit(ListingPage(nodePath, files, pageIndex = 0, isComplete = true)) },
            onFailure = { error -> emit(ListingPage.failed(nodePath, error)) }
        )
    }

    override suspend fun getCachedFolderStats(paths: Collection<String>): Map<String, FolderStats> =
        paths.mapNotNull { path -> cachedFolderStats[path]?.let { path to it } }.toMap()

    override fun queueFolderStats(paths: List<String>) {
        queuedFolderStatsRequests += paths
    }

    override fun observeFolderStatUpdates(): Flow<FolderStatUpdate> = folderStatUpdates

    override suspend fun getSelectionProperties(paths: List<String>): Result<SelectionProperties> =
        selectionPropertiesResultProvider?.invoke(paths) ?: Result.success(defaultSelectionProperties(paths))
}

class FakeFileMutationRepository : FileMutationRepository {
    var createDirectoryResultProvider: (suspend (String, String) -> Result<FileModel>)? = null
    var createFileResultProvider: (suspend (String, String) -> Result<FileModel>)? = null
    var createFakeFileResultProvider: (suspend (String, String, Long, ((BulkFileOperationProgress) -> Unit)?) -> Result<FileModel>)? = null
    var deleteFileResultProvider: (suspend (String) -> Result<Unit>)? = null
    var deletePermanentlyResult: Result<Unit> = Result.failure(NotImplementedError())
    var deletePermanentlyDetailedResultProvider: (suspend (List<String>) -> Result<BatchMutationResult>)? = null
    var shredResult: Result<Unit> = Result.success(Unit)
    var shredDetailedResultProvider: (suspend (List<String>) -> Result<BatchMutationResult>)? = null
    var renameFileResultProvider: (suspend (String, String) -> Result<FileModel>)? = null

    val createDirectoryRequests = mutableListOf<Pair<String, String>>()
    val createFileRequests = mutableListOf<Pair<String, String>>()
    val createFakeFileRequests = mutableListOf<CreateFakeFileRequest>()
    val deleteFileRequests = mutableListOf<String>()
    val deletePermanentlyRequests = mutableListOf<List<String>>()
    val shredRequests = mutableListOf<List<String>>()
    val renameRequests = mutableListOf<Pair<String, String>>()

    data class CreateFakeFileRequest(val parentPath: String, val name: String, val size: Long)

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> {
        createDirectoryRequests += parentPath to name
        return createDirectoryResultProvider?.invoke(parentPath, name)
            ?: Result.success(testFile(name, "$parentPath/$name", isDirectory = true))
    }

    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> {
        createFileRequests += parentPath to name
        return createFileResultProvider?.invoke(parentPath, name)
            ?: Result.success(testFile(name, "$parentPath/$name"))
    }

    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> {
        createFakeFileRequests += CreateFakeFileRequest(parentPath, name, size)
        return createFakeFileResultProvider?.invoke(parentPath, name, size, onProgress)
            ?: Result.success(testFile(name, "$parentPath/$name", size = size))
    }

    override suspend fun deleteFile(path: String): Result<Unit> {
        deleteFileRequests += path
        return deleteFileResultProvider?.invoke(path) ?: Result.failure(NotImplementedError())
    }

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> {
        deletePermanentlyRequests += paths
        return deletePermanentlyDetailedResultProvider?.invoke(paths)
            ?.fold(
                onSuccess = { it.requireCompleteSuccess("Permanent delete") },
                onFailure = { Result.failure(it) }
            )
            ?: deletePermanentlyResult
    }

    override suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult> {
        deletePermanentlyRequests += paths
        return deletePermanentlyDetailedResultProvider?.invoke(paths)
            ?: deletePermanentlyResult.map { BatchMutationResult(succeededPaths = paths) }
    }

    override suspend fun shred(paths: List<String>): Result<Unit> {
        shredRequests += paths
        return shredDetailedResultProvider?.invoke(paths)
            ?.fold(
                onSuccess = { it.requireCompleteSuccess("Secure shred") },
                onFailure = { Result.failure(it) }
            )
            ?: shredResult
    }

    override suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> {
        shredRequests += paths
        return shredDetailedResultProvider?.invoke(paths)
            ?: shredResult.map { BatchMutationResult(succeededPaths = paths) }
    }

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> {
        renameRequests += path to newName
        return renameFileResultProvider?.invoke(path, newName) ?: Result.failure(NotImplementedError())
    }
}

class FakeClipboardRepository : ClipboardRepository {
    var detectCopyConflictsResultProvider: (suspend (List<String>, String) -> Result<List<FileConflict>>)? = null
    var copyFilesResultProvider: (suspend (List<String>, String, Map<String, ConflictResolution>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null
    var moveFilesResultProvider: (suspend (List<String>, String, Map<String, ConflictResolution>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null

    val copyConflictRequests = mutableListOf<CopyConflictRequest>()
    val copyRequests = mutableListOf<TransferRequest>()
    val moveRequests = mutableListOf<TransferRequest>()

    data class CopyConflictRequest(val sourcePaths: List<String>, val destinationPath: String)
    data class TransferRequest(
        val sourcePaths: List<String>,
        val destinationPath: String,
        val resolutions: Map<String, ConflictResolution>
    )

    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> {
        copyConflictRequests += CopyConflictRequest(sourcePaths, destinationPath)
        return detectCopyConflictsResultProvider?.invoke(sourcePaths, destinationPath) ?: Result.success(emptyList())
    }

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        copyRequests += TransferRequest(sourcePaths, destinationPath, resolutions)
        return copyFilesResultProvider?.invoke(sourcePaths, destinationPath, resolutions, onProgress) ?: Result.success(Unit)
    }

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        moveRequests += TransferRequest(sourcePaths, destinationPath, resolutions)
        return moveFilesResultProvider?.invoke(sourcePaths, destinationPath, resolutions, onProgress) ?: Result.success(Unit)
    }
}

class FakeTrashRepository : TrashRepository {
    var moveToTrashResultProvider: (suspend (List<String>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null
    var restoreFromTrashResultProvider: (suspend (List<String>, String?) -> Result<Unit>)? = null
    var emptyTrashResult: Result<Unit> = Result.failure(NotImplementedError())
    var trashFilesResult: Result<List<TrashMetadata>> = Result.failure(NotImplementedError())
    var deletePermanentlyFromTrashResult: Result<Unit> = Result.failure(NotImplementedError())

    val moveToTrashRequests = mutableListOf<List<String>>()
    val restoreFromTrashRequests = mutableListOf<RestoreRequest>()
    val emptyTrashCalls = mutableListOf<Unit>()
    val deletePermanentlyFromTrashRequests = mutableListOf<List<String>>()

    data class RestoreRequest(val trashIds: List<String>, val destinationPath: String?)

    override suspend fun moveToTrash(
        paths: List<String>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        moveToTrashRequests += paths
        return moveToTrashResultProvider?.invoke(paths, onProgress) ?: Result.success(Unit)
    }

    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> {
        restoreFromTrashRequests += RestoreRequest(trashIds, destinationPath)
        return restoreFromTrashResultProvider?.invoke(trashIds, destinationPath) ?: Result.success(Unit)
    }

    override suspend fun emptyTrash(): Result<Unit> {
        emptyTrashCalls += Unit
        return emptyTrashResult
    }

    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = trashFilesResult

    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> {
        deletePermanentlyFromTrashRequests += trashIds
        return deletePermanentlyFromTrashResult
    }
}

class FakeArchiveRepository : ArchiveRepository {
    var archiveEntriesResultProvider: (suspend (String, String?, ArchiveNameEncoding) -> Result<List<ArchiveEntryModel>>)? = null
    var archiveMetadataResultProvider: (suspend (String, String?, ArchiveNameEncoding) -> Result<ArchiveSummary>)? = null
    var detectArchiveConflictsResultProvider: (suspend (String, String, String?, String?, ArchiveNameEncoding) -> Result<List<FileConflict>>)? = null
    var extractArchiveResultProvider: (suspend (String, String, String?, String?, ArchiveNameEncoding, Map<String, ConflictResolution>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null
    var createArchiveResultProvider: (suspend (List<String>, String, ArchiveFormat, String?, ArchiveNameEncoding, ArchiveCompressionLevel, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null

    val extractArchiveRequests = mutableListOf<ArchiveExtractRequest>()
    val createArchiveRequests = mutableListOf<ArchiveCreateRequest>()

    data class ArchiveExtractRequest(
        val archivePath: String,
        val destinationPath: String,
        val entryPrefix: String?,
        val password: String?,
        val nameEncoding: ArchiveNameEncoding,
        val resolutions: Map<String, ConflictResolution>
    )
    data class ArchiveCreateRequest(
        val sourcePaths: List<String>,
        val destinationArchivePath: String,
        val format: ArchiveFormat,
        val password: String?,
        val nameEncoding: ArchiveNameEncoding,
        val compressionLevel: ArchiveCompressionLevel
    )

    override suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>> =
        listArchiveEntries(archivePath, null, ArchiveNameEncoding.UTF_8)

    override suspend fun listArchiveEntries(archivePath: String, password: String?): Result<List<ArchiveEntryModel>> =
        listArchiveEntries(archivePath, password, ArchiveNameEncoding.UTF_8)

    override suspend fun listArchiveEntries(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<ArchiveEntryModel>> =
        archiveEntriesResultProvider?.invoke(archivePath, password, nameEncoding) ?: Result.success(emptyList())

    override suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary> =
        getArchiveMetadata(archivePath, null, ArchiveNameEncoding.UTF_8)

    override suspend fun getArchiveMetadata(archivePath: String, password: String?): Result<ArchiveSummary> =
        getArchiveMetadata(archivePath, password, ArchiveNameEncoding.UTF_8)

    override suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<ArchiveSummary> =
        archiveMetadataResultProvider?.invoke(archivePath, password, nameEncoding) ?: Result.failure(NotImplementedError())

    override suspend fun detectArchiveConflicts(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<FileConflict>> =
        detectArchiveConflictsResultProvider?.invoke(archivePath, destinationPath, entryPrefix, password, nameEncoding)
            ?: Result.success(emptyList())

    override suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        extractArchiveRequests += ArchiveExtractRequest(archivePath, destinationPath, entryPrefix, password, nameEncoding, resolutions)
        return extractArchiveResultProvider?.invoke(archivePath, destinationPath, entryPrefix, password, nameEncoding, resolutions, onProgress)
            ?: Result.success(Unit)
    }

    override suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        createArchiveRequests += ArchiveCreateRequest(sourcePaths, destinationArchivePath, format, password, nameEncoding, compressionLevel)
        return createArchiveResultProvider?.invoke(sourcePaths, destinationArchivePath, format, password, nameEncoding, compressionLevel, onProgress)
            ?: Result.success(Unit)
    }
}

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
    var trashStorageUsageResult: Result<TrashStorageUsage> = Result.success(TrashStorageUsage(0L, emptyMap()))

    val requestedRecentScopes = mutableListOf<StorageScope>()
    val requestedStorageInfoScopes = mutableListOf<StorageScope>()
    val requestedCategoryScopes = mutableListOf<StorageScope>()

    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>> {
        requestedRecentScopes += scope
        return recentFilesResultProvider?.invoke(scope, limit, offset, minTimestamp)
            ?: Result.success(recentFilesByScope[scope].orEmpty())
    }

    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> {
        requestedStorageInfoScopes += scope
        return storageInfoResultProvider?.invoke(scope) ?: Result.success(storageInfoForScope(scope, observedVolumes))
    }

    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> {
        requestedCategoryScopes += scope
        return categoryStorageResultProvider?.invoke(scope) ?: Result.success(categorySizesByScope[scope].orEmpty())
    }

    override suspend fun getTrashStorageUsage(): Result<TrashStorageUsage> = trashStorageUsageResult
}

class FakeVolumeRepository(volumes: List<StorageVolume> = emptyList()) : VolumeRepository {
    private val observedVolumes = MutableSharedFlow<List<StorageVolume>>(replay = 1).apply {
        tryEmit(volumes)
    }

    var volumeLookupOverride: ((String) -> Result<StorageVolume>)? = null

    fun emitVolumes(volumes: List<StorageVolume>) {
        observedVolumes.tryEmit(volumes)
    }

    fun currentObservedVolumes(): List<StorageVolume> = observedVolumes.replayCache.lastOrNull().orEmpty()

    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = observedVolumes

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> =
        Result.success(currentObservedVolumes())

    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> {
        volumeLookupOverride?.let { return it(path) }
        val volume = currentObservedVolumes()
            .sortedByDescending { it.path.length }
            .firstOrNull { path == it.path || path.startsWith(it.path + "/") || path.startsWith(it.path + File.separator) }
        return volume?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException("No volume for path"))
    }

    override fun getStandardFolders(): Map<String, String?> = emptyMap()
}

class FakeSearchRepository(
    initialFilesByCategory: Map<String, List<FileModel>> = emptyMap()
) : SearchRepository {
    var filesByCategory: Map<String, List<FileModel>> = initialFilesByCategory
    var filesByCategoryResultProvider: (suspend (StorageScope, String) -> Result<List<FileModel>>)? = null
    var searchFilesResultProvider: (suspend (String, StorageScope, SearchFilters?) -> Result<List<FileModel>>)? = null

    val requestedFilesByCategory = mutableListOf<Pair<StorageScope, String>>()
    val searchRequests = mutableListOf<SearchRequest>()

    data class SearchRequest(val query: String, val scope: StorageScope, val filters: SearchFilters?)

    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> {
        requestedFilesByCategory += scope to categoryName
        return filesByCategoryResultProvider?.invoke(scope, categoryName)
            ?: Result.success(filesByCategory[categoryName].orEmpty())
    }

    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> {
        searchRequests += SearchRequest(query, scope, filters)
        return searchFilesResultProvider?.invoke(query, scope, filters) ?: Result.success(emptyList())
    }
}

class FakeStorageRepositoryBundle(
    volumes: List<StorageVolume> = emptyList(),
    initialFilesByPath: Map<String, List<FileModel>> = emptyMap(),
    initialRecentFilesByScope: Map<StorageScope, List<FileModel>> = emptyMap(),
    initialCategorySizesByScope: Map<StorageScope, List<CategoryStorage>> = emptyMap(),
    initialFilesByCategory: Map<String, List<FileModel>> = emptyMap()
) {
    val volumeRepository = FakeVolumeRepository(volumes)
    val fileBrowserRepository = FakeFileBrowserRepository(initialFilesByPath)
    val fileMutationRepository = FakeFileMutationRepository()
    val clipboardRepository = FakeClipboardRepository()
    val trashRepository = FakeTrashRepository()
    val archiveRepository = FakeArchiveRepository()
    val storageAnalyticsRepository = FakeStorageAnalyticsRepository(
        volumes = volumes,
        initialRecentFilesByScope = initialRecentFilesByScope,
        initialCategorySizesByScope = initialCategorySizesByScope
    )
    val searchRepository = FakeSearchRepository(initialFilesByCategory)

    var filesByPath: Map<String, List<FileModel>>
        get() = fileBrowserRepository.filesByPath
        set(value) {
            fileBrowserRepository.filesByPath = value
        }
    var cachedFolderStats: Map<String, FolderStats>
        get() = fileBrowserRepository.cachedFolderStats
        set(value) {
            fileBrowserRepository.cachedFolderStats = value
        }
    var recentFilesByScope: Map<StorageScope, List<FileModel>>
        get() = storageAnalyticsRepository.recentFilesByScope
        set(value) {
            storageAnalyticsRepository.recentFilesByScope = value
        }
    var categorySizesByScope: Map<StorageScope, List<CategoryStorage>>
        get() = storageAnalyticsRepository.categorySizesByScope
        set(value) {
            storageAnalyticsRepository.categorySizesByScope = value
        }
    var filesByCategory: Map<String, List<FileModel>>
        get() = searchRepository.filesByCategory
        set(value) {
            searchRepository.filesByCategory = value
        }
    var storageInfoResultProvider: (suspend (StorageScope) -> Result<StorageInfo>)?
        get() = storageAnalyticsRepository.storageInfoResultProvider
        set(value) {
            storageAnalyticsRepository.storageInfoResultProvider = value
        }
    var recentFilesResultProvider: (suspend (StorageScope, Int, Int, Long) -> Result<List<FileModel>>)?
        get() = storageAnalyticsRepository.recentFilesResultProvider
        set(value) {
            storageAnalyticsRepository.recentFilesResultProvider = value
        }
    var categoryStorageResultProvider: (suspend (StorageScope) -> Result<List<CategoryStorage>>)?
        get() = storageAnalyticsRepository.categoryStorageResultProvider
        set(value) {
            storageAnalyticsRepository.categoryStorageResultProvider = value
        }
    var searchFilesResultProvider: (suspend (String, StorageScope, SearchFilters?) -> Result<List<FileModel>>)?
        get() = searchRepository.searchFilesResultProvider
        set(value) {
            searchRepository.searchFilesResultProvider = value
        }
    var filesByCategoryResultProvider: (suspend (StorageScope, String) -> Result<List<FileModel>>)?
        get() = searchRepository.filesByCategoryResultProvider
        set(value) {
            searchRepository.filesByCategoryResultProvider = value
        }
    var listFilesResultProvider: (suspend (String) -> Result<List<FileModel>>)?
        get() = fileBrowserRepository.listFilesResultProvider
        set(value) {
            fileBrowserRepository.listFilesResultProvider = value
        }
    var selectionPropertiesResultProvider: (suspend (List<String>) -> Result<SelectionProperties>)?
        get() = fileBrowserRepository.selectionPropertiesResultProvider
        set(value) {
            fileBrowserRepository.selectionPropertiesResultProvider = value
        }
    var createDirectoryResultProvider: (suspend (String, String) -> Result<FileModel>)?
        get() = fileMutationRepository.createDirectoryResultProvider
        set(value) {
            fileMutationRepository.createDirectoryResultProvider = value
        }
    var createFileResultProvider: (suspend (String, String) -> Result<FileModel>)?
        get() = fileMutationRepository.createFileResultProvider
        set(value) {
            fileMutationRepository.createFileResultProvider = value
        }
    var deleteFileResultProvider: (suspend (String) -> Result<Unit>)?
        get() = fileMutationRepository.deleteFileResultProvider
        set(value) {
            fileMutationRepository.deleteFileResultProvider = value
        }
    var deletePermanentlyResult: Result<Unit>
        get() = fileMutationRepository.deletePermanentlyResult
        set(value) {
            fileMutationRepository.deletePermanentlyResult = value
        }
    var shredResult: Result<Unit>
        get() = fileMutationRepository.shredResult
        set(value) {
            fileMutationRepository.shredResult = value
        }
    var renameFileResultProvider: (suspend (String, String) -> Result<FileModel>)?
        get() = fileMutationRepository.renameFileResultProvider
        set(value) {
            fileMutationRepository.renameFileResultProvider = value
        }
    var detectCopyConflictsResultProvider: (suspend (List<String>, String) -> Result<List<FileConflict>>)?
        get() = clipboardRepository.detectCopyConflictsResultProvider
        set(value) {
            clipboardRepository.detectCopyConflictsResultProvider = value
        }
    var copyFilesResultProvider: (suspend (List<String>, String, Map<String, ConflictResolution>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)?
        get() = clipboardRepository.copyFilesResultProvider
        set(value) {
            clipboardRepository.copyFilesResultProvider = value
        }
    var moveFilesResultProvider: (suspend (List<String>, String, Map<String, ConflictResolution>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)?
        get() = clipboardRepository.moveFilesResultProvider
        set(value) {
            clipboardRepository.moveFilesResultProvider = value
        }
    var moveToTrashResultProvider: (suspend (List<String>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)?
        get() = trashRepository.moveToTrashResultProvider
        set(value) {
            trashRepository.moveToTrashResultProvider = value
        }
    var restoreFromTrashResultProvider: (suspend (List<String>, String?) -> Result<Unit>)?
        get() = trashRepository.restoreFromTrashResultProvider
        set(value) {
            trashRepository.restoreFromTrashResultProvider = value
        }
    var emptyTrashResult: Result<Unit>
        get() = trashRepository.emptyTrashResult
        set(value) {
            trashRepository.emptyTrashResult = value
        }
    var trashFilesResult: Result<List<TrashMetadata>>
        get() = trashRepository.trashFilesResult
        set(value) {
            trashRepository.trashFilesResult = value
        }
    var trashStorageUsageResult: Result<TrashStorageUsage>
        get() = storageAnalyticsRepository.trashStorageUsageResult
        set(value) {
            storageAnalyticsRepository.trashStorageUsageResult = value
        }
    var deletePermanentlyFromTrashResult: Result<Unit>
        get() = trashRepository.deletePermanentlyFromTrashResult
        set(value) {
            trashRepository.deletePermanentlyFromTrashResult = value
        }

    val requestedRecentScopes: MutableList<StorageScope>
        get() = storageAnalyticsRepository.requestedRecentScopes
    val requestedStorageInfoScopes: MutableList<StorageScope>
        get() = storageAnalyticsRepository.requestedStorageInfoScopes
    val requestedCategoryScopes: MutableList<StorageScope>
        get() = storageAnalyticsRepository.requestedCategoryScopes
    val requestedFilesByCategory: MutableList<Pair<StorageScope, String>>
        get() = searchRepository.requestedFilesByCategory
    val searchRequests: MutableList<FakeSearchRepository.SearchRequest>
        get() = searchRepository.searchRequests
    val copyConflictRequests: MutableList<FakeClipboardRepository.CopyConflictRequest>
        get() = clipboardRepository.copyConflictRequests
    val createDirectoryRequests: MutableList<Pair<String, String>>
        get() = fileMutationRepository.createDirectoryRequests
    val createFileRequests: MutableList<Pair<String, String>>
        get() = fileMutationRepository.createFileRequests
    val deleteFileRequests: MutableList<String>
        get() = fileMutationRepository.deleteFileRequests
    val deletePermanentlyRequests: MutableList<List<String>>
        get() = fileMutationRepository.deletePermanentlyRequests
    val shredRequests: MutableList<List<String>>
        get() = fileMutationRepository.shredRequests
    val renameRequests: MutableList<Pair<String, String>>
        get() = fileMutationRepository.renameRequests
    val copyRequests: MutableList<FakeClipboardRepository.TransferRequest>
        get() = clipboardRepository.copyRequests
    val moveRequests: MutableList<FakeClipboardRepository.TransferRequest>
        get() = clipboardRepository.moveRequests
    val moveToTrashRequests: MutableList<List<String>>
        get() = trashRepository.moveToTrashRequests
    val restoreFromTrashRequests: MutableList<FakeTrashRepository.RestoreRequest>
        get() = trashRepository.restoreFromTrashRequests
    val emptyTrashCalls: MutableList<Unit>
        get() = trashRepository.emptyTrashCalls
    val deletePermanentlyFromTrashRequests: MutableList<List<String>>
        get() = trashRepository.deletePermanentlyFromTrashRequests
    val queuedFolderStatsRequests: MutableList<List<String>>
        get() = fileBrowserRepository.queuedFolderStatsRequests

    fun emitVolumes(volumes: List<StorageVolume>) {
        volumeRepository.emitVolumes(volumes)
        storageAnalyticsRepository.observedVolumes = volumes
    }

    fun currentObservedVolumes(): List<StorageVolume> = volumeRepository.currentObservedVolumes()

    fun emitFolderStatUpdate(update: FolderStatUpdate) {
        fileBrowserRepository.emitFolderStatUpdate(update)
    }
}

private fun defaultSelectionProperties(paths: List<String>): SelectionProperties =
    SelectionProperties(
        displayName = if (paths.size == 1) File(paths.first()).name else "${paths.size} items",
        pathSummary = File(paths.first()).parent.orEmpty(),
        itemCount = paths.size,
        fileCount = paths.size,
        folderCount = 0,
        totalBytes = paths.size.toLong(),
        newestModifiedAt = 1L,
        oldestModifiedAt = 1L,
        mimeTypeSummary = null,
        extensionSummary = null,
        hiddenCount = 0,
        accessStatus = PropertiesAccessStatus.Full,
        isSingleItem = paths.size == 1,
        isDirectory = false
    )

private fun storageInfoForScope(scope: StorageScope, volumes: List<StorageVolume>): StorageInfo =
    when (scope) {
        StorageScope.AllStorage -> StorageInfo(volumes)
        is StorageScope.Volume -> StorageInfo(volumes.filter { it.id == scope.volumeId })
        is StorageScope.Path -> StorageInfo(volumes.filter { it.id == scope.volumeId })
        is StorageScope.Category -> StorageInfo(volumes.filter { it.id == scope.volumeId })
    }
