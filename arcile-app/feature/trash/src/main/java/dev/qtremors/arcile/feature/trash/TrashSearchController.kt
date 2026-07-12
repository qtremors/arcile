package dev.qtremors.arcile.feature.trash

import dev.qtremors.arcile.core.presentation.DebouncedSearchController
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.ui.R
import kotlinx.coroutines.CoroutineScope

internal class TrashSearchController(
    scope: CoroutineScope,
    private val itemsProvider: () -> List<TrashMetadata>
) {
    private val engine = DebouncedSearchController(
        scope = scope,
        initialFilters = TrashSearchPresentation(TrashFilter.ALL, TrashSortOption.DELETED_NEWEST),
        debounceMillis = SEARCH_DEBOUNCE_MILLIS,
        fallbackError = UiText.StringResource(R.string.error_search_failed)
    ) { query, presentation ->
        Result.success(
            applyTrashPresentation(
                items = searchMatches(itemsProvider(), query),
                filter = presentation.filter,
                sortOption = presentation.sortOption
            )
        )
    }

    val state = engine.state

    fun updateQuery(query: String) = engine.updateQuery(query)

    fun updatePresentation(filter: TrashFilter, sortOption: TrashSortOption) {
        engine.updateFilters(TrashSearchPresentation(filter, sortOption), restartSearch = false)
    }

    fun refresh() = engine.refresh()

    fun clearError() = engine.clearError()

    private companion object {
        const val SEARCH_DEBOUNCE_MILLIS = 300L
    }
}

internal data class TrashSearchPresentation(
    val filter: TrashFilter,
    val sortOption: TrashSortOption
)
