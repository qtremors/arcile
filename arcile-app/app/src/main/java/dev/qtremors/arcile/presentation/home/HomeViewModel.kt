package dev.qtremors.arcile.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.presentation.FileSortOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeState(
    val storageInfo: StorageInfo? = null,
    val categoryStorages: List<CategoryStorage> = emptyList(),
    val recentFiles: List<FileModel> = emptyList(),
    val searchResults: List<FileModel> = emptyList(),
    val homeSearchQuery: String = "",
    val homeSortOption: FileSortOption = FileSortOption.DATE_NEWEST,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearching: Boolean = false,
    val isSearchFilterMenuVisible: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FileRepository
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val recentsPreviewLimit = 50
    private var searchJob: Job? = null

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)

            val recentResult = repository.getRecentFiles(limit = recentsPreviewLimit, minTimestamp = oneWeekAgo)
            val storageResult = repository.getStorageInfo()
            val categoryResult = repository.getCategoryStorageSizes()

            _state.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    recentFiles = recentResult.getOrNull() ?: emptyList(),
                    storageInfo = storageResult.getOrNull(),
                    categoryStorages = categoryResult.getOrNull() ?: emptyList()
                )
            }
        }
    }

    fun updateHomeSearchQuery(query: String) {
        _state.update { it.copy(homeSearchQuery = query) }
        debouncedSearch(query)
    }

    private fun debouncedSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(isSearching = true, error = null) }

            val filters = _state.value.activeSearchFilters
            // Path scope is null for MediaStore-wide search
            val result = repository.searchFiles(query, null, filters)
            
            result.onSuccess { files ->
                _state.update { it.copy(isSearching = false, searchResults = files) }
            }.onFailure { error ->
                _state.update { it.copy(isSearching = false, error = error.message ?: "Search failed") }
            }
        }
    }

    fun updateHomeSortOption(sortOption: FileSortOption) {
        _state.update { it.copy(homeSortOption = sortOption) }
    }

    fun updateSearchFilters(filters: SearchFilters) {
        _state.update { it.copy(activeSearchFilters = filters) }
        val currentQuery = _state.value.homeSearchQuery
        if (currentQuery.isNotBlank()) {
            debouncedSearch(currentQuery)
        }
    }

    fun toggleSearchFilterMenu(visible: Boolean) {
        _state.update { it.copy(isSearchFilterMenuVisible = visible) }
    }
}
