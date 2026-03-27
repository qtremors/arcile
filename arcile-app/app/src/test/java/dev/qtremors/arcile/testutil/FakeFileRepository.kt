package dev.qtremors.arcile.testutil

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
import dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

class FakeFileRepository(
    volumes: List<StorageVolume> = emptyList(),
    initialFilesByPath: Map<String, List<FileModel>> = emptyMap(),
    initialRecentFilesByScope: Map<StorageScope, List<FileModel>> = emptyMap(),
    initialCategorySizesByScope: Map<StorageScope, List<CategoryStorage>> = emptyMap(),
    initialFilesByCategory: Map<String, List<FileModel>> = emptyMap()
) : FileRepository {

    private val observedVolumes = MutableSharedFlow<List<StorageVolume>>(replay = 1).apply {
        tryEmit(volumes)
    }

    var filesByPath: Map<String, List<FileModel>> = initialFilesByPath
    var recentFilesByScope: Map<StorageScope, List<FileModel>> = initialRecentFilesByScope
    var categorySizesByScope: Map<StorageScope, List<CategoryStorage>> = initialCategorySizesByScope
    var filesByCategory: Map<String, List<FileModel>> = initialFilesByCategory
    var volumeLookupOverride: ((String) -> Result<StorageVolume>)? = null
    var listFilesResultProvider: (suspend (String) -> Result<List<FileModel>>)? = null
    var createDirectoryResultProvider: (suspend (String, String) -> Result<FileModel>)? = null
    var createFileResultProvider: (suspend (String, String) -> Result<FileModel>)? = null
    var deleteFileResultProvider: (suspend (String) -> Result<Unit>)? = null
    var deletePermanentlyResult: Result<Unit> = Result.failure(NotImplementedError())
    var renameFileResultProvider: (suspend (String, String) -> Result<FileModel>)? = null
    var storageInfoResultProvider: (suspend (StorageScope) -> Result<StorageInfo>)? = null
    var recentFilesResultProvider: (suspend (StorageScope, Int, Int, Long) -> Result<List<FileModel>>)? = null
    var categoryStorageResultProvider: (suspend (StorageScope) -> Result<List<CategoryStorage>>)? = null
    var filesByCategoryResultProvider: (suspend (StorageScope, String) -> Result<List<FileModel>>)? = null
    var searchFilesResultProvider: (suspend (String, StorageScope, SearchFilters?) -> Result<List<FileModel>>)? = null
    var detectCopyConflictsResultProvider: (suspend (List<String>, String) -> Result<List<FileConflict>>)? = null
    var copyFilesResultProvider: (suspend (List<String>, String, Map<String, ConflictResolution>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null
    var moveFilesResultProvider: (suspend (List<String>, String, Map<String, ConflictResolution>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null
    var moveToTrashResultProvider: (suspend (List<String>) -> Result<Unit>)? = null
    var restoreFromTrashResultProvider: (suspend (List<String>, String?) -> Result<Unit>)? = null
    var emptyTrashResult: Result<Unit> = Result.failure(NotImplementedError())
    var trashFilesResult: Result<List<TrashMetadata>> = Result.failure(NotImplementedError())
    var deletePermanentlyFromTrashResult: Result<Unit> = Result.failure(NotImplementedError())

    val requestedRecentScopes = mutableListOf<StorageScope>()
    val requestedStorageInfoScopes = mutableListOf<StorageScope>()
    val requestedCategoryScopes = mutableListOf<StorageScope>()
    val requestedFilesByCategory = mutableListOf<Pair<StorageScope, String>>()
    val searchRequests = mutableListOf<SearchRequest>()
    val copyConflictRequests = mutableListOf<CopyConflictRequest>()
    val createDirectoryRequests = mutableListOf<Pair<String, String>>()
    val createFileRequests = mutableListOf<Pair<String, String>>()
    val deleteFileRequests = mutableListOf<String>()
    val deletePermanentlyRequests = mutableListOf<List<String>>()
    val renameRequests = mutableListOf<Pair<String, String>>()
    val copyRequests = mutableListOf<TransferRequest>()
    val moveRequests = mutableListOf<TransferRequest>()
    val moveToTrashRequests = mutableListOf<List<String>>()
    val restoreFromTrashRequests = mutableListOf<RestoreRequest>()
    val emptyTrashCalls = mutableListOf<Unit>()
    val deletePermanentlyFromTrashRequests = mutableListOf<List<String>>()

    data class SearchRequest(val query: String, val scope: StorageScope, val filters: SearchFilters?)
    data class CopyConflictRequest(val sourcePaths: List<String>, val destinationPath: String)
    data class TransferRequest(
        val sourcePaths: List<String>,
        val destinationPath: String,
        val resolutions: Map<String, ConflictResolution>
    )
    data class RestoreRequest(val trashIds: List<String>, val destinationPath: String?)

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

    override suspend fun listFiles(path: String): Result<List<FileModel>> =
        listFilesResultProvider?.invoke(path) ?: Result.success(filesByPath[path].orEmpty())

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

    override suspend fun deleteFile(path: String): Result<Unit> {
        deleteFileRequests += path
        return deleteFileResultProvider?.invoke(path) ?: Result.failure(NotImplementedError())
    }

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> {
        deletePermanentlyRequests += paths
        return deletePermanentlyResult
    }

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> {
        renameRequests += path to newName
        return renameFileResultProvider?.invoke(path, newName) ?: Result.failure(NotImplementedError())
    }

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
        return storageInfoResultProvider?.invoke(scope) ?: Result.success(
            when (scope) {
                StorageScope.AllStorage -> StorageInfo(currentObservedVolumes())
                is StorageScope.Volume -> StorageInfo(currentObservedVolumes().filter { it.id == scope.volumeId })
                is StorageScope.Path -> StorageInfo(currentObservedVolumes().filter { it.id == scope.volumeId })
                is StorageScope.Category -> StorageInfo(currentObservedVolumes().filter { it.id == scope.volumeId })
            }
        )
    }

    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> {
        requestedCategoryScopes += scope
        return categoryStorageResultProvider?.invoke(scope)
            ?: Result.success(categorySizesByScope[scope].orEmpty())
    }

    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> {
        requestedFilesByCategory += scope to categoryName
        return filesByCategoryResultProvider?.invoke(scope, categoryName)
            ?: Result.success(filesByCategory[categoryName].orEmpty())
    }

    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> {
        searchRequests += SearchRequest(query, scope, filters)
        return searchFilesResultProvider?.invoke(query, scope, filters) ?: Result.success(emptyList())
    }

    override suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>> {
        copyConflictRequests += CopyConflictRequest(sourcePaths, destinationPath)
        return detectCopyConflictsResultProvider?.invoke(sourcePaths, destinationPath)
            ?: Result.success(emptyList())
    }

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        copyRequests += TransferRequest(sourcePaths, destinationPath, resolutions)
        return copyFilesResultProvider?.invoke(sourcePaths, destinationPath, resolutions, onProgress)
            ?: Result.success(Unit)
    }

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        moveRequests += TransferRequest(sourcePaths, destinationPath, resolutions)
        return moveFilesResultProvider?.invoke(sourcePaths, destinationPath, resolutions, onProgress)
            ?: Result.success(Unit)
    }

    override suspend fun moveToTrash(paths: List<String>): Result<Unit> {
        moveToTrashRequests += paths
        return moveToTrashResultProvider?.invoke(paths) ?: Result.success(Unit)
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
