package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.presentation.UiText
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
class OnlyFilesMutationFeedbackTest {
    @Test fun `new folder uses Browser completion text and retains the actual created node for Undo`() = runTest {
        val f = Fixture(this)
        val created = f.node("Folder", directory = true)
        coEvery { f.files.createDirectory(f.vault, DirectoryId.Root, created.name) } returns Result.success(created)

        f.controller.createFolder(created.name)
        advanceUntilIdle()

        assertEquals(UiText.StringResource(UiR.string.file_operation_folder_created), f.feedback.message)
        assertEquals(OnlyFilesUndoAction.Created(created), f.feedback.undo)
        assertEquals(12L, f.feedback.mountGeneration)
        assertEquals(1, f.reloads)
    }

    @Test fun `new empty and synthetic files use the shared file-created feedback`() = runTest {
        for (synthetic in listOf(false, true)) {
            val f = Fixture(this)
            val created = f.node("New.bin").copy(sizeBytes = if (synthetic) 2048 else 0)
            if (synthetic) coEvery { f.files.createSyntheticFile(f.vault, DirectoryId.Root, created.name, 2048) } returns Result.success(created)
            else coEvery { f.files.createEmptyFile(f.vault, DirectoryId.Root, created.name, null) } returns Result.success(created)

            if (synthetic) f.controller.createSyntheticFile(created.name, 2048) else f.controller.createEmptyFile(created.name)
            advanceUntilIdle()

            assertEquals(UiText.StringResource(UiR.string.file_operation_file_created), f.feedback.message)
            assertEquals(OnlyFilesUndoAction.Created(created), f.feedback.undo)
        }
    }

    @Test fun `single rename retains the submitted old name and published metadata`() = runTest {
        val f = Fixture(this)
        val original = f.node("Original.txt")
        val renamed = original.copy(name = "Renamed.txt", modifiedAtMillis = 5)
        f.state.value = f.state.value.copy(viewer = original, viewerQueue = listOf(original))
        coEvery { f.files.renameWithPreviousName(original.ref, renamed.name) } returns Result.success(VaultRenamedNode(renamed, original.name))
        var callback: VaultNodeMetadata? = null

        f.controller.rename(original, renamed.name) { callback = it }
        advanceUntilIdle()

        assertEquals(UiText.StringResource(UiR.string.file_operation_renamed), f.feedback.message)
        assertEquals(OnlyFilesUndoAction.Renamed(listOf(VaultRenameUndoEntry(renamed, original.name))), f.feedback.undo)
        assertEquals(renamed, callback)
        assertEquals(renamed, f.state.value.viewer)
        assertEquals(listOf(renamed), f.state.value.viewerQueue)
    }

    @Test fun `batch rename binds original names by node identity rather than response order`() = runTest {
        val f = Fixture(this)
        val first = f.node("A.txt")
        val second = f.node("B.txt")
        val items = listOf(VaultRenameItem(first.ref, first.name, "New A.txt"), VaultRenameItem(second.ref, second.name, "New B.txt"))
        val updated = listOf(second.copy(name = "New B.txt"), first.copy(name = "New A.txt"))
        coEvery { f.files.batchRename(items) } returns Result.success(updated)
        f.state.value = f.state.value.copy(selectedNodeIds = setOf(first.ref.nodeId.value, second.ref.nodeId.value))

        f.controller.batchRename(items)
        advanceUntilIdle()

        val action = f.feedback.undo as OnlyFilesUndoAction.Renamed
        assertEquals(listOf(VaultRenameUndoEntry(updated[0], second.name), VaultRenameUndoEntry(updated[1], first.name)), action.entries)
        assertTrue(f.state.value.selectedNodeIds.isEmpty())
        assertEquals(1, f.reloads)
    }

    @Test fun `unchanged names do not manufacture an Undo operation`() = runTest {
        val f = Fixture(this)
        val original = f.node("Same.txt")
        coEvery { f.files.renameWithPreviousName(original.ref, original.name) } returns Result.success(VaultRenamedNode(original, original.name))
        coEvery { f.files.batchRename(any()) } returns Result.success(listOf(original))
        f.controller.rename(original, original.name)
        advanceUntilIdle()
        assertNull(f.feedback.undo)
        f.controller.batchRename(listOf(VaultRenameItem(original.ref, original.name)))
        advanceUntilIdle()
        assertNull(f.feedback.undo)
    }

