package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.BrowserSearchState
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
    private val scope: CoroutineScope,
    private val repository: SearchRepository,
    private val contextProvider: () -> BrowserSearchContext,
    private val onStateChange: (BrowserSearchState) -> Unit,
    private val onError: (UiText?) -> Unit
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<BrowserSearchState> = _state.asStateFlow()
    private var searchJob: Job? = null

    fun updateQuery(query: String) {
        updateState { it.copy(browserSearchQuery = query) }
        search(query)
    }

    fun updateFilters(filters: SearchFilters) {
        updateState { it.copy(activeSearchFilters = filters) }
        state.value.browserSearchQuery.takeIf(String::isNotBlank)?.let(::search)
    }

    fun setFilterMenuVisible(visible: Boolean) {
        updateState { it.copy(isSearchFilterMenuVisible = visible) }
    }

    private fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            updateState {
                it.copy(
                    searchResults = kotlinx.collections.immutable.persistentListOf(),
                    isSearching = false
                )
            }
            return
        }
        searchJob = scope.launch {
            delay(SEARCH_DEBOUNCE_MILLIS)
            onError(null)
            updateState { it.copy(isSearching = true) }
            val context = contextProvider()
            val archiveFiles = context.archiveFiles
            if (archiveFiles != null) {
                val normalized = query.trim().lowercase()
                updateState {
                    it.copy(
                        searchResults = archiveFiles
                            .filter { file -> file.name.lowercase().contains(normalized) }
                            .toPersistentList(),
                        isSearching = false
                    )
                }
                return@launch
            }
            val storageScope = when {
                context.isVolumeRootScreen -> StorageScope.AllStorage
                context.isCategoryScreen ->
                    StorageScope.Category(context.currentVolumeId, context.activeCategoryName)
                context.currentVolumeId != null && context.currentPath.isNotEmpty() ->
                    StorageScope.Path(context.currentVolumeId, context.currentPath)
                else -> StorageScope.AllStorage
            }
            repository.searchFiles(
                query,
                storageScope,
                state.value.activeSearchFilters
            ).fold(
                onSuccess = { files ->
                    updateState {
                        it.copy(
                            searchResults = files.toPersistentList(),
                            isSearching = false
                        )
                    }
                },
                onFailure = { error ->
                    updateState { it.copy(isSearching = false) }
                    onError(
                        error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_search_failed)
                    )
                }
            )
        }
    }

    private inline fun updateState(
        transform: (BrowserSearchState) -> BrowserSearchState
    ) {
        _state.update(transform)
        onStateChange(_state.value)
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 400L
    }
}
