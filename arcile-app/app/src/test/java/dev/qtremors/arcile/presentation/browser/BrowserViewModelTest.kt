package dev.qtremors.arcile.presentation.browser

import android.content.IntentSender
import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.data.BrowserPreferencesStore
import dev.qtremors.arcile.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.domain.BrowserPreferences
import dev.qtremors.arcile.domain.BrowserViewMode
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.FolderStatUpdate
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.NativeConfirmationRequiredException
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import io.mockk.mockk
import dev.qtremors.arcile.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.domain.usecase.MoveToTrashUseCase
import dev.qtremors.arcile.presentation.ClipboardOperation
import dev.qtremors.arcile.presentation.operations.BulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.operations.BulkFileOperationEvent
import dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress
import dev.qtremors.arcile.presentation.operations.BulkFileOperationRequest
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.testutil.FakeFileRepository
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun createViewModel(
        repository: FileRepository,
        browserPreferencesRepository: BrowserPreferencesStore,
        savedStateHandle: SavedStateHandle
    ): BrowserViewModel {
        return BrowserViewModel(
            repository = repository,
            browserPreferencesRepository = browserPreferencesRepository,
            savedStateHandle = savedStateHandle,
            getStorageVolumesUseCase = GetStorageVolumesUseCase(repository),
            moveToTrashUseCase = MoveToTrashUseCase(repository),
            bulkFileOperationCoordinator = FakeBulkFileOperationCoordinator()
        )
    }

    @Test
    fun `multiple volumes open volume root screen with stored root sort option`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val sd = browserVolume("sd", "SD Card", "/storage/1234-5678", isPrimary = false, isRemovable = true)
        val preferences = FakeBrowserPreferencesStore(
            BrowserPreferences(
                globalPresentation = BrowserPresentationPreferences(sortOption = FileSortOption.NAME_ASC),
                pathPresentationOptions = mapOf(
                    "/" to BrowserPresentationPreferences(sortOption = FileSortOption.SIZE_LARGEST)
                )
            )
        )
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal, sd)),
            browserPreferencesRepository = preferences,
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.isVolumeRootScreen)
        assertEquals(listOf("Internal", "SD Card"), viewModel.state.value.files.map { it.name })
        assertEquals(FileSortOption.SIZE_LARGEST, viewModel.state.value.browserSortOption)
        assertEquals(BrowserViewMode.LIST, viewModel.state.value.browserViewMode)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `navigateToSpecificFolder falls back to volume roots when path storage is unavailable`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal, browserVolume("sd", "SD Card", "/storage/1234-5678", isPrimary = false, isRemovable = true))),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/missing/path")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isVolumeRootScreen)
        assertEquals("Storage for this path is not available", viewModel.state.value.error)
        assertEquals("", viewModel.state.value.currentPath)
    }

    @Test
    fun `directory search uses current path scope and active filters after debounce`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf("/storage/emulated/0/Download" to listOf(browserFile("one.txt", "/storage/emulated/0/Download/one.txt"))),
            searchResult = Result.success(listOf(browserFile("holiday.jpg", "/storage/emulated/0/Download/holiday.jpg")))
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        val filters = SearchFilters(fileType = "image", minSize = 100L)
        viewModel.updateSearchFilters(filters)
        viewModel.updateBrowserSearchQuery("holiday")
        advanceTimeBy(399)
        assertFalse(viewModel.state.value.isSearching)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals("holiday", repo.lastSearchQuery)
        assertEquals(StorageScope.Path("primary", "/storage/emulated/0/Download"), repo.lastSearchScope)
        assertEquals(filters, repo.lastSearchFilters)
        assertEquals(listOf("holiday.jpg"), viewModel.state.value.searchResults.map { it.name })
    }

    @Test
    fun `copy selection updates clipboard and clears selected files`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal)),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/alpha.txt")
        viewModel.toggleSelection("/storage/emulated/0/beta.txt")
        viewModel.copySelectedToClipboard()

        assertEquals(ClipboardOperation.COPY, viewModel.state.value.clipboardState?.operation)
        assertEquals(listOf("/storage/emulated/0/alpha.txt", "/storage/emulated/0/beta.txt"), viewModel.state.value.clipboardState?.sourcePaths)
        assertTrue(viewModel.state.value.selectedFiles.isEmpty())
    }

    @Test
    fun `pasteFromClipboard shows conflict dialog when destination contains duplicates`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val conflict = FileConflict(
            sourcePath = "/storage/emulated/0/source.txt",
            sourceFile = browserFile("source.txt", "/storage/emulated/0/source.txt"),
            existingFile = browserFile("source.txt", "/storage/emulated/0/Download/source.txt")
        )
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf("/storage/emulated/0/Download" to emptyList()),
            conflictsResult = Result.success(listOf(conflict))
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/source.txt")
        viewModel.cutSelectedToClipboard()

        viewModel.pasteFromClipboard()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showConflictDialog)
        assertEquals(listOf(conflict), viewModel.state.value.pasteConflicts)
        assertEquals(listOf("/storage/emulated/0/source.txt"), repo.lastConflictSourcePaths)
        assertEquals("/storage/emulated/0/Download", repo.lastConflictDestination)
    }

    @Test
    fun `requestDeleteSelected shows mixed explanation for cross-policy selection`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val otg = browserVolume("otg", "USB", "/storage/otg", isPrimary = false, isRemovable = true, kind = StorageKind.OTG)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal, otg)),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/alpha.txt")
        viewModel.toggleSelection("/storage/otg/beta.txt")

        viewModel.requestDeleteSelected()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showMixedDeleteExplanation)
        assertFalse(viewModel.state.value.showTrashConfirmation)
        assertFalse(viewModel.state.value.showPermanentDeleteConfirmation)
    }

    @Test
    fun `moveSelectedToTrash surfaces native confirmation request`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            moveToTrashResult = Result.failure(NativeConfirmationRequiredException(fakeIntentSender()))
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/alpha.txt")

        viewModel.moveSelectedToTrash()
        advanceUntilIdle()

        assertEquals(BrowserNativeAction.TRASH, viewModel.state.value.pendingNativeAction)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `native confirmation requests are not replayed and pending action clears after handling`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            moveToTrashResult = Result.failure(NativeConfirmationRequiredException(fakeIntentSender()))
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        viewModel.nativeRequestFlow.test {
            advanceUntilIdle()
            viewModel.navigateToSpecificFolder("/storage/emulated/0")
            advanceUntilIdle()
            viewModel.toggleSelection("/storage/emulated/0/alpha.txt")
            viewModel.moveSelectedToTrash()
            advanceUntilIdle()

            awaitItem()
            expectNoEvents()
            viewModel.handleNativeActionResult(confirmed = false)
            assertNull(viewModel.state.value.pendingNativeAction)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `navigation state saves committed destination`() = runTest(mainDispatcherRule.dispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByPath = mapOf("/storage/emulated/0/Download" to emptyList()),
                filesByCategory = mapOf("Images" to emptyList())
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Download", savedStateHandle.get<String>("currentPath"))
        assertEquals("primary", savedStateHandle.get<String>("currentVolumeId"))
        assertEquals(false, savedStateHandle.get<Boolean>("isCategoryScreen"))

        viewModel.navigateToCategory("Images", "primary")
        advanceUntilIdle()

        assertEquals(true, savedStateHandle.get<Boolean>("isCategoryScreen"))
        assertEquals("Images", savedStateHandle.get<String>("activeCategoryName"))
        assertEquals("primary", savedStateHandle.get<String>("currentVolumeId"))
    }

    @Test
    fun `restores saved directory location and back history`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "currentPath" to "/storage/emulated/0/Download",
                "currentVolumeId" to "primary",
                "isCategoryScreen" to false,
                "isVolumeRootScreen" to false,
                "pathHistory" to arrayOf("/storage/emulated/0")
            )
        )
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByPath = mapOf(
                    "/storage/emulated/0/Download" to listOf(browserFile("hello.txt", "/storage/emulated/0/Download/hello.txt")),
                    "/storage/emulated/0" to listOf(browserFile("Download", "/storage/emulated/0/Download", isDirectory = true))
                )
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Download", viewModel.state.value.currentPath)
        assertEquals(listOf("hello.txt"), viewModel.state.value.files.map { it.name })

        assertTrue(viewModel.navigateBack())
        advanceUntilIdle()

        assertEquals("/storage/emulated/0", viewModel.state.value.currentPath)
        assertEquals(listOf("Download"), viewModel.state.value.files.map { it.name })
    }

    @Test
    fun `restores saved category location with stored category sort`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByCategory = mapOf("Images" to listOf(browserFile("pic.jpg", "/storage/emulated/0/DCIM/pic.jpg")))
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(
                BrowserPreferences(
                    pathPresentationOptions = mapOf(
                        "category_Images" to BrowserPresentationPreferences(sortOption = FileSortOption.DATE_OLDEST)
                    )
                )
            ),
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "isCategoryScreen" to true,
                    "activeCategoryName" to "Images",
                    "currentVolumeId" to "primary",
                    "isVolumeRootScreen" to false
                )
            )
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.isCategoryScreen)
        assertEquals("Images", viewModel.state.value.activeCategoryName)
        assertEquals("primary", viewModel.state.value.currentVolumeId)
        assertEquals(FileSortOption.DATE_OLDEST, viewModel.state.value.browserSortOption)
        assertEquals(BrowserViewMode.LIST, viewModel.state.value.browserViewMode)
        assertEquals(listOf("pic.jpg"), viewModel.state.value.files.map { it.name })
    }

    @Test
    fun `updateBrowserPresentation persists category presentation key`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val preferences = FakeBrowserPreferencesStore()
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByCategory = mapOf("Images" to listOf(browserFile("pic.jpg", "/storage/emulated/0/DCIM/pic.jpg")))
            ),
            browserPreferencesRepository = preferences,
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToCategory("Images", "primary")
        advanceUntilIdle()
        viewModel.updateBrowserPresentation(
            BrowserPresentationPreferences(
                sortOption = FileSortOption.DATE_OLDEST,
                viewMode = BrowserViewMode.GRID,
                listZoom = 1.1f,
                gridMinCellSize = 144f
            ),
            applyToSubfolders = true
        )
        advanceUntilIdle()

        assertEquals("category_Images", preferences.lastUpdatedPath)
        assertEquals(FileSortOption.DATE_OLDEST, preferences.lastUpdatedPathPresentation?.sortOption)
        assertEquals(BrowserViewMode.GRID, preferences.lastUpdatedPathPresentation?.viewMode)
        assertNull(preferences.lastUpdatedGlobalSortOption)
    }

    @Test
    fun `renameFile handles collisions or append copy behavior without throwing error`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            renameResult = Result.success(browserFile("test - Copy.txt", "/storage/emulated/0/test - Copy.txt"))
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to false))
        )
        
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/test.txt")
        viewModel.renameFile("/storage/emulated/0/test.txt", "test - Copy.txt")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.selectedFiles.isEmpty())
        assertNull(viewModel.state.value.error)
        assertEquals("/storage/emulated/0/test.txt", repo.lastRenamePath)
        assertEquals("test - Copy.txt", repo.lastRenameNewName)
    }

    @Test
    fun `directory load hydrates cached folder stats and queues uncached folders`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val cachedStats = FolderStats(fileCount = 4, totalBytes = 4096L, cachedAt = System.currentTimeMillis())
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(
                    browserFile("Docs", "/storage/emulated/0/Download/Docs", isDirectory = true),
                    browserFile("Music", "/storage/emulated/0/Download/Music", isDirectory = true)
                )
            ),
            cachedFolderStats = mapOf("/storage/emulated/0/Download/Docs" to cachedStats)
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        assertEquals(cachedStats, viewModel.state.value.folderStatsByPath["/storage/emulated/0/Download/Docs"])
        assertTrue(viewModel.state.value.folderStatsLoadingPaths.contains("/storage/emulated/0/Download/Music"))
        assertEquals(listOf("/storage/emulated/0/Download/Music"), repo.lastQueuedFolderStats)
    }

    @Test
    fun `folder stat updates merge during normal browsing without presentation change`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(
                    browserFile("Docs", "/storage/emulated/0/Download/Docs", isDirectory = true)
                )
            )
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        val updatedStats = FolderStats(fileCount = 7, totalBytes = 7000L, cachedAt = System.currentTimeMillis())
        repo.emitFolderStatUpdate(FolderStatUpdate("/storage/emulated/0/Download/Docs", updatedStats))
        advanceUntilIdle()

        assertEquals(updatedStats, viewModel.state.value.folderStatsByPath["/storage/emulated/0/Download/Docs"])
        assertFalse(viewModel.state.value.folderStatsLoadingPaths.contains("/storage/emulated/0/Download/Docs"))
    }

    @Test
    fun `folder stat updates from previous directory are ignored after navigation`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(
                    browserFile("Docs", "/storage/emulated/0/Download/Docs", isDirectory = true)
                ),
                "/storage/emulated/0/Pictures" to listOf(
                    browserFile("Camera", "/storage/emulated/0/Pictures/Camera", isDirectory = true)
                )
            )
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Pictures")
        advanceUntilIdle()

        repo.emitFolderStatUpdate(
            FolderStatUpdate(
                "/storage/emulated/0/Download/Docs",
                FolderStats(fileCount = 2, totalBytes = 2048L, cachedAt = System.currentTimeMillis())
            )
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.folderStatsByPath.containsKey("/storage/emulated/0/Download/Docs"))
        assertEquals("/storage/emulated/0/Pictures", viewModel.state.value.currentPath)
    }

}

