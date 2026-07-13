package dev.qtremors.arcile.feature.imagegallery

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataWriteResult
import dev.qtremors.arcile.testutil.FakeFilePreferencesStore
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageViewerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `viewer initializes independently from explicit context paths`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.initialize(
                initialPath = "/photos/two.jpg",
                contextFiles = imageFiles("/photos/one.jpg", "/photos/two.jpg")
            )

            assertEquals(
                listOf("/photos/one.jpg", "/photos/two.jpg"),
                viewModel.state.value.displayedFiles.map { it.absolutePath }
            )
            assertEquals("/photos/two.jpg", viewModel.state.value.viewerCurrentPath)
        }

    @Test
    fun `single image launch discovers neighboring images only`() =
        runTest(mainDispatcherRule.dispatcher) {
            val repository = mockk<FileBrowserRepository>(relaxed = true)
            coEvery { repository.listFiles("/photos") } returns Result.success(
                listOf(
                    FileModel("one.jpg", "/photos/one.jpg", extension = "jpg"),
                    FileModel("notes.txt", "/photos/notes.txt", extension = "txt"),
                    FileModel("album", "/photos/album", isDirectory = true),
                    FileModel("two.png", "/photos/two.png", extension = "png")
                )
            )
            val viewModel = createViewModel(fileBrowserRepository = repository)

            viewModel.initialize("/photos/one.jpg", emptyList())
            advanceUntilIdle()

            assertEquals(
                listOf("/photos/one.jpg", "/photos/two.png"),
                viewModel.state.value.displayedFiles.map(FileModel::absolutePath)
            )
        }

    @Test
    fun `viewer preserves gallery selection supplied at launch`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()

            viewModel.initialize(
                initialPath = "/photos/two.jpg",
                contextFiles = imageFiles("/photos/one.jpg", "/photos/two.jpg"),
                selectedPaths = listOf("/photos/one.jpg")
            )

            assertEquals(setOf("/photos/one.jpg"), viewModel.state.value.selectedFiles)
        }

    @Test
    fun `viewer session restores from its own saved state handle`() =
        runTest(mainDispatcherRule.dispatcher) {
            val savedStateHandle = SavedStateHandle()
            val viewModel = createViewModel(savedStateHandle)
            val initialPath = "/photos/opened.jpg"
            val currentPath = "/photos/current.jpg"

            viewModel.initialize(initialPath, imageFiles(initialPath, currentPath))
            viewModel.setViewerCurrentPath(currentPath)
            viewModel.setViewerMetadataVisible(currentPath, visible = true)
            viewModel.toggleViewerUi()
            viewModel.rotateViewerImage(currentPath)
            viewModel.setViewerEraseDialogPath(currentPath)

            val restored = createViewModel(savedStateHandle).state.value

            assertEquals(initialPath, restored.viewerSessionInitialPath)
            assertEquals(currentPath, restored.viewerCurrentPath)
            assertEquals(currentPath, restored.viewerMetadataPath)
            assertFalse(restored.viewerUiVisible)
            assertEquals(90f, restored.viewerRotationDegrees[currentPath])
            assertEquals(currentPath, restored.viewerEraseDialogPath)
        }

    @Test
    fun `new viewer session clears stale transient state`() =
        runTest(mainDispatcherRule.dispatcher) {
            val viewModel = createViewModel()
            viewModel.initialize("/photos/first.jpg", emptyList())
            viewModel.setViewerCurrentPath("/photos/stale.jpg")
            viewModel.setViewerMetadataVisible("/photos/stale.jpg", visible = true)
            viewModel.toggleViewerUi()
            viewModel.setViewerEraseDialogPath("/photos/stale.jpg")

            viewModel.initialize("/photos/second.jpg", emptyList())

            val state = viewModel.state.value
            assertEquals("/photos/second.jpg", state.viewerCurrentPath)
            assertNull(state.viewerMetadataPath)
            assertTrue(state.viewerUiVisible)
            assertNull(state.viewerEraseDialogPath)
        }

    @Test
    fun `successful metadata erase advances viewer revision`() =
        runTest(mainDispatcherRule.dispatcher) {
            val metadataRepository = mockk<ImageMetadataRepository>()
            coEvery { metadataRepository.erase("/photos/item.jpg") } returns
                ImageMetadataWriteResult.Success
            val viewModel = createViewModel(metadataRepository = metadataRepository)

            viewModel.eraseMetadata("/photos/item.jpg")
            advanceUntilIdle()

            assertEquals(1L, viewModel.state.value.viewerMetadataRevision)
            assertNull(viewModel.state.value.viewerMetadataSavingPath)
        }

    @Test
    fun `successful current image delete preserves other gallery selections`() =
        runTest(mainDispatcherRule.dispatcher) {
            val coordinator = FakeBulkFileOperationCoordinator()
            val volumeRepository = trashableVolumeRepository()
            val viewModel = createViewModel(
                operationCoordinator = coordinator,
                volumeRepository = volumeRepository
            )
            val first = "/photos/one.jpg"
            val second = "/photos/two.jpg"
            viewModel.initialize(second, imageFiles(first, second), listOf(first, second))

            viewModel.requestDeleteCurrent(second)
            advanceUntilIdle()
            viewModel.confirmDeleteSelected()
            advanceUntilIdle()

            assertEquals(setOf(first), viewModel.state.value.selectedFiles)
            assertEquals(listOf(second), coordinator.startedRequests.single().sourcePaths)
        }

    @Test
    fun `failed current image delete restores complete gallery selection`() =
        runTest(mainDispatcherRule.dispatcher) {
            val coordinator = FakeBulkFileOperationCoordinator().apply { startResult = false }
            val viewModel = createViewModel(
                operationCoordinator = coordinator,
                volumeRepository = trashableVolumeRepository()
            )
            val selected = listOf("/photos/one.jpg", "/photos/two.jpg")
            viewModel.initialize(selected.last(), imageFiles(*selected.toTypedArray()), selected)

            viewModel.requestDeleteCurrent(selected.last())
            advanceUntilIdle()
            viewModel.confirmDeleteSelected()
            advanceUntilIdle()

            assertEquals(selected.toSet(), viewModel.state.value.selectedFiles)
            assertTrue(coordinator.startedRequests.isEmpty())
        }

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        metadataRepository: ImageMetadataRepository = mockk(relaxed = true),
        fileBrowserRepository: FileBrowserRepository? = null,
        operationCoordinator: BulkFileOperationCoordinator = FakeBulkFileOperationCoordinator(),
        volumeRepository: VolumeRepository = mockk(relaxed = true)
    ): ImageViewerViewModel {
        val repository = fileBrowserRepository ?: mockk<FileBrowserRepository>(relaxed = true).also {
            coEvery { it.listFiles(any()) } returns Result.success(emptyList())
        }
        coEvery { repository.getSelectionProperties(any()) } returns
            Result.failure(IllegalStateException("Properties are not needed by this test"))
        return ImageViewerViewModel(
            browserPreferencesStore = FakeFilePreferencesStore(),
            fileBrowserRepository = repository,
            volumeRepository = volumeRepository,
            operationCoordinator = operationCoordinator,
            imageMetadataRepository = metadataRepository,
            galleryRepository = mockk(relaxed = true),
            savedStateHandle = savedStateHandle
        )
    }

    private fun imageFiles(vararg paths: String) = paths.map { path ->
        FileModel(path.substringAfterLast('/'), path, extension = path.substringAfterLast('.'))
    }

    private fun trashableVolumeRepository(): VolumeRepository =
        mockk<VolumeRepository>(relaxed = true).also { repository ->
            coEvery { repository.getVolumeForPath(any()) } returns Result.success(
                StorageVolume("internal", "internal", "Internal", "/photos", 1, 1, true, false,
                    StorageKind.INTERNAL)
            )
        }
}
