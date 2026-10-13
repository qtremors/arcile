package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultDirectoryLocation
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPage
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesLocationControllerTest {
    private val vault = VaultId.of("vault")
    private val root = VaultDirectoryCrumb(DirectoryId.Root, "", VaultPath.Root)
    private val photos = DirectoryId.of("photos")
    private val documents = DirectoryId.of("documents")
    private val fs = mockk<VaultFileSystem>()
    private val preferences = mockk<VaultSecurityPreferences>(relaxed = true)

    @Test
    fun `a stored directory restores using the current authenticated breadcrumb names`() = runTest {
        val state = mounted()
        var reloads = 0
        coEvery { fs.directoryTrail(vault, photos) } returns Result.success(trail(photos, "Renamed photos"))
        val controller = OnlyFilesLocationController(
            fs, preferences, state, this, { reloads++ }, { error("Unexpected search") }, { throw it }
        )

        controller.restore(photos)
        advanceUntilIdle()

        assertEquals(listOf("", "Renamed photos"), state.value.directoryStack.map { it.name })
        assertEquals(VaultPath.Root.resolve("Renamed photos"), state.value.currentDirectory?.path)
        assertEquals(1, reloads)
        coVerify(exactly = 1) { preferences.setLastDirectory(vault, photos) }
    }

    @Test
    fun `folder picker pages and changes search scope without leaving the listing`() = runTest {
        val state = mounted()
        val first = folder(photos, "Photos")
        val second = folder(documents, "Documents")
        coEvery { fs.listDirectory(vault, root.id, match { it.foldersOnly && it.pageToken == null }) } returns
            Result.success(VaultPage(listOf(first), "next", 1L))
        coEvery { fs.listDirectory(vault, root.id, match { it.foldersOnly && it.pageToken == "next" }) } returns
            Result.success(VaultPage(listOf(second), null, 1L))
        coEvery { fs.listDirectory(vault, documents, match { it.foldersOnly }) } returns
            Result.success(VaultPage(emptyList(), null, 1L))
        var searches = 0
        val controller = OnlyFilesLocationController(
            fs, preferences, state, this, { error("Unexpected navigation") }, { searches++ }, { throw it }
        )

        controller.openScopePicker()
        advanceUntilIdle()
        controller.loadMoreFolders()
        advanceUntilIdle()
        assertEquals(listOf(first, second), state.value.locationPicker?.folders)
        controller.openPickerFolder(second)
        advanceUntilIdle()
        controller.applyScope()

        assertEquals(listOf(root), state.value.directoryStack)
        assertEquals(documents, state.value.searchScope?.last()?.id)
        assertNull(state.value.locationPicker)
        assertEquals(1, searches)
        controller.clearScope()
        assertNull(state.value.searchScope)
        assertEquals(2, searches)
    }

    @Test
    fun `picker reloads rather than mixing folder pages from different generations`() = runTest {
        val state = mounted()
        val first = folder(photos, "Photos")
        val replacement = folder(documents, "Documents")
        coEvery { fs.listDirectory(vault, root.id, match { it.pageToken == null }) } returnsMany listOf(
            Result.success(VaultPage(listOf(first), "next", 1L)),
            Result.success(VaultPage(listOf(replacement), null, 2L))
        )
        coEvery { fs.listDirectory(vault, root.id, match { it.pageToken == "next" }) } returns
            Result.success(VaultPage(listOf(first), null, 2L))
        val controller = OnlyFilesLocationController(fs, preferences, state, this, {}, {}, { throw it })

        controller.openScopePicker()
        advanceUntilIdle()
        assertEquals(listOf(first), state.value.locationPicker?.folders)

        controller.loadMoreFolders()
        advanceUntilIdle()

        assertEquals(listOf(replacement), state.value.locationPicker?.folders)
        assertEquals(2L, state.value.locationPicker?.generation)
        assertNull(state.value.locationPicker?.nextPageToken)
    }

    @Test
    fun `deleted pins are removed without discarding existing shortcuts`() = runTest {
        val state = mounted()
        state.value = state.value.copy(pinnedDirectoryIds = setOf(photos, documents))
        coEvery { fs.directoryTrail(vault, photos) } returns Result.success(trail(photos, "Photos"))
        coEvery { fs.directoryTrail(vault, documents) } returns Result.failure(VaultFailure.InvalidPath("Missing"))
        val controller = OnlyFilesLocationController(fs, preferences, state, this, {}, {}, { throw it })

        controller.openPinnedFolders()
        advanceUntilIdle()

        assertEquals(listOf("Photos"), state.value.locationPicker?.shortcuts?.map { it.last().name })
        coVerify(exactly = 1) { preferences.setDirectoryPinned(vault, documents, false) }
    }

    @Test
    fun `a late reveal cannot navigate a different vault mount`() = runTest {
        val state = mounted()
        coEvery { fs.directoryTrail(vault, photos) } coAnswers {
            delay(100)
            Result.success(trail(photos, "Private photos"))
        }
        val controller = OnlyFilesLocationController(
            fs, preferences, state, this, { error("Unexpected navigation") }, {}, { throw it }
        )

        controller.navigate(photos, focusNodeId = "photo")
        runCurrent()
        state.value = OnlyFilesUiState(mountGeneration = 2)
        advanceUntilIdle()

        assertEquals(emptyList<VaultDirectoryCrumb>(), state.value.directoryStack)
        assertNull(state.value.revealNodeId)
        coVerify(exactly = 0) { preferences.setLastDirectory(any(), any()) }
    }

    private fun mounted() = MutableStateFlow(
        OnlyFilesUiState(selectedVaultId = vault, mountGeneration = 1L, directoryStack = listOf(root))
    )

    private fun trail(id: DirectoryId, name: String) =
        listOf(VaultDirectoryLocation(root.id, ""), VaultDirectoryLocation(id, name))

    private fun folder(id: DirectoryId, name: String) = VaultNodeMetadata(
        VaultNodeRef(vault, NodeId.of(id.value), root.id, VaultNodeCapabilities(), directoryId = id),
        name, VaultNodeKind.DIRECTORY, 0L, 0L, 1L
    )
}
