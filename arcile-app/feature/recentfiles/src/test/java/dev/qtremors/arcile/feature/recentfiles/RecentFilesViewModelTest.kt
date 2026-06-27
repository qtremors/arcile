package dev.qtremors.arcile.feature.recentfiles

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageMutationEvent
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeBrowserPreferencesStore
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.MainDispatcherRule
import dev.qtremors.arcile.testutil.testFile
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RecentFilesViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `search query uses repository search after debounce`() = runTest(mainDispatcherRule.dispatcher) {
        val searchFiles = listOf(
            recentFile("Holiday.jpg", lastModified = 300L),
            recentFile("holiday-plan.pdf", lastModified = 200L)
        )
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to listOf(recentFile("notes.txt")))
        ).apply {
            searchFilesResultProvider = { query, scope, _ ->
                assertEquals("HOLIDAY", query)
                assertEquals(StorageScope.AllStorage, scope)
                Result.success(searchFiles)
            }
        }
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()

        viewModel.updateSearchQuery("HOLIDAY")
        advanceTimeBy(399)
        assertFalse(viewModel.state.value.isSearching)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("Holiday.jpg", "holiday-plan.pdf"), viewModel.state.value.searchResults.map { it.name })
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `default presentation is date newest and displayed files are sorted newest first`() = runTest(mainDispatcherRule.dispatcher) {
        val files = listOf(
            recentFile("old.txt", lastModified = 100L),
            recentFile("new.txt", lastModified = 300L),
            recentFile("middle.txt", lastModified = 200L)
        )
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to files)
        )
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()

        assertEquals(FileSortOption.DATE_NEWEST, viewModel.state.value.presentation.sortOption)
        assertEquals(listOf("new.txt", "middle.txt", "old.txt"), viewModel.state.value.displayedRecentFiles.map { it.name })
    }

    @Test
    fun `updatePresentation changes displayed order without replacing loaded files`() = runTest(mainDispatcherRule.dispatcher) {
        val files = listOf(
            recentFile("beta.txt"),
            recentFile("alpha.txt"),
            recentFile("gamma.txt")
        )
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to files)
        )
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updatePresentation(FileListingPreferences(sortOption = FileSortOption.NAME_ASC))

        assertEquals(FileSortOption.NAME_ASC, viewModel.state.value.presentation.sortOption)
        assertEquals(listOf("beta.txt", "alpha.txt", "gamma.txt"), viewModel.state.value.recentFiles.map { it.name })
        assertEquals(listOf("alpha.txt", "beta.txt", "gamma.txt"), viewModel.state.value.displayedRecentFiles.map { it.name })
    }

    @Test
    fun `search filters are search only and do not filter normal recent list`() = runTest(mainDispatcherRule.dispatcher) {
        val files = listOf(
            recentFile("photo.jpg", size = 20L, lastModified = 400L, mimeType = "image/jpeg"),
            recentFile("small.jpg", size = 5L, lastModified = 500L, mimeType = "image/jpeg"),
            recentFile("old.jpg", size = 30L, lastModified = 100L, mimeType = "image/jpeg"),
            recentFile("doc.pdf", size = 40L, lastModified = 450L, mimeType = "application/pdf")
        )
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to files)
        )
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSearchFilters(
            SearchFilters(
                itemType = "Files",
                fileType = "Images",
                minSize = 10L,
                minDateMillis = 300L
            )
        )

        assertEquals(listOf("small.jpg", "doc.pdf", "photo.jpg", "old.jpg"), viewModel.state.value.displayedRecentFiles.map { it.name })
    }

    @Test
    fun `search results pass active filters to repository and respect presentation`() = runTest(mainDispatcherRule.dispatcher) {
        val searchFiles = listOf(
            recentFile("holiday-small.jpg", size = 10L),
            recentFile("holiday-large.jpg", size = 30L)
        )
        val repository = FakeStorageRepositoryBundle().apply {
            searchFilesResultProvider = { _, _, filters ->
                assertEquals(SearchFilters(fileType = "Images"), filters)
                Result.success(searchFiles)
            }
        }
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSearchFilters(SearchFilters(fileType = "Images"))
        viewModel.updatePresentation(FileListingPreferences(sortOption = FileSortOption.SIZE_LARGEST))
        viewModel.updateSearchQuery("holiday")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(listOf("holiday-large.jpg", "holiday-small.jpg"), viewModel.state.value.searchResults.map { it.name })
    }

    @Test
    fun `blank search query clears recent file search state immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to listOf(recentFile("Holiday.jpg")))
        )
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSearchQuery("holiday")
        advanceTimeBy(400)
        advanceUntilIdle()

        viewModel.updateSearchQuery("")

        assertEquals(emptyList<FileModel>(), viewModel.state.value.searchResults)
        assertFalse(viewModel.state.value.isSearching)
    }

    @Test
    fun `pull to refresh reloads files and resets refresh flag`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to listOf(recentFile("Holiday.jpg")))
        )
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.loadRecentFiles(pullToRefresh = true)
        advanceUntilIdle()

        assertEquals(2, repository.requestedRecentScopes.size)
        assertEquals(StorageScope.AllStorage, repository.requestedRecentScopes.last())
        assertFalse(viewModel.state.value.isPullToRefreshing)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `storage mutation refreshes recent files without manual pull`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to listOf(recentFile("old.jpg")))
        )
        val notifier = FakeStorageMutationNotifier()
        val viewModel = recentViewModel(repository, storageMutationNotifier = notifier)

        advanceUntilIdle()
        repository.recentFilesByScope = mapOf(StorageScope.AllStorage to listOf(recentFile("new.jpg")))
        notifier.notify(listOf("/storage/emulated/0/Download/new.jpg"))
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("new.jpg"), viewModel.state.value.recentFiles.map { it.name })
        assertEquals(1, repository.invalidateAnalyticsCacheCalls)
    }

    @Test
    fun `older recent load cannot overwrite newer refresh result`() = runTest(mainDispatcherRule.dispatcher) {
        val firstLoad = CompletableDeferred<Result<List<FileModel>>>()
        val secondLoad = CompletableDeferred<Result<List<FileModel>>>()
        var requestCount = 0
        val repository = FakeStorageRepositoryBundle().apply {
            recentFilesResultProvider = { _, _, _, _ ->
                requestCount += 1
                if (requestCount == 1) firstLoad.await() else secondLoad.await()
            }
        }
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.loadRecentFiles()
        advanceUntilIdle()
        viewModel.loadRecentFiles()
        advanceUntilIdle()
        secondLoad.complete(Result.success(listOf(recentFile("new.jpg"))))
        advanceUntilIdle()
        firstLoad.complete(Result.success(listOf(recentFile("old.jpg"))))
        advanceUntilIdle()

        assertEquals(listOf("new.jpg"), viewModel.state.value.recentFiles.map { it.name })
    }

    @Test
    fun `older load more cannot append after newer refresh result`() = runTest(mainDispatcherRule.dispatcher) {
        val firstPage = List(50) { index ->
            recentFile("old_$index.jpg", "/storage/emulated/0/DCIM/old_$index.jpg")
        }
        val loadMore = CompletableDeferred<Result<List<FileModel>>>()
        var refreshCount = 0
        val repository = FakeStorageRepositoryBundle().apply {
            recentFilesResultProvider = { _, _, offset, _ ->
                if (offset > 0) {
                    loadMore.await()
                } else {
                    refreshCount += 1
                    Result.success(if (refreshCount == 1) firstPage else listOf(recentFile("fresh.jpg")))
                }
            }
        }
        val notifier = FakeStorageMutationNotifier()
        val viewModel = recentViewModel(repository, storageMutationNotifier = notifier)

        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()
        notifier.notify(listOf("/storage/emulated/0/Download/fresh.jpg"))
        advanceTimeBy(300)
        advanceUntilIdle()
        loadMore.complete(Result.success(listOf(recentFile("stale_more.jpg"))))
        advanceUntilIdle()

        assertEquals(listOf("fresh.jpg"), viewModel.state.value.recentFiles.map { it.name })
    }

    @Test
    fun `requestDeleteSelected shows trash confirmation for trash-capable volume`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = recentVolume("primary", "/storage/emulated/0", StorageKind.INTERNAL)
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(internal),
            initialRecentFilesByScope = mapOf(
                StorageScope.AllStorage to listOf(recentFile("Holiday.jpg", "/storage/emulated/0/Holiday.jpg"))
            )
        )
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Holiday.jpg")
        viewModel.requestDeleteSelected()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showTrashConfirmation)
        assertFalse(viewModel.state.value.showPermanentDeleteConfirmation)
        assertFalse(viewModel.state.value.showMixedDeleteExplanation)
    }

    @Test
    fun `moveSelectedToTrash starts foreground trash operation`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = recentVolume("primary", "/storage/emulated/0", StorageKind.INTERNAL)
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(internal),
            initialRecentFilesByScope = mapOf(
                StorageScope.AllStorage to listOf(recentFile("Holiday.jpg", "/storage/emulated/0/Holiday.jpg"))
            )
        )
        val coordinator = FakeBulkFileOperationCoordinator()
        val viewModel = recentViewModel(repository, coordinator)

        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Holiday.jpg")
        viewModel.moveSelectedToTrash()
        advanceUntilIdle()

        assertEquals(BulkFileOperationType.TRASH, coordinator.startedRequests.single().type)
        assertEquals(listOf("/storage/emulated/0/Holiday.jpg"), coordinator.startedRequests.single().sourcePaths)
        assertTrue(viewModel.state.value.selectedFiles.isEmpty())
    }

    @Test
    fun `select all uses displayed recent files in normal mode`() = runTest(mainDispatcherRule.dispatcher) {
        val files = listOf(
            recentFile("one.jpg", "/storage/emulated/0/DCIM/one.jpg", mimeType = "image/jpeg"),
            recentFile("two.pdf", "/storage/emulated/0/Download/two.pdf", mimeType = "application/pdf")
        )
        val repository = FakeStorageRepositoryBundle(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to files)
        )
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSearchFilters(SearchFilters(fileType = "Images"))
        viewModel.selectAll()

        assertEquals(
            setOf("/storage/emulated/0/DCIM/one.jpg", "/storage/emulated/0/Download/two.pdf"),
            viewModel.state.value.selectedFiles
        )
    }

    @Test
    fun `select all uses repository search results in search mode`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeStorageRepositoryBundle().apply {
            searchFilesResultProvider = { _, _, _ ->
                Result.success(listOf(recentFile("found.jpg", "/storage/emulated/0/DCIM/found.jpg")))
            }
        }
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSearchQuery("found")
        advanceTimeBy(400)
        advanceUntilIdle()
        viewModel.selectAll()

        assertEquals(setOf("/storage/emulated/0/DCIM/found.jpg"), viewModel.state.value.selectedFiles)
    }

    @Test
    fun `presentation updates are persisted for recent files`() = runTest(mainDispatcherRule.dispatcher) {
        val preferences = FakeBrowserPreferencesStore()
        val viewModel = recentViewModel(FakeStorageRepositoryBundle(), preferences = preferences)
        val presentation = FileListingPreferences(
            sortOption = FileSortOption.NAME_ASC,
            viewMode = dev.qtremors.arcile.core.storage.domain.FileViewMode.GRID
        )

        advanceUntilIdle()
        viewModel.updatePresentation(presentation)
        advanceUntilIdle()

        assertEquals(presentation, preferences.lastUpdatedRecentPresentation)
    }

    @Test
    fun `load more appends files and keeps filters and presentation active`() = runTest(mainDispatcherRule.dispatcher) {
        val firstPage = List(50) { index ->
            recentFile("image_$index.jpg", "/storage/emulated/0/DCIM/image_$index.jpg", lastModified = 1_000L - index, mimeType = "image/jpeg")
        }
        val secondPage = listOf(recentFile("download.jpg", "/storage/emulated/0/Download/download.jpg", lastModified = 2_000L, mimeType = "image/jpeg"))
        val repository = FakeStorageRepositoryBundle().apply {
            recentFilesResultProvider = { _, _, offset, _ ->
                Result.success(if (offset == 0) firstPage else secondPage)
            }
        }
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSearchFilters(SearchFilters(fileType = "Images"))
        viewModel.updatePresentation(FileListingPreferences(sortOption = FileSortOption.DATE_NEWEST))
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(51, viewModel.state.value.recentFiles.size)
        assertEquals(51, viewModel.state.value.displayedRecentFiles.size)
        assertEquals("download.jpg", viewModel.state.value.displayedRecentFiles.first().name)
        assertEquals("Images", viewModel.state.value.activeSearchFilters.fileType)
        assertEquals(FileSortOption.DATE_NEWEST, viewModel.state.value.presentation.sortOption)
    }

    @Test
    fun `load more deduplicates files with identical absolute paths`() = runTest(mainDispatcherRule.dispatcher) {
        val firstPage = (1..48).map {
            recentFile("dummy_$it.jpg", "/storage/emulated/0/DCIM/dummy_$it.jpg")
        } + listOf(
            recentFile("image_1.jpg", "/storage/emulated/0/DCIM/image_1.jpg"),
            recentFile("image_2.jpg", "/storage/emulated/0/DCIM/image_2.jpg")
        )
        val secondPage = listOf(
            recentFile("image_2.jpg", "/storage/emulated/0/DCIM/image_2.jpg"), // Duplicate
            recentFile("image_3.jpg", "/storage/emulated/0/DCIM/image_3.jpg")
        )
        val repository = FakeStorageRepositoryBundle().apply {
            recentFilesResultProvider = { _, _, offset, _ ->
                Result.success(if (offset == 0) firstPage else secondPage)
            }
        }
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(51, viewModel.state.value.recentFiles.size)
        val expectedNames = (1..48).map { "dummy_$it.jpg" } + listOf("image_1.jpg", "image_2.jpg", "image_3.jpg")
        assertEquals(expectedNames, viewModel.state.value.recentFiles.map { it.name })
    }
}

private fun recentFile(
    name: String,
    path: String = "/storage/emulated/0/$name",
    size: Long = 1L,
    lastModified: Long = 1L,
    mimeType: String? = null
) = testFile(
    name = name,
    path = path,
    size = size,
    lastModified = lastModified,
    mimeType = mimeType
)

private fun recentViewModel(
    repository: FakeStorageRepositoryBundle,
    coordinator: FakeBulkFileOperationCoordinator = FakeBulkFileOperationCoordinator(),
    preferences: FakeBrowserPreferencesStore = FakeBrowserPreferencesStore(),
    storageMutationNotifier: StorageMutationNotifier = FakeStorageMutationNotifier()
) = RecentFilesViewModel(
    volumeRepository = repository.volumeRepository,
    storageAnalyticsRepository = repository.storageAnalyticsRepository,
    fileBrowserRepository = repository.fileBrowserRepository,
    searchRepository = repository.searchRepository,
    browserPreferencesRepository = preferences,
    bulkFileOperationCoordinator = coordinator,
    storageMutationNotifier = storageMutationNotifier,
    savedStateHandle = SavedStateHandle()
)

private class FakeStorageMutationNotifier : StorageMutationNotifier {
    private val _events = MutableSharedFlow<StorageMutationEvent>(extraBufferCapacity = 16)
    override val events = _events
    override fun notify(paths: Collection<String>) {
        _events.tryEmit(StorageMutationEvent(paths.toList()))
    }
}

private fun recentVolume(id: String, path: String, kind: StorageKind) = testVolume(
    id = id,
    storageKey = id,
    name = id,
    path = path,
    totalBytes = 100L,
    freeBytes = 20L,
    isPrimary = kind == StorageKind.INTERNAL,
    isRemovable = kind != StorageKind.INTERNAL,
    kind = kind
)
