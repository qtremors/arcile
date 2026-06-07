package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.testutil.FakeBrowserPreferencesStore
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelFolderStatsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `directory load hydrates cached folder stats and queues uncached folders`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val cachedStats = FolderStats(fileCount = 4, totalBytes = 4096L, cachedAt = System.currentTimeMillis())
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(
                    browserFile("Docs", "/storage/emulated/0/Download/Docs", isDirectory = true),
                    browserFile("Music", "/storage/emulated/0/Download/Music", isDirectory = true)
                )
            ),
            cachedFolderStats = mapOf("/storage/emulated/0/Download/Docs" to cachedStats)
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        assertEquals(cachedStats, viewModel.state.value.folderStatsByPath["/storage/emulated/0/Download/Docs"])
        assertTrue(viewModel.state.value.folderStatsLoadingPaths.contains("/storage/emulated/0/Download/Music"))
        assertEquals(listOf("/storage/emulated/0/Download/Music"), repo.lastQueuedFolderStats)
    }

    @Test
    fun `folder stat updates merge during normal browsing without presentation change`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(
                    browserFile("Docs", "/storage/emulated/0/Download/Docs", isDirectory = true)
                )
            )
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        val updatedStats = FolderStats(fileCount = 7, totalBytes = 7000L, cachedAt = System.currentTimeMillis())
        repo.emitFolderStatUpdate(FolderStatUpdate("/storage/emulated/0/Download/Docs", updatedStats))
        advanceUntilIdle()

        assertEquals(updatedStats, viewModel.state.value.folderStatsByPath["/storage/emulated/0/Download/Docs"])
        assertEquals(updatedStats, viewModel.state.value.displayState.visibleListRows.single().folderStats)
        assertEquals(updatedStats, viewModel.state.value.displayState.visibleGridRows.single().folderStats)
        assertFalse(viewModel.state.value.folderStatsLoadingPaths.contains("/storage/emulated/0/Download/Docs"))
    }

    @Test
    fun `folder stat updates from previous directory are ignored after navigation`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(
                    browserFile("Docs", "/storage/emulated/0/Download/Docs", isDirectory = true)
                ),
                "/storage/emulated/0/Pictures" to listOf(
                    browserFile("Camera", "/storage/emulated/0/Pictures/Camera", isDirectory = true)
                )
            )
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Pictures")
        advanceUntilIdle()

        repo.emitFolderStatUpdate(
            FolderStatUpdate(
                "/storage/emulated/0/Download/Docs",
                FolderStats(fileCount = 2, totalBytes = 2048L, cachedAt = System.currentTimeMillis())
            )
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.folderStatsByPath.containsKey("/storage/emulated/0/Download/Docs"))
        assertEquals("/storage/emulated/0/Pictures", viewModel.state.value.currentPath)
    }

    @Test
    fun `openPropertiesForSelection loads repository-backed properties`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf("/storage/emulated/0/Download" to emptyList()),
            selectionPropertiesResult = Result.success(
                SelectionProperties(
                    displayName = "Docs",
                    pathSummary = "/storage/emulated/0/Download/Docs",
                    itemCount = 1,
                    fileCount = 0,
                    folderCount = 1,
                    totalBytes = 2048L,
                    newestModifiedAt = 20L,
                    oldestModifiedAt = 20L,
                    mimeTypeSummary = null,
                    extensionSummary = null,
                    hiddenCount = 0,
                    accessStatus = PropertiesAccessStatus.Partial,
                    folderStats = FolderStats(3L, 2048L, System.currentTimeMillis()),
                    isSingleItem = true,
                    isDirectory = true
                )
            )
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to false, "currentPath" to "/storage/emulated/0/Download", "currentVolumeId" to "primary"))
        )

        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Download/Docs")
        viewModel.openPropertiesForSelection()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isPropertiesVisible)
        assertFalse(viewModel.state.value.isPropertiesLoading)
        assertEquals("Docs", viewModel.state.value.properties?.title)
        assertEquals(PropertiesAccessStatus.Partial, viewModel.state.value.properties?.accessStatus)
    }
}
