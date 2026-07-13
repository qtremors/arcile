package dev.qtremors.arcile.feature.storageusage

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.NoOpBulkFileOperationCoordinator
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.NoOpStorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.StorageClassificationStore
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.isIndexed
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

@Immutable
internal data class StorageOverviewState(
    val selectedVolumeId: String? = null,
    val allStorageVolumes: List<StorageVolume> = emptyList(),
    val storageInfo: StorageInfo? = null,
    val categoryStorages: List<CategoryStorage> = emptyList(),
    val categoryStoragesByVolume: Map<String, List<CategoryStorage>> = emptyMap(),
    val trashStorageUsage: TrashStorageUsage = TrashStorageUsage(0L, emptyMap()),
    val isLoading: Boolean = true,
    val isCalculatingStorage: Boolean = false
) {
    val indexedVolumes: List<StorageVolume>
        get() = (storageInfo?.volumes ?: allStorageVolumes).filter { it.kind.isIndexed }

    val sortedCategoryStorages: List<CategoryStorage>
        get() = categoryStorages.sortedByDescending(CategoryStorage::sizeBytes)
}

@HiltViewModel
internal class StorageOverviewViewModel @Inject constructor(
    private val volumeRepository: VolumeRepository,
    private val storageAnalyticsRepository: StorageAnalyticsRepository,
    private val classificationStore: StorageClassificationStore,
    private val operationCoordinator: BulkFileOperationCoordinator = NoOpBulkFileOperationCoordinator,
    private val storageMutationNotifier: StorageMutationNotifier = NoOpStorageMutationNotifier
) : ViewModel() {
    private val _state = MutableStateFlow(StorageOverviewState())
    val state: StateFlow<StorageOverviewState> = _state.asStateFlow()

    private var refreshJob: Job? = null

    init {
        viewModelScope.launch {
            operationCoordinator.events.collect { event ->
                if (event is BulkFileOperationEvent.Completed) refresh()
            }
        }
        viewModelScope.launch {
            storageMutationNotifier.events.collect { refresh() }
        }
    }

    fun load(selectedVolumeId: String?) {
        if (_state.value.selectedVolumeId == selectedVolumeId && refreshJob?.isActive == true) return
        _state.update { it.copy(selectedVolumeId = selectedVolumeId) }
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = it.allStorageVolumes.isEmpty(), isCalculatingStorage = true) }
            supervisorScope {
                val volumesResult = async { volumeRepository.getStorageVolumes() }
                val storageInfoResult = async {
                    storageAnalyticsRepository.getStorageInfo(StorageScope.AllStorage)
                }
                val categoriesResult = async {
                    storageAnalyticsRepository.getCategoryStorageSizes(StorageScope.AllStorage)
                }
                val trashResult = async { storageAnalyticsRepository.getTrashStorageUsage() }

                val volumes = volumesResult.await().getOrElse { emptyList() }
                val storageInfo = storageInfoResult.await().getOrNull()
                val categories = categoriesResult.await().getOrElse { emptyList() }
                val trashUsage = trashResult.await().getOrElse { TrashStorageUsage(0L, emptyMap()) }
                val indexedVolumes = (storageInfo?.volumes ?: volumes).filter { it.kind.isIndexed }
                val categoryBreakdowns = loadCategoryBreakdowns(indexedVolumes, categories)

                _state.update {
                    it.copy(
                        allStorageVolumes = volumes,
                        storageInfo = storageInfo ?: StorageInfo(volumes),
                        categoryStorages = categories,
                        categoryStoragesByVolume = categoryBreakdowns,
                        trashStorageUsage = trashUsage,
                        isLoading = false,
                        isCalculatingStorage = false
                    )
                }
            }
        }
    }

    fun setVolumeClassification(storageKey: String, kind: StorageKind) {
        viewModelScope.launch {
            val volume = _state.value.allStorageVolumes.firstOrNull { it.storageKey == storageKey }
            classificationStore.setClassification(
                storageKey = storageKey,
                kind = kind,
                lastSeenName = volume?.name,
                lastSeenPath = volume?.path
            )
            refresh()
        }
    }

    fun resetVolumeClassification(storageKey: String) {
        viewModelScope.launch {
            classificationStore.resetClassification(storageKey)
            refresh()
        }
    }

    private suspend fun loadCategoryBreakdowns(
        indexedVolumes: List<StorageVolume>,
        allCategories: List<CategoryStorage>
    ): Map<String, List<CategoryStorage>> = supervisorScope {
        if (indexedVolumes.size == 1) {
            return@supervisorScope mapOf(
                indexedVolumes.first().id to allCategories.sortedByDescending(CategoryStorage::sizeBytes)
            )
        }
        indexedVolumes
            .map { volume ->
                async {
                    volume.id to storageAnalyticsRepository
                        .getCategoryStorageSizes(StorageScope.Volume(volume.id))
                        .getOrElse { error ->
                            if (error is CancellationException) throw error
                            emptyList()
                        }
                        .sortedByDescending(CategoryStorage::sizeBytes)
                }
            }
            .map { it.await() }
            .toMap()
    }
}
