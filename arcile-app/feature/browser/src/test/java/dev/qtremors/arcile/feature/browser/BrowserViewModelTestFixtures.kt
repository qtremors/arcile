package dev.qtremors.arcile.feature.browser

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageMutationEvent
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeFilePreferencesStore
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableSharedFlow

internal fun createViewModel(
    repository: BrowserFakeFileRepository,
    browserPreferencesRepository: BrowserLocationPreferencesStore = FakeFilePreferencesStore(),
    savedStateHandle: SavedStateHandle,
    bulkFileOperationCoordinator: BulkFileOperationCoordinator = FakeBulkFileOperationCoordinator(),
    storageMutationNotifier: StorageMutationNotifier = FakeStorageMutationNotifier()
): BrowserViewModel = BrowserViewModel(
    fileBrowserRepository = repository.fileBrowserRepository,
    fileMutationRepository = repository.fileMutationRepository,
    searchRepository = repository.searchRepository,
    clipboardRepository = repository.clipboardRepository,
    trashRepository = repository.trashRepository,
    archiveRepository = repository.archiveRepository,
    archivePathResolver = BrowserTestArchivePathResolver,
    volumeRepository = repository.volumeRepository,
    browserPreferencesRepository = browserPreferencesRepository,
    savedStateHandle = savedStateHandle,
    getStorageVolumesUseCase = GetStorageVolumesUseCase(repository.volumeRepository),
    bulkFileCoordinator = bulkFileOperationCoordinator,
    storageMutationNotifier = storageMutationNotifier
)

private object BrowserTestArchivePathResolver :
    dev.qtremors.arcile.core.storage.domain.ArchivePathResolver {
    override suspend fun resolve(
        request: dev.qtremors.arcile.core.storage.domain.ArchivePathRequest
    ): Result<String> {
        val name = request.requestedName
            ?.removeSuffix(".${request.format.extension}")
            ?.takeIf(String::isNotBlank)
            ?: "Archive"
        return Result.success(
            "${request.parentPath.orEmpty().trimEnd('/')}/$name.${request.format.extension}"
        )
    }
}

class FakeStorageMutationNotifier : StorageMutationNotifier {
    private val _events = MutableSharedFlow<StorageMutationEvent>(extraBufferCapacity = 16)
    override val events = _events
    override fun notify(paths: Collection<String>) {
        _events.tryEmit(StorageMutationEvent(paths.toList()))
    }
}

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
) {
    private val delegate = FakeStorageRepositoryBundle(
        volumes = volumes,
        initialFilesByPath = filesByPath,
        initialFilesByCategory = filesByCategory
    ).apply {
        this.cachedFolderStats = cachedFolderStats
        searchFilesResultProvider = { _, _, _ -> searchResult }
        detectCopyConflictsResultProvider = { _, _ -> conflictsResult }
        moveToTrashResultProvider = { _, _ -> moveToTrashResult }
        renameFileResultProvider = { _, _ -> renameResult }
        selectionPropertiesResultProvider = { selectionPropertiesResult }
    }

    val fileBrowserRepository = delegate.fileBrowserRepository
    val fileMutationRepository = delegate.fileMutationRepository
    val searchRepository = delegate.searchRepository
    val clipboardRepository = delegate.clipboardRepository
    val trashRepository = delegate.trashRepository
    val archiveRepository = delegate.archiveRepository
    val volumeRepository = delegate.volumeRepository

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
    val moveRequests
        get() = delegate.moveRequests
    val deletePermanentlyRequests
        get() = delegate.deletePermanentlyRequests
    var filesByPath: Map<String, List<FileModel>>
        get() = delegate.filesByPath
        set(value) {
            delegate.filesByPath = value
        }

    suspend fun listFiles(path: String) = delegate.fileBrowserRepository.listFiles(path)
    fun listFilePages(path: String, pageSize: Int) = delegate.fileBrowserRepository.listFilePages(path, pageSize)
    suspend fun getCachedFolderStats(paths: Collection<String>) = delegate.fileBrowserRepository.getCachedFolderStats(paths)
    fun queueFolderStats(paths: List<String>) = delegate.fileBrowserRepository.queueFolderStats(paths)
    fun observeFolderStatUpdates() = delegate.fileBrowserRepository.observeFolderStatUpdates()
    suspend fun getSelectionProperties(paths: List<String>) = delegate.fileBrowserRepository.getSelectionProperties(paths)
    suspend fun createDirectory(parentPath: String, name: String) = delegate.fileMutationRepository.createDirectory(parentPath, name)
    suspend fun createFile(parentPath: String, name: String) = delegate.fileMutationRepository.createFile(parentPath, name)
    suspend fun deleteFile(path: String) = delegate.fileMutationRepository.deleteFile(path)
    suspend fun deletePermanently(paths: List<String>) = delegate.fileMutationRepository.deletePermanently(paths)
    suspend fun renameFile(path: String, newName: String) = delegate.fileMutationRepository.renameFile(path, newName)
    fun observeStorageVolumes() = delegate.volumeRepository.observeStorageVolumes()
    suspend fun getStorageVolumes() = delegate.volumeRepository.getStorageVolumes()
    suspend fun getVolumeForPath(path: String) = delegate.volumeRepository.getVolumeForPath(path)
    fun getStandardFolders() = delegate.volumeRepository.getStandardFolders()
    suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long) =
        delegate.storageAnalyticsRepository.getRecentFiles(scope, limit, offset, minTimestamp)
    suspend fun getStorageInfo(scope: StorageScope) = delegate.storageAnalyticsRepository.getStorageInfo(scope)
    suspend fun getCategoryStorageSizes(scope: StorageScope) = delegate.storageAnalyticsRepository.getCategoryStorageSizes(scope)
    suspend fun getTrashStorageUsage(): Result<TrashStorageUsage> = Result.success(TrashStorageUsage(0L, emptyMap()))
    suspend fun getFilesByCategory(scope: StorageScope, categoryName: String) =
        delegate.searchRepository.getFilesByCategory(scope, categoryName)
    suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?) =
        delegate.searchRepository.searchFiles(query, scope, filters)
    suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String) =
        delegate.clipboardRepository.detectCopyConflicts(sourcePaths, destinationPath)
    suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) = delegate.clipboardRepository.copyFiles(sourcePaths, destinationPath, resolutions, onProgress)
    suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) = delegate.clipboardRepository.moveFiles(sourcePaths, destinationPath, resolutions, onProgress)
    suspend fun moveToTrash(paths: List<String>, onProgress: ((BulkFileOperationProgress) -> Unit)?) =
        delegate.trashRepository.moveToTrash(paths, onProgress)
    suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?) =
        delegate.trashRepository.restoreFromTrash(trashIds, destinationPath)
    suspend fun emptyTrash() = delegate.trashRepository.emptyTrash()
    suspend fun getTrashFiles() = delegate.trashRepository.getTrashFiles()
    suspend fun deletePermanentlyFromTrash(trashIds: List<String>) =
        delegate.trashRepository.deletePermanentlyFromTrash(trashIds)
    suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = delegate.fileMutationRepository.createFakeFile(parentPath, name, size, onProgress)

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
