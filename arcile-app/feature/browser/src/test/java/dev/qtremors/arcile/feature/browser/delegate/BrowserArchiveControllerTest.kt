package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.runtime.R
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.feature.browser.ArchiveExtractionTarget
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.feature.browser.PendingArchiveExtraction
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserArchiveControllerTest {
    private lateinit var scope: TestScope
    private lateinit var repository: ArchiveRepository
    private lateinit var pathResolver: ArchivePathResolver
    private lateinit var operations: BulkFileOperationCoordinator
    private lateinit var context: BrowserArchiveWorkflowContext
    private var selectionCleared = false
    private var error: UiText? = null

    @Before
    fun setUp() {
        scope = TestScope(UnconfinedTestDispatcher())
        repository = mockk(relaxed = true)
        pathResolver = mockk(relaxed = true)
        operations = mockk(relaxed = true)
        context = BrowserArchiveWorkflowContext(
            archiveContext = BrowserArchiveContext(
                archivePath = "/storage/Photos.zip",
                entryPrefix = "Trip",
                nameEncoding = ArchiveNameEncoding.UTF_8
            ),
            currentPath = "/storage/Photos.zip",
            selectedPaths = emptySet()
        )
        selectionCleared = false
        error = null
    }

    @Test
    fun `busy extraction preserves pending request and selection`() = scope.runTest {
        val pending = PendingArchiveExtraction(
            archivePath = "/storage/Photos.zip",
            destinationPath = "/storage/Photos",
            entryPrefix = "Trip"
        )
        every {
            operations.startOperation(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns false
        val controller = controller(
            BrowserArchiveWorkflowState(pendingExtraction = pending)
        )

        controller.confirmPendingExtraction(mapOf("Trip/photo.jpg" to ConflictResolution.REPLACE))

        assertEquals(pending, controller.state.value.pendingExtraction)
        assertFalse(selectionCleared)
        assertEquals(
            UiText.StringResource(R.string.error_operation_already_running),
            error
        )
    }

    @Test
    fun `busy archive creation does not discard selection`() = scope.runTest {
        context = context.copy(
            archiveContext = null,
            currentPath = "/storage",
            selectedPaths = setOf("/storage/photo.jpg")
        )
        coEvery { pathResolver.resolve(any()) } returns Result.success("/storage/Photos.zip")
        every {
            operations.startOperation(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns false
        val controller = controller()

        controller.createArchive(
            archiveName = "Photos",
            format = ArchiveFormat.ZIP,
            compressionLevel = ArchiveCompressionLevel.STORE,
            password = null
        )

        assertFalse(selectionCleared)
        assertEquals(
            UiText.StringResource(R.string.error_operation_already_running),
            error
        )
    }

    @Test
    fun `password retry remains pending when extraction cannot start`() = scope.runTest {
        val pending = PendingArchiveExtraction(
            archivePath = "/storage/Photos.zip",
            destinationPath = "/storage/Photos",
            entryPrefix = "Trip"
        )
        coEvery {
            repository.detectArchiveConflicts(any(), any(), any(), any(), any())
        } returns Result.success(emptyList())
        every {
            operations.startOperation(
                any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns false
        val controller = controller(
            BrowserArchiveWorkflowState(pendingExtraction = pending)
        )

        controller.retryWithPassword("secret")

        assertNotNull(controller.state.value.pendingExtraction)
        assertEquals("secret", controller.state.value.pendingExtraction?.password)
        assertFalse(selectionCleared)
    }

    private fun controller(
        initialState: BrowserArchiveWorkflowState = BrowserArchiveWorkflowState()
    ) = BrowserArchiveController(
        initialState = initialState,
        scope = scope,
        archiveRepository = repository,
        archivePathResolver = pathResolver,
        operationCoordinator = operations,
        contextProvider = { context },
        clearSelection = { selectionCleared = true },
        onWorkflowChanged = {},
        onConflicts = {},
        onDismissConflicts = {},
        onError = { error = it }
    )
}
