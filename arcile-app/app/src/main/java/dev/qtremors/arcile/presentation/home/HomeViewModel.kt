package dev.qtremors.arcile.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.NoOpBulkFileOperationCoordinator
import dev.qtremors.arcile.core.storage.domain.NoOpStorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.NoOpUtilityPreferencesStore
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.QuickAccessPreferencesStore
import dev.qtremors.arcile.core.storage.domain.StorageClassificationStore
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.UtilityPreferencesStore
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.shared.presentation.filterAndSortFiles
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@androidx.compose.runtime.Immutable
data class HomeDisplayState(
    val todayRecentFiles: PersistentList<FileModel> = persistentListOf(),
    val indexedDashboardVolumes: PersistentList<StorageVolume> = persistentListOf(),
    val sortedCategoryStorages: PersistentList<CategoryStorage> = persistentListOf()
)

@androidx.compose.runtime.Immutable
data class HomeState(
    val allStorageVolumes: PersistentList<StorageVolume> = persistentListOf(),
    val quickAccessItems: PersistentList<QuickAccessItem> = persistentListOf(),
    val storageInfo: StorageInfo? = null,
    val categoryStorages: PersistentList<CategoryStorage> = persistentListOf(),
    val categoryStoragesByVolume: PersistentMap<String, PersistentList<CategoryStorage>> = persistentMapOf(),
    val trashStorageUsage: TrashStorageUsage = TrashStorageUsage(0L, emptyMap()),
    val recentFiles: PersistentList<FileModel> = persistentListOf(),
    val searchResults: PersistentList<FileModel> = persistentListOf(),
    val homeSearchQuery: String = "",
    val homeSortOption: FileSortOption = FileSortOption.DATE_NEWEST,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearching: Boolean = false,
    val isSearchFilterMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val isCalculatingStorage: Boolean = false,
    val error: UiText? = null,
    val unclassifiedVolumes: PersistentList<StorageVolume> = persistentListOf(),
    val showClassificationPrompt: Boolean = false,
    val todayStart: Long = 0L,
    val displayState: HomeDisplayState = HomeDisplayState(),
    val homeUtilityIds: PersistentSet<String> = persistentSetOf("trash", "cleaner")
)

fun HomeState.withUpdatedDisplayState(): HomeState {
    val todayFiles = recentFiles.filter { it.lastModified >= todayStart }
    return copy(
        displayState = HomeDisplayState(
            todayRecentFiles = filterAndSortFiles(todayFiles, homeSearchQuery, homeSortOption).toPersistentList(),
            indexedDashboardVolumes = storageInfo?.volumes?.filter { it.kind.isIndexed }.orEmpty().toPersistentList(),
            sortedCategoryStorages = categoryStorages.sortedByDescending { it.sizeBytes }.toPersistentList()
        )
    )
}

enum class HomeRefreshMode {
    INITIAL,
    MANUAL,
    SILENT
}

