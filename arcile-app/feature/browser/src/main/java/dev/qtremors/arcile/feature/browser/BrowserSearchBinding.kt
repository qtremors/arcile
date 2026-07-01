package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.feature.browser.delegate.BrowserSearchContext
import dev.qtremors.arcile.feature.browser.delegate.SearchController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal fun createBrowserSearchController(
    state: MutableStateFlow<BrowserState>,
    scope: CoroutineScope,
    repository: SearchRepository
): SearchController = SearchController(
    initialState = state.value.searchState(),
    scope = scope,
    repository = repository,
    contextProvider = {
        val current = state.value
        BrowserSearchContext(
            currentPath = current.currentPath,
            currentVolumeId = current.currentVolumeId,
            isVolumeRootScreen = current.isVolumeRootScreen,
            isCategoryScreen = current.isCategoryScreen,
            activeCategoryName = current.activeCategoryName,
            archiveFiles = current.files.takeIf { current.archiveContext != null }
        )
    },
    onStateChange = { search ->
        state.update {
            it.copy(
                searchResults = search.searchResults,
                isSearching = search.isSearching,
                browserSearchQuery = search.browserSearchQuery,
                activeSearchFilters = search.activeSearchFilters,
                isSearchFilterMenuVisible = search.isSearchFilterMenuVisible,
                selectedFolderTabPath = if (search.browserSearchQuery.isBlank()) {
                    null
                } else {
                    it.selectedFolderTabPath
                }
            ).withUpdatedDisplayState()
        }
    },
    onError = { error -> state.update { it.copy(error = error) } }
)
