package dev.qtremors.arcile.feature.recentfiles

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.shared.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.shared.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.ui.UiText
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import dev.qtremors.arcile.shared.presentation.toUiModel

enum class RecentNativeAction { TRASH }

data class RecentFilesState(
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
    val pendingNativeAction: RecentNativeAction? = null,
    val searchQuery: String = "",
    val searchResults: List<FileModel> = emptyList(),
    val isSearching: Boolean = false,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val presentation: BrowserPresentationPreferences = BrowserPresentationPreferences(
        sortOption = FileSortOption.DATE_NEWEST
    ),
    val todayStart: Long = 0L,
    val yesterdayStart: Long = 0L,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: dev.qtremors.arcile.shared.presentation.PropertiesUiModel? = null
)

@HiltViewModel
class RecentFilesViewModel @Inject constructor(
    private val volumeRepository: VolumeRepository,
    private val storageAnalyticsRepository: StorageAnalyticsRepository,
    private val fileBrowserRepository: FileBrowserRepository,
    private val searchRepository: SearchRepository,
    private val browserPreferencesRepository: BrowserPreferencesStore,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(RecentFilesState())
    val state: StateFlow<RecentFilesState> = _state.asStateFlow()

    private val _nativeRequestFlow = MutableSharedFlow<android.content.IntentSender>()
    val nativeRequestFlow: SharedFlow<android.content.IntentSender> = _nativeRequestFlow.asSharedFlow()

    private var searchJob: Job? = null

    private val deleteFlowDelegate = DeleteFlowDelegate(
        coroutineScope = viewModelScope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = object : DeleteStateCallbacks {
            override fun getSelectedFiles(): List<String> = _state.value.selectedFiles.toList()
            override fun isPermanentDeleteChecked(): Boolean = _state.value.isPermanentDeleteChecked
            override fun isPermanentDeleteToggleEnabled(): Boolean = _state.value.isPermanentDeleteToggleEnabled
            override fun setLoading(isLoading: Boolean) {
                _state.update { it.copy(isLoading = isLoading) }
            }
            override fun showMixedDeleteExplanation() {
                _state.update { it.copy(showMixedDeleteExplanation = true) }
            }
            override fun showPermanentDeleteConfirmation() {
                _state.update {
                    it.copy(
                        showPermanentDeleteConfirmation = true,
                        isPermanentDeleteChecked = true,
                        isPermanentDeleteToggleEnabled = false
                    )
                }
            }
            override fun showTrashConfirmation() {
                _state.update {
                    it.copy(
                        showTrashConfirmation = true,
                        isPermanentDeleteChecked = false,
                        isPermanentDeleteToggleEnabled = true
                    )
                }
            }
            override fun togglePermanentDeleteChecked() {
                _state.update { it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked) }
            }
            override fun isShredChecked(): Boolean = _state.value.isShredChecked
            override fun toggleShredChecked() {
                _state.update { it.copy(isShredChecked = !it.isShredChecked) }
            }
            override fun dismissDeleteConfirmation() {
                _state.update {
                    it.copy(
                        showTrashConfirmation = false,
                        showPermanentDeleteConfirmation = false,
                        showMixedDeleteExplanation = false,
                        deleteDecision = null,
                        isShredChecked = false
                    )
                }
            }
            override fun setError(error: String) {
                _state.update { it.copy(error = UiText.Dynamic(error)) }
            }
            override fun setError(error: UiText) {
                _state.update { it.copy(error = error) }
            }
            override fun setDeleteDecision(decision: DeleteDecision) {
                _state.update { it.copy(deleteDecision = decision) }
            }
            override fun setPendingNativeAction() {
                _state.update { it.copy(pendingNativeAction = RecentNativeAction.TRASH) }
            }
            override fun clearSelection() {
                _state.update { it.copy(selectedFiles = emptySet()) }
            }
        },
        startBulkDeleteOperation = { type, selected ->
            bulkFileOperationCoordinator.startOperation(
                type = type,
                sourcePaths = selected,
                destinationPath = null,
                resolutions = emptyMap()
            )
        },
        emitNativeRequest = { sender -> _nativeRequestFlow.emit(sender) },
        onSuccess = { loadRecentFiles(false) },
        onFailure = { loadRecentFiles(false) }
    )

    init {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val tStart = cal.timeInMillis

        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val yStart = cal.timeInMillis

        val volumeId: String? = savedStateHandle.get<String>("volumeId")
        _state.update { it.copy(currentVolumeId = volumeId?.takeIf { value -> value.isNotBlank() }, todayStart = tStart, yesterdayStart = yStart) }
        viewModelScope.launch {
            browserPreferencesRepository.preferencesFlow.collectLatest { prefs ->
                _state.update {
                    val presentation = prefs.recentPresentation
                    it.copy(
                        presentation = presentation,
                        displayedRecentFiles = it.copy(presentation = presentation).displayRecentFiles(),
                        searchResults = it.copy(presentation = presentation).displaySearchResults()
                    )
                }
            }
        }
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            volumeRepository.observeStorageVolumes()
                .debounce(1000L)
                .distinctUntilChanged()
                .collectLatest {
                    loadRecentFiles(false)
                }
        }
    }

    fun loadRecentFiles(pullToRefresh: Boolean = false, loadMore: Boolean = false) {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val newTodayStart = cal.timeInMillis
        cal.add(java.util.Calendar.DAY_OF_YEAR, -1)
        val newYesterdayStart = cal.timeInMillis

        val capturedState = _state.value
        if (loadMore && (capturedState.isLoadingMore || !capturedState.hasMore)) return

        val offset = if (loadMore) capturedState.currentOffset + 50 else 0

        _state.update {
            if (loadMore) {
                it.copy(isLoadingMore = true, error = null, todayStart = newTodayStart, yesterdayStart = newYesterdayStart)
            } else {
                it.copy(
                    isLoading = !pullToRefresh,
                    isPullToRefreshing = pullToRefresh,
                    error = null,
                    currentOffset = 0,
                    hasMore = true,
                    todayStart = newTodayStart,
                    yesterdayStart = newYesterdayStart
                )
            }
        }
        viewModelScope.launch {
            val scope = capturedState.currentVolumeId?.let { StorageScope.Volume(it) } ?: StorageScope.AllStorage
            if (pullToRefresh && !loadMore) {
                storageAnalyticsRepository.invalidateAnalyticsCache()
            }
            val result = storageAnalyticsRepository.getRecentFiles(scope = scope, limit = 50, offset = offset)
            result.onSuccess { files ->
                _state.update {
                    if (loadMore && it.currentOffset != capturedState.currentOffset) return@update it
                    val newFiles = if (loadMore) {
                        (it.recentFiles + files).distinctBy { file -> file.absolutePath }
                    } else {
                        files.distinctBy { file -> file.absolutePath }
                    }
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        isLoadingMore = false,
                        recentFiles = newFiles,
                        displayedRecentFiles = it.displayRecentFiles(newFiles),
                        currentOffset = offset,
                        hasMore = files.size == 50,
                        searchResults = if (it.searchQuery.isNotBlank()) {
                            it.displaySearchResults(newFiles)
                        } else emptyList()
                    )
                }
            }.onFailure { error ->
                _state.update {
                    if (loadMore && it.currentOffset != capturedState.currentOffset) return@update it
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        isLoadingMore = false,
                        error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_recent_files_failed)
                    )
                }
            }
        }
    }

    fun loadMore() {
        loadRecentFiles(loadMore = true)
    }

    fun toggleSelection(path: String) {
        _state.update { currentState ->
            val updatedSelection = if (currentState.selectedFiles.contains(path)) {
                currentState.selectedFiles - path
            } else {
                currentState.selectedFiles + path
            }
            val totalSize = currentState.recentFiles
                .filter { updatedSelection.contains(it.absolutePath) }
                .sumOf { it.size }
            currentState.copy(
                selectedFiles = updatedSelection,
                selectedFilesTotalSize = totalSize,
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun clearSelection() {
        _state.update {
            it.copy(
                selectedFiles = emptySet(),
                selectedFilesTotalSize = 0L,
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun requestDeleteSelected() = deleteFlowDelegate.requestDeleteSelected()
    fun togglePermanentDelete() = deleteFlowDelegate.togglePermanentDelete()
    fun toggleShred() = deleteFlowDelegate.toggleShred()
    fun confirmDeleteSelected() = deleteFlowDelegate.confirmDeleteSelected()
    fun dismissDeleteConfirmation() = deleteFlowDelegate.dismissDeleteConfirmation()
    fun moveSelectedToTrash() = deleteFlowDelegate.moveSelectedToTrash()
    fun deleteSelectedPermanently() = deleteFlowDelegate.deleteSelectedPermanently()

    fun selectAll() {
        _state.update { currentState ->
            val allPaths = if (currentState.searchQuery.isNotBlank()) {
                currentState.searchResults.map { it.absolutePath }
            } else {
                currentState.displayedRecentFiles.map { it.absolutePath }
            }
            val allPathsSet = allPaths.toSet()
            val totalSize = currentState.recentFiles
                .filter { allPathsSet.contains(it.absolutePath) }
                .sumOf { it.size }
            currentState.copy(
                selectedFiles = allPathsSet,
                selectedFilesTotalSize = totalSize
            )
        }
    }

    fun selectMultiple(paths: List<String>) {
        _state.update { currentState ->
            val updatedSelection = currentState.selectedFiles + paths
            val totalSize = currentState.recentFiles
                .filter { updatedSelection.contains(it.absolutePath) }
                .sumOf { it.size }
            currentState.copy(
                selectedFiles = updatedSelection,
                selectedFilesTotalSize = totalSize
            )
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query) }
        debouncedSearch(query)
    }

    fun updateSearchFilters(filters: SearchFilters) {
        _state.update {
            it.copy(
                activeSearchFilters = filters,
                searchResults = it.copy(activeSearchFilters = filters).displaySearchResults()
            )
        }
        val currentQuery = _state.value.searchQuery
        if (currentQuery.isNotBlank()) {
            debouncedSearch(currentQuery)
        }
    }

    fun updatePresentation(preferences: BrowserPresentationPreferences) {
        val normalized = preferences.normalized()
        _state.update {
            it.copy(
                presentation = normalized,
                displayedRecentFiles = it.copy(presentation = normalized).displayRecentFiles(),
                searchResults = it.copy(presentation = normalized).displaySearchResults()
            )
        }
        viewModelScope.launch {
            browserPreferencesRepository.updateRecentPresentation(normalized)
        }
    }

    fun openPropertiesForSelection() {
        val selectedPaths = _state.value.selectedFiles.toList()
        if (selectedPaths.isEmpty()) return

        _state.update {
            it.copy(
                isPropertiesVisible = true,
                isPropertiesLoading = true,
                properties = null
            )
        }

        viewModelScope.launch {
            fileBrowserRepository.getSelectionProperties(selectedPaths).onSuccess { properties ->
                _state.update {
                    it.copy(
                        isPropertiesVisible = true,
                        isPropertiesLoading = false,
                        properties = properties.toUiModel()
                    )
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isPropertiesVisible = false,
                        isPropertiesLoading = false,
                        properties = null,
                        error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_properties_failed)
                    )
                }
            }
        }
    }

    fun dismissProperties() {
        _state.update {
            it.copy(
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
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
            val stateValue = _state.value
            searchRepository.searchFiles(query, stateValue.searchScope(), stateValue.activeSearchFilters)
                .onSuccess { files ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            searchResults = buildRecentFilesDisplay(
                                files = files,
                                query = "",
                                filters = SearchFilters(),
                                presentation = it.presentation
                            )
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isSearching = false,
                            error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_search_failed)
                        )
                    }
                }
        }
    }
}

private fun RecentFilesState.displayRecentFiles(
    source: List<FileModel> = recentFiles
): List<FileModel> = buildRecentFilesDisplay(
    files = source,
    query = "",
    filters = SearchFilters(),
    presentation = presentation
)

private fun RecentFilesState.displaySearchResults(
    source: List<FileModel> = recentFiles
): List<FileModel> = if (searchQuery.isBlank() || searchResults.isNotEmpty()) {
    if (searchQuery.isBlank()) emptyList() else buildRecentFilesDisplay(
        files = searchResults,
        query = "",
        filters = SearchFilters(),
        presentation = presentation
    )
} else {
    emptyList()
}

private fun RecentFilesState.searchScope(): StorageScope =
    currentVolumeId?.let { StorageScope.Volume(it) } ?: StorageScope.AllStorage
