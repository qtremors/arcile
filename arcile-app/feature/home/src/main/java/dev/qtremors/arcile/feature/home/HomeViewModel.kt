package dev.qtremors.arcile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.NoOpBulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.DebouncedSearchController
import dev.qtremors.arcile.core.storage.domain.NoOpStorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.NoOpUtilityPreferencesStore
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageClassificationStore
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.UtilityPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.presentation.UiText
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import javax.inject.Inject

@HiltViewModel
internal class HomeViewModel @Inject constructor(
    private val volumeRepository: VolumeRepository,
    private val storageAnalyticsRepository: StorageAnalyticsRepository,
    private val searchRepository: SearchRepository,
    private val classificationRepo: StorageClassificationStore,
    private val quickAccessRepo: QuickAccessPreferencesStore,
    private val utilityPreferencesStore: UtilityPreferencesStore = NoOpUtilityPreferencesStore,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator = NoOpBulkFileOperationCoordinator,
    private val storageMutationNotifier: StorageMutationNotifier = NoOpStorageMutationNotifier
) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val recentsPreviewLimit = 50
    private var refreshJob: Job? = null
    private var pendingSilentRefresh = false
    private var pendingSilentForceAnalytics = false
    private var lastAnalyticsRefreshTime = 0L
    private val searchController = DebouncedSearchController(
        scope = viewModelScope,
        initialFilters = SearchFilters(),
        debounceMillis = 400,
        fallbackError = UiText.StringResource(R.string.error_search_failed)
    ) { query, filters ->
        searchRepository.searchFiles(query, StorageScope.AllStorage, filters)
    }
    private val dashboardController = HomeDashboardController(
        scope = viewModelScope,
        repository = storageAnalyticsRepository,
        currentState = _state::value,
        onUpdate = ::applyDashboardUpdate
    )
    private val classificationController = HomeClassificationController(
        scope = viewModelScope,
        repository = classificationRepo,
        findVolume = { storageKey ->
            _state.value.allStorageVolumes.firstOrNull { it.storageKey == storageKey }
        },
        onUpdate = ::applyClassificationUpdate
    )

    init {
        viewModelScope.launch {
            searchController.state.collectLatest { searchState ->
                _state.update {
                    it.copy(
                        homeSearchQuery = searchState.query,
                        activeSearchFilters = searchState.filters,
                        searchResults = searchState.results.toPersistentList(),
                        isSearching = searchState.isSearching,
                        error = if (searchState.isSearching || searchState.error != null) {
                            searchState.error
                        } else {
                            it.error
                        }
                    ).withUpdatedDisplayState()
                }
            }
        }

        viewModelScope.launch {
            quickAccessRepo.quickAccessItems.collectLatest { items ->
                _state.update { it.copy(quickAccessItems = items.toPersistentList()) }
            }
        }

        viewModelScope.launch {
            utilityPreferencesStore.homeUtilityIds.collectLatest { ids ->
                _state.update { it.copy(homeUtilityIds = ids.toPersistentSet()) }
            }
        }

        viewModelScope.launch {
            bulkFileOperationCoordinator.events.collect { event ->
                val request = (event as? BulkFileOperationEvent.Completed)?.request ?: return@collect
                if (request.type.refreshesHomeAnalytics()) {
                    loadHomeData(HomeRefreshMode.SILENT, forceAnalytics = true)
                }
            }
        }

        viewModelScope.launch {
            storageMutationNotifier.events.collect {
                loadHomeData(HomeRefreshMode.SILENT, forceAnalytics = true)
            }
        }

        viewModelScope.launch {
            volumeRepository.observeStorageVolumes()
                .collectLatest { volumes ->
                    val currentState = _state.value
                    if (currentState.allStorageVolumes != volumes) {
                        _state.update {
                            it.copy(
                                allStorageVolumes = volumes.toPersistentList(),
                                storageInfo = StorageInfo(volumes)
                            ).withUpdatedDisplayState()
                        }
                        loadHomeData(HomeRefreshMode.SILENT, forceAnalytics = true)
                    }
                }
        }

        loadHomeData(HomeRefreshMode.INITIAL)
    }

    fun loadHomeData(refreshMode: HomeRefreshMode = HomeRefreshMode.INITIAL, forceAnalytics: Boolean = false) {
        if (refreshMode == HomeRefreshMode.SILENT && refreshJob?.isActive == true) {
            pendingSilentRefresh = true
            pendingSilentForceAnalytics = pendingSilentForceAnalytics || forceAnalytics
            return
        }
        val effectiveForceAnalytics = forceAnalytics || pendingSilentForceAnalytics
        pendingSilentRefresh = false
        pendingSilentForceAnalytics = false
        if (refreshMode != HomeRefreshMode.SILENT) {
            refreshJob?.cancel()
        }
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        val newTodayStart = cal.timeInMillis

        val hasVisibleContent = _state.value.storageInfo != null ||
            _state.value.categoryStorages.isNotEmpty() ||
            _state.value.recentFiles.isNotEmpty()

        _state.update {
            it.copy(
                isLoading = refreshMode == HomeRefreshMode.INITIAL && !hasVisibleContent,
                isPullToRefreshing = refreshMode == HomeRefreshMode.MANUAL,
                isCalculatingStorage = refreshMode != HomeRefreshMode.SILENT,
                error = null,
                todayStart = newTodayStart
            ).withUpdatedDisplayState()
        }
        val launchedRefresh = viewModelScope.launch {
            val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            val shouldRefreshAnalytics = refreshMode != HomeRefreshMode.SILENT ||
                effectiveForceAnalytics ||
                (System.currentTimeMillis() - lastAnalyticsRefreshTime > 5 * 60 * 1000)
            val forceFreshAnalytics = refreshMode == HomeRefreshMode.MANUAL

            if (shouldRefreshAnalytics) {
                lastAnalyticsRefreshTime = System.currentTimeMillis()
            }

            supervisorScope {
                var cacheInvalidationError: Throwable? = null
                if (forceFreshAnalytics) {
                    try {
                        storageAnalyticsRepository.invalidateAnalyticsCache()
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        cacheInvalidationError = e
                    }
                    _state.update {
                        it.copy(categoryStoragesByVolume = persistentMapOf())
                            .withUpdatedDisplayState()
                    }
                }
                val recentResultDef = async {
                    storageAnalyticsRepository.getRecentFiles(
                        scope = StorageScope.AllStorage,
                        limit = recentsPreviewLimit,
                        minTimestamp = oneWeekAgo
                    ).also { result ->
                        result.onSuccess { files ->
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    recentFiles = files.toPersistentList()
                                ).withUpdatedDisplayState()
                            }
                        }.onFailure { error ->
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    error = error.message?.let(UiText::Dynamic)
                                )
                            }
                        }
                    }
                }
                val allVolumesResultDef = async {
                    volumeRepository.getStorageVolumes().also { result ->
                        result.onSuccess { volumes ->
                            _state.update {
                                it.copy(
                                    isLoading = false,
                                    allStorageVolumes = volumes.toPersistentList(),
                                    storageInfo = it.storageInfo ?: StorageInfo(volumes)
                                ).withUpdatedDisplayState()
                            }
                        }
                    }
                }
                val storageResultDef = if (shouldRefreshAnalytics) async {
                    storageAnalyticsRepository.getStorageInfo(StorageScope.AllStorage).also { result ->
                        result.onSuccess { info ->
                            _state.update {
                                it.copy(storageInfo = info).withUpdatedDisplayState()
                            }
                        }
                    }
                } else null
                val categoryResultDef = if (shouldRefreshAnalytics) async {
                    storageAnalyticsRepository.getCategoryStorageSizes(StorageScope.AllStorage).also { result ->
                        result.onSuccess { categories ->
                            _state.update {
                                it.copy(categoryStorages = categories.toPersistentList())
                                    .withUpdatedDisplayState()
                            }
                        }
                    }
                } else null
                val trashUsageResultDef = if (shouldRefreshAnalytics) async {
                    storageAnalyticsRepository.getTrashStorageUsage().also { result ->
                        result.onSuccess { usage ->
                            _state.update { it.copy(trashStorageUsage = usage) }
                        }
                    }
                } else null

                val results = awaitHomeRefreshResults(
                    shouldRefreshAnalytics = shouldRefreshAnalytics,
                    cacheInvalidationError = cacheInvalidationError,
                    recentFiles = recentResultDef,
                    volumes = allVolumesResultDef,
                    storageInfo = storageResultDef,
                    categories = categoryResultDef,
                    trashUsage = trashUsageResultDef
                )
                val allStorageVolumes = results.resolveVolumes(_state.value)
                val unclassified = classificationController.unsuppressed(
                    allStorageVolumes.filter { it.kind == StorageKind.EXTERNAL_UNCLASSIFIED }
                )
                _state.update { it.afterRefresh(results, unclassified) }

                if (!results.timedOut && allStorageVolumes.size > 1) {
                    loadDashboardCategoryBreakdown()
                }
            }
        }
        refreshJob = launchedRefresh
        launchedRefresh.invokeOnCompletion {
            viewModelScope.launch {
                if (refreshJob !== launchedRefresh) return@launch
                refreshJob = null
                if (pendingSilentRefresh) {
                    val forcePendingAnalytics = pendingSilentForceAnalytics
                    pendingSilentRefresh = false
                    pendingSilentForceAnalytics = false
                    loadHomeData(
                        refreshMode = HomeRefreshMode.SILENT,
                        forceAnalytics = forcePendingAnalytics
                    )
                }
            }
        }
    }

    fun loadDashboardCategoryBreakdown(selectedVolumeId: String? = null) {
        dashboardController.load(selectedVolumeId)
    }

    fun ensureDashboardCategoryBreakdown(selectedVolumeId: String? = null) {
        dashboardController.ensureLoaded(selectedVolumeId)
    }

    fun setVolumeClassification(storageKey: String, kind: StorageKind) {
        classificationController.classify(storageKey, kind)
    }

    fun resetVolumeClassification(storageKey: String) {
        classificationController.reset(storageKey)
    }

    fun hideClassificationPrompt(storageKey: String) {
        classificationController.suppress(storageKey)
    }

    fun updateHomeSearchQuery(query: String) {
        searchController.updateQuery(query)
    }

    fun updateHomeSortOption(sortOption: FileSortOption) {
        _state.update { it.copy(homeSortOption = sortOption).withUpdatedDisplayState() }
    }

    fun updateSearchFilters(filters: SearchFilters) {
        searchController.updateFilters(filters)
    }

    fun toggleSearchFilterMenu(visible: Boolean) {
        _state.update { it.copy(isSearchFilterMenuVisible = visible) }
    }

    private fun applyDashboardUpdate(update: HomeDashboardUpdate) {
        _state.update { current ->
            val merged = current.categoryStoragesByVolume.toMutableMap()
            update.categoriesByVolume.forEach { (volumeId, categories) ->
                merged[volumeId] = categories.toPersistentList()
            }
            current.copy(
                isCalculatingStorage = update.isCalculating ?: current.isCalculatingStorage,
                categoryStoragesByVolume = merged.toPersistentMap()
            ).withUpdatedDisplayState()
        }
    }

    private fun applyClassificationUpdate(update: HomeClassificationUpdate) {
        _state.update { current ->
            val volumes = when (update) {
                is HomeClassificationUpdate.Dismiss ->
                    current.unclassifiedVolumes.filterNot { it.storageKey == update.storageKey }
                is HomeClassificationUpdate.Restore -> {
                    val volume = current.allStorageVolumes.firstOrNull {
                        it.storageKey == update.storageKey
                    }
                    if (volume != null && current.unclassifiedVolumes.none {
                            it.storageKey == update.storageKey
                        }
                    ) {
                        current.unclassifiedVolumes + volume
                    } else {
                        current.unclassifiedVolumes
                    }
                }
            }
            current.copy(
                unclassifiedVolumes = volumes.toPersistentList(),
                showClassificationPrompt = volumes.isNotEmpty(),
                error = (update as? HomeClassificationUpdate.Restore)?.error ?: current.error
            )
        }
    }
}

private fun BulkFileOperationType.refreshesHomeAnalytics(): Boolean =
    when (this) {
        BulkFileOperationType.MOVE,
        BulkFileOperationType.TRASH,
        BulkFileOperationType.DELETE,
        BulkFileOperationType.SHRED,
        BulkFileOperationType.CREATE_FAKE,
        BulkFileOperationType.EXTRACT_ARCHIVE,
        BulkFileOperationType.CREATE_ARCHIVE,
        BulkFileOperationType.SAVE_TO_ARCILE_IMPORT -> true
        BulkFileOperationType.COPY -> false
    }
