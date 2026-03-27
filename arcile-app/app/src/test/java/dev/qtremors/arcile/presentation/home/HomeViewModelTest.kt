package dev.qtremors.arcile.presentation.home

import dev.qtremors.arcile.data.StorageClassification
import dev.qtremors.arcile.data.StorageClassificationStore
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.isIndexed
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `loadHomeData exposes repository errors and clears loading flags`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = HomeFakeFileRepository(
            storageInfoResult = Result.failure(IllegalStateException("storage failed"))
        )
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        viewModel.loadHomeData()
        advanceUntilIdle()

        assertEquals("storage failed", viewModel.state.value.error)
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isCalculatingStorage)
        assertFalse(viewModel.state.value.isPullToRefreshing)
    }

    @Test
    fun `updateHomeSearchQuery searches all storage with active filters after debounce`() = runTest(mainDispatcherRule.dispatcher) {
        val filters = SearchFilters(fileType = "image", minSize = 10L)
        val expectedResults = listOf(homeFile("holiday.jpg"))
        val repository = HomeFakeFileRepository(
            searchFilesResult = Result.success(expectedResults)
        )
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        advanceTimeBy(1_000)
        advanceUntilIdle()

        viewModel.updateSearchFilters(filters)
        viewModel.updateHomeSearchQuery("holiday")
        advanceTimeBy(399)
        assertFalse(viewModel.state.value.isSearching)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals("holiday", repository.lastSearchQuery)
        assertEquals(StorageScope.AllStorage, repository.lastSearchScope)
        assertEquals(filters, repository.lastSearchFilters)
        assertEquals(expectedResults, viewModel.state.value.searchResults)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `blank home search clears current search state immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = HomeFakeFileRepository()
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        advanceTimeBy(1_000)
        advanceUntilIdle()

        viewModel.updateHomeSearchQuery("photos")
        advanceTimeBy(400)
        advanceUntilIdle()

        viewModel.updateHomeSearchQuery("")

        assertEquals(emptyList<FileModel>(), viewModel.state.value.searchResults)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `setVolumeClassification restores optimistic state when persistence fails`() = runTest(mainDispatcherRule.dispatcher) {
        val volume = homeVolume(
            id = "otg",
            storageKey = "otg",
            name = "USB",
            path = "/storage/otg",
            kind = StorageKind.EXTERNAL_UNCLASSIFIED,
            isPrimary = false,
            isRemovable = true
        )
        val repository = HomeFakeFileRepository(volumes = listOf(volume))
        val store = HomeFakeStorageClassificationStore(setFailure = IllegalStateException("disk full"))
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository, store, quickAccessRepo)

        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.showClassificationPrompt)

        viewModel.setVolumeClassification(volume.storageKey, StorageKind.OTG)

        assertFalse(viewModel.state.value.showClassificationPrompt)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showClassificationPrompt)
        assertEquals(listOf(volume), viewModel.state.value.unclassifiedVolumes)
        assertEquals("Failed to save classification: disk full", viewModel.state.value.error)
    }

    @Test
    fun `hideClassificationPrompt suppresses only the selected volume`() = runTest(mainDispatcherRule.dispatcher) {
        val first = homeVolume(
            id = "u1",
            storageKey = "u1",
            name = "USB 1",
            path = "/storage/u1",
            kind = StorageKind.EXTERNAL_UNCLASSIFIED,
            isPrimary = false,
            isRemovable = true
        )
        val second = homeVolume(
            id = "u2",
            storageKey = "u2",
            name = "USB 2",
            path = "/storage/u2",
            kind = StorageKind.EXTERNAL_UNCLASSIFIED,
            isPrimary = false,
            isRemovable = true
        )
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(
            HomeFakeFileRepository(volumes = listOf(first, second)),
            HomeFakeStorageClassificationStore(),
            quickAccessRepo
        )

        advanceTimeBy(1_000)
        advanceUntilIdle()

        viewModel.hideClassificationPrompt("u1")

        assertEquals(listOf(second), viewModel.state.value.unclassifiedVolumes)
        assertTrue(viewModel.state.value.showClassificationPrompt)
    }

    @Test
    fun `rapid storage volume emissions are debounced to prevent redundant data loads`() = runTest(mainDispatcherRule.dispatcher) {
        val volume1 = homeVolume("v1", "v1", "Vol1", "/v1", StorageKind.INTERNAL, true, false)
        val volume2 = homeVolume("v2", "v2", "Vol2", "/v2", StorageKind.SD_CARD, false, true)

        val repository = HomeFakeFileRepository()
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        advanceTimeBy(1_000)
        advanceUntilIdle()

        val initialCalls = repository.getStorageInfoCalls

        repository.emitVolumes(listOf(volume1))
        advanceTimeBy(500)
        repository.emitVolumes(listOf(volume1, volume2))
        advanceTimeBy(500)
        repository.emitVolumes(listOf(volume2))

        assertEquals(initialCalls, repository.getStorageInfoCalls)

        advanceTimeBy(1_000)
        advanceUntilIdle()

        assertEquals(initialCalls + 1, repository.getStorageInfoCalls)
    }

    @Test
    fun `loadHomeData times out and preserves partial results`() = runTest(mainDispatcherRule.dispatcher) {
        val volume = homeVolume("primary", "primary", "Internal", "/storage/emulated/0", StorageKind.INTERNAL, true, false)
        val repository = HomeFakeFileRepository(
            volumes = listOf(volume),
            recentFilesResult = Result.success(listOf(homeFile("recent.txt"))),
            hangStorageInfo = true
        )
        val quickAccessRepo = io.mockk.mockk<dev.qtremors.arcile.data.QuickAccessPreferencesRepository> { io.mockk.every { quickAccessItems } returns kotlinx.coroutines.flow.flowOf(emptyList()) }
        val viewModel = HomeViewModel(repository, HomeFakeStorageClassificationStore(), quickAccessRepo)

        viewModel.loadHomeData()
        advanceTimeBy(15_000)
        advanceUntilIdle()

        assertEquals("Home data loading timed out. Showing partial data.", viewModel.state.value.error)
        assertEquals(listOf("recent.txt"), viewModel.state.value.recentFiles.map { it.name })
        assertFalse(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.isCalculatingStorage)
    }
}

private class HomeFakeFileRepository(
    volumes: List<StorageVolume> = emptyList(),
    private val storageInfoResult: Result<StorageInfo> = Result.success(StorageInfo(volumes.filter { it.kind.isIndexed })),
    private val recentFilesResult: Result<List<FileModel>> = Result.success(emptyList()),
    private val categoryStorageResult: Result<List<CategoryStorage>> = Result.success(emptyList()),
    private val searchFilesResult: Result<List<FileModel>> = Result.success(listOf(homeFile("holiday.jpg"))),
    private val hangStorageInfo: Boolean = false
) : FileRepository {

    private val observedVolumes = MutableSharedFlow<List<StorageVolume>>(replay = 1).apply {
        tryEmit(volumes)
    }

    var lastSearchQuery: String? = null
    var lastSearchScope: StorageScope? = null
    var lastSearchFilters: SearchFilters? = null
    var getStorageInfoCalls: Int = 0

    fun emitVolumes(volumes: List<StorageVolume>) {
        observedVolumes.tryEmit(volumes)
    }

    override suspend fun listFiles(path: String): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun deleteFile(path: String): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = Result.failure(NotImplementedError())
    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = observedVolumes
    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(observedVolumes.replayCache.lastOrNull().orEmpty())
    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> = Result.failure(NotImplementedError())
    override fun getStandardFolders(): Map<String, String?> = emptyMap()
    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>> = recentFilesResult
    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> {
        getStorageInfoCalls++
        if (hangStorageInfo) {
            delay(20_000)
        }
        return storageInfoResult
    }
    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> = categoryStorageResult
    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = Result.failure(NotImplementedError())

    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> {
        lastSearchQuery = query
        lastSearchScope = scope
        lastSearchFilters = filters
        return searchFilesResult
    }

    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> = Result.failure(NotImplementedError())
    override suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun moveToTrash(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun emptyTrash(): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = Result.failure(NotImplementedError())
    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> = Result.failure(NotImplementedError())
}

private class HomeFakeStorageClassificationStore(
    private val setFailure: Throwable? = null
) : StorageClassificationStore {
    override fun observeClassifications() = MutableStateFlow<Map<String, StorageClassification>>(emptyMap()).asStateFlow()

    override suspend fun getClassification(storageKey: String): StorageClassification? = null

    override suspend fun setClassification(
        storageKey: String,
        kind: StorageKind,
        lastSeenName: String?,
        lastSeenPath: String?
    ) {
        if (setFailure != null) throw setFailure
    }

    override suspend fun resetClassification(storageKey: String) = Unit
}

private fun homeVolume(
    id: String,
    storageKey: String,
    name: String,
    path: String,
    kind: StorageKind,
    isPrimary: Boolean,
    isRemovable: Boolean
) = StorageVolume(
    id = id,
    storageKey = storageKey,
    name = name,
    path = path,
    totalBytes = 2_000L,
    freeBytes = 500L,
    isPrimary = isPrimary,
    isRemovable = isRemovable,
    kind = kind
)

private fun homeFile(name: String) = FileModel(
    name = name,
    absolutePath = "/storage/emulated/0/$name",
    size = 10L,
    lastModified = 20L,
    extension = name.substringAfterLast('.', ""),
    isHidden = false
)