@HiltViewModel
class HomeViewModel @Inject constructor(
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
    private var searchJob: Job? = null
    private var refreshJob: Job? = null
    private var dashboardBreakdownJob: Job? = null
    private val suppressedVolumeKeys = ConcurrentHashMap.newKeySet<String>()
    private var lastAnalyticsRefreshTime = 0L

    init {
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
    }

    fun loadHomeData(refreshMode: HomeRefreshMode = HomeRefreshMode.INITIAL, forceAnalytics: Boolean = false) {
        refreshJob?.cancel()
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
        refreshJob = viewModelScope.launch {
            val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            val shouldRefreshAnalytics = refreshMode != HomeRefreshMode.SILENT || forceAnalytics || (System.currentTimeMillis() - lastAnalyticsRefreshTime > 5 * 60 * 1000)

            if (shouldRefreshAnalytics) {
                lastAnalyticsRefreshTime = System.currentTimeMillis()
            }

            supervisorScope {
                val recentResultDef = async {
                    storageAnalyticsRepository.getRecentFiles(
                        scope = StorageScope.AllStorage,
                        limit = recentsPreviewLimit,
                        minTimestamp = oneWeekAgo
                    )
                }
                val allVolumesResultDef = async { volumeRepository.getStorageVolumes() }
                val storageResultDef = if (shouldRefreshAnalytics) async { storageAnalyticsRepository.getStorageInfo(StorageScope.AllStorage) } else null
                val categoryResultDef = if (shouldRefreshAnalytics) async { storageAnalyticsRepository.getCategoryStorageSizes(StorageScope.AllStorage) } else null
                val trashUsageResultDef = if (shouldRefreshAnalytics) async { storageAnalyticsRepository.getTrashStorageUsage() } else null

                var recentResult: Result<List<FileModel>>? = null
                var allVolumesResult: Result<List<StorageVolume>>? = null
                var storageResult: Result<StorageInfo>? = null
                var categoryResult: Result<List<CategoryStorage>>? = null
                var trashUsageResult: Result<TrashStorageUsage>? = null

                val completedWithinTimeout = withTimeoutOrNull(15_000) {
                    recentResult = recentResultDef.await()
                    allVolumesResult = allVolumesResultDef.await()
                    storageResult = storageResultDef?.await()
                    categoryResult = categoryResultDef?.await()
                    trashUsageResult = trashUsageResultDef?.await()
                } != null

                val timedOut = !completedWithinTimeout
                val storageInfo = if (shouldRefreshAnalytics && !timedOut) storageResult?.getOrNull() else _state.value.storageInfo
                val allStorageVolumes = allVolumesResult?.getOrNull().orEmpty()
                val unclassified = allStorageVolumes.filter {
                    it.kind == StorageKind.EXTERNAL_UNCLASSIFIED && !suppressedVolumeKeys.contains(it.storageKey)
                }

                val errorMsg = listOfNotNull(
                    storageResult?.exceptionOrNull()?.message,
                    allVolumesResult?.exceptionOrNull()?.message,
                    recentResult?.exceptionOrNull()?.message,
                    categoryResult?.exceptionOrNull()?.message,
                    trashUsageResult?.exceptionOrNull()?.message
                ).firstOrNull()
                val errorText = if (timedOut) {
                    UiText.StringResource(R.string.error_home_data_timeout)
                } else {
                    errorMsg?.let(UiText::Dynamic)
                }

                _state.update { currentState ->
                    val nextState = currentState.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        isCalculatingStorage = false,
                        error = errorText,
                        allStorageVolumes = allStorageVolumes.toPersistentList(),
                        recentFiles = (recentResult?.getOrNull() ?: currentState.recentFiles).toPersistentList(),
                        storageInfo = storageInfo,
                        categoryStorages = (if (shouldRefreshAnalytics && !timedOut) categoryResult?.getOrNull() ?: emptyList() else currentState.categoryStorages).toPersistentList(),
                        trashStorageUsage = if (shouldRefreshAnalytics && !timedOut) trashUsageResult?.getOrNull() ?: currentState.trashStorageUsage else currentState.trashStorageUsage,
                        unclassifiedVolumes = unclassified.toPersistentList(),
                        showClassificationPrompt = unclassified.isNotEmpty()
                    ).withUpdatedDisplayState()
                    nextState
                }

                if (!timedOut && allStorageVolumes.size > 1) {
                    loadDashboardCategoryBreakdown()
                }
            }
        }
    }

    fun loadDashboardCategoryBreakdown(selectedVolumeId: String? = null) {
        val currentState = _state.value
        val indexedVolumes = currentState.storageInfo?.volumes
            ?.filter { it.kind.isIndexed }
            ?: currentState.allStorageVolumes.filter { it.kind.isIndexed }
        val targetVolumes = if (selectedVolumeId != null) {
            indexedVolumes.filter { it.id == selectedVolumeId }
        } else {
            indexedVolumes
        }.filter { currentState.categoryStoragesByVolume[it.id] == null }

        if (targetVolumes.isEmpty()) return

        dashboardBreakdownJob?.cancel()
        dashboardBreakdownJob = viewModelScope.launch {
            _state.update { it.copy(isCalculatingStorage = true) }
            val results = supervisorScope {
                targetVolumes.map { volume ->
                    async {
                        volume.id to storageAnalyticsRepository
                            .getCategoryStorageSizes(StorageScope.Volume(volume.id))
                            .getOrNull()
                            .orEmpty()
                    }
                }.map { it.await() }
            }

            _state.update { current ->
                val merged = current.categoryStoragesByVolume.toMutableMap()
                results.forEach { (volumeId, categories) ->
                    merged[volumeId] = categories
                        .sortedByDescending(CategoryStorage::sizeBytes)
                        .toPersistentList()
                }
                current.copy(
                    isCalculatingStorage = false,
                    categoryStoragesByVolume = merged.toPersistentMap()
                ).withUpdatedDisplayState()
            }
        }
    }

    fun setVolumeClassification(storageKey: String, kind: StorageKind) {
        _state.update { currentState ->
            val remaining = currentState.unclassifiedVolumes.filter { v -> v.storageKey != storageKey }
            currentState.copy(
                unclassifiedVolumes = remaining.toPersistentList(),
                showClassificationPrompt = remaining.isNotEmpty()
            )
        }

        viewModelScope.launch {
            try {
                val volume = _state.value.allStorageVolumes.firstOrNull { it.storageKey == storageKey }
                classificationRepo.setClassification(
                    storageKey = storageKey,
                    kind = kind,
                    lastSeenName = volume?.name,
                    lastSeenPath = volume?.path
                )
                suppressedVolumeKeys.remove(storageKey)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { currentState ->
                    val volume = currentState.allStorageVolumes.firstOrNull { it.storageKey == storageKey }
                    val restoredVolumes = if (volume != null && !currentState.unclassifiedVolumes.any { it.storageKey == storageKey }) {
                        currentState.unclassifiedVolumes + volume
                    } else currentState.unclassifiedVolumes

                    currentState.copy(
                        unclassifiedVolumes = restoredVolumes.toPersistentList(),
                        showClassificationPrompt = restoredVolumes.isNotEmpty(),
                        error = UiText.StringResource(
                            R.string.error_save_classification_failed,
                            listOf(e.message.orEmpty())
                        )
                    )
                }
            }
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
        _state.update {
            it.copy(
                unclassifiedVolumes = remaining.toPersistentList(),
                showClassificationPrompt = remaining.isNotEmpty()
            )
        }
    }

    fun updateHomeSearchQuery(query: String) {
        _state.update { it.copy(homeSearchQuery = query).withUpdatedDisplayState() }
        debouncedSearch(query)
    }

    private fun debouncedSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = persistentListOf(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            kotlinx.coroutines.delay(400)
            _state.update { it.copy(isSearching = true, error = null) }

            val filters = _state.value.activeSearchFilters
            val result = searchRepository.searchFiles(query, StorageScope.AllStorage, filters)

            result.onSuccess { files ->
                _state.update { it.copy(isSearching = false, searchResults = files.toPersistentList()) }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isSearching = false,
                        error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_search_failed)
                    )
                }
            }
        }
    }

    fun updateHomeSortOption(sortOption: FileSortOption) {
        _state.update { it.copy(homeSortOption = sortOption).withUpdatedDisplayState() }
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

private fun BulkFileOperationType.refreshesHomeAnalytics(): Boolean =
    when (this) {
        BulkFileOperationType.MOVE,
        BulkFileOperationType.TRASH,
        BulkFileOperationType.DELETE,
        BulkFileOperationType.SHRED,
        BulkFileOperationType.CREATE_FAKE,
        BulkFileOperationType.EXTRACT_ARCHIVE,
        BulkFileOperationType.CREATE_ARCHIVE -> true
        BulkFileOperationType.COPY -> false
    }
