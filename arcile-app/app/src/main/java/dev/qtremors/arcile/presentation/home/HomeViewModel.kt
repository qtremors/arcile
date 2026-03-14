package dev.qtremors.arcile.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.presentation.FileSortOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import dev.qtremors.arcile.domain.StorageVolume
import dev.qtremors.arcile.domain.StorageKind
import dev.qtremors.arcile.domain.isIndexed
import dev.qtremors.arcile.data.StorageClassificationStore

data class HomeState(
    val allStorageVolumes: List<StorageVolume> = emptyList(),
    val storageInfo: StorageInfo? = null,
    val categoryStorages: List<CategoryStorage> = emptyList(),
    val categoryStoragesByVolume: Map<String, List<CategoryStorage>> = emptyMap(),
    val recentFiles: List<FileModel> = emptyList(),
    val searchResults: List<FileModel> = emptyList(),
    val homeSearchQuery: String = "",
    val homeSortOption: FileSortOption = FileSortOption.DATE_NEWEST,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearching: Boolean = false,
    val isSearchFilterMenuVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isPullToRefreshing: Boolean = false,
    val error: String? = null,
    val unclassifiedVolumes: List<StorageVolume> = emptyList(),
    val showClassificationPrompt: Boolean = false
)

enum class HomeRefreshMode {
    INITIAL,
    MANUAL,
    SILENT
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: FileRepository,
    private val classificationRepo: StorageClassificationStore
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val recentsPreviewLimit = 50
    private var searchJob: Job? = null
    private val suppressedVolumeKeys = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            repository.observeStorageVolumes().collectLatest {
                loadHomeData(HomeRefreshMode.SILENT)
            }
        }
        loadHomeData(HomeRefreshMode.INITIAL)
    }

    fun loadHomeData(refreshMode: HomeRefreshMode = HomeRefreshMode.INITIAL) {
        viewModelScope.launch {
            val hasVisibleContent = _state.value.storageInfo != null ||
                _state.value.categoryStorages.isNotEmpty() ||
                _state.value.recentFiles.isNotEmpty()

            _state.update {
                it.copy(
                    isLoading = refreshMode == HomeRefreshMode.INITIAL && !hasVisibleContent,
                    isPullToRefreshing = refreshMode == HomeRefreshMode.MANUAL,
                    error = null
                )
            }
            val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)

            val recentResult = repository.getRecentFiles(
                scope = StorageScope.AllStorage,
                limit = recentsPreviewLimit,
                minTimestamp = oneWeekAgo
            )
            val allVolumesResult = repository.getStorageVolumes()
            val storageResult = repository.getStorageInfo(StorageScope.AllStorage)
            val categoryResult = repository.getCategoryStorageSizes(StorageScope.AllStorage)
            val storageInfo = storageResult.getOrNull()
            val allStorageVolumes = allVolumesResult.getOrNull().orEmpty()
            
            val categoryByVolume = coroutineScope {
                storageInfo?.volumes
                    ?.filter { it.kind.isIndexed }
                    ?.map { volume ->
                        async {
                            volume.id to (repository.getCategoryStorageSizes(StorageScope.Volume(volume.id)).getOrNull() ?: emptyList())
                        }
                    }
                    ?.awaitAll()
                    ?.toMap()
                    ?: emptyMap()
            }

            val unclassified = allStorageVolumes.filter {
                it.kind == StorageKind.EXTERNAL_UNCLASSIFIED && !suppressedVolumeKeys.contains(it.storageKey)
            }

            _state.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    isPullToRefreshing = false,
                    allStorageVolumes = allStorageVolumes,
                    recentFiles = recentResult.getOrNull() ?: emptyList(),
                    storageInfo = storageInfo,
                    categoryStorages = categoryResult.getOrNull() ?: emptyList(),
                    categoryStoragesByVolume = categoryByVolume,
                    unclassifiedVolumes = unclassified,
                    showClassificationPrompt = unclassified.isNotEmpty()
                )
            }
        }
    }

    fun setVolumeClassification(storageKey: String, kind: StorageKind) {
        viewModelScope.launch {
            val volume = _state.value.storageInfo?.volumes?.firstOrNull { it.storageKey == storageKey }
            classificationRepo.setClassification(
                storageKey = storageKey,
                kind = kind,
                lastSeenName = volume?.name,
                lastSeenPath = volume?.path
            )
            suppressedVolumeKeys.remove(storageKey)
        }
    }

    fun resetVolumeClassification(storageKey: String) {
        viewModelScope.launch {
            classificationRepo.resetClassification(storageKey)
            suppressedVolumeKeys.remove(storageKey)
        }
    }

    fun hideClassificationPrompt(storageKey: String) {
        suppressedVolumeKeys.add(storageKey)
        val remaining = _state.value.unclassifiedVolumes.filter { it.storageKey != storageKey }
        _state.update { it.copy(
            unclassifiedVolumes = remaining,
            showClassificationPrompt = remaining.isNotEmpty()
        ) }
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
            val result = repository.searchFiles(query, StorageScope.AllStorage, filters)
            
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
