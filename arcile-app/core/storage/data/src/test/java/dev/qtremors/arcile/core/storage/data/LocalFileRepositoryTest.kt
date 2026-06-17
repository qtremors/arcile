package dev.qtremors.arcile.core.storage.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.storage.data.db.ArcileDatabase
import dev.qtremors.arcile.core.storage.data.manager.TrashManager
import dev.qtremors.arcile.core.storage.data.manager.TrashTarget
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.data.source.MediaStoreClient
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.testutil.createTempStorageRoot
import dev.qtremors.arcile.testutil.testFile
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
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

    @Test
    fun `recent files query returns fresh media store results instead of stale snapshot`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val database = Room.inMemoryDatabaseBuilder(context, ArcileDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val snapshotStore = RecentFilesSnapshotStore(database.recentFilesSnapshotDao())
            val scope = StorageScope.AllStorage
            val stale = testFile("stale.jpg", "/storage/emulated/0/DCIM/stale.jpg")
            val fresh = testFile("fresh.jpg", "/storage/emulated/0/DCIM/fresh.jpg")
            snapshotStore.put(scope, limit = 10, minTimestamp = 0L, files = listOf(stale))

            val mediaStoreClient = RecordingMediaStoreClient().apply {
                recentFilesResult = Result.success(listOf(fresh))
            }
            val repository = LocalFileRepository(
                RecordingVolumeProvider(listOf(testVolume("primary", "/storage/emulated/0", kind = StorageKind.INTERNAL))),
                mediaStoreClient,
                RecordingTrashManager(),
                RecordingFileSystemDataSource(),
                RecordingFolderStatsStore(),
                recentFilesSnapshotStore = snapshotStore
            )

            val result = repository.getRecentFiles(scope, limit = 10, offset = 0, minTimestamp = 0L).getOrThrow()

            assertEquals(listOf(fresh), result)
            assertEquals(1, mediaStoreClient.recentFilesRequests)
        } finally {
            database.close()
        }
    }

    @Test
    fun `selection properties include thumbnails descendants excluded from folder stats`() = runTest {
        val root = createTempStorageRoot("repo-properties-thumbnails")
        try {
            val folder = File(root, "Pictures").apply { mkdirs() }
            File(folder, "photo.jpg").writeText("photo")
            val thumbnails = File(folder, ".thumbnails").apply { mkdirs() }
            File(thumbnails, "thumb.jpg").writeText("thumbnail")
            val repository = LocalFileRepository(
                RecordingVolumeProvider(listOf(testVolume("primary", root.absolutePath, kind = StorageKind.INTERNAL))),
                RecordingMediaStoreClient(),
                RecordingTrashManager(),
                RecordingFileSystemDataSource(),
                RecordingFolderStatsStore()
            )

            val properties = repository.getSelectionProperties(listOf(folder.absolutePath)).getOrThrow()

            assertEquals(2, properties.fileCount)
            assertEquals(2, properties.folderCount)
            assertEquals("photothumbnail".length.toLong(), properties.totalBytes)
            assertEquals(1, properties.hiddenCount)
            assertEquals(PropertiesAccessStatus.Full, properties.accessStatus)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `selection properties aggregate mixed file and folder descendants`() = runTest {
        val root = createTempStorageRoot("repo-properties-mixed")
        try {
            val selectedFile = File(root, ".note.txt").apply { writeText("note") }
            val folder = File(root, "Folder").apply { mkdirs() }
            File(folder, "child.txt").writeText("child")
            File(folder, ".hidden").writeText("hidden")
            val repository = LocalFileRepository(
                RecordingVolumeProvider(listOf(testVolume("primary", root.absolutePath, kind = StorageKind.INTERNAL))),
                RecordingMediaStoreClient(),
                RecordingTrashManager(),
                RecordingFileSystemDataSource(),
                RecordingFolderStatsStore()
            )

            val properties = repository.getSelectionProperties(listOf(selectedFile.absolutePath, folder.absolutePath)).getOrThrow()

            assertEquals(2, properties.itemCount)
            assertEquals(3, properties.fileCount)
            assertEquals(1, properties.folderCount)
            assertEquals("notechildhidden".length.toLong(), properties.totalBytes)
            assertEquals(2, properties.hiddenCount)
            assertEquals(PropertiesAccessStatus.Full, properties.accessStatus)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `selection properties report partial when selected path disappears`() = runTest {
        val root = createTempStorageRoot("repo-properties-partial")
        try {
            val file = File(root, "available.txt").apply { writeText("available") }
            val missing = File(root, "missing.txt")
            val repository = LocalFileRepository(
                RecordingVolumeProvider(listOf(testVolume("primary", root.absolutePath, kind = StorageKind.INTERNAL))),
                RecordingMediaStoreClient(),
                RecordingTrashManager(),
                RecordingFileSystemDataSource(),
                RecordingFolderStatsStore()
            )

            val properties = repository.getSelectionProperties(listOf(file.absolutePath, missing.absolutePath)).getOrThrow()

            assertEquals(1, properties.itemCount)
            assertEquals(1, properties.fileCount)
            assertEquals(PropertiesAccessStatus.Partial, properties.accessStatus)
        } finally {
            root.deleteRecursively()
        }
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
    var recentFilesResult: Result<List<FileModel>> = Result.success(emptyList())
    var recentFilesRequests = 0
    var lastCategoryScope: Pair<String, StorageScope>? = null
    var lastSearchRequest: Triple<String, StorageScope, SearchFilters?>? = null

    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>> {
        recentFilesRequests += 1
        return recentFilesResult
    }

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

    override suspend fun moveToTrash(
        paths: List<String>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        moveToTrashRequests += paths
        return Result.success(Unit)
    }

    override suspend fun moveToTrashTargets(
        targets: List<TrashTarget>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        moveToTrashRequests += targets.map { it.path }
        return Result.success(Unit)
    }

    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> = Result.success(Unit)
    override suspend fun emptyTrash(): Result<Unit> = Result.success(Unit)
    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = Result.success(emptyList())
    override suspend fun getTrashStorageUsage(): Result<TrashStorageUsage> = Result.success(TrashStorageUsage(0L, emptyMap()))
    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> = Result.success(Unit)
}

private class RecordingFileSystemDataSource : FileSystemDataSource {
    val deletePermanentlyRequests = mutableListOf<List<String>>()
    val copyRequests = mutableListOf<TransferCall>()
    val moveRequests = mutableListOf<TransferCall>()

    override fun getStandardFolders(): Map<String, String?> = emptyMap()
    override suspend fun listFiles(path: String): Result<List<FileModel>> = Result.success(emptyList())
    override fun list(path: StorageNodePath, pageSize: Int): Flow<ListingPage> =
        flowOf(ListingPage(path, emptyList(), pageIndex = 0, isComplete = true))
    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = Result.success(testFile(name, "$parentPath/$name", isDirectory = true))
    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = Result.success(testFile(name, "$parentPath/$name"))
    override suspend fun deletePermanently(paths: List<String>): Result<Unit> {
        deletePermanentlyRequests += paths
        return Result.success(Unit)
    }
    override suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult> {
        deletePermanentlyRequests += paths
        return Result.success(BatchMutationResult(succeededPaths = paths))
    }
    override suspend fun shred(paths: List<String>): Result<Unit> = Result.success(Unit)
    override suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> =
        Result.success(BatchMutationResult(succeededPaths = paths))
    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = Result.success(testFile(newName, path.substringBeforeLast('/') + "/$newName"))
    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> = Result.success(emptyList())
    override suspend fun createFakeFile(parentPath: String, name: String, size: Long, onProgress: ((dev.qtremors.arcile.core.operation.BulkFileOperationProgress) -> Unit)?): Result<FileModel> = Result.success(testFile(name, "$parentPath/$name", false, size))
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
