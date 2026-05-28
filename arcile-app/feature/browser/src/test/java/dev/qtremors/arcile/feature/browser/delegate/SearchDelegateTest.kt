package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.feature.browser.BrowserState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.collections.immutable.toPersistentList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class SearchDelegateTest {

    private lateinit var state: MutableStateFlow<BrowserState>
    private lateinit var repository: FileRepository
    private lateinit var delegate: SearchDelegate
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        testScope = TestScope()
        state = MutableStateFlow(BrowserState())
        repository = mockk(relaxed = true)
        delegate = SearchDelegate(state, testScope, repository)
    }

    @Test
    fun `updateBrowserSearchQuery updates query and performs debounced search`() = testScope.runTest {
        val testFiles = listOf(FileModel("test.txt", "/test.txt", 0L, 0L, false, "txt", false))
        coEvery { repository.searchFiles(any(), any(), any()) } returns Result.success(testFiles)

        delegate.updateBrowserSearchQuery("test query")
        
        assertEquals("test query", state.value.browserSearchQuery)
        
        // Advance time to pass the 400ms delay in debouncedSearch
        advanceTimeBy(401)
        
        assertFalse(state.value.isSearching)
        assertEquals(testFiles, state.value.searchResults)
        
        coVerify(exactly = 1) { repository.searchFiles("test query", any(), any()) }
    }

    @Test
    fun `updateBrowserSearchQuery with blank query clears results`() = testScope.runTest {
        state.value = state.value.copy(
            searchResults = listOf(FileModel("old.txt", "/old.txt", 0L, 0L, false, "txt", false)).toPersistentList(),
            isSearching = true
        )
        
        delegate.updateBrowserSearchQuery("   ")
        
        assertTrue(state.value.searchResults.isEmpty())
        assertFalse(state.value.isSearching)
        coVerify(exactly = 0) { repository.searchFiles(any(), any(), any()) }
    }

    @Test
    fun `updateSearchFilters updates filters and triggers search if query is not blank`() = testScope.runTest {
        coEvery { repository.searchFiles(any(), any(), any()) } returns Result.success(emptyList())
        state.value = state.value.copy(browserSearchQuery = "test")
        val filters = SearchFilters() 

        delegate.updateSearchFilters(filters)

        assertEquals(filters, state.value.activeSearchFilters)

        advanceTimeBy(401)

        coVerify(exactly = 1) { repository.searchFiles("test", any(), eq(filters)) }
    }

    @Test
    fun `toggleSearchFilterMenu updates state`() {
        delegate.toggleSearchFilterMenu(true)
        assertTrue(state.value.isSearchFilterMenuVisible)
        
        delegate.toggleSearchFilterMenu(false)
        assertFalse(state.value.isSearchFilterMenuVisible)
    }
}
