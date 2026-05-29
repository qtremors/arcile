package dev.qtremors.arcile.feature.browser.delegate

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.FolderStatsCachePolicy
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.feature.browser.BrowserNavigationEvent
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.feature.browser.reduce
import dev.qtremors.arcile.feature.browser.withUpdatedDisplayState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class NavigationDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val viewModelScope: CoroutineScope,
    private val repository: FileRepository,
    private val browserPreferencesRepository: BrowserPreferencesStore,
    private val savedStateHandle: SavedStateHandle,
    private val onClearSearch: () -> Unit
) {
    private val pathHistory = ArrayDeque<String>()

    fun restoreLocationFromState(): StorageBrowserLocation? {
        val isVolumeRootScreen = savedStateHandle.get<Boolean>("isVolumeRootScreen")
        val restoredPath = savedStateHandle.get<String>("currentPath")
        val restoredVolumeId = savedStateHandle.get<String>("currentVolumeId")
        val restoredIsCategory = savedStateHandle.get<Boolean>("isCategoryScreen")
        val restoredCategoryName = savedStateHandle.get<String>("activeCategoryName")
        val restoredHistory = savedStateHandle.get<Array<String>>("pathHistory")

        if (restoredHistory != null) {
            pathHistory.clear()
            pathHistory.addAll(restoredHistory)
        }

        return when {
            isVolumeRootScreen == true -> StorageBrowserLocation.Roots
            restoredIsCategory == true && !restoredCategoryName.isNullOrEmpty() -> {
                StorageBrowserLocation.Category(StorageScope.Category(restoredVolumeId, restoredCategoryName))
            }
            !restoredPath.isNullOrEmpty() && !restoredVolumeId.isNullOrEmpty() ->
                StorageBrowserLocation.Directory(StorageScope.Path(restoredVolumeId, restoredPath))
            else -> null
        }
    }

    fun initializeFromArgs() {
        val path = savedStateHandle.get<String>("path")?.takeIf { it.isNotEmpty() }
        val category = savedStateHandle.get<String>("category")?.takeIf { it.isNotEmpty() }
        val volumeId = savedStateHandle.get<String>("volumeId")
        val seedInitialPathHistory = savedStateHandle.get<Boolean>("seedInitialPathHistory") ?: true
        val restorePersistentLocation = savedStateHandle.get<Boolean>("restorePersistentLocation") ?: true

        when {
            path != null -> navigateToSpecificFolder(
                path,
                seedInitialPathHistory = seedInitialPathHistory
            )
            category != null -> navigateToCategory(category, volumeId)
            else -> openFileBrowser(restorePersistentLocation = restorePersistentLocation)
        }
    }

    private fun saveNavState() {
        savedStateHandle["currentPath"] = state.value.currentPath
        savedStateHandle["currentVolumeId"] = state.value.currentVolumeId
        savedStateHandle["isVolumeRootScreen"] = state.value.isVolumeRootScreen
        savedStateHandle["isCategoryScreen"] = state.value.isCategoryScreen
        savedStateHandle["activeCategoryName"] = state.value.activeCategoryName
        savedStateHandle["pathHistory"] = pathHistory.toTypedArray()
    }

    fun volumeFiles() = state.value.storageVolumes.map { volume ->
        FileModel(
            name = volume.name,
            absolutePath = volume.path,
            size = volume.totalBytes - volume.freeBytes,
            lastModified = 0L,
            isDirectory = true,
            extension = "",
            isHidden = false
        )
    }

    private fun findVolumeForPath(path: String) =
        state.value.storageVolumes
            .sortedByDescending { it.path.length }
            .firstOrNull {
                path == it.path ||
                    path.startsWith(it.path + "/") ||
                    path.startsWith(it.path + java.io.File.separator)
            }

    fun openFileBrowser(restorePersistentLocation: Boolean = false, errorMessage: UiText? = null) {
        viewModelScope.launch {
            if (restorePersistentLocation) {
                val prefs = browserPreferencesRepository.preferencesFlow.first()
                val lastPath = prefs.lastOpenedPath
                val lastVolumeId = prefs.lastOpenedVolumeId

                if (!lastPath.isNullOrEmpty() && !lastVolumeId.isNullOrEmpty()) {
                    val volume = state.value.storageVolumes.firstOrNull { it.id == lastVolumeId }
                    if (volume != null) {
                        loadDirectory(lastPath, lastVolumeId, clearHistory = true, errorMessage = errorMessage)
                        return@launch
                    }
                }
            }

            val volumes = state.value.storageVolumes
            if (volumes.size > 1) {
                openVolumeRoots(errorMessage)
            } else {
                val primaryVolume = volumes.find { it.isPrimary } ?: volumes.firstOrNull()

                if (primaryVolume != null) {
                    loadDirectory(
                        primaryVolume.path,
                        primaryVolume.id,
                        clearHistory = true,
                        errorMessage = errorMessage,
                        persistAsLastOpened = false
                    )
                } else {
                    openVolumeRoots(errorMessage)
                }
            }
        }
    }

    fun openVolumeRoots(errorMessage: UiText? = null) {
        pathHistory.clear()
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            val presentation = prefs.getPresentationForPath("/")
            state.update {
                it.reduce(BrowserNavigationEvent.OpenVolumeRoots(volumeFiles())).copy(
                    error = errorMessage,
                    browserSortOption = presentation.sortOption,
                    browserViewMode = presentation.viewMode,
                    browserListZoom = presentation.listZoom,
                    browserGridMinCellSize = presentation.gridMinCellSize,
                    browserShowThumbnails = presentation.showThumbnails
                ).withUpdatedDisplayState()
            }
            saveNavState()
        }
    }

    fun navigateToSpecificFolder(path: String, seedInitialPathHistory: Boolean = true) {
        val volume = findVolumeForPath(path)
        if (volume == null) {
            openFileBrowser(errorMessage = UiText.StringResource(R.string.error_storage_for_path_unavailable))
            return
        }
        pathHistory.clear()
        if (seedInitialPathHistory && path != volume.path) {
            pathHistory.push(volume.path)
        }
        loadDirectory(path, volume.id, clearHistory = false)
    }

    fun navigateToCategory(categoryName: String, volumeId: String? = null) {
        pathHistory.clear()
        loadCategory(categoryName, volumeId)
    }

    fun navigateToFolder(path: String) {
        if (state.value.isVolumeRootScreen) {
            val volume = state.value.storageVolumes.firstOrNull { it.path == path }
            if (volume != null) {
                loadDirectory(volume.path, volume.id, clearHistory = true)
            }
            return
        }

        if (state.value.currentPath.isNotEmpty() && state.value.currentPath != path) {
            pathHistory.push(state.value.currentPath)
        }
        loadDirectory(path, state.value.currentVolumeId, clearHistory = false)
    }

    fun navigateBack(): Boolean {
        if (state.value.browserSearchQuery.isNotEmpty()) {
            onClearSearch()
            return true
        }

        if (state.value.selectedFiles.isNotEmpty()) {
            state.update {
                it.copy(
                    selectedFiles = persistentSetOf(),
                    selectedFilesTotalSize = 0L,
                    isPropertiesVisible = false,
                    isPropertiesLoading = false,
                    properties = null
                )
            }
            return true
        }

        if (state.value.isCategoryScreen) {
            return false
        }

        if (pathHistory.isNotEmpty()) {
            val previousPath = pathHistory.pop()
            val volume = findVolumeForPath(previousPath)
            if (volume != null) {
                loadDirectory(previousPath, volume.id, clearHistory = false)
                return true
            }
        }

        if (!state.value.isVolumeRootScreen && state.value.storageVolumes.size > 1) {
            openVolumeRoots()
            return true
        }

        return false
    }

    fun refresh(pullToRefresh: Boolean = false) {
        state.update { it.copy(isPullToRefreshing = pullToRefresh) }
        saveNavState()
        when {
            state.value.isVolumeRootScreen -> openVolumeRoots()
            state.value.isCategoryScreen -> loadCategory(state.value.activeCategoryName, state.value.currentVolumeId)
            state.value.currentPath.isNotEmpty() -> loadDirectory(state.value.currentPath, state.value.currentVolumeId, clearHistory = false)
        }
    }

    private fun loadDirectory(
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
        if (clearHistory) {
            pathHistory.clear()
        }
        state.update {
            it.reduce(BrowserNavigationEvent.OpenDirectory(path, resolvedVolumeId)).copy(
                isLoading = true,
                error = errorMessage,
            ).withUpdatedDisplayState()
        }
        saveNavState()
        viewModelScope.launch {
            if (persistAsLastOpened) {
                browserPreferencesRepository.updateLastOpenedLocation(path, resolvedVolumeId)
            }
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            applyPresentation(prefs.getPresentationForPath(path))

            repository.listFilePages(path).collect { page ->
                page.error?.let { error ->
                    state.update {
                        it.copy(
                            isLoading = false,
                            isPullToRefreshing = false,
                            error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_directory_failed)
                        )
                    }
                    return@collect
                }

                val updatedFiles = if (page.pageIndex == 0) {
                    page.files
                } else {
                    state.value.files + page.files
                }
                val folderPaths = page.files.filter { it.isDirectory }.map { it.absolutePath }
                val cachedStats = repository.getCachedFolderStats(folderPaths)
                val now = System.currentTimeMillis()
                val pathsToQueue = folderPaths.filter { folderPath ->
                    val cached = cachedStats[folderPath] ?: return@filter true
                    val ttl = if (cached.status == dev.qtremors.arcile.core.storage.domain.FolderStatsStatus.Unavailable) {
                        FolderStatsCachePolicy.FAILURE_TTL_MS
                    } else {
                        FolderStatsCachePolicy.FRESH_TTL_MS
                    }
                    now - cached.cachedAt > ttl
                }
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = if (page.isComplete) false else it.isPullToRefreshing,
                        files = updatedFiles.toPersistentList(),
                        folderStatsByPath = (it.folderStatsByPath + cachedStats).toPersistentMap(),
                        folderStatsLoadingPaths = (it.folderStatsLoadingPaths + pathsToQueue).toPersistentSet()
                    ).withUpdatedDisplayState()
                }
                repository.queueFolderStats(pathsToQueue)
                if (page.isComplete) saveNavState()
            }
        }
    }

    private fun loadCategory(categoryName: String, volumeId: String?) {
        state.update {
            it.reduce(BrowserNavigationEvent.OpenCategory(categoryName, volumeId)).copy(
                isLoading = true,
                error = null,
            ).withUpdatedDisplayState()
        }
        saveNavState()
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            val categoryPresentation = prefs.getPresentationForCategory(categoryName)

            val scope = StorageScope.Category(volumeId?.takeIf { it.isNotEmpty() }, categoryName)
            repository.getFilesByCategory(scope, categoryName).onSuccess { files ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        files = files.toPersistentList(),
                        browserSortOption = categoryPresentation.sortOption,
                        browserViewMode = categoryPresentation.viewMode,
                        browserListZoom = categoryPresentation.listZoom,
                        browserGridMinCellSize = categoryPresentation.gridMinCellSize,
                        browserShowThumbnails = categoryPresentation.showThumbnails
                    ).withUpdatedDisplayState()
                }
                saveNavState()
            }.onFailure { error ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_category_failed)
                    )
                }
            }
        }
    }

    private fun applyPresentation(presentation: BrowserPresentationPreferences) {
        state.update {
            it.copy(
                browserSortOption = presentation.sortOption,
                browserViewMode = presentation.viewMode,
                browserListZoom = presentation.listZoom,
                browserGridMinCellSize = presentation.gridMinCellSize,
                browserShowThumbnails = presentation.showThumbnails
            ).withUpdatedDisplayState()
        }
    }

}
