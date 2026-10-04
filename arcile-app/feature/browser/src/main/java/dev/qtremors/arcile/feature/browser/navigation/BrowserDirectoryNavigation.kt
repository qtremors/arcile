package dev.qtremors.arcile.feature.browser.navigation

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStatsCachePolicy
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.BrowserNavigationEvent
import dev.qtremors.arcile.feature.browser.reduce
import dev.qtremors.arcile.feature.browser.withUpdatedDisplayState
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal fun BrowserNavigationController.loadDirectory(
    path: String,
    volumeId: String?,
    clearHistory: Boolean,
    errorMessage: UiText? = null,
    persistAsLastOpened: Boolean = true,
    allowDirectPath: Boolean = false,
    isRootStorageScope: Boolean = state.value.isRootStorageScope
) {
    val resolvedVolumeId = if (isRootStorageScope) null else volumeId ?: findVolumeForPath(path)?.id
    val isContinuingDirectPath = state.value.currentVolumeId == null &&
        state.value.currentPath.isDirectLocalPath()
    if (resolvedVolumeId == null && !allowDirectPath && !isContinuingDirectPath) {
        openFileBrowser(errorMessage = UiText.StringResource(R.string.error_storage_for_path_unavailable))
        return
    }
    val preserveCurrentListing = state.value.archiveContext == null &&
        !state.value.isVolumeRootScreen &&
        !state.value.isCategoryScreen &&
        state.value.currentPath == path &&
        state.value.currentVolumeId == resolvedVolumeId
    if (clearHistory) navigationPersistence.clear()
    val generation = nextLoadGeneration()
    if (!preserveCurrentListing) onLocationChanged()
    update {
        it.reduce(BrowserNavigationEvent.OpenDirectory(path, resolvedVolumeId, isRootStorageScope)).withValues(
            isLoading = true,
            error = errorMessage
        ).withUpdatedDisplayState()
    }
    saveNavStateIfActive(generation)
    if (persistAsLastOpened && resolvedVolumeId != null) persistLocation(path, resolvedVolumeId)
    activeLoadJob = viewModelScope.launch {
        val preferences = browserPreferencesRepository.locationPreferencesFlow.first()
        if (!isActiveLoad(generation)) return@launch
        applyPresentation(preferences.getPresentationForPath(path), generation)

        val loadedFiles = mutableListOf<FileModel>()
        fileBrowserRepository.listFilePages(path).collect { page ->
            if (!isActiveLoad(generation)) return@collect
            page.error?.let { error ->
                update {
                    it.withValues(
                        isLoading = false,
                        isPullToRefreshing = false,
                        error = error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_load_directory_failed)
                    )
                }
                return@collect
            }

            if (page.pageIndex == 0) loadedFiles.clear()
            loadedFiles += page.files
            val updatedFiles = if (preserveCurrentListing && !page.isComplete) {
                state.value.files
            } else {
                loadedFiles
            }
            val folderPaths = page.files.filter(FileModel::isDirectory).map(FileModel::reference)
            update {
                it.withValues(
                    isLoading = !page.isComplete,
                    isPullToRefreshing = if (page.isComplete) false else it.isPullToRefreshing,
                    files = updatedFiles.toPersistentList(),
                    folderStatsLoadingPaths = (it.folderStatsLoadingPaths + folderPaths).toPersistentSet()
                ).withUpdatedDisplayState()
            }
            if (folderPaths.isNotEmpty()) launch {
                try {
                    val cachedStats = try {
                        fileBrowserRepository.getCachedFolderStats(folderPaths)
                    } catch (error: Exception) {
                        if (error is kotlinx.coroutines.CancellationException) throw error
                        dev.qtremors.arcile.core.runtime.logging.AppLogger.e("Browser", "Unable to load saved folder sizes", error)
                        emptyMap()
                    }
                    if (!isActiveLoad(generation)) return@launch
                    update { current ->
                        val merged = current.folderStatsByPath.toMutableMap()
                        cachedStats.forEach { (folderPath, cached) ->
                            if ((merged[folderPath]?.cachedAt ?: Long.MIN_VALUE) <= cached.cachedAt) {
                                merged[folderPath] = cached
                            }
                        }
                        val now = System.currentTimeMillis()
                        val fresh = folderPaths.filter { folderPath ->
                            val cached = merged[folderPath] ?: return@filter false
                            val ttl = if (cached.status == FolderStatsStatus.Unavailable)
                                FolderStatsCachePolicy.FAILURE_TTL_MS else FolderStatsCachePolicy.FRESH_TTL_MS
                            now - cached.cachedAt <= ttl
                        }.toSet()
                        current.withValues(
                            folderStatsByPath = merged.toPersistentMap(),
                            folderStatsLoadingPaths = (current.folderStatsLoadingPaths - fresh).toPersistentSet()
                        ).withUpdatedDisplayState(cachedStats.keys)
                    }
                    fileBrowserRepository.queueFolderStats(folderPaths.filter { it in state.value.folderStatsLoadingPaths })
                } catch (error: Exception) {
                    if (error is kotlinx.coroutines.CancellationException) throw error
                    dev.qtremors.arcile.core.runtime.logging.AppLogger.e("Browser", "Unable to refresh folder sizes", error)
                }
            }
            if (page.isComplete) saveNavStateIfActive(generation)
        }
    }
}

private fun String.isDirectLocalPath(): Boolean =
    replace('\\', '/').startsWith('/')
