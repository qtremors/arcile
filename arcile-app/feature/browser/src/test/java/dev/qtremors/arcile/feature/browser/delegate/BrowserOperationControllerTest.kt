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
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.feature.browser.BrowserOperationState
import dev.qtremors.arcile.feature.browser.BrowserUndoAction
import dev.qtremors.arcile.feature.browser.MoveUndoEntry
import dev.qtremors.arcile.feature.browser.RenameUndoEntry
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
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
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
        every { clipboardRepository.clearClipboardState(any()) } answers {
            val sessionId = firstArg<String>()
            if (clipboardState.value?.sessionId == sessionId) {
                clipboardState.value = null
                true
            } else {
                false
            }
        }
        coEvery { clipboardRepository.moveFiles(any(), any()) } returns Result.success(Unit)
        fileMutationRepository = mockk(relaxed = true)
        trashRepository = mockk(relaxed = true)
        coEvery { trashRepository.getTrashFiles() } returns Result.success(emptyList())
        busy = false
        latestError = null
        refreshCount = 0
        controller = BrowserOperationController(
            initialState = BrowserOperationState(),
            scope = scope,
            trashRepository = trashRepository,
            fileMutationRepository = fileMutationRepository,
            clipboardRepository = clipboardRepository,
            clipboardController = ClipboardController(clipboardRepository),
            coordinator = coordinator,
            onBusyChange = { busy = it },
            onError = { latestError = it },
            refreshAction = { refreshCount += 1 }
        )
    }

    @Test
    fun `progress and completion update owned operation state`() = scope.runTest {
        val clipboard = ClipboardState(
            ClipboardOperation.COPY,
            listOf(file("/source.txt"))
        )
        clipboardState.value = clipboard
        controller.startObserving()
        advanceUntilIdle()

        coordinator.startOperation(
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap(),
            clipboardSessionId = clipboard.sessionId
        )
        advanceUntilIdle()
        val request = coordinator.activeRequest.value!!
        coordinator.onOperationProgress(
            request,
            BulkFileOperationProgress(1, 2, "/source.txt")
        )
        advanceUntilIdle()

        assertTrue(busy)
        assertEquals(1, controller.state.value.activeFileOperation?.completedItems)
        assertEquals(2, controller.state.value.activeFileOperation?.totalItems)

        coordinator.onOperationCompleted(request)
        advanceTimeBy(799)
        runCurrent()

        assertFalse(busy)
        assertEquals(
            OperationCompletionStatus.SUCCESS,
            controller.state.value.activeFileOperation?.terminalStatus
        )
        assertNull(controller.state.value.clipboardState)
        assertEquals(1, refreshCount)
        assertNull(latestError)
        advanceTimeBy(1)
        runCurrent()
        assertNull(controller.state.value.activeFileOperation)
        assertNull(controller.state.value.fileOperationStatusMessage)
        controller.stopObserving()
    }

    @Test
    fun `foreign operation events do not populate browser ui or clear clipboard`() = scope.runTest {
        val clipboard = ClipboardState(
            ClipboardOperation.COPY,
            listOf(file("/queued.txt"))
        )
        clipboardState.value = clipboard
        controller.startObserving()
        advanceUntilIdle()

        coordinator.startOperation(
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/gallery.jpg"),
            destinationPath = "/Pictures",
            resolutions = emptyMap(),
            presentationOwnerId = "gallery-owner"
        )
        val request = requireNotNull(coordinator.activeRequest.value)
        coordinator.onOperationCompleted(request)
        runCurrent()

        assertNull(controller.state.value.activeFileOperation)
        assertNull(controller.state.value.fileOperationStatusMessage)
        assertEquals(clipboard, controller.state.value.clipboardState)
        assertEquals(clipboard, clipboardState.value)
        assertEquals(1, refreshCount)
        controller.stopObserving()
    }

    @Test
    fun `recreated controller does not replay terminal events`() = scope.runTest {
        listOf("completed", "failed", "cancelled").forEach { terminal ->
            val lateCoordinator = FakeBulkFileOperationCoordinator()
            lateCoordinator.startOperation(
                type = BulkFileOperationType.TRASH,
                sourcePaths = listOf("/old.txt"),
                destinationPath = null,
                resolutions = emptyMap(),
                presentationOwnerId = "late-owner"
            )
            val request = requireNotNull(lateCoordinator.activeRequest.value)
            when (terminal) {
                "completed" -> lateCoordinator.onOperationCompleted(request)
                "failed" -> lateCoordinator.onOperationFailed(request, "Old failure")
                else -> lateCoordinator.onOperationCancelled(request)
            }

            var lateBusy = false
            var lateRefreshCount = 0
            val recreated = BrowserOperationController(
                initialState = BrowserOperationState(),
                scope = scope,
                trashRepository = trashRepository,
                fileMutationRepository = fileMutationRepository,
                clipboardRepository = clipboardRepository,
                clipboardController = ClipboardController(clipboardRepository),
                coordinator = lateCoordinator,
                operationOwnerId = "late-owner",
                onBusyChange = { lateBusy = it },
                onError = {},
                refreshAction = { lateRefreshCount += 1 }
            )

            recreated.startObserving()
            runCurrent()

            assertFalse(lateBusy)
            assertNull(recreated.state.value.activeFileOperation)
            assertNull(recreated.state.value.fileOperationStatusMessage)
            assertNull(recreated.state.value.pendingUndoAction)
            assertEquals(0, lateRefreshCount)
            recreated.stopObserving()
        }
    }

    @Test
    fun `owned non clipboard completion preserves queued clipboard`() = scope.runTest {
        val clipboard = ClipboardState(
            ClipboardOperation.COPY,
            listOf(file("/queued.txt"))
        )
        clipboardState.value = clipboard
        controller.startObserving()
        advanceUntilIdle()

        coordinator.startOperation(
            type = BulkFileOperationType.CREATE_ARCHIVE,
            sourcePaths = listOf("/source.txt"),
            destinationPath = "/archive.zip",
            resolutions = emptyMap()
        )
        val request = requireNotNull(coordinator.activeRequest.value)
        coordinator.onOperationCompleted(request)
        advanceTimeBy(799)
        runCurrent()

        assertEquals(clipboard, controller.state.value.clipboardState)
        assertEquals(clipboard, clipboardState.value)
        assertEquals(
            OperationCompletionStatus.SUCCESS,
            controller.state.value.activeFileOperation?.terminalStatus
        )
        controller.stopObserving()
    }

    @Test
    fun `active request state clears progress when terminal event is unavailable`() = scope.runTest {
        controller.startObserving()
        coordinator.startOperation(
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap()
        )
        runCurrent()
        assertTrue(busy)
        assertTrue(controller.state.value.activeFileOperation != null)

        coordinator.clearActiveRequestWithoutEvent()
        runCurrent()

        assertFalse(busy)
        assertNull(controller.state.value.activeFileOperation)
        controller.stopObserving()
    }

    @Test
    fun `clipboard completion preserves a replacement clipboard session`() = scope.runTest {
        val original = ClipboardState(
            ClipboardOperation.COPY,
            listOf(file("/source.txt")),
            sessionId = "clipboard-a"
        )
        val replacement = ClipboardState(
            ClipboardOperation.COPY,
            listOf(file("/replacement.txt")),
            sessionId = "clipboard-b"
        )
        clipboardState.value = original
        controller.startObserving()
        coordinator.startOperation(
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap(),
            clipboardSessionId = original.sessionId
        )
        val request = requireNotNull(coordinator.activeRequest.value)
        clipboardState.value = replacement
        runCurrent()

        coordinator.onOperationCompleted(request)
        advanceTimeBy(799)
        runCurrent()

        assertEquals(replacement, clipboardState.value)
        assertEquals(replacement, controller.state.value.clipboardState)
        controller.stopObserving()
    }

    @Test
    fun `move undo uses original parent and clears pending action`() = scope.runTest {
        val initialState = BrowserOperationState(
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
            initialState = initialState,
            scope = scope,
            trashRepository = trashRepository,
            fileMutationRepository = fileMutationRepository,
            clipboardRepository = clipboardRepository,
            clipboardController = ClipboardController(clipboardRepository),
            coordinator = coordinator,
            onBusyChange = { busy = it },
            onError = { latestError = it },
            refreshAction = { refreshCount += 1 }
        )

        controller.undoLastOperation()
        advanceUntilIdle()

        coVerify(exactly = 1) {
            clipboardRepository.moveFiles(listOf("/dest/item.txt"), "/source")
        }
        assertNull(controller.state.value.pendingUndoAction)
        assertEquals(1, refreshCount)
    }

    @Test
    fun `privileged rename undo uses renamed node instead of local path`() = scope.runTest {
        val original = rootNode("/system/a.conf")
        val renamed = rootNode("/system/one.conf")
        coEvery { fileMutationRepository.renameNode(renamed, "a.conf") } returns
            Result.success(file("/system/a.conf", original))
        controller = operationController(
            BrowserOperationState(
                pendingUndoAction = BrowserUndoAction.Rename(
                    originalPath = "/system/a.conf",
                    renamedPath = "/system/one.conf",
                    originalNode = original,
                    renamedNode = renamed
                )
            )
        )

        controller.undoLastOperation()
        advanceUntilIdle()

        coVerify(exactly = 1) { fileMutationRepository.renameNode(renamed, "a.conf") }
        coVerify(exactly = 0) { fileMutationRepository.renameFile(any(), any()) }
        assertEquals(1, refreshCount)
        assertNull(controller.state.value.pendingUndoAction)
    }

    @Test
    fun `privileged batch rename undo retains each renamed node`() = scope.runTest {
        val firstOriginal = rootNode("/system/a.conf")
        val secondOriginal = rootNode("/system/b.conf")
        val firstRenamed = rootNode("/system/one.conf")
        val secondRenamed = rootNode("/system/two.conf")
        val reverse = listOf(firstRenamed to "a.conf", secondRenamed to "b.conf")
        coEvery { fileMutationRepository.batchRenameNodes(reverse) } returns Result.success(
            listOf(firstRenamed to firstOriginal, secondRenamed to secondOriginal)
        )
        controller = operationController(
            BrowserOperationState(
                pendingUndoAction = BrowserUndoAction.BatchRename(
                    persistentListOf(
                        RenameUndoEntry("/system/a.conf", "/system/one.conf", firstOriginal, firstRenamed),
                        RenameUndoEntry("/system/b.conf", "/system/two.conf", secondOriginal, secondRenamed)
                    )
                )
            )
        )

        controller.undoLastOperation()
        advanceUntilIdle()

        coVerify(exactly = 1) { fileMutationRepository.batchRenameNodes(reverse) }
        coVerify(exactly = 0) { fileMutationRepository.batchRenameFiles(any()) }
        assertEquals(1, refreshCount)
        assertNull(controller.state.value.pendingUndoAction)
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

        assertEquals(requirement, controller.state.value.pendingAuthorization)
        assertFalse(controller.handleAuthorizationResult("stale-request", confirmed = true))
        assertEquals(requirement, controller.state.value.pendingAuthorization)
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

        assertNull(controller.state.value.pendingAuthorization)
        assertEquals(listOf("trash-1"), controller.state.value.pendingTrashUndoIds)
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

        assertNull(controller.state.value.pendingAuthorization)
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

        assertNull(controller.state.value.pendingAuthorization)
        assertTrue(latestError != null)
        coVerify(exactly = 1) { trashRepository.restoreFromTrash(listOf("trash-1")) }
    }

    private fun operationController(initialState: BrowserOperationState): BrowserOperationController {
        return BrowserOperationController(
            initialState = initialState,
            scope = scope,
            trashRepository = trashRepository,
            fileMutationRepository = fileMutationRepository,
            clipboardRepository = clipboardRepository,
            clipboardController = ClipboardController(clipboardRepository),
            coordinator = coordinator,
            onBusyChange = { busy = it },
            onError = { latestError = it },
            refreshAction = { refreshCount += 1 }
        )
    }

    private fun authorizationRequirement(requestId: String) = StorageAuthorizationRequirement(
        requestId = requestId,
        operation = StorageAuthorizationOperation.RESTORE_TRASH
    )

    private fun file(path: String, nodeRef: StorageNodeRef = StorageNodeRef.local(path)) = FileModel(
        name = path.substringAfterLast('/'),
        absolutePath = path,
        size = 0,
        lastModified = 0,
        isDirectory = false,
        extension = path.substringAfterLast('.', ""),
        nodeRef = nodeRef
    )

    private fun rootNode(path: String): StorageNodeRef = StorageNodeRef.root(
        displayPath = path,
        remoteCanonicalIdentity = path
    )
}
