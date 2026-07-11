package dev.qtremors.arcile.feature.recentfiles

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.presentation.PropertiesUiModel

internal data class RecentFilesState(
    val currentVolumeId: String? = null,
    val recentFiles: List<FileModel> = emptyList(),
    val displayedRecentFiles: List<FileModel> = emptyList(),
    val selectedFiles: Set<String> = emptySet(),
    val selectedFilesTotalSize: Long = 0L,
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val currentOffset: Int = 0,
    val error: UiText? = null,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isShredChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val searchQuery: String = "",
    val searchResults: List<FileModel> = emptyList(),
    val isSearching: Boolean = false,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val presentation: FileListingPreferences = FileListingPreferences(
        sortOption = FileSortOption.DATE_NEWEST
    ),
    val todayStart: Long = 0L,
    val yesterdayStart: Long = 0L,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: PropertiesUiModel? = null
)

internal fun RecentFilesState.displayRecentFiles(
    source: List<FileModel> = recentFiles
): List<FileModel> = buildRecentFilesDisplay(
    files = source,
    query = "",
    filters = activeSearchFilters,
    presentation = presentation
)

internal fun RecentFilesState.displaySearchResults(
    source: List<FileModel> = recentFiles
): List<FileModel> = if (searchQuery.isBlank() || searchResults.isNotEmpty()) {
    if (searchQuery.isBlank()) {
        emptyList()
    } else {
        buildRecentFilesDisplay(
            files = searchResults,
            query = "",
            filters = activeSearchFilters,
            presentation = presentation
        )
    }
} else {
    emptyList()
}

internal fun RecentFilesState.searchScope(): StorageScope =
    currentVolumeId?.let(StorageScope::Volume) ?: StorageScope.AllStorage
