package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.testutil.FakeBrowserPreferencesStore
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelClipboardTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `copy selection updates clipboard and clears selected files`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByPath = mapOf(
                    "/storage/emulated/0" to listOf(
                        browserFile("alpha.txt", "/storage/emulated/0/alpha.txt"),
                        browserFile("beta.txt", "/storage/emulated/0/beta.txt")
                    )
                )
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/alpha.txt")
        viewModel.toggleSelection("/storage/emulated/0/beta.txt")
        viewModel.copySelectedToClipboard()
        advanceUntilIdle()

        assertEquals(ClipboardOperation.COPY, viewModel.state.value.clipboardState?.operation)
        assertEquals(listOf("/storage/emulated/0/alpha.txt", "/storage/emulated/0/beta.txt"), viewModel.state.value.clipboardState?.files?.map { it.absolutePath })
        assertTrue(viewModel.state.value.selectedFiles.isEmpty())
    }

    @Test
    fun `pasteFromClipboard shows conflict dialog when destination contains duplicates`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val conflict = FileConflict(
            sourcePath = "/storage/emulated/0/source.txt",
            sourceFile = browserFile("source.txt", "/storage/emulated/0/source.txt"),
            existingFile = browserFile("source.txt", "/storage/emulated/0/Download/source.txt")
        )
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0" to listOf(browserFile("source.txt", "/storage/emulated/0/source.txt")),
                "/storage/emulated/0/Download" to emptyList()
            ),
            conflictsResult = Result.success(listOf(conflict))
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/source.txt")
        viewModel.cutSelectedToClipboard()

        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.pasteFromClipboard()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showConflictDialog)
        assertEquals(listOf(conflict), viewModel.state.value.pasteConflicts)
        assertEquals(listOf("/storage/emulated/0/source.txt"), repo.lastConflictSourcePaths)
        assertEquals("/storage/emulated/0/Download", repo.lastConflictDestination)
    }
}
