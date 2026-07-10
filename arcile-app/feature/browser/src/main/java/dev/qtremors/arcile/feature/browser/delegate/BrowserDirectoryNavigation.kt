package dev.qtremors.arcile.feature.browser.delegate

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
    persistAsLastOpened: Boolean = true
) {
    val resolvedVolumeId = volumeId ?: findVolumeForPath(path)?.id
    if (resolvedVolumeId == null) {
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
        it.reduce(BrowserNavigationEvent.OpenDirectory(path, resolvedVolumeId)).withValues(
            isLoading = true,
            error = errorMessage
        ).withUpdatedDisplayState()
    }
    saveNavStateIfActive(generation)
    activeLoadJob = viewModelScope.launch {
        if (persistAsLastOpened) {
            browserPreferencesRepository.updateLastOpenedLocation(path, resolvedVolumeId)
        }
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
            val folderPaths = page.files.filter(FileModel::isDirectory).map(FileModel::absolutePath)
            val cachedStats = fileBrowserRepository.getCachedFolderStats(folderPaths)
            if (!isActiveLoad(generation)) return@collect
            val now = System.currentTimeMillis()
            val pathsToQueue = folderPaths.filter { folderPath ->
                val cached = cachedStats[folderPath] ?: return@filter true
                val ttl = if (cached.status == FolderStatsStatus.Unavailable) {
                    FolderStatsCachePolicy.FAILURE_TTL_MS
                } else {
                    FolderStatsCachePolicy.FRESH_TTL_MS
                }
                now - cached.cachedAt > ttl
            }
            update {
                it.withValues(
                    isLoading = false,
                    isPullToRefreshing = if (page.isComplete) false else it.isPullToRefreshing,
                    files = updatedFiles.toPersistentList(),
                    folderStatsByPath = (it.folderStatsByPath + cachedStats).toPersistentMap(),
                    folderStatsLoadingPaths =
                        (it.folderStatsLoadingPaths + pathsToQueue).toPersistentSet()
                ).withUpdatedDisplayState()
            }
            fileBrowserRepository.queueFolderStats(pathsToQueue)
            if (page.isComplete) saveNavStateIfActive(generation)
        }
    }
}
