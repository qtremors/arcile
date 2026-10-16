package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.SaveDestinationDirectory
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.VaultId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesLocalPickerControllerTest {
    @Test
    fun `file selections survive the storage roots and folders remain navigable`() = runTest {
        val fixture = Fixture(this)
        val first = FileModel("first.txt", "/storage/one/first.txt")
        val second = FileModel("second.txt", "/storage/two/second.txt")
        val child = FileModel("Folder", "/storage/two/Folder", isDirectory = true)
        coEvery { fixture.browser.listFiles("/storage/one") } returns Result.success(listOf(first))
        coEvery { fixture.browser.listFiles("/storage/two") } returns Result.success(listOf(second, child))
        fixture.controller.openImportFiles()
        advanceUntilIdle()
        fixture.controller.openDirectory("/storage/one")
        advanceUntilIdle()
        fixture.controller.toggleFile(first.reference)
        fixture.controller.up()
        advanceUntilIdle()

        assertNull(fixture.picker.current)
        assertTrue(fixture.picker.canChoose)
        assertEquals(setOf(first.reference), fixture.picker.selectedPaths)

        fixture.controller.openDirectory("/storage/two")
        advanceUntilIdle()
        fixture.controller.toggleFile(child.reference)
        fixture.controller.toggleFile(second.reference)
        assertEquals(setOf(first.reference, second.reference), fixture.picker.selectedPaths)
        fixture.controller.choose()

        assertEquals(listOf(listOf(first.reference, second.reference)), fixture.imports)
        assertNull(fixture.state.value.localPicker)
    }

    @Test
    fun `a readable directory can be imported but cannot be used as an export destination`() = runTest {
        val fixture = Fixture(this)
        fixture.controller.openImportFolder()
        advanceUntilIdle()
        fixture.controller.openDirectory("/storage/two")
        advanceUntilIdle()
        assertTrue(fixture.picker.canChoose)
        fixture.controller.choose()
        assertEquals(listOf(listOf("/storage/two")), fixture.imports)

        fixture.controller.openExport(emptyList(), move = false)
        advanceUntilIdle()
        fixture.controller.openDirectory("/storage/two")
        advanceUntilIdle()
        assertFalse(fixture.picker.canChoose)
        fixture.controller.choose()
        assertTrue(fixture.exports.isEmpty())
        assertEquals("/storage/two", fixture.picker.current?.path)
        coVerify(exactly = 0) { fixture.browser.listFiles(any()) }
    }

    @Test
    fun `a folder selection cannot be confirmed while destination validation is pending`() = runTest {
        val fixture = Fixture(this)
        val pending = CompletableDeferred<Result<List<SaveDestinationDirectory>>>()
        coEvery { fixture.destinations.children("/storage/one", any()) } coAnswers { pending.await() }
        fixture.controller.openExport(emptyList(), move = true)
        advanceUntilIdle()
        fixture.controller.openDirectory("/storage/one")
        runCurrent()

        assertTrue(fixture.picker.isLoading)
        fixture.controller.choose()
        assertTrue(fixture.exports.isEmpty())
        pending.complete(Result.success(emptyList()))
        advanceUntilIdle()
        fixture.controller.choose()
        assertEquals(listOf("/storage/one" to true), fixture.exports)
    }

    private class Fixture(scope: CoroutineScope) {
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = VaultId.of("vault")))
        val picker get() = requireNotNull(state.value.localPicker)
        val imports = mutableListOf<List<String>>()
        val exports = mutableListOf<Pair<String, Boolean>>()
        val browser = mockk<FileBrowserRepository>()
        private val roots = listOf(
            SaveDestinationDirectory("/storage/one", "Storage one", canSave = true),
            SaveDestinationDirectory("/storage/two", "Storage two", canSave = false)
        )
        val destinations = mockk<SaveDestinationBrowser> {
            coEvery { resolve(any(), any()) } answers { Result.success(roots.firstOrNull { it.path == firstArg<String>() }) }
            coEvery { children(any(), any()) } returns Result.success(emptyList())
            coEvery { parent(any(), any()) } returns Result.success(null)
        }
        private val volumes = mockk<VolumeRepository> {
            coEvery { getStorageVolumes() } returns Result.success(roots.mapIndexed { index, root ->
                StorageVolume(index.toString(), index.toString(), root.name, root.path, 0L, 0L, index == 0, index != 0)
            })
        }
        val controller = OnlyFilesLocalPickerController(
            browser, destinations, volumes, state, scope,
            { throw it }, { imports += it }, { _, path, move -> exports += path to move }
        )
    }
}
