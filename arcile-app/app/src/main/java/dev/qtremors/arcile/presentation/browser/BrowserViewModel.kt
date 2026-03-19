package dev.qtremors.arcile.presentation.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.data.BrowserPreferencesRepository
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageBrowserLocation
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.supportsTrash
import dev.qtremors.arcile.presentation.ClipboardOperation
import dev.qtremors.arcile.presentation.ClipboardState
import dev.qtremors.arcile.presentation.FileSortOption
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import javax.inject.Inject
import androidx.navigation.toRoute
import dev.qtremors.arcile.navigation.AppRoutes

enum class BrowserNativeAction { TRASH }

data class BrowserState(
    val currentPath: String = "",
    val currentVolumeId: String? = null,
    val isVolumeRootScreen: Boolean = false,
    val isCategoryScreen: Boolean = false,
    val activeCategoryName: String = "",
    val files: List<FileModel> = emptyList(),
    val searchResults: List<FileModel> = emptyList(),
    val isSearching: Boolean = false,
    val browserSearchQuery: String = "",
    val browserSortOption: FileSortOption = FileSortOption.NAME_ASC,
    val isGridView: Boolean = false,
    val selectedFiles: Set<String> = emptySet(),
    val clipboardState: ClipboardState? = null,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearchFilterMenuVisible: Boolean = false,
    val isLoading: Boolean = true,
    val isPullToRefreshing: Boolean = false,
    val error: String? = null,
    val pasteConflicts: List<FileConflict> = emptyList(),
    val showConflictDialog: Boolean = false,
    val storageVolumes: List<dev.qtremors.arcile.domain.StorageVolume> = emptyList(),
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val nativeRequest: android.content.IntentSender? = null,
    val pendingNativeAction: BrowserNativeAction? = null
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: FileRepository,
    private val browserPreferencesRepository: BrowserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val pathHistory = ArrayDeque<String>()
    private var searchJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeStorageVolumes().collectLatest { volumes ->
                _state.update { it.copy(storageVolumes = volumes) }
                val currentVolumeId = _state.value.currentVolumeId
                if (currentVolumeId != null && volumes.none { it.id == currentVolumeId }) {
                    openVolumeRoots("Selected storage was removed")
                } else {
                    when (val location = restoreLocationFromState()) {
                        StorageBrowserLocation.Roots -> openFileBrowser()
                        is StorageBrowserLocation.Directory -> {
                            _state.update {
                                it.copy(
                                    currentPath = location.pathScope.absolutePath,
                                    currentVolumeId = location.pathScope.volumeId,
                                    isVolumeRootScreen = false,
                                    isCategoryScreen = false,
                                    activeCategoryName = ""
                                )
                            }
                            refresh()
                        }
                        is StorageBrowserLocation.Category -> {
                            _state.update {
                                it.copy(
                                    currentPath = "",
                                    currentVolumeId = location.categoryScope.volumeId,
                                    isVolumeRootScreen = false,
                                    isCategoryScreen = true,
                                    activeCategoryName = location.categoryScope.categoryName
                                )
                            }
                            refresh()
                        }
                        null -> initializeFromArgs()
                    }
                }
            }
        }
    }
    private fun initializeFromArgs() {
        val explorer = savedStateHandle.toRoute<AppRoutes.Explorer>()

        when {
            !explorer.path.isNullOrEmpty() -> navigateToSpecificFolder(explorer.path)
            !explorer.category.isNullOrEmpty() -> navigateToCategory(explorer.category, explorer.volumeId)
            else -> openFileBrowser()
        }
    }

    private fun restoreLocationFromState(): StorageBrowserLocation? {
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

    private fun saveNavState() {
        savedStateHandle["currentPath"] = _state.value.currentPath
        savedStateHandle["currentVolumeId"] = _state.value.currentVolumeId
        savedStateHandle["isVolumeRootScreen"] = _state.value.isVolumeRootScreen
        savedStateHandle["isCategoryScreen"] = _state.value.isCategoryScreen
        savedStateHandle["activeCategoryName"] = _state.value.activeCategoryName
        savedStateHandle["pathHistory"] = pathHistory.toTypedArray()
    }

    private fun volumeFiles() = _state.value.storageVolumes.map { volume ->
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
        _state.value.storageVolumes
            .sortedByDescending { it.path.length }
            .firstOrNull { path == it.path || path.startsWith(it.path + java.io.File.separator) }

    fun openFileBrowser(errorMessage: String? = null) {
        val volumes = _state.value.storageVolumes
        if (volumes.size <= 1) {
            val onlyVolume = volumes.firstOrNull()
            if (onlyVolume != null) {
                loadDirectory(onlyVolume.path, onlyVolume.id, clearHistory = true, errorMessage = errorMessage)
                return
            }
        }
        openVolumeRoots(errorMessage)
    }

    private fun openVolumeRoots(errorMessage: String? = null) {
        pathHistory.clear()
        _state.update {
            it.copy(
                currentPath = "",
                currentVolumeId = null,
                isVolumeRootScreen = true,
                isCategoryScreen = false,
                activeCategoryName = "",
                files = volumeFiles(),
                selectedFiles = emptySet(),
                error = errorMessage,
                browserSortOption = FileSortOption.NAME_ASC,
                isLoading = false,
                isPullToRefreshing = false
            )
        }
        saveNavState()
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
        if (_state.value.isVolumeRootScreen) {
            val volume = _state.value.storageVolumes.firstOrNull { it.path == path }
            if (volume != null) {
                loadDirectory(volume.path, volume.id, clearHistory = true)
            }
            return
        }

        if (_state.value.currentPath.isNotEmpty() && _state.value.currentPath != path) {
            pathHistory.push(_state.value.currentPath)
        }
        loadDirectory(path, _state.value.currentVolumeId, clearHistory = false)
    }

    fun navigateBack(): Boolean {
        if (_state.value.browserSearchQuery.isNotEmpty()) {
            updateBrowserSearchQuery("")
            return true
        }

        if (_state.value.isCategoryScreen) {
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

        if (!_state.value.isVolumeRootScreen && _state.value.storageVolumes.size > 1) {
            openVolumeRoots()
            return true
        }

        return false
    }

    fun refresh(pullToRefresh: Boolean = false) {
        _state.update { it.copy(isPullToRefreshing = pullToRefresh) }
        when {
            _state.value.isVolumeRootScreen -> openVolumeRoots()
            _state.value.isCategoryScreen -> loadCategory(_state.value.activeCategoryName, _state.value.currentVolumeId)
            _state.value.currentPath.isNotEmpty() -> loadDirectory(_state.value.currentPath, _state.value.currentVolumeId, clearHistory = false)
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
        saveNavState()
        _state.update {
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
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            val sortOptionForPath = prefs.getSortOptionForPath(path)
            _state.update { it.copy(browserSortOption = sortOptionForPath) }

            repository.listFiles(path).onSuccess { files ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        files = files
                    )
                }
                saveNavState()
            }.onFailure { error ->
                _state.update {
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
        saveNavState()
        _state.update {
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
        viewModelScope.launch {
            val scope = StorageScope.Category(volumeId?.takeIf { it.isNotEmpty() }, categoryName)
            repository.getFilesByCategory(scope, categoryName).onSuccess { files ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        files = files,
                        browserSortOption = FileSortOption.DATE_NEWEST
                    )
                }
                saveNavState()
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isLoading = false,
                        isPullToRefreshing = false,
                        error = error.message ?: "Failed to load category"
                    )
                }
            }
        }
    }

    fun toggleSelection(path: String) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            val updatedSelection = if (currentState.selectedFiles.contains(path)) {
                currentState.selectedFiles - path
            } else {
                currentState.selectedFiles + path
            }
            currentState.copy(selectedFiles = updatedSelection)
        }
    }

    fun selectMultiple(paths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            currentState.copy(selectedFiles = currentState.selectedFiles + paths)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet()) }
    }

    fun updateBrowserSearchQuery(query: String) {
        _state.update { it.copy(browserSearchQuery = query) }
        debouncedSearch(query)
    }

    private fun debouncedSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(400)
            _state.update { it.copy(isSearching = true, error = null) }

            val stateVal = _state.value
            val scope = when {
                stateVal.isVolumeRootScreen -> StorageScope.AllStorage
                stateVal.isCategoryScreen -> stateVal.currentVolumeId?.let {
                    StorageScope.Category(it, stateVal.activeCategoryName)
                } ?: StorageScope.AllStorage
                stateVal.currentVolumeId != null && stateVal.currentPath.isNotEmpty() ->
                    StorageScope.Path(stateVal.currentVolumeId, stateVal.currentPath)
                else -> StorageScope.AllStorage
            }

            repository.searchFiles(query, scope, stateVal.activeSearchFilters).onSuccess { files ->
                val filtered = if (stateVal.isCategoryScreen) {
                    val category = dev.qtremors.arcile.domain.FileCategories.all.find { it.name == stateVal.activeCategoryName }
                    if (category != null) {
                        files.filter { file -> category.extensions.contains(file.extension.lowercase()) }
                    } else {
                        files
                    }
                } else {
                    files
                }
                _state.update { it.copy(isSearching = false, searchResults = filtered) }
            }.onFailure { error ->
                _state.update { it.copy(isSearching = false, error = error.message ?: "Search failed") }
            }
        }
    }

    fun updateBrowserSortOption(sortOption: FileSortOption, applyToSubfolders: Boolean) {
        if (_state.value.isVolumeRootScreen) return
        viewModelScope.launch {
            if (applyToSubfolders) {
                val path = _state.value.currentPath
                if (path.isNotEmpty() && !_state.value.isCategoryScreen) {
                    browserPreferencesRepository.updatePathSortOption(path, sortOption)
                }
            } else {
                browserPreferencesRepository.updateGlobalSortOption(sortOption)
            }
            _state.update { it.copy(browserSortOption = sortOption) }
        }
    }

    fun setGridView(enabled: Boolean) {
        _state.update { it.copy(isGridView = enabled) }
    }

    fun updateSearchFilters(filters: SearchFilters) {
        _state.update { it.copy(activeSearchFilters = filters) }
        val currentQuery = _state.value.browserSearchQuery
        if (currentQuery.isNotBlank()) {
            debouncedSearch(currentQuery)
        }
    }

    fun toggleSearchFilterMenu(visible: Boolean) {
        _state.update { it.copy(isSearchFilterMenuVisible = visible) }
    }

    fun createFolder(name: String) {
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty() || _state.value.isVolumeRootScreen) return

        val invalidChars = listOf('/', '\\', '\u0000')
        if (name.isBlank() || invalidChars.any { name.contains(it) } || name.contains("..")) {
            _state.update { it.copy(error = "Invalid folder name: must not be blank or contain /, \\, or ..") }
            return
        }

        viewModelScope.launch {
            repository.createDirectory(currentPath, name).onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to create folder") }
            }
        }
    }

    fun createFile(name: String) {
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty() || _state.value.isVolumeRootScreen) return

        val invalidChars = listOf('/', '\\', '\u0000')
        if (name.isBlank() || invalidChars.any { name.contains(it) } || name.contains("..")) {
            _state.update { it.copy(error = "Invalid file name: must not be blank or contain /, \\, or ..") }
            return
        }

        viewModelScope.launch {
            repository.createFile(currentPath, name).onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to create file") }
            }
        }
    }

    fun requestDeleteSelected() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            
            val policyResult = dev.qtremors.arcile.domain.evaluateDeletePolicy(selectedFiles, repository)

            when (policyResult) {
                is dev.qtremors.arcile.domain.DeletePolicyResult.MixedSelection -> {
                    _state.update { it.copy(isLoading = false, showMixedDeleteExplanation = true) }
                }
                is dev.qtremors.arcile.domain.DeletePolicyResult.PermanentDelete -> {
                    _state.update { it.copy(isLoading = false, showPermanentDeleteConfirmation = true) }
                }
                is dev.qtremors.arcile.domain.DeletePolicyResult.Trash -> {
                    _state.update { it.copy(isLoading = false, showTrashConfirmation = true) }
                }
            }
        }
    }

    fun dismissDeleteConfirmation() {
        _state.update { it.copy(showTrashConfirmation = false, showPermanentDeleteConfirmation = false, showMixedDeleteExplanation = false) }
    }

    fun moveSelectedToTrash() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showTrashConfirmation = false) }
            repository.moveToTrash(selectedFiles).onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                if (error is dev.qtremors.arcile.domain.NativeConfirmationRequiredException) {
                    _state.update { it.copy(isLoading = false, nativeRequest = error.intentSender, pendingNativeAction = BrowserNativeAction.TRASH) }
                } else {
                    _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to move files to Trash") }
                }
            }
        }
    }

    fun clearNativeRequest() {
        _state.update { it.copy(nativeRequest = null) }
    }

    fun deleteSelectedPermanently() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, showPermanentDeleteConfirmation = false) }
            repository.deletePermanently(selectedFiles).onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to delete files") }
            }
        }
    }

    fun renameFile(path: String, newName: String) {
        val invalidChars = listOf('/', '\\', '\u0000')
        if (newName.isBlank() || invalidChars.any { newName.contains(it) } || newName.contains("..")) {
            _state.update { it.copy(error = "Invalid name: must not be blank or contain /, \\, or ..") }
            return
        }

        viewModelScope.launch {
            repository.renameFile(path, newName).onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to rename file") }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun copySelectedToClipboard() {
        val selected = _state.value.selectedFiles.toList()
        if (selected.isNotEmpty()) {
            _state.update {
                it.copy(
                    clipboardState = ClipboardState(ClipboardOperation.COPY, selected),
                    selectedFiles = emptySet()
                )
            }
        }
    }

    fun cutSelectedToClipboard() {
        val selected = _state.value.selectedFiles.toList()
        if (selected.isNotEmpty()) {
            _state.update {
                it.copy(
                    clipboardState = ClipboardState(ClipboardOperation.CUT, selected),
                    selectedFiles = emptySet()
                )
            }
        }
    }

    fun cancelClipboard() {
        _state.update { it.copy(clipboardState = null) }
    }

    fun pasteFromClipboard() {
        val clipboard = _state.value.clipboardState ?: return
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }

            repository.detectCopyConflicts(clipboard.sourcePaths, currentPath).onSuccess { conflicts ->
                if (conflicts.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            isLoading = false,
                            pasteConflicts = conflicts,
                            showConflictDialog = true
                        )
                    }
                } else {
                    executePaste(clipboard, currentPath, emptyMap())
                }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to check for conflicts") }
            }
        }
    }

    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) {
        val clipboard = _state.value.clipboardState ?: return
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(showConflictDialog = false, pasteConflicts = emptyList(), isLoading = true) }
            executePaste(clipboard, currentPath, resolutions)
        }
    }

    fun dismissConflictDialog() {
        _state.update { it.copy(showConflictDialog = false, pasteConflicts = emptyList()) }
    }

    private suspend fun executePaste(
        clipboard: ClipboardState,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>
    ) {
        val result = if (clipboard.operation == ClipboardOperation.COPY) {
            repository.copyFiles(clipboard.sourcePaths, destinationPath, resolutions)
        } else {
            repository.moveFiles(clipboard.sourcePaths, destinationPath, resolutions)
        }

        result.onSuccess {
            _state.update { it.copy(clipboardState = null) }
            refresh()
        }.onFailure { error ->
            _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to paste files") }
        }
    }

}