private class FakeBrowserPreferencesStore(
    initialPreferences: BrowserPreferences = BrowserPreferences()
) : BrowserPreferencesStore {
    private val _preferencesFlow = MutableStateFlow(initialPreferences)
    override val preferencesFlow: Flow<BrowserPreferences> = _preferencesFlow

    var lastUpdatedGlobalSortOption: FileSortOption? = null
    var lastUpdatedPath: String? = null
    var lastUpdatedPathPresentation: BrowserPresentationPreferences? = null

    override suspend fun updateGlobalPresentation(presentation: BrowserPresentationPreferences) {
        lastUpdatedGlobalSortOption = presentation.sortOption
        _preferencesFlow.value = _preferencesFlow.value.copy(globalPresentation = presentation)
    }

    override suspend fun updatePathPresentation(
        path: String,
        presentation: BrowserPresentationPreferences?,
        applyToSubfolders: Boolean
    ) {
        lastUpdatedPath = path
        lastUpdatedPathPresentation = presentation
        val updatedMap = if (applyToSubfolders) {
            _preferencesFlow.value.pathPresentationOptions.toMutableMap().apply {
                if (presentation == null) remove(path) else put(path, presentation)
            }
        } else {
            _preferencesFlow.value.exactPathPresentationOptions.toMutableMap().apply {
                if (presentation == null) remove(path) else put(path, presentation)
            }
        }
        _preferencesFlow.value = if (applyToSubfolders) {
            _preferencesFlow.value.copy(pathPresentationOptions = updatedMap)
        } else {
            _preferencesFlow.value.copy(exactPathPresentationOptions = updatedMap)
        }
    }
}

