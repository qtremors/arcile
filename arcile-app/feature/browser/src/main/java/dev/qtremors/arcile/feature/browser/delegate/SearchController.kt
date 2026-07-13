package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.DebouncedSearchController
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.BrowserSearchState
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

internal data class BrowserSearchContext(
    val currentPath: String,
    val currentVolumeId: String?,
    val isVolumeRootScreen: Boolean,
    val isCategoryScreen: Boolean,
    val activeCategoryName: String,
    val archiveFiles: List<FileModel>?
)

internal class SearchController(
    initialState: BrowserSearchState,
    scope: CoroutineScope,
    repository: SearchRepository,
    private val contextProvider: () -> BrowserSearchContext
) {
    private val filterMenuVisible = MutableStateFlow(initialState.isSearchFilterMenuVisible)

    private val searchEngine = DebouncedSearchController(
        scope = scope,
        initialFilters = initialState.activeSearchFilters,
        initialQuery = initialState.browserSearchQuery,
        initialResults = initialState.searchResults,
        debounceMillis = SEARCH_DEBOUNCE_MILLIS,
        fallbackError = UiText.StringResource(R.string.error_search_failed)
    ) { query, filters ->
        search(query, filters, repository)
    }
    val state: StateFlow<BrowserSearchState> = BrowserSearchStateFlow(
        searchState = searchEngine.state,
        filterMenuVisible = filterMenuVisible
    )

    fun updateQuery(query: String) {
        searchEngine.updateQuery(query)
    }

    fun updateFilters(filters: SearchFilters) {
        searchEngine.updateFilters(filters)
    }

    fun setFilterMenuVisible(visible: Boolean) {
        filterMenuVisible.value = visible
    }

    fun clearError() {
        searchEngine.clearError()
    }

    private suspend fun search(
        query: String,
        filters: SearchFilters,
        repository: SearchRepository
    ): Result<List<FileModel>> {
        val context = contextProvider()
        context.archiveFiles?.let { archiveFiles ->
            val normalized = query.trim().lowercase()
            return Result.success(
                archiveFiles.filter { file -> file.name.lowercase().contains(normalized) }
            )
        }
        val storageScope = when {
            context.isVolumeRootScreen -> StorageScope.AllStorage
            context.isCategoryScreen ->
                StorageScope.Category(context.currentVolumeId, context.activeCategoryName)
            context.currentVolumeId != null && context.currentPath.isNotEmpty() ->
                StorageScope.Path(context.currentVolumeId, context.currentPath)
            else -> StorageScope.AllStorage
        }
        return repository.searchFiles(query, storageScope, filters)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}

@OptIn(kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi::class)
private class BrowserSearchStateFlow(
    private val searchState: StateFlow<dev.qtremors.arcile.core.presentation.DebouncedSearchState<FileModel, SearchFilters>>,
    private val filterMenuVisible: StateFlow<Boolean>
) : StateFlow<BrowserSearchState> {
    override val value: BrowserSearchState
        get() = searchState.value.toBrowserState(filterMenuVisible.value)

    override val replayCache: List<BrowserSearchState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<BrowserSearchState>): Nothing {
        combine(searchState, filterMenuVisible) { search, menuVisible ->
            search.toBrowserState(menuVisible)
        }.collect(collector)
        awaitCancellation()
    }
}

private fun dev.qtremors.arcile.core.presentation.DebouncedSearchState<FileModel, SearchFilters>.toBrowserState(
    filterMenuVisible: Boolean
) = BrowserSearchState(
    browserSearchQuery = query,
    searchResults = results.toPersistentList(),
    isSearching = isSearching,
    error = error,
    activeSearchFilters = filters,
    isSearchFilterMenuVisible = filterMenuVisible
)
