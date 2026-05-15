package dev.qtremors.arcile.presentation.recentfiles

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.domain.BrowserPresentationPreferences
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
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeBrowserPreferencesStore
import dev.qtremors.arcile.testutil.FakeFileRepository
import dev.qtremors.arcile.testutil.MainDispatcherRule
import dev.qtremors.arcile.testutil.testFile
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        val repository = FakeFileRepository(
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
        val repository = FakeFileRepository(
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
        val repository = FakeFileRepository(
            initialRecentFilesByScope = mapOf(StorageScope.AllStorage to files)
        )
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updatePresentation(BrowserPresentationPreferences(sortOption = FileSortOption.NAME_ASC))

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
        val repository = FakeFileRepository(
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
        val repository = FakeFileRepository().apply {
            searchFilesResultProvider = { _, _, filters ->
                assertEquals(SearchFilters(fileType = "Images"), filters)
                Result.success(searchFiles)
            }
        }
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSearchFilters(SearchFilters(fileType = "Images"))
        viewModel.updatePresentation(BrowserPresentationPreferences(sortOption = FileSortOption.SIZE_LARGEST))
        viewModel.updateSearchQuery("holiday")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertEquals(listOf("holiday-large.jpg", "holiday-small.jpg"), viewModel.state.value.searchResults.map { it.name })
    }

    @Test
    fun `blank search query clears recent file search state immediately`() = runTest(mainDispatcherRule.dispatcher) {
        val repository = FakeFileRepository(
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
        val repository = FakeFileRepository(
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
    fun `requestDeleteSelected shows trash confirmation for trash-capable volume`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = recentVolume("primary", "/storage/emulated/0", StorageKind.INTERNAL)
        val repository = FakeFileRepository(
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
        val repository = FakeFileRepository(
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
        val repository = FakeFileRepository(
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
        val repository = FakeFileRepository().apply {
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
        val viewModel = recentViewModel(FakeFileRepository(), preferences = preferences)
        val presentation = BrowserPresentationPreferences(
            sortOption = FileSortOption.NAME_ASC,
            viewMode = dev.qtremors.arcile.domain.BrowserViewMode.GRID
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
        val repository = FakeFileRepository().apply {
            recentFilesResultProvider = { _, _, offset, _ ->
                Result.success(if (offset == 0) firstPage else secondPage)
            }
        }
        val viewModel = recentViewModel(repository)

        advanceUntilIdle()
        viewModel.updateSearchFilters(SearchFilters(fileType = "Images"))
        viewModel.updatePresentation(BrowserPresentationPreferences(sortOption = FileSortOption.DATE_NEWEST))
        viewModel.loadMore()
        advanceUntilIdle()

        assertEquals(51, viewModel.state.value.recentFiles.size)
        assertEquals(51, viewModel.state.value.displayedRecentFiles.size)
        assertEquals("download.jpg", viewModel.state.value.displayedRecentFiles.first().name)
        assertEquals("Images", viewModel.state.value.activeSearchFilters.fileType)
        assertEquals(FileSortOption.DATE_NEWEST, viewModel.state.value.presentation.sortOption)
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
    repository: FakeFileRepository,
    coordinator: FakeBulkFileOperationCoordinator = FakeBulkFileOperationCoordinator(),
    preferences: FakeBrowserPreferencesStore = FakeBrowserPreferencesStore()
) = RecentFilesViewModel(
    repository = repository,
    browserPreferencesRepository = preferences,
    bulkFileOperationCoordinator = coordinator,
    savedStateHandle = SavedStateHandle()
)

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
