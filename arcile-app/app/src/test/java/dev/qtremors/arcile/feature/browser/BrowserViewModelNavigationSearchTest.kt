package dev.qtremors.arcile.feature.browser

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.testutil.FakeBrowserPreferencesStore
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelNavigationSearchTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `multiple volumes open volume root screen with stored root sort option`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val sd = browserVolume("sd", "SD Card", "/storage/1234-5678", isPrimary = false, isRemovable = true)
        val preferences = FakeBrowserPreferencesStore(
            BrowserPreferences(
                globalPresentation = BrowserPresentationPreferences(sortOption = FileSortOption.NAME_ASC),
                pathPresentationOptions = mapOf(
                    "/" to BrowserPresentationPreferences(sortOption = FileSortOption.SIZE_LARGEST)
                )
            )
        )
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal, sd)),
            browserPreferencesRepository = preferences,
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()

        assertTrue(viewModel.state.value.isVolumeRootScreen)
        assertEquals(listOf("Internal", "SD Card"), viewModel.state.value.files.map { it.name })
        assertEquals(FileSortOption.SIZE_LARGEST, viewModel.state.value.browserSortOption)
        assertEquals(BrowserViewMode.LIST, viewModel.state.value.browserViewMode)
        assertFalse(viewModel.state.value.isLoading)
    }

    @Test
    fun `navigateToSpecificFolder falls back to volume roots when path storage is unavailable`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal, browserVolume("sd", "SD Card", "/storage/1234-5678", isPrimary = false, isRemovable = true))),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/missing/path")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.isVolumeRootScreen)
        assertEquals(UiText.StringResource(dev.qtremors.arcile.R.string.error_storage_for_path_unavailable), viewModel.state.value.error)
        assertEquals("", viewModel.state.value.currentPath)
    }

    @Test
    fun `directory search uses current path scope and active filters after debounce`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf("/storage/emulated/0/Download" to listOf(browserFile("one.txt", "/storage/emulated/0/Download/one.txt"))),
            searchResult = Result.success(listOf(browserFile("holiday.jpg", "/storage/emulated/0/Download/holiday.jpg")))
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()

        val filters = SearchFilters(fileType = "image", minSize = 100L)
        viewModel.updateSearchFilters(filters)
        viewModel.updateBrowserSearchQuery("holiday")
        advanceTimeBy(399)
        assertFalse(viewModel.state.value.isSearching)
        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals("holiday", repo.lastSearchQuery)
        assertEquals(StorageScope.Path("primary", "/storage/emulated/0/Download"), repo.lastSearchScope)
        assertEquals(filters, repo.lastSearchFilters)
        assertEquals(listOf("holiday.jpg"), viewModel.state.value.searchResults.map { it.name })
    }
}
