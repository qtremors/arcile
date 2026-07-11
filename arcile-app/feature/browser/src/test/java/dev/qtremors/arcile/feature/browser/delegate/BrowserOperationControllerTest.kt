package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationOperation
import dev.qtremors.arcile.core.storage.domain.StorageAuthorizationRequirement
import dev.qtremors.arcile.core.storage.domain.StorageMutationResult
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.feature.browser.BrowserOperationState
import dev.qtremors.arcile.feature.browser.BrowserUndoAction
import dev.qtremors.arcile.feature.browser.MoveUndoEntry
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserOperationControllerTest {
    private lateinit var scope: TestScope
    private lateinit var coordinator: FakeBulkFileOperationCoordinator
    private lateinit var clipboardRepository: ClipboardRepository
    private lateinit var clipboardState: MutableStateFlow<ClipboardState?>
    private lateinit var fileMutationRepository: FileMutationRepository
    private lateinit var trashRepository: TrashRepository
    private lateinit var controller: BrowserOperationController
    private var latestState = BrowserOperationState()
    private var busy = false
    private var latestError: UiText? = null
    private var refreshCount = 0

    @Before
    fun setup() {
        scope = TestScope()
        coordinator = FakeBulkFileOperationCoordinator()
        clipboardState = MutableStateFlow(null)
        clipboardRepository = mockk(relaxed = true)
        every { clipboardRepository.clipboardState } returns clipboardState
        every { clipboardRepository.clearClipboardState() } answers {
            clipboardState.value = null
        }
        coEvery { clipboardRepository.moveFiles(any(), any()) } returns Result.success(Unit)
        fileMutationRepository = mockk(relaxed = true)
        trashRepository = mockk(relaxed = true)
        coEvery { trashRepository.getTrashFiles() } returns Result.success(emptyList())
        latestState = BrowserOperationState()
        busy = false
        latestError = null
        refreshCount = 0
        controller = BrowserOperationController(
            initialState = latestState,
            scope = scope,
            trashRepository = trashRepository,
            fileMutationRepository = fileMutationRepository,
            clipboardRepository = clipboardRepository,
            clipboardController = ClipboardController(clipboardRepository),
            coordinator = coordinator,
            onStateChange = { latestState = it },
            onBusyChange = { busy = it },
            onError = { latestError = it },
            refreshAction = { refreshCount += 1 }
        )
    }

    @Test
    fun `progress and completion update owned operation state`() = scope.runTest {
        clipboardState.value = ClipboardState(
            ClipboardOperation.COPY,
            listOf(file("/source.txt"))
        )
        controller.startObserving()
        advanceUntilIdle()

        coordinator.startOperation(
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap()
        )
        advanceUntilIdle()
        val request = coordinator.activeRequest.value!!
        coordinator.onOperationProgress(
            request,
            BulkFileOperationProgress(1, 2, "/source.txt")
        )
        advanceUntilIdle()

        assertTrue(busy)
        assertEquals(1, latestState.activeFileOperation?.completedItems)
        assertEquals(2, latestState.activeFileOperation?.totalItems)

        coordinator.onOperationCompleted(request)
        advanceUntilIdle()

        assertFalse(busy)
        assertEquals(
            OperationCompletionStatus.SUCCESS,
            latestState.activeFileOperation?.terminalStatus
        )
        assertNull(latestState.clipboardState)
        assertEquals(1, refreshCount)
        assertNull(latestError)
        controller.stopObserving()
    }

    @Test
    fun `move undo uses original parent and clears pending action`() = scope.runTest {
        latestState = BrowserOperationState(
            pendingUndoAction = BrowserUndoAction.Moved(
                persistentListOf(
                    MoveUndoEntry(
                        originalPath = "/source/item.txt",
                        movedPath = "/dest/item.txt"
                    )
                )
            )
        )
        controller = BrowserOperationController(
            initialState = latestState,
            scope = scope,
            trashRepository = trashRepository,
            fileMutationRepository = fileMutationRepository,
            clipboardRepository = clipboardRepository,
            clipboardController = ClipboardController(clipboardRepository),
            coordinator = coordinator,
            onStateChange = { latestState = it },
            onBusyChange = { busy = it },
            onError = { latestError = it },
            refreshAction = { refreshCount += 1 }
        )

        controller.undoLastOperation()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            clipboardRepository.moveFiles(listOf("/dest/item.txt"), "/source")
        }
        assertNull(latestState.pendingUndoAction)
        assertEquals(1, refreshCount)
    }

    @Test
    fun `trash undo exposes neutral authorization and ignores stale result ids`() = scope.runTest {
        val requirement = authorizationRequirement("undo-request")
        coEvery { trashRepository.restoreFromTrash(listOf("trash-1")) } returns
            StorageMutationResult.AuthorizationRequired(requirement)
        controller = operationController(
            BrowserOperationState(pendingTrashUndoIds = persistentListOf("trash-1"))
        )

        controller.undoLastTrashMove()
        advanceUntilIdle()

        assertEquals(requirement, latestState.pendingAuthorization)
        assertFalse(controller.handleAuthorizationResult("stale-request", confirmed = true))
        assertEquals(requirement, latestState.pendingAuthorization)
        coVerify(exactly = 1) { trashRepository.restoreFromTrash(listOf("trash-1")) }
    }

    @Test
    fun `denied trash undo clears authorization without retrying`() = scope.runTest {
        val requirement = authorizationRequirement("denied-request")
        coEvery { trashRepository.restoreFromTrash(listOf("trash-1")) } returns
            StorageMutationResult.AuthorizationRequired(requirement)
        controller = operationController(
            BrowserOperationState(pendingTrashUndoIds = persistentListOf("trash-1"))
        )
        controller.undoLastTrashMove()
        advanceUntilIdle()

        assertTrue(controller.handleAuthorizationResult(requirement.requestId, confirmed = false))

        assertNull(latestState.pendingAuthorization)
        assertEquals(listOf("trash-1"), latestState.pendingTrashUndoIds)
        coVerify(exactly = 1) { trashRepository.restoreFromTrash(listOf("trash-1")) }
    }

    @Test
    fun `confirmed trash undo retries and refreshes after completion`() = scope.runTest {
        val requirement = authorizationRequirement("confirmed-request")
        coEvery { trashRepository.restoreFromTrash(listOf("trash-1")) } returnsMany listOf(
            StorageMutationResult.AuthorizationRequired(requirement),
            StorageMutationResult.Completed
        )
        controller = operationController(
            BrowserOperationState(pendingTrashUndoIds = persistentListOf("trash-1"))
        )
        controller.undoLastTrashMove()
        advanceUntilIdle()

        assertTrue(controller.handleAuthorizationResult(requirement.requestId, confirmed = true))
        advanceUntilIdle()

        assertNull(latestState.pendingAuthorization)
        assertEquals(1, refreshCount)
        coVerify(exactly = 2) { trashRepository.restoreFromTrash(listOf("trash-1")) }
    }

    @Test
    fun `expired trash authorization clears pending state and reports error`() = scope.runTest {
        val requirement = authorizationRequirement("expired-request")
        coEvery { trashRepository.restoreFromTrash(listOf("trash-1")) } returns
            StorageMutationResult.AuthorizationRequired(requirement)
        controller = operationController(
            BrowserOperationState(pendingTrashUndoIds = persistentListOf("trash-1"))
        )
        controller.undoLastTrashMove()
        advanceUntilIdle()

        assertTrue(controller.handleAuthorizationUnavailable(requirement.requestId))

        assertNull(latestState.pendingAuthorization)
        assertTrue(latestError != null)
        coVerify(exactly = 1) { trashRepository.restoreFromTrash(listOf("trash-1")) }
    }

    private fun operationController(initialState: BrowserOperationState): BrowserOperationController {
        latestState = initialState
        return BrowserOperationController(
            initialState = initialState,
            scope = scope,
            trashRepository = trashRepository,
            fileMutationRepository = fileMutationRepository,
            clipboardRepository = clipboardRepository,
            clipboardController = ClipboardController(clipboardRepository),
            coordinator = coordinator,
            onStateChange = { latestState = it },
            onBusyChange = { busy = it },
            onError = { latestError = it },
            refreshAction = { refreshCount += 1 }
        )
    }

    private fun authorizationRequirement(requestId: String) = StorageAuthorizationRequirement(
        requestId = requestId,
        operation = StorageAuthorizationOperation.RESTORE_TRASH
    )

    private fun file(path: String) = FileModel(
        name = path.substringAfterLast('/'),
        absolutePath = path,
        size = 0,
        lastModified = 0,
        isDirectory = false,
        extension = path.substringAfterLast('.', "")
    )
}
