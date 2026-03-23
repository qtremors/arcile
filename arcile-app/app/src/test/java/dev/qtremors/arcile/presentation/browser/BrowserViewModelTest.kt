package dev.qtremors.arcile.presentation.browser

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.data.BrowserPreferencesStore
import dev.qtremors.arcile.domain.BrowserPreferences
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.NativeConfirmationRequiredException
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.domain.usecase.MoveToTrashUseCase
import dev.qtremors.arcile.domain.usecase.PasteFilesUseCase
import dev.qtremors.arcile.presentation.ClipboardOperation
import dev.qtremors.arcile.presentation.FileSortOption
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
import org.junit.Assert.assertNotNull
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
            pasteFilesUseCase = PasteFilesUseCase(repository)
        )
    }

    @Test
    fun `multiple volumes open volume root screen with stored root sort option`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val sd = browserVolume("sd", "SD Card", "/storage/1234-5678", isPrimary = false, isRemovable = true)
        val preferences = FakeBrowserPreferencesStore(
            BrowserPreferences(globalSortOption = FileSortOption.NAME_ASC, pathSortOptions = mapOf("/" to FileSortOption.SIZE_LARGEST))
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
    fun `updateBrowserSortOption persists category sort key`() = runTest(mainDispatcherRule.dispatcher) {
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
        viewModel.updateBrowserSortOption(FileSortOption.DATE_OLDEST, applyToSubfolders = true)
        advanceUntilIdle()

        assertEquals("category_Images", preferences.lastUpdatedPath)
        assertEquals(FileSortOption.DATE_OLDEST, preferences.lastUpdatedPathSortOption)
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
}

private class FakeBrowserPreferencesStore(
    initialPreferences: BrowserPreferences = BrowserPreferences()
) : BrowserPreferencesStore {
    private val _preferencesFlow = MutableStateFlow(initialPreferences)
    override val preferencesFlow: Flow<BrowserPreferences> = _preferencesFlow

    var lastUpdatedGlobalSortOption: FileSortOption? = null
    var lastUpdatedPath: String? = null
    var lastUpdatedPathSortOption: FileSortOption? = null

    override suspend fun updateGlobalSortOption(sortOption: FileSortOption) {
        lastUpdatedGlobalSortOption = sortOption
        _preferencesFlow.value = _preferencesFlow.value.copy(globalSortOption = sortOption)
    }

    override suspend fun updatePathSortOption(path: String, sortOption: FileSortOption?, applyToSubfolders: Boolean) {
        lastUpdatedPath = path
        lastUpdatedPathSortOption = sortOption
        _preferencesFlow.value = _preferencesFlow.value.copy(
            pathSortOptions = _preferencesFlow.value.pathSortOptions.toMutableMap().apply {
                if (sortOption == null) remove(path) else put(path, sortOption)
            }
        )
    }
}

private class BrowserFakeFileRepository(
    volumes: List<StorageVolume> = emptyList(),
    private val filesByPath: Map<String, List<FileModel>> = emptyMap(),
    private val filesByCategory: Map<String, List<FileModel>> = emptyMap(),
    private val searchResult: Result<List<FileModel>> = Result.success(emptyList()),
    private val conflictsResult: Result<List<FileConflict>> = Result.success(emptyList()),
    private val moveToTrashResult: Result<Unit> = Result.success(Unit),
    private val renameResult: Result<FileModel> = Result.failure(NotImplementedError())
) : FileRepository {

    private val observedVolumes = MutableSharedFlow<List<StorageVolume>>(replay = 1).apply {
        tryEmit(volumes)
    }
    private val volumeList = volumes

    var lastSearchQuery: String? = null
    var lastSearchScope: StorageScope? = null
    var lastSearchFilters: SearchFilters? = null
    var lastConflictSourcePaths: List<String>? = null
    var lastConflictDestination: String? = null
    var createDirectoryCalls: Int = 0
    var lastRenamePath: String? = null
    var lastRenameNewName: String? = null

    override suspend fun listFiles(path: String): Result<List<FileModel>> = Result.success(filesByPath[path].orEmpty())
    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> {
        createDirectoryCalls += 1
        return Result.success(browserFile(name, "$parentPath/$name", isDirectory = true))
    }
    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun deleteFile(path: String): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun renameFile(path: String, newName: String): Result<FileModel> {
        lastRenamePath = path
        lastRenameNewName = newName
        return renameResult
    }
    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = observedVolumes
    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(observedVolumes.replayCache.lastOrNull().orEmpty())
    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> {
        val volume = volumeList.sortedByDescending { it.path.length }
            .firstOrNull { path == it.path || path.startsWith(it.path + "/") }
        return volume?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException("No volume for path"))
    }
    override fun getStandardFolders(): Map<String, String?> = emptyMap()
    override suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>> = Result.failure(NotImplementedError())
    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> = Result.failure(NotImplementedError())
    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> = Result.failure(NotImplementedError())
    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = Result.success(filesByCategory[categoryName].orEmpty())
    override suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>> {
        lastSearchQuery = query
        lastSearchScope = scope
        lastSearchFilters = filters
        return searchResult
    }
    override suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>> {
        lastConflictSourcePaths = sourcePaths
        lastConflictDestination = destinationPath
        return conflictsResult
    }
    override suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit> = Result.success(Unit)
    override suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit> = Result.success(Unit)
    override suspend fun moveToTrash(paths: List<String>): Result<Unit> = moveToTrashResult
    override suspend fun restoreFromTrash(trashIds: List<String>, destinationPath: String?): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun emptyTrash(): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = Result.failure(NotImplementedError())
    override suspend fun deletePermanentlyFromTrash(trashIds: List<String>): Result<Unit> = Result.failure(NotImplementedError())
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
    val field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
    field.isAccessible = true
    val unsafe = field.get(null)
    val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
    return allocateInstance.invoke(unsafe, IntentSender::class.java) as IntentSender
}
