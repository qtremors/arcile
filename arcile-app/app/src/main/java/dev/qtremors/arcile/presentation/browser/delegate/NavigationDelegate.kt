package dev.qtremors.arcile.presentation.browser.delegate

import androidx.lifecycle.SavedStateHandle
import androidx.navigation.toRoute
import dev.qtremors.arcile.data.BrowserPreferencesStore
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageBrowserLocation
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.browser.BrowserState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
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
        val explorer = savedStateHandle.toRoute<AppRoutes.Explorer>()

        when {
            !explorer.path.isNullOrEmpty() -> navigateToSpecificFolder(explorer.path)
            !explorer.category.isNullOrEmpty() -> navigateToCategory(explorer.category, explorer.volumeId)
            else -> openFileBrowser()
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

    fun openFileBrowser(errorMessage: String? = null) {
        val volumes = state.value.storageVolumes
        if (volumes.size <= 1) {
            val onlyVolume = volumes.firstOrNull()
            if (onlyVolume != null) {
                loadDirectory(onlyVolume.path, onlyVolume.id, clearHistory = true, errorMessage = errorMessage)
                return
            }
        }
        openVolumeRoots(errorMessage)
    }

    fun openVolumeRoots(errorMessage: String? = null) {
        pathHistory.clear()
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            val sortOption = prefs.getSortOptionForPath("/")
            state.update {
                it.copy(
                    currentPath = "",
                    currentVolumeId = null,
                    isVolumeRootScreen = true,
                    isCategoryScreen = false,
                    activeCategoryName = "",
                    files = volumeFiles(),
                    selectedFiles = emptySet(),
                    error = errorMessage,
                    browserSortOption = sortOption,
                    isLoading = false,
                    isPullToRefreshing = false
                )
            }
            saveNavState()
        }
    }

    fun navigateToSpecificFolder(path: String) {
        val volume = findVolumeForPath(path)
        if (volume == null) {
            openFileBrowser("Storage for this path is not available")
            return
        }
        pathHistory.clear()
        if (path != volume.path) {
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
        errorMessage: String? = null
    ) {
        val resolvedVolumeId = volumeId ?: findVolumeForPath(path)?.id
        if (resolvedVolumeId == null) {
            openFileBrowser("Storage for this path is not available")
            return
        }
        if (clearHistory) {
            pathHistory.clear()
        }
        state.update {
            it.copy(
                isLoading = true,
                error = errorMessage,
                currentPath = path,
                currentVolumeId = resolvedVolumeId,
                selectedFiles = emptySet(),
                isCategoryScreen = false,
                isVolumeRootScreen = false
            )
        }
        saveNavState()
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            val sortOptionForPath = prefs.getSortOptionForPath(path)
            state.update { it.copy(browserSortOption = sortOptionForPath) }

            repository.listFiles(path).onSuccess { files ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        files = files
                    )
                }
                saveNavState()
            }.onFailure { error ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        error = error.message ?: "Failed to load directory"
                    )
                }
            }
        }
    }

    private fun loadCategory(categoryName: String, volumeId: String?) {
        state.update {
            it.copy(
                isLoading = true,
                error = null,
                isCategoryScreen = true,
                isVolumeRootScreen = false,
                activeCategoryName = categoryName,
                currentVolumeId = volumeId,
                selectedFiles = emptySet()
            )
        }
        saveNavState()
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            val sortOptionForCategory = prefs.getSortOptionForCategory(categoryName)

            val scope = StorageScope.Category(volumeId?.takeIf { it.isNotEmpty() }, categoryName)
            repository.getFilesByCategory(scope, categoryName).onSuccess { files ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        files = files,
                        browserSortOption = sortOptionForCategory
                    )
                }
                saveNavState()
            }.onFailure { error ->
                state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        error = error.message ?: "Failed to load category"
                    )
                }
            }
        }
    }
}