private class BrowserFakeFileRepository(
    volumes: List<StorageVolume> = emptyList(),
    filesByPath: Map<String, List<FileModel>> = emptyMap(),
    filesByCategory: Map<String, List<FileModel>> = emptyMap(),
    cachedFolderStats: Map<String, FolderStats> = emptyMap(),
    searchResult: Result<List<FileModel>> = Result.success(emptyList()),
    conflictsResult: Result<List<FileConflict>> = Result.success(emptyList()),
    moveToTrashResult: Result<Unit> = Result.success(Unit),
    renameResult: Result<FileModel> = Result.failure(NotImplementedError())
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
    val createDirectoryCalls: Int
        get() = delegate.createDirectoryRequests.size
    val lastQueuedFolderStats: List<String>?
        get() = delegate.queuedFolderStatsRequests.lastOrNull()
    val lastRenamePath: String?
        get() = delegate.renameRequests.lastOrNull()?.first
    val lastRenameNewName: String?
        get() = delegate.renameRequests.lastOrNull()?.second

    override suspend fun listFiles(path: String) = delegate.listFiles(path)
    override suspend fun getCachedFolderStats(paths: Collection<String>) = delegate.getCachedFolderStats(paths)
    override fun queueFolderStats(paths: List<String>) = delegate.queueFolderStats(paths)
    override fun observeFolderStatUpdates() = delegate.observeFolderStatUpdates()
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
    override suspend fun moveToTrash(paths: List<String>) = delegate.moveToTrash(paths)
    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?) =
        delegate.restoreFromTrash(trashIds, destinationPath)
    override suspend fun emptyTrash() = delegate.emptyTrash()
    override suspend fun getTrashFiles() = delegate.getTrashFiles()
    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>) =
        delegate.deletePermanentlyFromTrash(trashIds)

    fun emitFolderStatUpdate(update: FolderStatUpdate) {
        delegate.emitFolderStatUpdate(update)
    }
}

