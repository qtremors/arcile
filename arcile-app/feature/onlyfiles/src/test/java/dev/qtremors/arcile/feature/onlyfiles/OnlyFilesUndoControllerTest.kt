package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import dev.qtremors.arcile.core.ui.R as UiR

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesUndoControllerTest {
    @Test fun `undo creation clears the removed node from viewers properties and selection`() = runTest {
        val f = Fixture(this)
        val other = f.node.copy(ref = f.node.ref.copy(nodeId = NodeId.of("other")), name = "Other.txt")
        f.state.value = f.state.value.copy(viewer = f.node, viewerQueue = listOf(f.node, other),
            properties = listOf(f.node, other), selectedNodeIds = setOf(f.node.ref.nodeId.value, other.ref.nodeId.value))
        coEvery { f.files.undoCreated(f.node) } returns Result.success(VaultUndoResult(removedNodeIds = setOf(f.node.ref.nodeId)))

        f.controller.undo(f.feedback)
        advanceUntilIdle()

        assertNull(f.state.value.viewer)
        assertEquals(listOf(other), f.state.value.viewerQueue)
        assertEquals(listOf(other), f.state.value.properties)
        assertEquals(setOf(other.ref.nodeId.value), f.state.value.selectedNodeIds)
        assertEquals(UiText.StringResource(R.string.onlyfiles_undo_complete), f.state.value.operationFeedback?.message)
        assertNull(f.state.value.operationFeedback?.undo)
        assertEquals(setOf(f.node.ref.nodeId), f.state.value.operationFeedback?.undoResult?.removedNodeIds)
        assertEquals(1, f.reloads)
    }

    @Test fun `undo folder creation cleans opaque folder preferences after verified removal`() = runTest {
        val f = Fixture(this)
        val folder = f.node.copy(kind = VaultNodeKind.DIRECTORY, ref = f.node.ref.copy(directoryId = DirectoryId.of("created")))
        val feedback = f.setUndo(OnlyFilesUndoAction.Created(folder))
        coEvery { f.files.undoCreated(folder) } returns Result.success(VaultUndoResult(
            removedNodeIds = setOf(folder.ref.nodeId), removedDirectoryIds = setOf(requireNotNull(folder.ref.directoryId))))

        f.controller.undo(feedback)
        advanceUntilIdle()

        assertEquals(listOf(f.vault to setOf(folder.ref.directoryId)), f.removedFolders)
    }

    @Test fun `undo rename refreshes the active viewer and queue metadata`() = runTest {
        val f = Fixture(this)
        val restored = f.node.copy(name = "Original.txt")
        val entries = listOf(VaultRenameUndoEntry(f.node, restored.name))
        val feedback = f.setUndo(OnlyFilesUndoAction.Renamed(entries))
        f.state.value = f.state.value.copy(viewer = f.node, viewerQueue = listOf(f.node), properties = listOf(f.node))
        coEvery { f.files.undoRenames(entries) } returns Result.success(VaultUndoResult(updated = listOf(restored)))

        f.controller.undo(feedback)
        advanceUntilIdle()

        assertEquals(restored, f.state.value.viewer)
        assertEquals(listOf(restored), f.state.value.viewerQueue)
        assertEquals(listOf(restored), f.state.value.properties)
    }

    @Test fun `undoing the empty folder currently being viewed returns to its surviving parent`() = runTest {
        val f = Fixture(this)
        val id = DirectoryId.of("created")
        val folder = f.node.copy(kind = VaultNodeKind.DIRECTORY, ref = f.node.ref.copy(directoryId = id))
        val feedback = f.setUndo(OnlyFilesUndoAction.Created(folder))
        f.state.value = f.state.value.copy(directoryStack = listOf(
            VaultDirectoryCrumb(DirectoryId.Root, "Vault", VaultPath.Root),
            VaultDirectoryCrumb(id, folder.name, VaultPath.of(folder.name))))
        coEvery { f.files.undoCreated(folder) } returns Result.success(VaultUndoResult(
            removedNodeIds = setOf(folder.ref.nodeId), removedDirectoryIds = setOf(id)))

        f.controller.undo(feedback)
        advanceUntilIdle()

        assertEquals(listOf(DirectoryId.Root), f.navigations)
        assertEquals(0, f.reloads)
    }

    @Test fun `undo complete move sends all proofs in a single backend request`() = runTest {
        val f = Fixture(this)
        val first = mockk<VaultMoveUndo> { every { vaultId } returns f.vault }
        val second = mockk<VaultMoveUndo> { every { vaultId } returns f.vault }
        val proofs = listOf(first, second)
        val feedback = f.setUndo(OnlyFilesUndoAction.Moved(proofs))
        coEvery { f.files.undoMoves(proofs) } returns Result.success(VaultUndoResult())

        f.controller.undo(feedback)
        advanceUntilIdle()

        coVerify(exactly = 1) { f.files.undoMoves(proofs) }
        assertEquals(1, f.reloads)
    }

    @Test fun `repeated clicks and a stale snackbar cannot execute undo twice`() = runTest {
        val f = Fixture(this)
        coEvery { f.files.undoCreated(f.node) } returns Result.success(VaultUndoResult())

        f.controller.undo(f.feedback)
        f.controller.undo(f.feedback)
        advanceUntilIdle()
        f.controller.undo(f.feedback)
        advanceUntilIdle()

        coVerify(exactly = 1) { f.files.undoCreated(f.node) }
    }

    @Test fun `new feedback invalidates the earlier undo callback`() = runTest {
        val f = Fixture(this)
        val newer = clipboardFeedback(VaultClipboardAction.COPY, 1, 12)
        f.state.value = f.state.value.copy(operationFeedback = newer)

        f.controller.undo(f.feedback)
        advanceUntilIdle()

        coVerify(exactly = 0) { f.files.undoCreated(any()) }
        assertSame(newer, f.state.value.operationFeedback)
    }

    @Test fun `lock and reopen cannot revive the old mount undo`() = runTest {
        val f = Fixture(this)
        f.state.value = f.state.value.copy(mountGeneration = 13)

        f.controller.undo(f.feedback)
        advanceUntilIdle()

        coVerify(exactly = 0) { f.files.undoCreated(any()) }
        assertEquals(0, f.reloads)
    }

    @Test fun `vault switch and busy state reject the action before touching storage`() = runTest {
        for (changeVault in listOf(true, false)) {
            val f = Fixture(this)
            f.state.value = if (changeVault) f.state.value.copy(selectedVaultId = VaultId.of("other"))
                else f.state.value.copy(busy = true)
            f.controller.undo(f.feedback)
            advanceUntilIdle()
            coVerify(exactly = 0) { f.files.undoCreated(any()) }
        }
    }

    @Test fun `late completion after locking cannot repopulate private feedback or viewers`() = runTest {
        val f = Fixture(this)
        val finish = CompletableDeferred<Unit>()
        coEvery { f.files.undoCreated(f.node) } coAnswers {
            finish.await()
            Result.success(VaultUndoResult(removedNodeIds = setOf(f.node.ref.nodeId)))
        }
        f.controller.undo(f.feedback)
        runCurrent()
        f.state.value = OnlyFilesUiState(mountGeneration = 13)
        finish.complete(Unit)
        advanceUntilIdle()

        assertNull(f.state.value.operationFeedback)
        assertNull(f.state.value.viewer)
        assertEquals(0, f.reloads)
        assertTrue(f.removedFolders.isEmpty())
    }

    @Test fun `changed item refusal reports a localized error without restoring the unsafe callback`() = runTest {
        val f = Fixture(this)
        coEvery { f.files.undoCreated(f.node) } returns Result.failure(VaultFailure.ConcurrentMutation())

        f.controller.undo(f.feedback)
        advanceUntilIdle()

        val feedback = requireNotNull(f.state.value.operationFeedback)
        assertEquals(UiText.StringResource(R.string.onlyfiles_operation_changed), feedback.message)
        assertEquals(ArcileFeedbackSeverity.Error, feedback.severity)
        assertNull(feedback.undo)
        assertNull(feedback.destination)
        assertEquals(0, f.reloads)
    }

    @Test fun `shared snackbar routes its Undo action without navigating to a destination`() {
        val vault = VaultId.of("vault")
        val node = VaultNodeMetadata(VaultNodeRef(vault, NodeId.of("file"), DirectoryId.Root, VaultNodeCapabilities()),
            "Created.txt", VaultNodeKind.FILE, 0, 0, 1)
        val feedback = operationSuccess(UiR.string.file_operation_file_created, 12).copy(
            destination = OnlyFilesOperationDestination.VaultDirectory(vault, DirectoryId.Root),
            undo = OnlyFilesUndoAction.Created(node))
        var undone = false
        var revealed = false
        var dismissed = false
        val event = feedback.event({ revealed = true }, { dismissed = true }, { undone = true })
        assertEquals(UiText.StringResource(UiR.string.undo), event.actionLabel)
        event.onAction?.invoke()
        assertTrue(undone)
        assertFalse(revealed)
        assertFalse(dismissed)
    }

    @Test fun `copy undo removes the proven copies in one request without confirmation`() = runTest {
        val f = Fixture(this)
        val proofs = listOf(f.created())
        val feedback = f.setUndo(OnlyFilesUndoAction.Copied(proofs))
        coEvery { f.files.undoCreatedCopies(proofs) } returns Result.success(VaultUndoResult(removedNodeIds = setOf(f.node.ref.nodeId)))

        f.controller.undo(feedback)
        advanceUntilIdle()

        coVerify(exactly = 1) { f.files.undoCreatedCopies(proofs) }
        assertNull(f.state.value.pendingUndoConfirmation)
        assertEquals(UiText.StringResource(R.string.onlyfiles_undo_complete), f.state.value.operationFeedback?.message)
    }

    @Test fun `plaintext undo waits for confirmation before touching exported files`() = runTest {
        val f = Fixture(this)
        val proofs = listOf(f.plaintext())
        val feedback = f.setUndo(OnlyFilesUndoAction.Plaintext(proofs, OnlyFilesOperationKind.EXPORT, f.vault))
        coEvery { f.boundary.undoPlaintext(proofs, any()) } returns VaultBatchResult(listOf(
            VaultItemResult("Exported.txt", "Exported.txt", VaultItemOutcome.COMPLETED)))

        f.controller.undo(feedback)
        advanceUntilIdle()
        coVerify(exactly = 0) { f.boundary.undoPlaintext(any(), any()) }
        assertEquals(feedback, f.state.value.pendingUndoConfirmation)
        assertNull(f.state.value.operationFeedback)

        f.controller.undo(feedback, confirmed = true)
        advanceUntilIdle()
        coVerify(exactly = 1) { f.boundary.undoPlaintext(proofs, any()) }
        assertNull(f.state.value.pendingUndoConfirmation)
        assertEquals(UiText.StringResource(R.string.onlyfiles_undo_complete), f.state.value.operationFeedback?.message)
    }

    @Test fun `cancelling a boundary undo confirmation discards it without running`() = runTest {
        val f = Fixture(this)
        val feedback = f.setUndo(OnlyFilesUndoAction.Plaintext(listOf(f.plaintext()), OnlyFilesOperationKind.EXPORT, f.vault))
        f.controller.undo(feedback)
        f.controller.cancelConfirmation(feedback.id)
        f.controller.undo(feedback, confirmed = true)
        advanceUntilIdle()
        assertNull(f.state.value.pendingUndoConfirmation)
        coVerify(exactly = 0) { f.boundary.undoPlaintext(any(), any()) }
    }

    @Test fun `a confirmation from an earlier mount cannot run after the vault reopens`() = runTest {
        val f = Fixture(this)
        val feedback = f.setUndo(OnlyFilesUndoAction.Plaintext(listOf(f.plaintext()), OnlyFilesOperationKind.TRASH, f.vault))
        f.controller.undo(feedback)
        f.state.update { it.copy(mountGeneration = 13) }
        f.controller.undo(feedback, confirmed = true)
        advanceUntilIdle()
        coVerify(exactly = 0) { f.boundary.undoPlaintext(any(), any()) }
    }

    @Test fun `move in undo verifies the vault copy then exports it back with move semantics`() = runTest {
        val f = Fixture(this)
        val proofs = listOf(f.created(origin = VaultUndoOrigin.LocalFolder("/storage/emulated/0/Download", f.node.name)))
        val feedback = f.setUndo(OnlyFilesUndoAction.MovedIn(proofs))
        val reservation = mockk<VaultBoundaryTransferReservation>(relaxed = true)
        coEvery { f.files.verifyCreated(proofs) } returns Result.success(listOf(f.node))
        every { f.boundary.prepareExport(listOf(f.node.ref)) } returns Result.success(reservation)
        coEvery { f.boundary.exportToDestination(reservation, "/storage/emulated/0/Download", true, any(), any()) } returns
            VaultBatchResult(listOf(VaultItemResult("file", f.node.name, VaultItemOutcome.COMPLETED)))

        f.controller.undo(feedback)
        f.controller.undo(feedback, confirmed = true)
        advanceUntilIdle()

        coVerifyOrder {
            f.files.verifyCreated(proofs)
            f.boundary.exportToDestination(reservation, "/storage/emulated/0/Download", true, any(), any())
        }
        assertEquals(setOf(f.node.ref.nodeId), f.state.value.operationFeedback?.undoResult?.removedNodeIds)
    }

    @Test fun `cross-vault move undo returns items and restores a keep-both name`() = runTest {
        val f = Fixture(this)
        val source = VaultId.of("source")
        val parent = DirectoryId.of("parent")
        val renamed = f.node.copy(name = "Created (1).txt")
        val proofs = listOf(f.created(renamed, VaultUndoOrigin.VaultFolder(source, parent, f.node.name)))
        val feedback = f.setUndo(OnlyFilesUndoAction.MovedAcrossVaults(proofs))
        val returned = renamed.copy(ref = VaultNodeRef(source, NodeId.of("returned"), parent, VaultNodeCapabilities()))
        coEvery { f.files.verifyCreated(proofs) } returns Result.success(listOf(renamed))
        coEvery { f.transfers.transferAcrossVaults(listOf(renamed.ref), source, parent, true, any(), any()) } returns
            VaultBatchResult(listOf(VaultItemResult("file", renamed.name, VaultItemOutcome.COMPLETED,
                createdUndo = f.created(returned))))
        coEvery { f.files.rename(returned.ref, f.node.name) } returns Result.success(returned.copy(name = f.node.name))

        f.controller.undo(feedback)
        advanceUntilIdle()

        coVerify(exactly = 1) { f.files.rename(returned.ref, f.node.name) }
        assertEquals(UiText.StringResource(R.string.onlyfiles_undo_complete), f.state.value.operationFeedback?.message)
    }

    @Test fun `a skipped return reports a name conflict instead of success`() = runTest {
        val f = Fixture(this)
        val proofs = listOf(f.created(origin = VaultUndoOrigin.VaultFolder(VaultId.of("source"), DirectoryId.Root, f.node.name)))
        val feedback = f.setUndo(OnlyFilesUndoAction.MovedAcrossVaults(proofs))
        coEvery { f.files.verifyCreated(proofs) } returns Result.success(listOf(f.node))
        coEvery { f.transfers.transferAcrossVaults(any(), any(), any(), true, any(), any()) } returns
            VaultBatchResult(listOf(VaultItemResult("file", f.node.name, VaultItemOutcome.SKIPPED)))

        f.controller.undo(feedback)
        advanceUntilIdle()

        assertEquals(ArcileFeedbackSeverity.Error, f.state.value.operationFeedback?.severity)
        assertEquals(UiText.StringResource(R.string.onlyfiles_operation_conflict), f.state.value.operationFeedback?.message)
    }

    @Test fun `batch helpers only offer undo when every item has a matching proof`() {
        val f = Fixture(TestScope())
        val complete = VaultBatchResult(listOf(VaultItemResult("a", "A", VaultItemOutcome.COMPLETED, createdUndo = f.created())))
        assertTrue(createdUndoAction(complete, OnlyFilesOperationKind.COPY) is OnlyFilesUndoAction.Copied)
        assertNull(createdUndoAction(complete, OnlyFilesOperationKind.MOVE_IN))
        val mixed = VaultBatchResult(complete.items + VaultItemResult("b", "B", VaultItemOutcome.COMPLETED))
        assertNull(createdUndoAction(mixed, OnlyFilesOperationKind.COPY))
        val skipped = VaultBatchResult(complete.items + VaultItemResult("b", "B", VaultItemOutcome.SKIPPED))
        assertNull(createdUndoAction(skipped, OnlyFilesOperationKind.COPY))
        val exported = VaultBatchResult(listOf(VaultItemResult("a", "A", VaultItemOutcome.COMPLETED, plaintextUndo = f.plaintext())))
        assertTrue(plaintextUndoAction(exported, OnlyFilesOperationKind.EXPORT, f.vault)!!.requiresConfirmation)
        val foreign = VaultBatchResult(listOf(VaultItemResult("a", "A", VaultItemOutcome.COMPLETED,
            plaintextUndo = f.plaintext(VaultUndoOrigin.VaultFolder(VaultId.of("other"), DirectoryId.Root, "A")))))
        assertNull(plaintextUndoAction(foreign, OnlyFilesOperationKind.MOVE_OUT, f.vault))
    }

    private class Fixture(scope: TestScope) {
        val vault = VaultId.of("vault")
        val node = VaultNodeMetadata(VaultNodeRef(vault, NodeId.of("file"), DirectoryId.Root, VaultNodeCapabilities()),
            "Created.txt", VaultNodeKind.FILE, 0, 0, 1)
        val feedback = operationSuccess(UiR.string.file_operation_file_created, 12).copy(undo = OnlyFilesUndoAction.Created(node))
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = vault, mountGeneration = 12, operationFeedback = feedback))
        val files = mockk<VaultFileSystem>()
        var reloads = 0
        val navigations = mutableListOf<DirectoryId>()
        val removedFolders = mutableListOf<Pair<VaultId, Set<DirectoryId>>>()
        val transfers = mockk<VaultTransferCoordinator>()
        val boundary = mockk<VaultBoundaryTransferCoordinator>()
        val controller = OnlyFilesUndoController(files, state, { block -> scope.launch {
            state.update { it.copy(busy = true) }
            try { block() } finally { state.update { it.copy(busy = false) } }
        } }, { reloads++ }, { vault, ids -> removedFolders += vault to ids }, { navigations += it }, transfers, boundary)
        fun created(node: VaultNodeMetadata = this.node, origin: VaultUndoOrigin? = null) = object : VaultCreatedUndo {
            override val expected = node
            override val origin = origin
        }
        fun plaintext(origin: VaultUndoOrigin.VaultFolder? = null) = object : VaultPlaintextUndo {
            override val displayName = "Exported.txt"
            override val origin = origin
            override val trashed = false
        }
        fun setUndo(action: OnlyFilesUndoAction): OnlyFilesOperationFeedback = feedback.copy(undo = action).also { next ->
            state.value = state.value.copy(operationFeedback = next)
        }
    }
}
