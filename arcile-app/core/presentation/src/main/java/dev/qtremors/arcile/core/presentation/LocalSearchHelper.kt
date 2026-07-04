package dev.qtremors.arcile.core.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class LocalSearchHelper<T>(
    private val scope: CoroutineScope,
    private val debounceMs: Long = 300L,
    private val source: () -> List<T>,
    private val matches: (T, String) -> Boolean,
    private val onQueryChanged: (String) -> Unit,
    private val onSearchingChanged: (Boolean) -> Unit,
    private val onResultsChanged: (List<T>) -> Unit
) {
    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        onQueryChanged(query)
        searchJob?.cancel()
        if (query.isBlank()) {
            onSearchingChanged(false)
            onResultsChanged(emptyList())
            return
        }

        searchJob = scope.launch {
            delay(debounceMs)
            onSearchingChanged(true)
            val filtered = source().filter { matches(it, query) }
            onResultsChanged(filtered)
            onSearchingChanged(false)
        }
    }

    fun cancel() {
        searchJob?.cancel()
        searchJob = null
    }
}
