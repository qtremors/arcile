package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
}
