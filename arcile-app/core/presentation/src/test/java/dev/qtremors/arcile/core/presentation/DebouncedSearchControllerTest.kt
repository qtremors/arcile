package dev.qtremors.arcile.core.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncedSearchControllerTest {

    @Test
    fun `only latest query runs after debounce`() = runTest {
        val requests = mutableListOf<Pair<String, Int>>()
        val controller = controller { query, filters ->
            requests += query to filters
            Result.success(listOf(query))
        }

        controller.updateQuery("first")
        advanceTimeBy(200)
        controller.updateQuery("second")
        advanceTimeBy(399)
        assertTrue(requests.isEmpty())

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("second" to 0), requests)
        assertEquals(listOf("second"), controller.state.value.results)
        assertFalse(controller.state.value.isSearching)
    }

    @Test
    fun `changing filters restarts pending search with latest filters`() = runTest {
        val requests = mutableListOf<Pair<String, Int>>()
        val controller = controller { query, filters ->
            requests += query to filters
            Result.success(emptyList())
        }

        controller.updateQuery("photo")
        advanceTimeBy(300)
        controller.updateFilters(7)
        advanceTimeBy(399)
        assertTrue(requests.isEmpty())

        advanceTimeBy(1)
        advanceUntilIdle()

        assertEquals(listOf("photo" to 7), requests)
    }

    @Test
    fun `blank query cancels active search and clears results`() = runTest {
        val controller = controller { query, _ ->
            if (query == "blocked") awaitCancellation()
            Result.success(listOf(query))
        }

        controller.updateQuery("ready")
        advanceUntilIdle()
        assertEquals(listOf("ready"), controller.state.value.results)

        controller.updateQuery("blocked")
        advanceTimeBy(400)
        runCurrent()
        assertTrue(controller.state.value.isSearching)

        controller.updateQuery("")
        runCurrent()

        assertEquals(emptyList<String>(), controller.state.value.results)
        assertFalse(controller.state.value.isSearching)
    }

    @Test
    fun `failed search exposes dynamic or fallback error`() = runTest {
        val fallback = UiText.Dynamic("fallback")
        val controller = DebouncedSearchController<String, Unit>(
            scope = this,
            initialFilters = Unit,
            debounceMillis = 400,
            fallbackError = fallback
        ) { query, _ ->
            Result.failure(IllegalStateException(query.takeIf { it == "message" }))
        }

        controller.updateQuery("message")
        advanceUntilIdle()
        assertEquals(UiText.Dynamic("message"), controller.state.value.error)

        controller.updateQuery("fallback")
        advanceUntilIdle()
        assertEquals(fallback, controller.state.value.error)
        assertFalse(controller.state.value.isSearching)
    }

    private fun kotlinx.coroutines.test.TestScope.controller(
        search: suspend (String, Int) -> Result<List<String>>
    ) = DebouncedSearchController(
        scope = this,
        initialFilters = 0,
        debounceMillis = 400,
        fallbackError = UiText.Dynamic("failed"),
        search = search
    )
}
