package dev.qtremors.arcile.data

import dev.qtremors.arcile.data.manager.TrashManager
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.FileSystemDataSource
import dev.qtremors.arcile.data.source.MediaStoreClient
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FolderStatUpdate
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress
import dev.qtremors.arcile.testutil.createTempStorageRoot
import dev.qtremors.arcile.testutil.testFile
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LocalFileRepositoryTest {

    @Test
    fun `deleteFile routes trash-capable volume to trash manager`() = runTest {
        val root = createTempStorageRoot("repo-trash")
        try {
            val filePath = File(root, "Download/file.txt").absolutePath
            File(filePath).parentFile?.mkdirs()
            File(filePath).writeText("hi")
            val volume = testVolume("primary", root.absolutePath, kind = StorageKind.INTERNAL)
            val volumeProvider = RecordingVolumeProvider(listOf(volume))
            val trashManager = RecordingTrashManager()
            val dataSource = RecordingFileSystemDataSource()
            val repository = LocalFileRepository(volumeProvider, RecordingMediaStoreClient(), trashManager, dataSource, RecordingFolderStatsStore())

            val result = repository.deleteFile(filePath)

            assertTrue(result.isSuccess)
            assertEquals(listOf(listOf(filePath)), trashManager.moveToTrashRequests)
            assertTrue(dataSource.deletePermanentlyRequests.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `deleteFile routes non-trash volume to permanent delete`() = runTest {
        val root = createTempStorageRoot("repo-delete")
        try {
            val filePath = File(root, "file.txt").absolutePath
            File(filePath).writeText("hi")
            val volume = testVolume("otg", root.absolutePath, kind = StorageKind.OTG, isPrimary = false, isRemovable = true)
            val volumeProvider = RecordingVolumeProvider(listOf(volume))
            val trashManager = RecordingTrashManager()
            val dataSource = RecordingFileSystemDataSource()
            val repository = LocalFileRepository(volumeProvider, RecordingMediaStoreClient(), trashManager, dataSource, RecordingFolderStatsStore())

            val result = repository.deleteFile(filePath)

            assertTrue(result.isSuccess)
            assertEquals(listOf(listOf(filePath)), dataSource.deletePermanentlyRequests)
            assertTrue(trashManager.moveToTrashRequests.isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `category and search queries delegate to media store client`() = runTest {
        val mediaStoreClient = RecordingMediaStoreClient().apply {
            categorySizesResult = Result.success(listOf(CategoryStorage("Images", 42L, setOf("jpg"))))
            categoryFilesResult = Result.success(listOf(testFile("photo.jpg", "/storage/emulated/0/photo.jpg")))
            searchResult = Result.success(listOf(testFile("match.txt", "/storage/emulated/0/match.txt")))
        }
        val repository = LocalFileRepository(
            RecordingVolumeProvider(listOf(testVolume("primary", "/storage/emulated/0", kind = StorageKind.INTERNAL))),
            mediaStoreClient,
            RecordingTrashManager(),
            RecordingFileSystemDataSource(),
            RecordingFolderStatsStore()
        )

        val scope = StorageScope.Volume("primary")
        val filters = SearchFilters(fileType = "Docs")

        assertEquals(42L, repository.getCategoryStorageSizes(scope).getOrThrow().single().sizeBytes)
        assertEquals("Images" to scope, mediaStoreClient.lastCategoryScope)
        assertEquals("photo.jpg", repository.getFilesByCategory(scope, "Images").getOrThrow().single().name)
        repository.searchFiles("match", scope, filters)

        assertEquals(Triple("match", scope, filters), mediaStoreClient.lastSearchRequest)
    }

    @Test
    fun `copy and move delegate to file system data source with resolutions`() = runTest {
        val dataSource = RecordingFileSystemDataSource()
        val repository = LocalFileRepository(
            RecordingVolumeProvider(emptyList()),
            RecordingMediaStoreClient(),
            RecordingTrashManager(),
            dataSource,
            RecordingFolderStatsStore()
        )
        val resolutions = mapOf("/from/a.txt" to ConflictResolution.REPLACE)

        repository.copyFiles(listOf("/from/a.txt"), "/to", resolutions, onProgress = null)
        repository.moveFiles(listOf("/from/a.txt"), "/to", resolutions, onProgress = null)

        assertEquals(TransferCall(listOf("/from/a.txt"), "/to", resolutions), dataSource.copyRequests.single())
        assertEquals(TransferCall(listOf("/from/a.txt"), "/to", resolutions), dataSource.moveRequests.single())
    }
}

private data class TransferCall(
    val sourcePaths: List<String>,
    val destinationPath: String,
    val resolutions: Map<String, ConflictResolution>
)

private class RecordingVolumeProvider(
    private val volumes: List<StorageVolume>
) : VolumeProvider {
    private val flow = MutableStateFlow(volumes)
    override val activeStorageRoots: List<String> = volumes.map { it.path }
    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = flow.asStateFlow()
    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(volumes)
    override suspend fun currentVolumes(): List<StorageVolume> = volumes
    override fun invalidateCache() = Unit
}

private class RecordingMediaStoreClient : MediaStoreClient {
    var categorySizesResult: Result<List<CategoryStorage>> = Result.success(emptyList())
    var categoryFilesResult: Result<List<FileModel>> = Result.success(emptyList())
    var searchResult: Result<List<FileModel>> = Result.success(emptyList())
    var lastCategoryScope: Pair<String, StorageScope>? = null
    var lastSearchRequest: Triple<String, StorageScope, SearchFilters?>? = null

    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>> =
        Result.success(emptyList())

    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> {
        lastCategoryScope = "Images" to scope
        return categorySizesResult
    }

    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> {
        lastCategoryScope = categoryName to scope
        return categoryFilesResult
    }

    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> {
        lastSearchRequest = Triple(query, scope, filters)
        return searchResult
    }

    override suspend fun invalidateCache(vararg paths: String) = Unit
}

private class RecordingTrashManager : TrashManager {
    val moveToTrashRequests = mutableListOf<List<String>>()

    override suspend fun moveToTrash(paths: List<String>): Result<Unit> {
        moveToTrashRequests += paths
        return Result.success(Unit)
    }

    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> = Result.success(Unit)
    override suspend fun emptyTrash(): Result<Unit> = Result.success(Unit)
    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = Result.success(emptyList())
    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> = Result.success(Unit)
}

private class RecordingFileSystemDataSource : FileSystemDataSource {
    val deletePermanentlyRequests = mutableListOf<List<String>>()
    val copyRequests = mutableListOf<TransferCall>()
    val moveRequests = mutableListOf<TransferCall>()

    override fun getStandardFolders(): Map<String, String?> = emptyMap()
    override suspend fun listFiles(path: String): Result<List<FileModel>> = Result.success(emptyList())
    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = Result.success(testFile(name, "$parentPath/$name", isDirectory = true))
    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = Result.success(testFile(name, "$parentPath/$name"))
    override suspend fun deletePermanently(paths: List<String>): Result<Unit> {
        deletePermanentlyRequests += paths
        return Result.success(Unit)
    }
    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = Result.success(testFile(newName, path.substringBeforeLast('/') + "/$newName"))
    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> = Result.success(emptyList())
    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        copyRequests += TransferCall(sourcePaths, destinationPath, resolutions)
        return Result.success(Unit)
    }
    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        moveRequests += TransferCall(sourcePaths, destinationPath, resolutions)
        return Result.success(Unit)
    }
}

private class RecordingFolderStatsStore : FolderStatsStore {
    override suspend fun getCached(paths: Collection<String>): Map<String, FolderStats> = emptyMap()
    override fun observeUpdates(): Flow<FolderStatUpdate> = emptyFlow()
    override fun queue(paths: List<String>) = Unit
    override fun invalidate(paths: Collection<String>) = Unit
}