private class FakeBulkFileOperationCoordinator : BulkFileOperationCoordinator {
    private val _activeRequest = MutableStateFlow<BulkFileOperationRequest?>(null)
    override val activeRequest = _activeRequest
    private val _events = MutableSharedFlow<BulkFileOperationEvent>()
    override val events = _events

    override fun startOperation(
        type: BulkFileOperationType,
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>
    ): Boolean {
        val request = BulkFileOperationRequest("test-op", type, sourcePaths, destinationPath, resolutions)
        _activeRequest.value = request
        _events.tryEmit(BulkFileOperationEvent.Started(request))
        return true
    }

    override fun cancelActiveOperation() {
        val request = _activeRequest.value
        _activeRequest.value = null
        _events.tryEmit(BulkFileOperationEvent.Cancelled(request))
    }

    override fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress) {
        _events.tryEmit(BulkFileOperationEvent.Progress(request, progress))
    }

    override fun onOperationCancelling(request: BulkFileOperationRequest) {
        _events.tryEmit(BulkFileOperationEvent.Cancelling(request))
    }

    override fun onOperationCompleted(request: BulkFileOperationRequest) {
        _activeRequest.value = null
        _events.tryEmit(BulkFileOperationEvent.Completed(request))
    }

    override fun onOperationFailed(request: BulkFileOperationRequest, message: String) {
        _activeRequest.value = null
        _events.tryEmit(BulkFileOperationEvent.Failed(request, message))
    }

    override fun onOperationCancelled(request: BulkFileOperationRequest?) {
        _activeRequest.value = null
        _events.tryEmit(BulkFileOperationEvent.Cancelled(request))
    }
}

private fun browserVolume(
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

private fun browserFile(name: String, path: String, isDirectory: Boolean = false) = FileModel(
    name = name,
    absolutePath = path,
    size = 10L,
    lastModified = 20L,
    isDirectory = isDirectory,
    extension = if (isDirectory) "" else name.substringAfterLast('.', ""),
    isHidden = false
)

private fun fakeIntentSender(): IntentSender {
    return mockk(relaxed = true)
}