    @Test fun `failed creation cannot leave a successful creation Undo in feedback`() = runTest {
        val f = Fixture(this)
        coEvery { f.files.createDirectory(f.vault, DirectoryId.Root, "Folder") } returns Result.failure(VaultFailure.NameConflict("Folder"))

        f.controller.createFolder("Folder")
        advanceUntilIdle()

        assertEquals(1, f.errors.size)
        assertNull(f.feedback.undo)
        assertEquals(0, f.reloads)
        assertEquals(UiText.StringResource(R.string.onlyfiles_operation_conflict), f.feedback.message)
    }

    @Test fun `creation completing after lock never brings back private feedback`() = runTest {
        val f = Fixture(this)
        val complete = CompletableDeferred<Unit>()
        val node = f.node("Created.txt")
        coEvery { f.files.createEmptyFile(f.vault, DirectoryId.Root, node.name, null) } coAnswers {
            complete.await()
            Result.success(node)
        }
        f.controller.createEmptyFile(node.name)
        runCurrent()
        f.state.value = OnlyFilesUiState(mountGeneration = 13)
        complete.complete(Unit)
        advanceUntilIdle()

        assertNull(f.state.value.operationFeedback)
        assertEquals(0, f.reloads)
    }

    @Test fun `rename Undo uses the backend previous name when the displayed row was stale`() = runTest {
        val f = Fixture(this)
        val displayed = f.node("Stale.txt")
        val updated = displayed.copy(name = "Final.txt", revision = 3)
        coEvery { f.files.renameWithPreviousName(displayed.ref, updated.name) } returns
            Result.success(VaultRenamedNode(updated, "Actual previous.txt"))

        f.controller.rename(displayed, updated.name)
        advanceUntilIdle()

        assertEquals(OnlyFilesUndoAction.Renamed(listOf(VaultRenameUndoEntry(updated, "Actual previous.txt"))), f.feedback.undo)
    }

    @Test fun `rename provider without an atomic previous-name result preserves completion but omits Undo`() = runTest {
        val f = Fixture(this)
        val displayed = f.node("Original.txt")
        val updated = displayed.copy(name = "Final.txt")
        coEvery { f.files.renameWithPreviousName(displayed.ref, updated.name) } returns
            Result.success(VaultRenamedNode(updated, null))

        f.controller.rename(displayed, updated.name)
        advanceUntilIdle()

        assertEquals(UiText.StringResource(UiR.string.file_operation_renamed), f.feedback.message)
        assertNull(f.feedback.undo)
        assertEquals(1, f.reloads)
    }

    @Test fun `default provider rename result cannot invent previous-name proof`() = runTest {
        val f = Fixture(this)
        val original = f.node("Original.txt")
        val updated = original.copy(name = "Final.txt")
        coEvery { f.files.rename(original.ref, updated.name) } returns Result.success(updated)
        val provider = object : VaultFileSystem by f.files {
            override suspend fun renameWithPreviousName(ref: VaultNodeRef, newName: String) =
                super<VaultFileSystem>.renameWithPreviousName(ref, newName)
        }

        val result = provider.renameWithPreviousName(original.ref, updated.name).getOrThrow()

        assertEquals(updated, result.updated)
        assertNull(result.previousName)
    }

    private class Fixture(scope: TestScope) {
        val vault = VaultId.of("vault")
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = vault, mountGeneration = 12,
            directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "Vault", VaultPath.Root))))
        var reloads = 0
        val files = mockk<VaultFileSystem> {
            coEvery { listDirectory(any(), any(), any()) } coAnswers {
                reloads++
                Result.success(VaultPage(emptyList(), null, 1))
            }
        }
        val errors = mutableListOf<Throwable>()
        val controller = OnlyFilesBrowserController(files, state, scope, { error ->
            errors += error
            state.update { it.copy(operationFeedback = vaultOperationFailure(error, R.string.onlyfiles_error_operation, it.mountGeneration)) }
        }, { block -> scope.launch { block() } })
        val feedback get() = requireNotNull(state.value.operationFeedback)
        fun node(name: String, directory: Boolean = false) = VaultNodeMetadata(
            VaultNodeRef(vault, NodeId.of(name), DirectoryId.Root, VaultNodeCapabilities(), if (directory) DirectoryId.of("folder-$name") else null),
            name, if (directory) VaultNodeKind.DIRECTORY else VaultNodeKind.FILE, 0, 0, 1)
    }
}
