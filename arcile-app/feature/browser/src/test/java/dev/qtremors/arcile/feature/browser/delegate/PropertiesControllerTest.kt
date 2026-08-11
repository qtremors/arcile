package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.feature.browser.BrowserPropertiesState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PropertiesControllerTest {
    private lateinit var fileBrowserRepository: FileBrowserRepository
    private lateinit var archiveRepository: ArchiveRepository
    private lateinit var scope: TestScope
    private lateinit var controller: PropertiesController
    private var context = BrowserPropertiesContext(emptyList(), emptyList(), null)
    private var latestError: UiText? = null

    @Before
    fun setup() {
        fileBrowserRepository = mockk()
        archiveRepository = mockk(relaxed = true)
        scope = TestScope()
        latestError = null
        controller = PropertiesController(
            initialState = BrowserPropertiesState(),
            scope = scope,
            fileBrowserRepository = fileBrowserRepository,
            archiveRepository = archiveRepository,
            contextProvider = { context },
            onError = { latestError = it }
        )
    }

    @Test
    fun `repository properties load into owned state`() = scope.runTest {
        val path = "/root/report.pdf"
        val selectedFile = file("report.pdf", path, 12)
        context = BrowserPropertiesContext(listOf(path), listOf(selectedFile), null)
        coEvery { fileBrowserRepository.getNodeSelectionProperties(listOf(selectedFile.nodeRef)) } returns
            Result.success(properties("report.pdf", path, 12))

        controller.openForSelection()
        assertTrue(controller.state.value.isLoading)
        advanceUntilIdle()

        assertTrue(controller.state.value.isVisible)
        assertFalse(controller.state.value.isLoading)
        assertEquals("report.pdf", controller.state.value.properties?.title)
        assertNull(latestError)
    }

    @Test
    fun `archive properties are calculated without repository access`() {
        val path = "archive:///photos/image.jpg"
        context = BrowserPropertiesContext(
            selectedPaths = listOf(path),
            files = listOf(file("image.jpg", path, 32)),
            archiveContext = BrowserArchiveContext("/root/photos.zip", entryPrefix = "photos")
        )

        controller.openForSelection()

        assertTrue(controller.state.value.isVisible)
        assertFalse(controller.state.value.isLoading)
        assertEquals(32L, controller.state.value.properties?.totalBytes)
        assertEquals("photos.zip/photos", controller.state.value.properties?.pathSummary)
    }

    @Test
    fun `protected archive properties load metadata through retained node identity`() = scope.runTest {
        val archive = StorageNodeRef.root(
            displayPath = "/data/local/tmp/private.zip",
            remoteCanonicalIdentity = "/data/local/tmp/private.zip"
        )
        val selected = file("private.zip", archive.displayPath.absolutePath, 100).copy(nodeRef = archive)
        context = BrowserPropertiesContext(
            selectedPaths = listOf(selected.absolutePath),
            files = listOf(selected),
            archiveContext = null
        )
        coEvery { fileBrowserRepository.getNodeSelectionProperties(listOf(archive)) } returns
            Result.success(properties(selected.name, selected.absolutePath, selected.size))
        val summary = ArchiveSummary(
            archivePath = selected.absolutePath,
            format = ArchiveFormat.ZIP,
            archiveSize = 100,
            totalUncompressedSize = 300,
            fileCount = 2,
            folderCount = 1,
            newestModifiedAt = null,
            oldestModifiedAt = null,
            hasUnreadableEntries = false
        )
        coEvery { archiveRepository.getArchiveMetadata(archive, null, any()) } returns
            Result.success(summary)

        controller.openForSelection()
        advanceUntilIdle()

        assertEquals(summary, controller.state.value.properties?.archiveSummary)
        coVerify(exactly = 1) { archiveRepository.getArchiveMetadata(archive, null, any()) }
        coVerify(exactly = 0) { archiveRepository.getArchiveMetadata(selected.absolutePath) }
    }

    @Test
    fun `dismiss cancels pending load and late result cannot reopen dialog`() = scope.runTest {
        val path = "/root/slow.txt"
        val result = CompletableDeferred<Result<SelectionProperties>>()
        val selectedFile = file("slow.txt", path, 1)
        context = BrowserPropertiesContext(listOf(path), listOf(selectedFile), null)
        coEvery { fileBrowserRepository.getNodeSelectionProperties(listOf(selectedFile.nodeRef)) } coAnswers {
            result.await()
        }

        controller.openForSelection()
        controller.dismiss()
        result.complete(Result.success(properties("slow.txt", path, 1)))
        advanceUntilIdle()

        assertFalse(controller.state.value.isVisible)
        assertFalse(controller.state.value.isLoading)
        assertNull(controller.state.value.properties)
    }

    @Test
    fun `load failure closes dialog and reports error`() = scope.runTest {
        val path = "/root/missing.txt"
        context = BrowserPropertiesContext(listOf(path), emptyList(), null)
        coEvery { fileBrowserRepository.getSelectionProperties(listOf(path)) } returns
            Result.failure(IllegalStateException("Unavailable"))

        controller.openForSelection()
        advanceUntilIdle()

        assertFalse(controller.state.value.isVisible)
        assertNotNull(latestError)
    }

    private fun properties(name: String, path: String, size: Long) = SelectionProperties(
        displayName = name,
        pathSummary = path,
        itemCount = 1,
        fileCount = 1,
        folderCount = 0,
        totalBytes = size,
        newestModifiedAt = null,
        oldestModifiedAt = null,
        mimeTypeSummary = null,
        extensionSummary = path.substringAfterLast('.', ""),
        hiddenCount = 0,
        accessStatus = PropertiesAccessStatus.Full,
        isSingleItem = true,
        isDirectory = false
    )

    private fun file(name: String, path: String, size: Long) = FileModel(
        name = name,
        absolutePath = path,
        size = size,
        lastModified = 0,
        isDirectory = false,
        extension = path.substringAfterLast('.', "")
    )
}
