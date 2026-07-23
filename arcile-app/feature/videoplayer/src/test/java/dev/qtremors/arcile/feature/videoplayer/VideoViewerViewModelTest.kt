package dev.qtremors.arcile.feature.videoplayer

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.testutil.FakeFilePreferencesStore
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VideoViewerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `viewer initializes independently from explicit context paths`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.initialize(
                initialPath = "/movies/two.mp4",
                contextFiles = videoFiles("/movies/one.mp4", "/movies/two.mp4")
            )

            assertEquals(
                listOf("/movies/one.mp4", "/movies/two.mp4"),
                viewModel.state.value.displayedFiles.map { it.absolutePath }
            )
            assertEquals("/movies/two.mp4", viewModel.state.value.viewerCurrentPath)
        }

    @Test
    fun `single video launch discovers neighboring videos only`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = mockk<FileBrowserRepository>(relaxed = true)
            coEvery { repository.listFiles("/movies") } returns Result.success(
                listOf(
                    FileModel("one.mp4", "/movies/one.mp4", extension = "mp4"),
                    FileModel("notes.txt", "/movies/notes.txt", extension = "txt"),
                    FileModel("album", "/movies/album", isDirectory = true),
                    FileModel("two.mkv", "/movies/two.mkv", extension = "mkv")
                )
            )
            val viewModel = createViewModel(fileBrowserRepository = repository)

            viewModel.initialize("/movies/one.mp4", emptyList())
            advanceUntilIdle()

            assertEquals(
                listOf("/movies/one.mp4", "/movies/two.mkv"),
                viewModel.state.value.displayedFiles.map(FileModel::absolutePath)
            )
        }

    @Test
    fun `viewer preserves gallery selection supplied at launch`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.initialize(
                initialPath = "/movies/two.mp4",
                contextFiles = videoFiles("/movies/one.mp4", "/movies/two.mp4"),
                selectedPaths = listOf("/movies/one.mp4")
            )

            assertEquals(setOf("/movies/one.mp4"), viewModel.state.value.selectedFiles)
        }

    @Test
    fun `viewer session restores from its own saved state handle`() =
        runTest(mainDispatcherRule.dispatcher) {
            val savedStateHandle = SavedStateHandle()
            val viewModel = createViewModel(savedStateHandle)
            val initialPath = "/movies/opened.mp4"
            val currentPath = "/movies/current.mp4"

            viewModel.initialize(initialPath, videoFiles(initialPath, currentPath))
            viewModel.setViewerCurrentPath(currentPath)
            viewModel.setViewerMetadataVisible(currentPath, visible = true)
            viewModel.toggleViewerUi()
            viewModel.setViewerEraseDialogPath(currentPath)

            val restored = createViewModel(savedStateHandle).state.value

            assertFalse(restored.isInitialized)
            assertEquals(initialPath, restored.viewerSessionInitialPath)
            assertEquals(currentPath, restored.viewerCurrentPath)
            assertEquals(currentPath, restored.viewerMetadataPath)
            assertFalse(restored.viewerUiVisible)
            assertEquals(currentPath, restored.viewerEraseDialogPath)
        }

    @Test
    fun `completed delete removes its page and stale selection from viewer`() =
        runTest(mainDispatcherRule.dispatcher) {
            val coordinator = FakeBulkFileOperationCoordinator()
            val viewModel = createViewModel(operationCoordinator = coordinator)
            val first = "/movies/one.mp4"
            val deleted = "/movies/two.mp4"
            viewModel.initialize(
                initialPath = deleted,
                contextFiles = videoFiles(first, deleted),
                selectedPaths = listOf(deleted)
            )
            runCurrent()
            coordinator.startOperation(
                type = BulkFileOperationType.TRASH,
                sourcePaths = listOf(deleted),
                destinationPath = null,
                resolutions = emptyMap()
            )

            coordinator.onOperationCompleted(coordinator.startedRequests.single())
            advanceUntilIdle()

            assertEquals(listOf(first), viewModel.state.value.displayedFiles.map(FileModel::absolutePath))
            assertTrue(viewModel.state.value.selectedFiles.isEmpty())
        }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        fileBrowserRepository: FileBrowserRepository = mockk(relaxed = true),
        volumeRepository: VolumeRepository = mockk(relaxed = true),
        operationCoordinator: BulkFileOperationCoordinator = FakeBulkFileOperationCoordinator()
    ): VideoViewerViewModel {
        val volume = StorageVolume(
            id = "primary",
            storageKey = "primary",
            name = "Internal Storage",
            path = "/movies",
            totalBytes = 1000L,
            freeBytes = 500L,
            isPrimary = true,
            isRemovable = false
        )
        coEvery { volumeRepository.getVolumeForPath(any()) } returns Result.success(volume)

        return VideoViewerViewModel(
            browserPreferencesStore = FakeFilePreferencesStore(),
            fileBrowserRepository = fileBrowserRepository,
            volumeRepository = volumeRepository,
            operationCoordinator = operationCoordinator,
            videoMetadataRepository = mockk(relaxed = true),
            savedStateHandle = savedStateHandle
        )
    }

    private fun videoFiles(vararg paths: String) = paths.map { path ->
        FileModel(
            name = path.substringAfterLast('/'),
            absolutePath = path,
            size = 1L,
            lastModified = 0L,
            isDirectory = false,
            extension = path.substringAfterLast('.', "")
        )
    }
}
