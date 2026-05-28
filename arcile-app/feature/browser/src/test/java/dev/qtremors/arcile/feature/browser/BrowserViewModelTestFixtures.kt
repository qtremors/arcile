package dev.qtremors.arcile.feature.browser

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeBrowserPreferencesStore
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeFileRepository
import io.mockk.mockk

fun createViewModel(
    repository: FileRepository,
    browserPreferencesRepository: BrowserPreferencesStore = FakeBrowserPreferencesStore(),
    savedStateHandle: SavedStateHandle,
    bulkFileOperationCoordinator: BulkFileOperationCoordinator = FakeBulkFileOperationCoordinator()
): BrowserViewModel = BrowserViewModel(
    repository = repository,
    browserPreferencesRepository = browserPreferencesRepository,
    savedStateHandle = savedStateHandle,
    getStorageVolumesUseCase = GetStorageVolumesUseCase(repository),
    bulkFileCoordinator = bulkFileOperationCoordinator
)

class BrowserFakeFileRepository(
    volumes: List<StorageVolume> = emptyList(),
    filesByPath: Map<String, List<FileModel>> = emptyMap(),
    filesByCategory: Map<String, List<FileModel>> = emptyMap(),
    cachedFolderStats: Map<String, FolderStats> = emptyMap(),
    searchResult: Result<List<FileModel>> = Result.success(emptyList()),
    conflictsResult: Result<List<FileConflict>> = Result.success(emptyList()),
    moveToTrashResult: Result<Unit> = Result.success(Unit),
    renameResult: Result<FileModel> = Result.failure(NotImplementedError()),
    selectionPropertiesResult: Result<SelectionProperties> = Result.failure(NotImplementedError())
) : FileRepository {
    private val delegate = FakeFileRepository(
        volumes = volumes,
        initialFilesByPath = filesByPath,
        initialFilesByCategory = filesByCategory
    ).apply {
        this.cachedFolderStats = cachedFolderStats
        searchFilesResultProvider = { _, _, _ -> searchResult }
        detectCopyConflictsResultProvider = { _, _ -> conflictsResult }
        moveToTrashResultProvider = { moveToTrashResult }
        renameFileResultProvider = { _, _ -> renameResult }
        selectionPropertiesResultProvider = { selectionPropertiesResult }
    }

    val lastSearchQuery: String?
        get() = delegate.searchRequests.lastOrNull()?.query
    val lastSearchScope: StorageScope?
        get() = delegate.searchRequests.lastOrNull()?.scope
    val lastSearchFilters: SearchFilters?
        get() = delegate.searchRequests.lastOrNull()?.filters
    val lastConflictSourcePaths: List<String>?
        get() = delegate.copyConflictRequests.lastOrNull()?.sourcePaths
    val lastConflictDestination: String?
        get() = delegate.copyConflictRequests.lastOrNull()?.destinationPath
    val lastQueuedFolderStats: List<String>?
        get() = delegate.queuedFolderStatsRequests.lastOrNull()
    val lastRenamePath: String?
        get() = delegate.renameRequests.lastOrNull()?.first
    val lastRenameNewName: String?
        get() = delegate.renameRequests.lastOrNull()?.second
    var filesByPath: Map<String, List<FileModel>>
        get() = delegate.filesByPath
        set(value) {
            delegate.filesByPath = value
        }

    override suspend fun listFiles(path: String) = delegate.listFiles(path)
    override fun listFilePages(path: String, pageSize: Int) = delegate.listFilePages(path, pageSize)
    override suspend fun getCachedFolderStats(paths: Collection<String>) = delegate.getCachedFolderStats(paths)
    override fun queueFolderStats(paths: List<String>) = delegate.queueFolderStats(paths)
    override fun observeFolderStatUpdates() = delegate.observeFolderStatUpdates()
    override suspend fun getSelectionProperties(paths: List<String>) = delegate.getSelectionProperties(paths)
    override suspend fun createDirectory(parentPath: String, name: String) = delegate.createDirectory(parentPath, name)
    override suspend fun createFile(parentPath: String, name: String) = delegate.createFile(parentPath, name)
    override suspend fun deleteFile(path: String) = delegate.deleteFile(path)
    override suspend fun deletePermanently(paths: List<String>) = delegate.deletePermanently(paths)
    override suspend fun renameFile(path: String, newName: String) = delegate.renameFile(path, newName)
    override fun observeStorageVolumes() = delegate.observeStorageVolumes()
    override suspend fun getStorageVolumes() = delegate.getStorageVolumes()
    override suspend fun getVolumeForPath(path: String) = delegate.getVolumeForPath(path)
    override fun getStandardFolders() = delegate.getStandardFolders()
    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long) =
        delegate.getRecentFiles(scope, limit, offset, minTimestamp)
    override suspend fun getStorageInfo(scope: StorageScope) = delegate.getStorageInfo(scope)
    override suspend fun getCategoryStorageSizes(scope: StorageScope) = delegate.getCategoryStorageSizes(scope)
    override suspend fun getTrashStorageUsage(): Result<TrashStorageUsage> = Result.success(TrashStorageUsage(0L, emptyMap()))
    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String) =
        delegate.getFilesByCategory(scope, categoryName)
    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?) =
        delegate.searchFiles(query, scope, filters)
    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String) =
        delegate.detectCopyConflicts(sourcePaths, destinationPath)
    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) = delegate.copyFiles(sourcePaths, destinationPath, resolutions, onProgress)
    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) = delegate.moveFiles(sourcePaths, destinationPath, resolutions, onProgress)
    override suspend fun moveToTrash(paths: List<String>, onProgress: ((BulkFileOperationProgress) -> Unit)?) =
        delegate.moveToTrash(paths, onProgress)
    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?) =
        delegate.restoreFromTrash(trashIds, destinationPath)
    override suspend fun emptyTrash() = delegate.emptyTrash()
    override suspend fun getTrashFiles() = delegate.getTrashFiles()
    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>) =
        delegate.deletePermanentlyFromTrash(trashIds)
    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = delegate.createFakeFile(parentPath, name, size, onProgress)

    fun emitFolderStatUpdate(update: FolderStatUpdate) {
        delegate.emitFolderStatUpdate(update)
    }
}

fun browserVolume(
    id: String,
    name: String,
    path: String,
    isPrimary: Boolean,
    isRemovable: Boolean = false,
    kind: StorageKind = if (isPrimary) StorageKind.INTERNAL else StorageKind.SD_CARD
) = StorageVolume(
    id = id,
    storageKey = id,
    name = name,
    path = path,
    totalBytes = 1_000L,
    freeBytes = 250L,
    isPrimary = isPrimary,
    isRemovable = isRemovable,
    kind = kind
)

fun browserFile(name: String, path: String, isDirectory: Boolean = false) = FileModel(
    name = name,
    absolutePath = path,
    size = 10L,
    lastModified = 20L,
    isDirectory = isDirectory,
    extension = if (isDirectory) "" else name.substringAfterLast('.', ""),
    isHidden = false
)

fun fakeIntentSender(): IntentSender = mockk(relaxed = true)
