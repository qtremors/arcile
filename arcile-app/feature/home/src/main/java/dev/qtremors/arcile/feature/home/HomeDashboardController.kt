package dev.qtremors.arcile.feature.home

import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.isIndexed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

internal data class HomeDashboardUpdate(
    val isCalculating: Boolean? = null,
    val categoriesByVolume: Map<String, List<CategoryStorage>> = emptyMap()
)

internal class HomeDashboardController(
    private val scope: CoroutineScope,
    private val repository: StorageAnalyticsRepository,
    private val currentState: () -> HomeState,
    private val onUpdate: (HomeDashboardUpdate) -> Unit
) {
    private var loadJob: Job? = null

    fun load(selectedVolumeId: String? = null, forceRefresh: Boolean = false) {
        val state = currentState()
        val indexedVolumes = state.storageInfo?.volumes
            ?.filter { it.kind.isIndexed }
            ?: state.allStorageVolumes.filter { it.kind.isIndexed }
        val targetVolumes = indexedVolumes
            .let { volumes ->
                selectedVolumeId
                    ?.let { selected -> volumes.filter { it.id == selected } }
                    ?: volumes
            }
            .filter { forceRefresh || state.categoryStoragesByVolume[it.id] == null }

        if (targetVolumes.isEmpty()) return
        if (indexedVolumes.size == 1 && state.categoryStorages.isNotEmpty()) {
            onUpdate(
                HomeDashboardUpdate(
                    categoriesByVolume = mapOf(
                        indexedVolumes.first().id to state.categoryStorages
                            .sortedByDescending(CategoryStorage::sizeBytes)
                    )
                )
            )
            return
        }

        loadJob?.cancel()
        loadJob = scope.launch {
            targetVolumes.filter { state.categoryStoragesByVolume[it.id] == null }.forEach { volume ->
                repository.getCachedCategoryStorageSizes(StorageScope.Volume(volume.id))?.let { cached ->
                    onUpdate(HomeDashboardUpdate(categoriesByVolume = mapOf(volume.id to cached)))
                }
            }
            onUpdate(HomeDashboardUpdate(isCalculating = true))
            val results = supervisorScope {
                targetVolumes.map { volume ->
                    async {
                        volume.id to repository
                            .getCategoryStorageSizes(StorageScope.Volume(volume.id))
                            .getOrNull()
                    }
                }.map { it.await() }
            }
            onUpdate(
                HomeDashboardUpdate(
                    isCalculating = false,
                    categoriesByVolume = results.mapNotNull { (volumeId, categories) ->
                        categories?.let { volumeId to it.sortedByDescending(CategoryStorage::sizeBytes) }
                    }.toMap()
                )
            )
        }
    }

    fun ensureLoaded(selectedVolumeId: String? = null) {
        val state = currentState()
        val indexedVolumes = state.storageInfo?.volumes
            ?.filter { it.kind.isIndexed }
            ?: state.allStorageVolumes.filter { it.kind.isIndexed }
        val isMissing = selectedVolumeId
            ?.let { selected ->
                indexedVolumes.any {
                    it.id == selected && state.categoryStoragesByVolume[it.id] == null
                }
            }
            ?: indexedVolumes.any { state.categoryStoragesByVolume[it.id] == null }
        if (isMissing) {
            load(selectedVolumeId)
        }
    }
}
