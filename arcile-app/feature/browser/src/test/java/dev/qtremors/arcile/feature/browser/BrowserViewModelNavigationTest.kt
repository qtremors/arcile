package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.testutil.FakeBrowserPreferencesStore
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelNavigationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `navigation state saves committed destination`() = runTest(mainDispatcherRule.dispatcher) {
        val savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByPath = mapOf("/storage/emulated/0/Download" to emptyList()),
                filesByCategory = mapOf("Images" to emptyList())
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Download", savedStateHandle.get<String>("currentPath"))
        assertEquals("primary", savedStateHandle.get<String>("currentVolumeId"))
        assertEquals(false, savedStateHandle.get<Boolean>("isCategoryScreen"))

        viewModel.navigateToCategory("Images", "primary")
        advanceUntilIdle()

        assertEquals(true, savedStateHandle.get<Boolean>("isCategoryScreen"))
        assertEquals("Images", savedStateHandle.get<String>("activeCategoryName"))
        assertEquals("primary", savedStateHandle.get<String>("currentVolumeId"))
    }

    @Test
    fun `restores saved directory location and back history`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val savedStateHandle = SavedStateHandle(
            mapOf(
                "currentPath" to "/storage/emulated/0/Download",
                "currentVolumeId" to "primary",
                "isCategoryScreen" to false,
                "isVolumeRootScreen" to false,
                "pathHistory" to arrayOf("/storage/emulated/0")
            )
        )
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByPath = mapOf(
                    "/storage/emulated/0/Download" to listOf(browserFile("hello.txt", "/storage/emulated/0/Download/hello.txt")),
                    "/storage/emulated/0" to listOf(browserFile("Download", "/storage/emulated/0/Download", isDirectory = true))
                )
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = savedStateHandle
        )

        advanceUntilIdle()

        assertEquals("/storage/emulated/0/Download", viewModel.state.value.currentPath)
        assertEquals(listOf("hello.txt"), viewModel.state.value.files.map { it.name })

        assertTrue(viewModel.navigateBack())
        advanceUntilIdle()

        assertEquals("/storage/emulated/0", viewModel.state.value.currentPath)
        assertEquals(listOf("Download"), viewModel.state.value.files.map { it.name })
    }

    @Test
    fun `restores saved category location with stored category sort`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByCategory = mapOf("Images" to listOf(browserFile("pic.jpg", "/storage/emulated/0/DCIM/pic.jpg")))
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(
                BrowserPreferences(
                    pathPresentationOptions = mapOf(
                        "category_Images" to BrowserPresentationPreferences(sortOption = FileSortOption.DATE_OLDEST)
                    )
                )
            ),
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "isCategoryScreen" to true,
                    "activeCategoryName" to "Images",
                    "currentVolumeId" to "primary",
                    "isVolumeRootScreen" to false
                )
            )
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.isCategoryScreen)
        assertEquals("Images", viewModel.state.value.activeCategoryName)
        assertEquals("", viewModel.state.value.currentPath)
        assertEquals("primary", viewModel.state.value.currentVolumeId)
        assertEquals(FileSortOption.DATE_OLDEST, viewModel.state.value.browserSortOption)
        assertEquals(BrowserViewMode.LIST, viewModel.state.value.browserViewMode)
        assertEquals(listOf("pic.jpg"), viewModel.state.value.files.map { it.name })
    }

    @Test
    fun `restored category location ignores stale saved folder path`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByCategory = mapOf("Audio" to listOf(browserFile("song.mp3", "/storage/emulated/0/Music/song.mp3")))
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(
                mapOf(
                    "currentPath" to "/storage/emulated/0/Download",
                    "isCategoryScreen" to true,
                    "activeCategoryName" to "Audio",
                    "currentVolumeId" to "primary",
                    "isVolumeRootScreen" to false
                )
            )
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.isCategoryScreen)
        assertEquals("Audio", viewModel.state.value.activeCategoryName)
        assertEquals("", viewModel.state.value.currentPath)
        assertEquals(listOf("song.mp3"), viewModel.state.value.files.map { it.name })
    }

    @Test
    fun `updateBrowserPresentation persists category presentation key`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val preferences = FakeBrowserPreferencesStore()
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByCategory = mapOf("Images" to listOf(browserFile("pic.jpg", "/storage/emulated/0/DCIM/pic.jpg")))
            ),
            browserPreferencesRepository = preferences,
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToCategory("Images", "primary")
        advanceUntilIdle()
        viewModel.updateBrowserPresentation(
            BrowserPresentationPreferences(
                sortOption = FileSortOption.DATE_OLDEST,
                viewMode = BrowserViewMode.GRID,
                listZoom = 1.1f,
                gridMinCellSize = 144f
            ),
            applyToSubfolders = true
        )
        advanceUntilIdle()

        assertEquals("category_Images", preferences.lastUpdatedPath)
        assertEquals(FileSortOption.DATE_OLDEST, preferences.lastUpdatedPathPresentation?.sortOption)
        assertEquals(BrowserViewMode.GRID, preferences.lastUpdatedPathPresentation?.viewMode)
        assertNull(preferences.lastUpdatedGlobalPresentation)
    }

    @Test
    fun `category folder tab starts on all and can be selected`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByCategory = mapOf(
                    "Images" to listOf(
                        browserFile("one.jpg", "/storage/emulated/0/DCIM/one.jpg"),
                        browserFile("two.jpg", "/storage/emulated/0/Download/two.jpg")
                    )
                )
            ),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToCategory("Images", "primary")
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedFolderTabPath)

        viewModel.selectFolderTab("/storage/emulated/0/DCIM")

        assertEquals("/storage/emulated/0/DCIM", viewModel.state.value.selectedFolderTabPath)
    }

    @Test
    fun `category folder tab selection clears selection and resets on normal folder navigation`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByPath = mapOf("/storage/emulated/0/Download" to emptyList()),
                filesByCategory = mapOf(
                    "Images" to listOf(
                        browserFile("one.jpg", "/storage/emulated/0/DCIM/one.jpg"),
                        browserFile("two.jpg", "/storage/emulated/0/DCIM/two.jpg")
                    )
                )
            ),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToCategory("Images", "primary")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/DCIM/one.jpg")
        viewModel.selectFolderTab("/storage/emulated/0/DCIM")

        assertTrue(viewModel.state.value.selectedFiles.isEmpty())

        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        assertNull(viewModel.state.value.selectedFolderTabPath)
        assertFalse(viewModel.state.value.isCategoryScreen)
    }
}
