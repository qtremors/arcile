package dev.qtremors.arcile.core.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DebouncedSearchState<T, F>(
    val query: String,
    val filters: F,
    val results: List<T> = emptyList(),
    val isSearching: Boolean = false,
    val error: UiText? = null
)

class DebouncedSearchController<T, F>(
    private val scope: CoroutineScope,
    initialFilters: F,
    private val debounceMillis: Long,
    private val fallbackError: UiText,
    private val search: suspend (query: String, filters: F) -> Result<List<T>>
) {
    private val _state = MutableStateFlow(
        DebouncedSearchState<T, F>(
            query = "",
            filters = initialFilters
        )
    )
    val state: StateFlow<DebouncedSearchState<T, F>> = _state.asStateFlow()

    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
        scheduleSearch()
    }

    fun updateFilters(filters: F) {
        _state.update { it.copy(filters = filters) }
        if (_state.value.query.isNotBlank()) {
            scheduleSearch()
        }
    }

    private fun scheduleSearch() {
        searchJob?.cancel()
        val current = _state.value
        if (current.query.isBlank()) {
            _state.update {
                it.copy(
                    results = emptyList(),
                    isSearching = false,
                    error = null
                )
            }
            return
        }

        searchJob = scope.launch {
            delay(debounceMillis)
            val request = _state.value
            _state.update { it.copy(isSearching = true, error = null) }
            search(request.query, request.filters).fold(
                onSuccess = { results ->
                    _state.update {
                        it.copy(
                            results = results,
                            isSearching = false
                        )
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            error = error.message?.let(UiText::Dynamic) ?: fallbackError
                        )
                    }
                }
            )
        }
    }
}
