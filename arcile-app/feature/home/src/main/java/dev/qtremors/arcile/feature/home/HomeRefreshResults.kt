package dev.qtremors.arcile.feature.home

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageInfo
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.isIndexed
import dev.qtremors.arcile.core.ui.R
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.withTimeoutOrNull

internal data class HomeRefreshResults(
    val timedOut: Boolean,
    val shouldRefreshAnalytics: Boolean,
    val cacheInvalidationError: Throwable?,
    val recentFiles: Result<List<FileModel>>?,
    val volumes: Result<List<StorageVolume>>?,
    val storageInfo: Result<StorageInfo>?,
    val categories: Result<List<CategoryStorage>>?,
    val trashUsage: Result<TrashStorageUsage>?
) {
    fun resolveVolumes(current: HomeState): List<StorageVolume> =
        volumes?.getOrNull() ?: current.allStorageVolumes

    fun resolveStorageInfo(current: HomeState): StorageInfo? =
        if (shouldRefreshAnalytics && !timedOut) {
            storageInfo?.getOrNull()
        } else {
            current.storageInfo
        }

    fun errorText(): UiText? {
        if (timedOut) return UiText.StringResource(R.string.error_home_data_timeout)
        return listOfNotNull(
            cacheInvalidationError?.message,
            storageInfo?.exceptionOrNull()?.message,
            volumes?.exceptionOrNull()?.message,
            recentFiles?.exceptionOrNull()?.message,
            categories?.exceptionOrNull()?.message,
            trashUsage?.exceptionOrNull()?.message
        ).firstOrNull()?.let(UiText::Dynamic)
    }
}

internal suspend fun awaitHomeRefreshResults(
    shouldRefreshAnalytics: Boolean,
    cacheInvalidationError: Throwable?,
    recentFiles: Deferred<Result<List<FileModel>>>,
    volumes: Deferred<Result<List<StorageVolume>>>,
    storageInfo: Deferred<Result<StorageInfo>>?,
    categories: Deferred<Result<List<CategoryStorage>>>?,
    trashUsage: Deferred<Result<TrashStorageUsage>>?
): HomeRefreshResults {
    var recentResult: Result<List<FileModel>>? = null
    var volumeResult: Result<List<StorageVolume>>? = null
    var storageResult: Result<StorageInfo>? = null
    var categoryResult: Result<List<CategoryStorage>>? = null
    var trashResult: Result<TrashStorageUsage>? = null
    val completed = withTimeoutOrNull(15_000) {
        recentResult = recentFiles.await()
        volumeResult = volumes.await()
        storageResult = storageInfo?.await()
        categoryResult = categories?.await()
        trashResult = trashUsage?.await()
    } != null
    if (!completed) {
        recentFiles.cancel()
        volumes.cancel()
        storageInfo?.cancel()
        categories?.cancel()
        trashUsage?.cancel()
    }
    return HomeRefreshResults(
        timedOut = !completed,
        shouldRefreshAnalytics = shouldRefreshAnalytics,
        cacheInvalidationError = cacheInvalidationError,
        recentFiles = recentResult,
        volumes = volumeResult,
        storageInfo = storageResult,
        categories = categoryResult,
        trashUsage = trashResult
    )
}

internal fun HomeState.afterRefresh(
    results: HomeRefreshResults,
    unclassifiedVolumes: List<StorageVolume>
): HomeState {
    val volumes = results.resolveVolumes(this)
    val storageInfo = results.resolveStorageInfo(this)
    val nextCategories = if (results.shouldRefreshAnalytics && !results.timedOut) {
        results.categories?.getOrNull().orEmpty()
    } else {
        categoryStorages
    }.toPersistentList()
    val indexedVolumes = storageInfo?.volumes
        ?.filter { it.kind.isIndexed }
        ?: volumes.filter { it.kind.isIndexed }
    val categoriesByVolume = categoryStoragesByVolume.toMutableMap()
    if (
        !results.timedOut &&
        results.shouldRefreshAnalytics &&
        indexedVolumes.size == 1 &&
        nextCategories.isNotEmpty()
    ) {
        categoriesByVolume[indexedVolumes.first().id] = nextCategories
            .sortedByDescending(CategoryStorage::sizeBytes)
            .toPersistentList()
    }
    return copy(
        isLoading = false,
        isPullToRefreshing = false,
        isCalculatingStorage = false,
        error = results.errorText(),
        allStorageVolumes = volumes.toPersistentList(),
        recentFiles = (results.recentFiles?.getOrNull() ?: recentFiles).toPersistentList(),
        storageInfo = storageInfo,
        categoryStorages = nextCategories,
        categoryStoragesByVolume = categoriesByVolume.toPersistentMap(),
        trashStorageUsage = if (results.shouldRefreshAnalytics && !results.timedOut) {
            results.trashUsage?.getOrNull() ?: trashStorageUsage
        } else {
            trashStorageUsage
        },
        unclassifiedVolumes = unclassifiedVolumes.toPersistentList(),
        showClassificationPrompt = unclassifiedVolumes.isNotEmpty()
    ).withUpdatedDisplayState()
}
