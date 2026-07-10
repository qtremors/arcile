package dev.qtremors.arcile.feature.imagegallery

import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImageGalleryFileActionControllerTest {
    @Test
    fun `selection commands mutate only controller state`() = runTest {
        val controller = controller(
            files = listOf(file("/a"), file("/b"), file("/c")),
            displayedPaths = listOf("/a", "/b", "/c")
        )

        controller.toggleSelection("/a")
        controller.selectMultiple(listOf("/b"))
        assertEquals(setOf("/a", "/b"), controller.state.value.selectedFiles)

        controller.invertSelection()
        assertEquals(setOf("/c"), controller.state.value.selectedFiles)

        controller.removePaths(listOf("/other"))
        assertEquals(setOf("/c"), controller.state.value.selectedFiles)

        controller.removePaths(listOf("/c"))
        assertEquals(emptySet<String>(), controller.state.value.selectedFiles)
    }

    @Test
    fun `late properties result cannot reopen dismissed selection`() = runTest {
        val result = CompletableDeferred<Result<SelectionProperties>>()
        val fileBrowserRepository = mockk<FileBrowserRepository>(relaxed = true)
        coEvery { fileBrowserRepository.getSelectionProperties(listOf("/a")) } coAnswers {
            result.await()
        }
        val controller = controller(
            files = listOf(file("/a")),
            displayedPaths = listOf("/a"),
            fileBrowserRepository = fileBrowserRepository
        )
        controller.toggleSelection("/a")
        controller.openProperties()
        runCurrent()

        controller.clearSelection()
        result.complete(Result.success(properties()))
        advanceUntilIdle()

        assertFalse(controller.state.value.isPropertiesVisible)
        assertNull(controller.state.value.properties)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        files: List<FileModel>,
        displayedPaths: List<String>,
        fileBrowserRepository: FileBrowserRepository = mockk(relaxed = true)
    ) = ImageGalleryFileActionController(
        initialState = ImageGalleryFileActionState(),
        scope = this,
        fileBrowserRepository = fileBrowserRepository,
        fileMutationRepository = mockk<FileMutationRepository>(relaxed = true),
        clipboardRepository = mockk<ClipboardRepository>(relaxed = true),
        volumeRepository = mockk<VolumeRepository>(relaxed = true),
        operationCoordinator = mockk<BulkFileOperationCoordinator>(relaxed = true),
        archivePathResolver = mockk<ArchivePathResolver>(relaxed = true),
        files = { files },
        displayedPaths = { displayedPaths },
        onStateChange = {},
        onBusyChange = {},
        onError = {},
        onPathsRemoved = {},
        onRefreshRequested = {}
    )

    private fun file(path: String) = FileModel(
        name = path.substringAfterLast('/'),
        absolutePath = path,
        size = 1,
        lastModified = 1,
        isDirectory = false
    )

    private fun properties() = SelectionProperties(
        displayName = "a",
        pathSummary = "/a",
        itemCount = 1,
        fileCount = 1,
        folderCount = 0,
        totalBytes = 1,
        newestModifiedAt = 1,
        oldestModifiedAt = 1,
        mimeTypeSummary = "image/jpeg",
        extensionSummary = "jpg",
        hiddenCount = 0,
        accessStatus = PropertiesAccessStatus.Full,
        isSingleItem = true,
        isDirectory = false
    )
}
