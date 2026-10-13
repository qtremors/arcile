package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPage
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSearchHit
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesBrowserControllerTest {
    @Test
    fun `load more restarts a search when its result generation changes`() = runTest {
        val vault = VaultId.of("vault")
        val directory = DirectoryId.Root
        val state = MutableStateFlow(OnlyFilesUiState(
            selectedVaultId = vault,
            directoryStack = listOf(VaultDirectoryCrumb(directory, "Vault", VaultPath.Root))
        ))
        fun hit(name: String) = VaultSearchHit(
            VaultNodeMetadata(
                VaultNodeRef(vault, NodeId.of(name), directory, VaultNodeCapabilities()),
                name, VaultNodeKind.FILE, 0L, 0L, 0L
            ),
            emptyList()
        )
        val oldHit = hit("old.txt")
        val refreshedHit = hit("new.txt")
        val fileSystem = mockk<VaultFileSystem> {
            coEvery { search(vault, directory, match { it.pageToken == null }) } returnsMany listOf(
                Result.success(VaultPage(listOf(oldHit), "1", 1L)),
                Result.success(VaultPage(listOf(refreshedHit), "1", 2L))
            )
            coEvery { search(vault, directory, match { it.pageToken == "1" }) } returns
                Result.success(VaultPage(listOf(oldHit), null, 2L))
        }
        val controller = OnlyFilesBrowserController(fileSystem, state, this, { throw it }, { error("Unexpected mutation") })

        controller.updateSearch(".txt")
        advanceUntilIdle()
        assertEquals(listOf(oldHit), state.value.searchHits)

        controller.loadNextPage()
        advanceUntilIdle()
        assertEquals(listOf(refreshedHit), state.value.searchHits)
        assertEquals("1", state.value.searchNextPageToken)
    }
}
