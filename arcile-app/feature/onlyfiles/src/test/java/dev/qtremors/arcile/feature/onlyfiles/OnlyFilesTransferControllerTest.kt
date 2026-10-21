package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesTransferControllerTest {
    @Test
    fun `partial move keeps clipboard and tells user skipped files remain at source`() = runTest {
        val vaultId = VaultId.of("vault")
        val ref = VaultNodeRef(vaultId, NodeId.of("folder"), DirectoryId.Root, VaultNodeCapabilities())
        val clipboard = VaultClipboard(VaultClipboardAction.MOVE, listOf(ref))
        val state = MutableStateFlow(OnlyFilesUiState(
            selectedVaultId = vaultId,
            directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "Vault", VaultPath.Root)),
            clipboard = clipboard
        ))
        val coordinator = mockk<VaultTransferCoordinator>()
        var outcome = VaultItemOutcome.PARTIAL
        coEvery { coordinator.moveWithinVault(any(), any(), any(), any()) } answers {
            VaultBatchResult(listOf(VaultItemResult(ref.backendIdentity, "Folder", outcome)))
        }
        var reloads = 0
        val controller = OnlyFilesTransferController(coordinator, state, this, { reloads++ })
        controller.paste()
        advanceUntilIdle()
        assertEquals(clipboard, state.value.clipboard)
        assertTrue(state.value.message.orEmpty().contains("1 partially completed"))
        assertTrue(state.value.message.orEmpty().contains("Skipped files remain at the source"))
        assertFalse(state.value.busy)
        assertEquals(1, reloads)

        outcome = VaultItemOutcome.COMPLETED
        controller.paste()
        advanceUntilIdle()
        assertNull(state.value.clipboard)
        assertEquals("1 completed, 0 skipped, 0 failed", state.value.message)
    }

    @Test
    fun `partial cross vault move clears only removed folders from the source vault`() = runTest {
        val source = VaultId.of("source")
        val destination = VaultId.of("destination")
        val retained = DirectoryId.of("retained")
        val removed = DirectoryId.of("removed")
        val ref = VaultNodeRef(source, NodeId.of("folder"), DirectoryId.Root, VaultNodeCapabilities(), retained)
        val clipboard = VaultClipboard(VaultClipboardAction.MOVE, listOf(ref))
        val state = MutableStateFlow(OnlyFilesUiState(
            selectedVaultId = destination,
            directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "Destination", VaultPath.Root)),
            clipboard = clipboard
        ))
        val coordinator = mockk<VaultTransferCoordinator>()
        coEvery { coordinator.transferAcrossVaults(listOf(ref), destination, DirectoryId.Root, true, any(), any()) } returns
            VaultBatchResult(listOf(VaultItemResult(
                ref.backendIdentity, "Folder", VaultItemOutcome.PARTIAL, removedFolders = setOf(removed)
            )))
        val cleaned = mutableListOf<Pair<VaultId, Set<DirectoryId>>>()
        val controller = OnlyFilesTransferController(coordinator, state, this, {}) { vault, folders ->
            cleaned += vault to folders
        }

        controller.paste()
        advanceUntilIdle()

        assertEquals(listOf(source to setOf(removed)), cleaned)
        assertEquals(clipboard, state.value.clipboard)
    }

    @Test
    fun `successful copy consumes its clipboard`() = runTest {
        val vaultId = VaultId.of("vault")
        val ref = VaultNodeRef(vaultId, NodeId.of("file"), DirectoryId.Root, VaultNodeCapabilities())
        val node = VaultNodeMetadata(ref, "File", VaultNodeKind.FILE, 0L, 0L, 0L)
        val clipboard = VaultClipboard(VaultClipboardAction.COPY, listOf(ref), listOf(node))
        val state = MutableStateFlow(OnlyFilesUiState(
            selectedVaultId = vaultId,
            directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "Vault", VaultPath.Root)),
            clipboard = clipboard
        ))
        val coordinator = mockk<VaultTransferCoordinator>()
        coEvery { coordinator.copyWithinVault(any(), any(), any(), any()) } returns
            VaultBatchResult(listOf(VaultItemResult(ref.backendIdentity, "File", VaultItemOutcome.COMPLETED)))
        val controller = OnlyFilesTransferController(coordinator, state, this, {})

        controller.paste()
        advanceUntilIdle()
        assertNull(state.value.clipboard)

        state.update { it.copy(clipboard = clipboard) }
        val replacement = clipboard.copy(action = VaultClipboardAction.MOVE)
        coEvery { coordinator.copyWithinVault(any(), any(), any(), any()) } answers {
            state.update { it.copy(clipboard = replacement) }
            assertEquals(listOf("File"), state.value.pasteQueueNames)
            VaultBatchResult(listOf(VaultItemResult(ref.backendIdentity, "File", VaultItemOutcome.COMPLETED)))
        }
        controller.paste()
        advanceUntilIdle()
        assertEquals(replacement, state.value.clipboard)
        assertTrue(state.value.pasteQueueNames.isEmpty())
    }

    @Test
    fun `retry contains only sources that did not complete`() = runTest {
        val vault = VaultId.of("vault")
        val copied = VaultNodeRef(vault, NodeId.of("copied"), DirectoryId.Root, VaultNodeCapabilities())
        val skipped = copied.copy(nodeId = NodeId.of("skipped"))
        val nodes = listOf(copied, skipped).map { VaultNodeMetadata(it, it.nodeId.value, VaultNodeKind.FILE, 0, 0, 0) }
        val state = MutableStateFlow(OnlyFilesUiState(
            selectedVaultId = vault,
            directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "Vault", VaultPath.Root)),
            clipboard = VaultClipboard(VaultClipboardAction.COPY, listOf(copied, skipped), nodes)
        ))
        val coordinator = mockk<VaultTransferCoordinator>()
        coEvery { coordinator.copyWithinVault(any(), any(), any(), any()) } returns VaultBatchResult(listOf(
            VaultItemResult(copied.backendIdentity, "copied", VaultItemOutcome.COMPLETED),
            VaultItemResult(skipped.backendIdentity, "skipped", VaultItemOutcome.SKIPPED)
        ))

        OnlyFilesTransferController(coordinator, state, this, {}).paste()
        advanceUntilIdle()

        assertEquals(listOf(skipped), state.value.clipboard?.sources)
        assertEquals(listOf(nodes.last()), state.value.clipboard?.displayNodes)
    }

    @Test
    fun `paste resumes a partial merge only at its recorded destination`() = runTest {
        val vault = VaultId.of("vault")
        val destination = DirectoryId.of("destination")
        val other = DirectoryId.of("other")
        val ref = VaultNodeRef(
            vault, NodeId.of("folder"), DirectoryId.Root, VaultNodeCapabilities(), DirectoryId.of("source")
        )
        val clipboard = VaultClipboard(VaultClipboardAction.COPY, listOf(ref))
        val retry = object : VaultMergeRetry {
            override val source = ref
            override val destinationVault = vault
            override val destinationDirectory = destination
            override val move = false
            override val remainingCount = 1
        }
        val state = MutableStateFlow(OnlyFilesUiState(
            selectedVaultId = vault,
            directoryStack = listOf(VaultDirectoryCrumb(destination, "Destination", VaultPath.of("/Destination"))),
            clipboard = clipboard
        ))
        val coordinator = mockk<VaultTransferCoordinator>()
        coEvery { coordinator.copyWithinVault(listOf(ref), destination, any(), any()) } returns
            VaultBatchResult(listOf(VaultItemResult(ref.backendIdentity, "Folder", VaultItemOutcome.PARTIAL, mergeRetry = retry)))
        coEvery { coordinator.copyWithinVault(listOf(ref), other, any(), any()) } returns
            VaultBatchResult(listOf(VaultItemResult(ref.backendIdentity, "Folder", VaultItemOutcome.SKIPPED)))
        coEvery { coordinator.resumeMerge(retry, any(), any()) } returns
            VaultItemResult(ref.backendIdentity, "Folder", VaultItemOutcome.COMPLETED)
        val controller = OnlyFilesTransferController(coordinator, state, this, {})

        controller.paste()
        advanceUntilIdle()
        assertEquals(listOf(retry), state.value.clipboard?.retriesAt(vault, destination))

        val firstStack = state.value.directoryStack
        state.update { it.copy(directoryStack = listOf(VaultDirectoryCrumb(other, "Other", VaultPath.of("/Other")))) }
        controller.paste()
        advanceUntilIdle()
        assertEquals(listOf(retry), state.value.clipboard?.retriesAt(vault, destination))
        coVerify(exactly = 0) { coordinator.resumeMerge(any(), any(), any()) }

        state.update { it.copy(directoryStack = firstStack) }
        controller.paste()
        advanceUntilIdle()
        assertNull(state.value.clipboard)
        coVerify(exactly = 1) { coordinator.copyWithinVault(listOf(ref), destination, any(), any()) }
        coVerify(exactly = 1) { coordinator.resumeMerge(retry, any(), any()) }
    }
}
