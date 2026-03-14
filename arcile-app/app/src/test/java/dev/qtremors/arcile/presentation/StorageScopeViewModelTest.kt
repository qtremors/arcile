package dev.qtremors.arcile.presentation

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.data.StorageClassification
import dev.qtremors.arcile.data.StorageClassificationStore
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageMountState
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.presentation.home.HomeViewModel
import dev.qtremors.arcile.presentation.recentfiles.RecentFilesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StorageScopeViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `home view model loads global and indexed per-volume storage scopes`() = runTest(dispatcher) {
        val internal = volume(id = "primary", name = "Internal", path = "/storage/emulated/0")
        val sd = volume(id = "sd", name = "SD Card", path = "/storage/1234-5678", removable = true)
        val repository = FakeFileRepository(
            volumes = listOf(internal, sd),
            categorySizesByScope = mapOf(
                StorageScope.AllStorage to listOf(CategoryStorage("Images", 10L, setOf("jpg"))),
                StorageScope.Volume("primary") to listOf(CategoryStorage("Images", 7L, setOf("jpg"))),
                StorageScope.Volume("sd") to listOf(CategoryStorage("Images", 3L, setOf("jpg")))
            )
        )

        val viewModel = HomeViewModel(repository, FakeStorageClassificationStore())
        advanceUntilIdle()

        assertTrue(repository.requestedStorageInfoScopes.contains(StorageScope.AllStorage))
        assertTrue(repository.requestedCategoryScopes.contains(StorageScope.AllStorage))
        assertTrue(repository.requestedCategoryScopes.contains(StorageScope.Volume("primary")))
        assertTrue(repository.requestedCategoryScopes.none { it == StorageScope.Volume("sd") })
        assertEquals(1, viewModel.state.value.categoryStoragesByVolume.size)
    }

    @Test
    fun `recent files view model scopes queries to selected volume`() = runTest(dispatcher) {
        val repository = FakeFileRepository(
            recentFilesByScope = mapOf(
                StorageScope.Volume("sd") to listOf(file("clip.mp4", "/storage/1234-5678/Movies/clip.mp4"))
            )
        )

        val viewModel = RecentFilesViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf("volumeId" to "sd"))
        )
        advanceUntilIdle()

        assertTrue(repository.requestedRecentScopes.contains(StorageScope.Volume("sd")))
        assertEquals(listOf("/storage/1234-5678/Movies/clip.mp4"), viewModel.state.value.recentFiles.map { it.absolutePath })
    }

    @Test
    fun `recent files view model treats blank volume id as all storage`() = runTest(dispatcher) {
        val repository = FakeFileRepository(
            recentFilesByScope = mapOf(
                StorageScope.AllStorage to listOf(file("note.txt", "/storage/emulated/0/Download/note.txt"))
            )
        )

        val viewModel = RecentFilesViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(mapOf("volumeId" to ""))
        )
        advanceUntilIdle()

        assertTrue(repository.requestedRecentScopes.contains(StorageScope.AllStorage))
        assertEquals(1, viewModel.state.value.recentFiles.size)
    }

    private fun volume(
        id: String,
        name: String,
        path: String,
        removable: Boolean = false
    ) = StorageVolume(
        id = id,
        storageKey = id,
        name = name,
        path = path,
        totalBytes = 100L,
        freeBytes = 40L,
        isPrimary = !removable,
        isRemovable = removable,
        mountState = StorageMountState.MOUNTED
    )

    private fun file(name: String, path: String) = FileModel(
        name = name,
        absolutePath = path,
        size = 1L,
        lastModified = 1L,
        isDirectory = false,
        extension = name.substringAfterLast('.', ""),
        isHidden = false
    )
}

private class FakeFileRepository(
    volumes: List<StorageVolume> = emptyList(),
    private val recentFilesByScope: Map<StorageScope, List<FileModel>> = emptyMap(),
    private val categorySizesByScope: Map<StorageScope, List<CategoryStorage>> = emptyMap()
) : FileRepository {

    private val observedVolumes = MutableSharedFlow<List<StorageVolume>>(replay = 1).apply {
        runBlocking { emit(volumes) }
    }

    val requestedRecentScopes = mutableListOf<StorageScope>()
    val requestedStorageInfoScopes = mutableListOf<StorageScope>()
    val requestedCategoryScopes = mutableListOf<StorageScope>()

    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = observedVolumes.asSharedFlow()

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(observedVolumes.replayCache.lastOrNull().orEmpty())

    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> {
        val volume = observedVolumes.replayCache.lastOrNull()
            ?.sortedByDescending { it.path.length }
            ?.firstOrNull { path == it.path || path.startsWith(it.path + "/") }
        return volume?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException("No volume for path"))
    }

    override suspend fun listFiles(path: String): Result<List<FileModel>> = Result.success(emptyList())

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())

    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())

    override suspend fun deleteFile(path: String): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = Result.failure(NotImplementedError())

    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, minTimestamp: Long): Result<List<FileModel>> {
        requestedRecentScopes += scope
        return Result.success(recentFilesByScope[scope].orEmpty())
    }

    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> {
        requestedStorageInfoScopes += scope
        val allVolumes = observedVolumes.replayCache.lastOrNull().orEmpty()
        val scopedVolumes = when (scope) {
            StorageScope.AllStorage -> allVolumes
            is StorageScope.Volume -> allVolumes.filter { it.id == scope.volumeId }
            is StorageScope.Path -> allVolumes.filter { it.id == scope.volumeId }
            is StorageScope.Category -> allVolumes.filter { it.id == scope.volumeId }
        }
        return Result.success(StorageInfo(scopedVolumes))
    }

    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> {
        requestedCategoryScopes += scope
        return Result.success(categorySizesByScope[scope].orEmpty())
    }

    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = Result.success(emptyList())

    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> = Result.success(emptyList())

    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> = Result.success(emptyList())

    override suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit> = Result.success(Unit)

    override suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit> = Result.success(Unit)

    override suspend fun moveToTrash(paths: List<String>): Result<Unit> = Result.success(Unit)

    override suspend fun restoreFromTrash(trashIds: List<String>): Result<Unit> = Result.success(Unit)

    override suspend fun emptyTrash(): Result<Unit> = Result.success(Unit)

    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = Result.success(emptyList())
}

private class FakeStorageClassificationStore : StorageClassificationStore {
    override fun observeClassifications(): Flow<Map<String, StorageClassification>> =
        MutableStateFlow<Map<String, StorageClassification>>(emptyMap()).asStateFlow()

    override suspend fun getClassification(storageKey: String): StorageClassification? = null

    override suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String?,
        lastSeenPath: String?
    ) = Unit

    override suspend fun resetClassification(storageKey: String) = Unit
}
