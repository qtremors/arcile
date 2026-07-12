package dev.qtremors.arcile.core.presentation

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
    initialQuery: String = "",
    initialResults: List<T> = emptyList(),
    private val debounceMillis: Long,
    private val fallbackError: UiText,
    private val search: suspend (query: String, filters: F) -> Result<List<T>>
) {
    private val _state = MutableStateFlow(
        DebouncedSearchState<T, F>(
            query = initialQuery,
            filters = initialFilters,
            results = initialResults
        )
    )
    val state: StateFlow<DebouncedSearchState<T, F>> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var requestGeneration = 0L

    fun updateQuery(query: String) {
        _state.update { it.copy(query = query) }
        scheduleSearch()
    }

    fun updateFilters(filters: F, restartSearch: Boolean = true) {
        _state.update { it.copy(filters = filters) }
        if (restartSearch && _state.value.query.isNotBlank()) {
            scheduleSearch()
        }
    }

    fun refresh() {
        if (_state.value.query.isNotBlank()) {
            scheduleSearch()
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun scheduleSearch() {
        val generation = ++requestGeneration
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
            val result = try {
                search(request.query, request.filters)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Result.failure(error)
            }
            if (generation != requestGeneration) return@launch
            result.fold(
                onSuccess = { results ->
                    _state.update { current ->
                        if (generation != requestGeneration) return@update current
                        current.copy(
                            results = results,
                            isSearching = false
                        )
                    }
                },
                onFailure = { error ->
                    _state.update { current ->
                        if (generation != requestGeneration) return@update current
                        current.copy(
                            isSearching = false,
                            error = error.message?.let(UiText::Dynamic) ?: fallbackError
                        )
                    }
                }
            )
        }
    }
}
