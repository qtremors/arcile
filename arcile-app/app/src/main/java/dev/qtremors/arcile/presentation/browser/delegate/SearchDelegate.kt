package dev.qtremors.arcile.presentation.browser.delegate

import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.presentation.browser.BrowserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val viewModelScope: CoroutineScope,
    private val repository: FileRepository
) {
    private var searchJob: Job? = null

    fun updateBrowserSearchQuery(query: String) {
        state.update { it.copy(browserSearchQuery = query) }
        debouncedSearch(query)
    }

    private fun debouncedSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            state.update { it.copy(isSearching = true, error = null) }

            val stateVal = state.value
            val scope = when {
                stateVal.isVolumeRootScreen -> StorageScope.AllStorage
                stateVal.isCategoryScreen -> StorageScope.Category(stateVal.currentVolumeId, stateVal.activeCategoryName)
                stateVal.currentVolumeId != null && stateVal.currentPath.isNotEmpty() ->
                    StorageScope.Path(stateVal.currentVolumeId, stateVal.currentPath)
                else -> StorageScope.AllStorage
            }

            repository.searchFiles(query, scope, stateVal.activeSearchFilters).onSuccess { files ->
                state.update { it.copy(isSearching = false, searchResults = files) }
            }.onFailure { error ->
                state.update { it.copy(isSearching = false, error = error.message ?: "Search failed") }
            }
        }
    }

    fun updateSearchFilters(filters: SearchFilters) {
        state.update { it.copy(activeSearchFilters = filters) }
        val currentQuery = state.value.browserSearchQuery
        if (currentQuery.isNotBlank()) {
            debouncedSearch(currentQuery)
        }
    }

    fun toggleSearchFilterMenu(visible: Boolean) {
        state.update { it.copy(isSearchFilterMenuVisible = visible) }
    }
}