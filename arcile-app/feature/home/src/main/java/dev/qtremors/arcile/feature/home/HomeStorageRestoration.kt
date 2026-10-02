package dev.qtremors.arcile.feature.home

import dev.qtremors.arcile.core.storage.domain.StorageAnalyticsRepository
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageScope
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class HomeStorageRestoration(
    private val scope: CoroutineScope,
    private val storageAnalyticsRepository: StorageAnalyticsRepository,
    private val state: MutableStateFlow<HomeState>,
    private val recentsPreviewLimit: Int
) {
    private var rootStorageUsageJob: Job? = null

    suspend fun restoreCachedHomeData(minTimestamp: Long) {
        try {
            val categories = storageAnalyticsRepository.getCachedCategoryStorageSizes(StorageScope.AllStorage)
            // Publish the breakdown first; recent-file decoding must not delay the storage segments.
            if (categories != null) {
                state.update {
                    it.copy(
                        categoryStorages = categories.toPersistentList(),
                        hasRestoredCachedHomeData = true
                    ).withUpdatedDisplayState()
                }
            }
            val recent = storageAnalyticsRepository.getCachedRecentFiles(
                StorageScope.AllStorage, recentsPreviewLimit, minTimestamp
            )
            state.update {
                it.copy(
                    recentFiles = recent?.toPersistentList() ?: it.recentFiles,
                    categoryStorages = categories?.toPersistentList() ?: it.categoryStorages,
                    isLoading = it.isLoading && recent == null && categories == null
                ).withUpdatedDisplayState()
            }
        } catch (error: Exception) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            // A damaged cache must not prevent the live query from repairing it.
        }
        state.update { it.copy(hasRestoredCachedHomeData = true) }
    }

    fun loadRootStorageUsage() {
        val currentState = state.value
        if (
            currentState.hasLoadedRootStorageUsage ||
            currentState.storageInfo?.rootStorageUsage != null ||
            rootStorageUsageJob?.isActive == true
        ) {
            return
        }

        state.update { it.copy(isRootStorageUsageLoading = true) }
        rootStorageUsageJob = scope.launch {
            val result = storageAnalyticsRepository.getStorageInfo(StorageScope.AllStorage)
            state.update { state ->
                val rootUsage = result.getOrNull()?.rootStorageUsage
                val storageInfo = (state.storageInfo ?: StorageInfo(state.allStorageVolumes))
                    .copy(rootStorageUsage = rootUsage)
                state.copy(
                    storageInfo = storageInfo,
                    isRootStorageUsageLoading = false,
                    hasLoadedRootStorageUsage = true
                ).withUpdatedDisplayState()
            }
        }.also { job ->
            job.invokeOnCompletion {
                if (rootStorageUsageJob === job) rootStorageUsageJob = null
            }
        }
    }

}
