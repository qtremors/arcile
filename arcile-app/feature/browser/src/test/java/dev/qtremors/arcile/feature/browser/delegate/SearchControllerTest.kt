package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.feature.browser.BrowserSearchState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchControllerTest {
    private lateinit var repository: FileRepository
    private lateinit var controller: SearchController
    private lateinit var testScope: TestScope
    private var latestState = BrowserSearchState()
    private var latestError: UiText? = null
    private var context = BrowserSearchContext(
        currentPath = "",
        currentVolumeId = null,
        isVolumeRootScreen = true,
        isCategoryScreen = false,
        activeCategoryName = "",
        archiveFiles = null
    )

    @Before
    fun setup() {
        testScope = TestScope()
        repository = mockk(relaxed = true)
        latestState = BrowserSearchState()
        latestError = null
        controller = SearchController(
            initialState = latestState,
            scope = testScope,
            repository = repository,
            contextProvider = { context },
            onStateChange = { latestState = it },
            onError = { latestError = it }
        )
    }

    @Test
    fun `query updates owned state and performs debounced search`() = testScope.runTest {
        val files = listOf(FileModel("test.txt", "/test.txt", 0L, 0L, false, "txt", false))
        coEvery { repository.searchFiles(any(), any(), any()) } returns Result.success(files)

        controller.updateQuery("test query")
        assertEquals("test query", controller.state.value.browserSearchQuery)

        advanceTimeBy(401)

        assertFalse(controller.state.value.isSearching)
        assertEquals(files, controller.state.value.searchResults)
        assertEquals(controller.state.value, latestState)
        coVerify(exactly = 1) { repository.searchFiles("test query", any(), any()) }
    }

    @Test
    fun `blank query cancels work and clears results`() = testScope.runTest {
        coEvery { repository.searchFiles(any(), any(), any()) } returns Result.success(
            listOf(FileModel("old.txt", "/old.txt", 0L, 0L, false, "txt", false))
        )
        controller.updateQuery("old")
        advanceTimeBy(401)

        controller.updateQuery(" ")

        assertTrue(controller.state.value.searchResults.isEmpty())
        assertFalse(controller.state.value.isSearching)
    }

    @Test
    fun `filters trigger search with current query`() = testScope.runTest {
        coEvery { repository.searchFiles(any(), any(), any()) } returns Result.success(emptyList())
        controller.updateQuery("test")
        val filters = SearchFilters(minSize = 10L)

        controller.updateFilters(filters)
        advanceTimeBy(401)

        assertEquals(filters, controller.state.value.activeSearchFilters)
        coVerify(exactly = 1) { repository.searchFiles("test", any(), eq(filters)) }
    }

    @Test
    fun `archive search stays local`() = testScope.runTest {
        context = context.copy(
            archiveFiles = listOf(
                FileModel("match.txt", "match.txt", 0L, 0L, false, "txt", false),
                FileModel("other.jpg", "other.jpg", 0L, 0L, false, "jpg", false)
            )
        )

        controller.updateQuery("MATCH")
        advanceTimeBy(401)

        assertEquals(listOf("match.txt"), controller.state.value.searchResults.map { it.name })
        coVerify(exactly = 0) { repository.searchFiles(any(), any(), any()) }
    }

    @Test
    fun `repository failure clears searching and emits error`() = testScope.runTest {
        coEvery { repository.searchFiles(any(), any(), any()) } returns
            Result.failure(IllegalStateException("search unavailable"))

        controller.updateQuery("test")
        advanceTimeBy(401)

        assertFalse(controller.state.value.isSearching)
        assertEquals(UiText.Dynamic("search unavailable"), latestError)
    }
}
