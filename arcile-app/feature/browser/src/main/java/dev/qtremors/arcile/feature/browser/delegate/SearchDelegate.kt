package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.feature.browser.BrowserSearchEvent
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.feature.browser.reduce
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SearchDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val viewModelScope: CoroutineScope,
    private val repository: SearchRepository
) {
    private var searchJob: Job? = null

    fun updateBrowserSearchQuery(query: String) {
        state.update { it.reduce(BrowserSearchEvent.QueryChanged(query)) }
        debouncedSearch(query)
    }

    private fun debouncedSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            state.update { it.copy(searchResults = persistentListOf()).reduce(BrowserSearchEvent.SearchingChanged(false)) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            state.update { it.reduce(BrowserSearchEvent.SearchingChanged(true)).copy(error = null) }

            val stateVal = state.value
            if (stateVal.archiveContext != null) {
                val normalized = query.trim().lowercase()
                val files = stateVal.files.filter { it.name.lowercase().contains(normalized) }
                state.update { it.reduce(BrowserSearchEvent.ResultsLoaded(files)) }
                return@launch
            }
            val scope = when {
                stateVal.isVolumeRootScreen -> StorageScope.AllStorage
                stateVal.isCategoryScreen -> StorageScope.Category(stateVal.currentVolumeId, stateVal.activeCategoryName)
                stateVal.currentVolumeId != null && stateVal.currentPath.isNotEmpty() ->
                    StorageScope.Path(stateVal.currentVolumeId, stateVal.currentPath)
                else -> StorageScope.AllStorage
            }

            repository.searchFiles(query, scope, stateVal.activeSearchFilters).onSuccess { files ->
                state.update { it.reduce(BrowserSearchEvent.ResultsLoaded(files)) }
            }.onFailure { error ->
                state.update {
                    it.copy(
                        isSearching = false,
                        error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_search_failed)
                    )
                }
            }
        }
    }

    fun updateSearchFilters(filters: SearchFilters) {
        state.update { it.reduce(BrowserSearchEvent.FiltersChanged(filters)) }
        val currentQuery = state.value.browserSearchQuery
        if (currentQuery.isNotBlank()) {
            debouncedSearch(currentQuery)
        }
    }

    fun toggleSearchFilterMenu(visible: Boolean) {
        state.update { it.reduce(BrowserSearchEvent.FilterMenuChanged(visible)) }
    }
}
