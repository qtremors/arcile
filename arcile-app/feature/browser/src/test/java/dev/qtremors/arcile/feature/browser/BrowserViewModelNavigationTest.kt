package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.ui.image.ArchiveEntryThumbnailData
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeFilePreferencesStore
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class BrowserViewModelNavigationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

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
            browserPreferencesRepository = FakeFilePreferencesStore(),
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
            browserPreferencesRepository = FakeFilePreferencesStore(),
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
            browserPreferencesRepository = FakeFilePreferencesStore(
                BrowserPreferences(
                    pathPresentationOptions = mapOf(
                        "category_Images" to FileListingPreferences(sortOption = FileSortOption.DATE_OLDEST)
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
        assertEquals("", viewModel.state.value.currentPath)
        assertEquals("primary", viewModel.state.value.currentVolumeId)
        assertEquals(FileSortOption.DATE_OLDEST, viewModel.state.value.browserSortOption)
        assertEquals(FileViewMode.LIST, viewModel.state.value.browserViewMode)
        assertEquals(listOf("pic.jpg"), viewModel.state.value.files.map { it.name })
    }

    @Test
    fun `restored category location ignores stale saved folder path`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByCategory = mapOf("Audio" to listOf(browserFile("song.mp3", "/storage/emulated/0/Music/song.mp3")))
            ),
            browserPreferencesRepository = FakeFilePreferencesStore(),
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "currentPath" to "/storage/emulated/0/Download",
                    "isCategoryScreen" to true,
                    "activeCategoryName" to "Audio",
                    "currentVolumeId" to "primary",
                    "isVolumeRootScreen" to false
                )
            )
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.isCategoryScreen)
        assertEquals("Audio", viewModel.state.value.activeCategoryName)
        assertEquals("", viewModel.state.value.currentPath)
        assertEquals(listOf("song.mp3"), viewModel.state.value.files.map { it.name })
    }

    @Test
    fun `updateBrowserPresentation persists category presentation key`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val preferences = FakeFilePreferencesStore()
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
            FileListingPreferences(
                sortOption = FileSortOption.DATE_OLDEST,
                viewMode = FileViewMode.GRID,
                listZoom = 1.1f,
                gridMinCellSize = 144f
            ),
            applyToSubfolders = true
        )
        advanceUntilIdle()

        assertEquals("category_Images", preferences.lastUpdatedPath)
        assertEquals(FileSortOption.DATE_OLDEST, preferences.lastUpdatedPathPresentation?.sortOption)
        assertEquals(FileViewMode.GRID, preferences.lastUpdatedPathPresentation?.viewMode)
        assertNull(preferences.lastUpdatedGlobalPresentation)
    }

    @Test
    fun `category folder tab starts on all and can be selected`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByCategory = mapOf(
                    "Images" to listOf(
                        browserFile("one.jpg", "/storage/emulated/0/DCIM/one.jpg"),
                        browserFile("two.jpg", "/storage/emulated/0/Download/two.jpg")
                    )
                )
            ),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToCategory("Images", "primary")
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedFolderTabPath)

        viewModel.selectFolderTab("/storage/emulated/0/DCIM")

        assertEquals("/storage/emulated/0/DCIM", viewModel.state.value.selectedFolderTabPath)
    }

    @Test
    fun `storage mutation refreshes current folder without manual pull`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repository = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(browserFile("old.jpg", "/storage/emulated/0/Download/old.jpg"))
            )
        )
        val notifier = FakeStorageMutationNotifier()
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "currentPath" to "/storage/emulated/0/Download",
                    "currentVolumeId" to "primary",
                    "isVolumeRootScreen" to false,
                    "isCategoryScreen" to false
                )
            ),
            storageMutationNotifier = notifier
        )

        advanceUntilIdle()
        repository.filesByPath = mapOf(
            "/storage/emulated/0/Download" to listOf(browserFile("new.jpg", "/storage/emulated/0/Download/new.jpg"))
        )
        notifier.notify(listOf("/storage/emulated/0/Download/new.jpg"))
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("new.jpg"), viewModel.state.value.files.map { it.name })
    }

    @Test
    fun `unrelated storage mutation does not refresh current folder or clear selection`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val selectedPath = "/storage/emulated/0/Download/keep.jpg"
        val repository = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(browserFile("keep.jpg", selectedPath))
            )
        )
        val notifier = FakeStorageMutationNotifier()
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "currentPath" to "/storage/emulated/0/Download",
                    "currentVolumeId" to "primary",
                    "isVolumeRootScreen" to false,
                    "isCategoryScreen" to false
                )
            ),
            storageMutationNotifier = notifier
        )

        advanceUntilIdle()
        viewModel.toggleSelection(selectedPath)
        repository.filesByPath = mapOf(
            "/storage/emulated/0/Download" to listOf(browserFile("replacement.jpg", "/storage/emulated/0/Download/replacement.jpg"))
        )
        notifier.notify(listOf("/storage/emulated/0/Pictures/new.jpg", "/storage/emulated/0/Pictures"))
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("keep.jpg"), viewModel.state.value.files.map { it.name })
        assertEquals(setOf(selectedPath), viewModel.state.value.selectedFiles)
    }

    @Test
    fun `parent folder storage mutation does not refresh selected child folder`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val selectedPath = "/storage/emulated/0/Download/Sub/keep.jpg"
        val repository = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download/Sub" to listOf(browserFile("keep.jpg", selectedPath))
            )
        )
        val notifier = FakeStorageMutationNotifier()
        val viewModel = createViewModel(
            repository = repository,
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "currentPath" to "/storage/emulated/0/Download/Sub",
                    "currentVolumeId" to "primary",
                    "isVolumeRootScreen" to false,
                    "isCategoryScreen" to false
                )
            ),
            storageMutationNotifier = notifier
        )

        advanceUntilIdle()
        viewModel.toggleSelection(selectedPath)
        repository.filesByPath = mapOf(
            "/storage/emulated/0/Download/Sub" to listOf(browserFile("replacement.jpg", "/storage/emulated/0/Download/Sub/replacement.jpg"))
        )
        notifier.notify(listOf("/storage/emulated/0/Download/sibling.jpg", "/storage/emulated/0/Download"))
        advanceTimeBy(300)
        advanceUntilIdle()

        assertEquals(listOf("keep.jpg"), viewModel.state.value.files.map { it.name })
        assertEquals(setOf(selectedPath), viewModel.state.value.selectedFiles)
    }

    @Test
    fun `category folder tab selection clears selection and resets on normal folder navigation`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByPath = mapOf("/storage/emulated/0/Download" to emptyList()),
                filesByCategory = mapOf(
                    "Images" to listOf(
                        browserFile("one.jpg", "/storage/emulated/0/DCIM/one.jpg"),
                        browserFile("two.jpg", "/storage/emulated/0/DCIM/two.jpg")
                    )
                )
            ),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToCategory("Images", "primary")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/DCIM/one.jpg")
        viewModel.selectFolderTab("/storage/emulated/0/DCIM")

        assertTrue(viewModel.state.value.selectedFiles.isEmpty())

        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedFolderTabPath)
        assertFalse(viewModel.state.value.isCategoryScreen)
    }

    @Test
    fun `archive back exits to real parent directory without restoring virtual path as directory`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val archivePath = "/storage/emulated/0/Download/bundle.zip"
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(browserFile("bundle.zip", archivePath))
            )
        )
        repo.archiveRepository.archiveEntriesResultProvider = { _, _, _ ->
            Result.success(
                listOf(
                    ArchiveEntryModel("docs", "docs/", 0L, null, 10L, isDirectory = true),
                    ArchiveEntryModel("note.txt", "docs/note.txt", 12L, null, 20L, isDirectory = false)
                )
            )
        }
        val savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        val viewModel = createViewModel(
            repository = repo,
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()
        viewModel.openArchive(archivePath)
        advanceUntilIdle()
        viewModel.navigateToFolder(ArchiveEntryThumbnailData.virtualPath(archivePath, "docs"))
        advanceUntilIdle()

        assertEquals("docs", viewModel.state.value.archiveContext?.entryPrefix)

        assertTrue(viewModel.navigateBack())
        advanceUntilIdle()
        assertNull(viewModel.state.value.archiveContext?.entryPrefix)

        assertTrue(viewModel.navigateBack())
        advanceUntilIdle()

        assertNull(viewModel.state.value.archiveContext)
        assertEquals("/storage/emulated/0/Download", viewModel.state.value.currentPath)
        assertEquals("/storage/emulated/0/Download", savedStateHandle.get<String>("currentPath"))
    }

    @Test
    fun `selected archive entry extraction starts targeted archive operation`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val archivePath = "/storage/emulated/0/Download/bundle.zip"
        val repo = BrowserFakeFileRepository(volumes = listOf(internal))
        repo.archiveRepository.archiveEntriesResultProvider = { _, _, _ ->
            Result.success(
                listOf(
                    ArchiveEntryModel("note.txt", "docs/note.txt", 12L, null, 20L, isDirectory = false),
                    ArchiveEntryModel("image.jpg", "image.jpg", 24L, null, 30L, isDirectory = false)
                )
            )
        }
        val coordinator = FakeBulkFileOperationCoordinator()
        val viewModel = createViewModel(
            repository = repo,
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true)),
            bulkFileOperationCoordinator = coordinator
        )

        advanceUntilIdle()
        viewModel.openArchive(archivePath)
        advanceUntilIdle()
        viewModel.toggleSelection(ArchiveEntryThumbnailData.virtualPath(archivePath, "image.jpg"))
        viewModel.extractSelectedArchiveEntries(ArchiveExtractionTarget.SAME_FOLDER, null)
        advanceUntilIdle()

        val request = coordinator.startedRequests.single()
        assertEquals(BulkFileOperationType.EXTRACT_ARCHIVE, request.type)
        assertEquals(listOf(archivePath), request.sourcePaths)
        assertEquals("/storage/emulated/0/Download", request.destinationPath)
        assertEquals("image.jpg", request.archiveEntryPrefix)
        assertTrue(viewModel.state.value.selectedFiles.isEmpty())
    }
}
