package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.feature.browser.BrowserPropertiesState
import io.mockk.coEvery
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
    private var latestState = BrowserPropertiesState()
    private var latestError: UiText? = null

    @Before
    fun setup() {
        fileBrowserRepository = mockk()
        archiveRepository = mockk(relaxed = true)
        scope = TestScope()
        latestState = BrowserPropertiesState()
        latestError = null
        controller = PropertiesController(
            initialState = latestState,
            scope = scope,
            fileBrowserRepository = fileBrowserRepository,
            archiveRepository = archiveRepository,
            contextProvider = { context },
            onStateChange = { latestState = it },
            onError = { latestError = it }
        )
    }

    @Test
    fun `repository properties load into owned state`() = scope.runTest {
        val path = "/root/report.pdf"
        context = BrowserPropertiesContext(listOf(path), listOf(file("report.pdf", path, 12)), null)
        coEvery { fileBrowserRepository.getSelectionProperties(listOf(path)) } returns
            Result.success(properties("report.pdf", path, 12))

        controller.openForSelection()
        assertTrue(latestState.isLoading)
        advanceUntilIdle()

        assertTrue(latestState.isVisible)
        assertFalse(latestState.isLoading)
        assertEquals("report.pdf", latestState.properties?.title)
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

        assertTrue(latestState.isVisible)
        assertFalse(latestState.isLoading)
        assertEquals(32L, latestState.properties?.totalBytes)
        assertEquals("photos.zip/photos", latestState.properties?.pathSummary)
    }

    @Test
    fun `dismiss cancels pending load and late result cannot reopen dialog`() = scope.runTest {
        val path = "/root/slow.txt"
        val result = CompletableDeferred<Result<SelectionProperties>>()
        context = BrowserPropertiesContext(listOf(path), listOf(file("slow.txt", path, 1)), null)
        coEvery { fileBrowserRepository.getSelectionProperties(listOf(path)) } coAnswers { result.await() }

        controller.openForSelection()
        controller.dismiss()
        result.complete(Result.success(properties("slow.txt", path, 1)))
        advanceUntilIdle()

        assertFalse(latestState.isVisible)
        assertFalse(latestState.isLoading)
        assertNull(latestState.properties)
    }

    @Test
    fun `load failure closes dialog and reports error`() = scope.runTest {
        val path = "/root/missing.txt"
        context = BrowserPropertiesContext(listOf(path), emptyList(), null)
        coEvery { fileBrowserRepository.getSelectionProperties(listOf(path)) } returns
            Result.failure(IllegalStateException("Unavailable"))

        controller.openForSelection()
        advanceUntilIdle()

        assertFalse(latestState.isVisible)
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
