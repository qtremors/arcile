package dev.qtremors.arcile.feature.imagegallery

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
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
                contextPaths = listOf("/photos/one.jpg", "/photos/two.jpg")
            )

            assertEquals(
                listOf("/photos/one.jpg", "/photos/two.jpg"),
                viewModel.state.value.displayedFiles.map { it.absolutePath }
            )
            assertEquals("/photos/two.jpg", viewModel.state.value.viewerCurrentPath)
        }

    @Test
    fun `viewer session restores from its own saved state handle`() =
        runTest(mainDispatcherRule.dispatcher) {
            val savedStateHandle = SavedStateHandle()
            val viewModel = createViewModel(savedStateHandle)
            val initialPath = "/photos/opened.jpg"
            val currentPath = "/photos/current.jpg"

            viewModel.initialize(initialPath, listOf(initialPath, currentPath))
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

    private fun createViewModel(
        savedStateHandle: SavedStateHandle = SavedStateHandle(),
        metadataRepository: ImageMetadataRepository = mockk(relaxed = true)
    ) = ImageViewerViewModel(
        browserPreferencesStore = FakeFilePreferencesStore(),
        fileBrowserRepository = mockk<FileBrowserRepository>(relaxed = true),
        volumeRepository = mockk<VolumeRepository>(relaxed = true),
        operationCoordinator = FakeBulkFileOperationCoordinator() as BulkFileOperationCoordinator,
        imageMetadataRepository = metadataRepository,
        galleryRepository = mockk(relaxed = true),
        savedStateHandle = savedStateHandle
    )
}
