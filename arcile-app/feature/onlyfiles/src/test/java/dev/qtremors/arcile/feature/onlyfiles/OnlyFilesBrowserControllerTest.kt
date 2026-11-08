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
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesBrowserControllerTest {
    private val vault = VaultId.of("vault")
    private val root = DirectoryId.Root

    @Test
    fun `loading another page grows the active image queue and preserves the selected image`() = runTest {
        val first = node("first.png")
        val text = node("middle.txt")
        val last = node("last.png")
        val state = listing(first)
        state.value = state.value.copy(
            viewer = first, viewerQueue = listOf(first), viewerSelectionEnabled = true,
            selectedNodeIds = setOf(first.ref.nodeId.value)
        )
        val fs = mockk<VaultFileSystem> {
            coEvery { listDirectory(vault, root, match { it.pageToken == null }) } returns
                Result.success(VaultPage(listOf(first, text), "2", 7L))
            coEvery { listDirectory(vault, root, match { it.pageToken == "2" }) } returns
                Result.success(VaultPage(listOf(last), null, 7L))
        }
        val controller = OnlyFilesBrowserController(fs, state, this, { throw it }, { error("Unexpected mutation") })

        controller.reload()
        advanceUntilIdle()
        controller.loadNextPage()
        advanceUntilIdle()

        assertEquals(first, state.value.viewer)
        assertEquals(listOf(first, last), state.value.viewerQueue)
        assertEquals(listOf(first), state.value.selectedNodes)
        assertTrue(state.value.viewerSelectionEnabled)
        assertFalse(state.value.hasMoreRows)
    }

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

    @Test
    fun `select all fetches one complete folder snapshot and includes newly loaded rows`() = runTest {
        val visible = node("first.txt")
        val later = node("later.txt")
        val state = listing(visible)
        val fs = mockk<VaultFileSystem> {
            coEvery { selectDirectoryRows(vault, root, match { it.pageToken == null }) } returns
                Result.success(VaultPage(listOf(visible, later), null, 2L))
        }
        val controller = OnlyFilesBrowserController(fs, state, this, { throw it }, { error("Unexpected mutation") })

        controller.selectRows(invert = false)
        assertTrue(state.value.isSelectingRows)
        advanceUntilIdle()

        assertEquals(listOf(visible, later), state.value.selectedNodes)
        assertFalse(state.value.hasMoreRows)
        assertFalse(state.value.isSelectingRows)
        coVerify(exactly = 0) { fs.listDirectory(any(), any(), any()) }
    }

    @Test
    fun `invert uses the active search scope and includes unselected results on later pages`() = runTest {
        val folder = DirectoryId.of("folder")
        val visible = node("first.txt", folder)
        val later = node("later.txt", folder)
        val visibleHit = VaultSearchHit(visible, emptyList())
        val laterHit = VaultSearchHit(later, emptyList())
        val state = listing(visible)
        state.value = state.value.copy(
            searchQuery = " .txt ",
            searchScope = state.value.directoryStack + VaultDirectoryCrumb(folder, "Folder", VaultPath.of("Folder")),
            searchHits = listOf(visibleHit),
            searchNextPageToken = "1"
        )
        val fs = mockk<VaultFileSystem> {
            coEvery { selectSearchRows(vault, folder, match { it.text == ".txt" && it.pageToken == null }) } returns
                Result.success(VaultPage(listOf(visibleHit, laterHit), null, 2L))
        }
        val controller = OnlyFilesBrowserController(fs, state, this, { throw it }, { error("Unexpected mutation") })

        controller.selectRows(invert = true)
        advanceUntilIdle()

        assertEquals(listOf(visibleHit, laterHit), state.value.searchHits)
        assertEquals(listOf(later), state.value.selectedNodes)
        assertFalse(state.value.hasMoreRows)
        assertFalse(state.value.isSelectingRows)
        coVerify(exactly = 0) { fs.search(any(), any(), any()) }
    }

    @Test
    fun `a failed complete read leaves the original selection and page token intact`() = runTest {
        val visible = node("first.txt")
        val state = listing(visible)
        val failure = IllegalStateException("Selection too large")
        val errors = mutableListOf<Throwable>()
        val fs = mockk<VaultFileSystem> {
            coEvery { selectDirectoryRows(vault, root, any()) } returns Result.failure(failure)
        }
        val controller = OnlyFilesBrowserController(fs, state, this, { errors += it }, { error("Unexpected mutation") })

        controller.selectRows(invert = true)
        advanceUntilIdle()

        assertEquals(listOf(failure), errors)
        assertEquals(listOf(visible), state.value.selectedNodes)
        assertEquals("1", state.value.nextPageToken)
        assertFalse(state.value.isSelectingRows)
    }

    @Test
    fun `cancelling complete selection keeps loaded rows and resumes paging`() = runTest {
        val visible = node("first.txt")
        val later = node("later.txt")
        val state = listing(visible)
        val pending = CompletableDeferred<Result<VaultPage<VaultNodeMetadata>>>()
        val fs = mockk<VaultFileSystem> {
            coEvery { selectDirectoryRows(vault, root, any()) } coAnswers { pending.await() }
        }
        val controller = OnlyFilesBrowserController(fs, state, this, { throw it }, { error("Unexpected mutation") })

        controller.selectRows(invert = true)
        runCurrent()
        controller.loadNextPage()
        controller.cancelSelection()
        pending.complete(Result.success(VaultPage(listOf(visible, later), null, 2L)))
        advanceUntilIdle()

        assertEquals(listOf(visible), state.value.nodes)
        assertEquals(listOf(visible), state.value.selectedNodes)
        assertEquals("1", state.value.nextPageToken)
        assertFalse(state.value.isSelectingRows)
        coVerify(exactly = 0) { fs.listDirectory(any(), any(), any()) }
    }

    @Test
    fun `locking the mount clears the pending selection and discards its private results`() = runTest {
        val visible = node("private-first.txt")
        val later = node("private-later.txt")
        val state = listing(visible)
        val pending = CompletableDeferred<Result<VaultPage<VaultNodeMetadata>>>()
        val fs = mockk<VaultFileSystem> {
            coEvery { selectDirectoryRows(vault, root, any()) } coAnswers { pending.await() }
        }
        val errors = mutableListOf<Throwable>()
        val controller = OnlyFilesBrowserController(fs, state, this, { errors += it }, { error("Unexpected mutation") })

        controller.selectRows(invert = false)
        runCurrent()
        controller.cancel()
        state.value = OnlyFilesUiState(mountGeneration = 1L)
        pending.complete(Result.success(VaultPage(listOf(visible, later), null, 2L)))
        advanceUntilIdle()

        assertEquals(null, state.value.selectedVaultId)
        assertTrue(state.value.displayedNodes.isEmpty())
        assertTrue(state.value.selectedNodes.isEmpty())
        assertTrue(errors.isEmpty())
        assertFalse(state.value.isSelectingRows)
    }

    private fun listing(visible: VaultNodeMetadata) = MutableStateFlow(
        OnlyFilesUiState(
            selectedVaultId = vault,
            directoryStack = listOf(VaultDirectoryCrumb(root, "Vault", VaultPath.Root)),
            nodes = listOf(visible),
            nextPageToken = "1",
            selectedNodeIds = setOf(visible.ref.nodeId.value)
        )
    )

    private fun node(name: String, parent: DirectoryId = root) = VaultNodeMetadata(
        VaultNodeRef(vault, NodeId.of(name), parent, VaultNodeCapabilities()),
        name, VaultNodeKind.FILE, 0L, 0L, 1L
    )
}
