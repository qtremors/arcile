package dev.qtremors.arcile.core.presentation

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    fun `filters can update without restarting a local search`() = runTest {
        val requests = mutableListOf<Pair<String, Int>>()
        val controller = controller { query, filters ->
            requests += query to filters
            Result.success(emptyList())
        }

        controller.updateQuery("photo")
        advanceUntilIdle()
        controller.updateFilters(9, restartSearch = false)
        advanceUntilIdle()

        assertEquals(listOf("photo" to 0), requests)
        assertEquals(9, controller.state.value.filters)
    }

    @Test
    fun `refresh reruns the current query against a changed source`() = runTest {
        var source = listOf("old")
        val controller = controller { _, _ -> Result.success(source) }

        controller.updateQuery("photo")
        advanceUntilIdle()
        source = listOf("new")
        controller.refresh()
        advanceUntilIdle()

        assertEquals(listOf("new"), controller.state.value.results)
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

    @Test
    fun `cancelled search cannot publish after a cancellation-ignoring backend returns`() = runTest {
        val controller = controller { query, _ ->
            if (query == "stale") {
                withContext(NonCancellable) { delay(800) }
            }
            Result.success(listOf(query))
        }

        controller.updateQuery("stale")
        advanceTimeBy(400)
        runCurrent()
        controller.updateQuery("current")
        advanceUntilIdle()

        assertEquals(listOf("current"), controller.state.value.results)
        assertEquals("current", controller.state.value.query)
    }

    @Test
    fun `thrown backend failure is converted to the configured error`() = runTest {
        val controller = controller { _, _ -> error("backend exploded") }

        controller.updateQuery("photo")
        advanceUntilIdle()

        assertEquals(UiText.Dynamic("backend exploded"), controller.state.value.error)
        assertFalse(controller.state.value.isSearching)
    }

    @Test
    fun `clearError dismisses failure without changing query filters or results`() = runTest {
        val controller = controller { _, _ -> Result.failure(IllegalStateException("unavailable")) }

        controller.updateQuery("photo")
        advanceUntilIdle()
        controller.clearError()

        assertEquals("photo", controller.state.value.query)
        assertEquals(0, controller.state.value.filters)
        assertEquals(emptyList<String>(), controller.state.value.results)
        assertEquals(null, controller.state.value.error)
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
