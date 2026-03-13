package dev.qtremors.arcile.presentation.browser

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.presentation.ClipboardOperation
import dev.qtremors.arcile.presentation.ClipboardState
import dev.qtremors.arcile.presentation.FileSortOption
import dev.qtremors.arcile.data.BrowserPreferencesRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Named

data class BrowserState(
    val currentPath: String = "",
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
    val isLoading: Boolean = false,
    val isPullToRefreshing: Boolean = false,
    val error: String? = null,
    val pasteConflicts: List<FileConflict> = emptyList(),
    val showConflictDialog: Boolean = false
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val repository: FileRepository,
    private val browserPreferencesRepository: BrowserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
    @Named("storageRootPath") val storageRootPath: String
) : ViewModel() {

    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()

    private val pathHistory = ArrayDeque<String>()
    private var searchJob: Job? = null

    init {
        val restoredPath = savedStateHandle.get<String>("currentPath")
        val restoredIsCategory = savedStateHandle.get<Boolean>("isCategoryScreen")
        val restoredCategoryName = savedStateHandle.get<String>("activeCategoryName")
        val restoredHistory = savedStateHandle.get<Array<String>>("pathHistory")

        if (restoredHistory != null) {
            pathHistory.clear()
            pathHistory.addAll(restoredHistory)
        }

        if (restoredIsCategory != null) {
            // Process death recovery
            if (restoredIsCategory && !restoredCategoryName.isNullOrEmpty()) {
                _state.update { it.copy(isCategoryScreen = true, activeCategoryName = restoredCategoryName, browserSortOption = FileSortOption.DATE_NEWEST) }
                loadCategory(restoredCategoryName)
            } else if (!restoredPath.isNullOrEmpty()) {
                _state.update { it.copy(currentPath = restoredPath) }
                loadDirectory(restoredPath)
            } else {
                openFileBrowser()
            }
        } else {
            // First time init: check nav arguments from SavedStateHandle
            val argPath = savedStateHandle.get<String>("path")
            val argCategory = savedStateHandle.get<String>("category")
            
            if (argPath != null) {
                navigateToSpecificFolder(argPath)
            } else if (argCategory != null) {
                navigateToCategory(argCategory)
            } else {
                openFileBrowser()
            }
        }
    }

    private fun saveNavState(currentPath: String?, isCategoryScreen: Boolean, activeCategoryName: String?) {
        savedStateHandle["currentPath"] = currentPath ?: ""
        savedStateHandle["isCategoryScreen"] = isCategoryScreen
        savedStateHandle["activeCategoryName"] = activeCategoryName ?: ""
    }

    private fun savePathHistory() {
        savedStateHandle["pathHistory"] = pathHistory.toTypedArray()
    }

    fun openFileBrowser() {
        _state.update { it.copy(isCategoryScreen = false, selectedFiles = emptySet()) }
        pathHistory.clear()
        savePathHistory()
        loadDirectory(storageRootPath)
    }

    fun navigateToSpecificFolder(path: String) {
        _state.update { it.copy(isCategoryScreen = false, selectedFiles = emptySet()) }
        pathHistory.clear()
        pathHistory.push(storageRootPath)
        savePathHistory()
        loadDirectory(path)
    }

    fun navigateToCategory(categoryName: String) {
        _state.update { it.copy(isCategoryScreen = true, activeCategoryName = categoryName, selectedFiles = emptySet(), browserSortOption = FileSortOption.DATE_NEWEST) }
        pathHistory.clear()
        savePathHistory()
        loadCategory(categoryName)
    }

    fun navigateToFolder(path: String) {
        if (_state.value.currentPath.isNotEmpty() && _state.value.currentPath != path) {
            pathHistory.push(_state.value.currentPath)
            savePathHistory()
        }
        loadDirectory(path)
    }

    fun navigateBack(): Boolean {
        if (_state.value.browserSearchQuery.isNotEmpty()) {
            updateBrowserSearchQuery("")
            return true
        }

        if (pathHistory.isNotEmpty()) {
            val previousPath = pathHistory.pop()
            savePathHistory()
            loadDirectory(previousPath)
            return true
        }
        
        return false // Let the UI completely back out of the browser
    }

    fun refresh(pullToRefresh: Boolean = false) {
        _state.update { it.copy(isPullToRefreshing = pullToRefresh) }
        when {
            _state.value.isCategoryScreen -> loadCategory(_state.value.activeCategoryName)
            _state.value.currentPath.isNotEmpty() -> loadDirectory(_state.value.currentPath)
        }
    }

    private fun loadDirectory(path: String) {
        saveNavState(path, false, null)
        viewModelScope.launch {
            val prefs = browserPreferencesRepository.preferencesFlow.first()
            val sortOptionForPath = prefs.getSortOptionForPath(path)

            _state.update { it.copy(isLoading = true, error = null, currentPath = path, selectedFiles = emptySet(), isCategoryScreen = false, browserSortOption = sortOptionForPath) }

            val result = repository.listFiles(path)

            result.onSuccess { files ->
                _state.update { it.copy(isLoading = false, isPullToRefreshing = false, files = files) }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, isPullToRefreshing = false, error = error.message ?: "Failed to load directory") }
                if (pathHistory.isNotEmpty()) {
                    pathHistory.pop()
                }
            }
        }
    }

    private fun loadCategory(categoryName: String) {
        saveNavState(null, true, categoryName)
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, isCategoryScreen = true, activeCategoryName = categoryName, selectedFiles = emptySet()) }

            val result = repository.getFilesByCategory(categoryName)

            result.onSuccess { files ->
                _state.update { it.copy(isLoading = false, isPullToRefreshing = false, files = files) }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, isPullToRefreshing = false, error = error.message ?: "Failed to load category") }
                if (pathHistory.isNotEmpty()) {
                    pathHistory.pop()
                }
            }
        }
    }

    fun toggleSelection(path: String) {
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
            val pathScope = if (!stateVal.isCategoryScreen && stateVal.currentPath.isNotEmpty()) stateVal.currentPath else null
            val filters = stateVal.activeSearchFilters

            val result = repository.searchFiles(query, pathScope, filters)
            result.onSuccess { files ->
                _state.update { it.copy(isSearching = false, searchResults = files) }
            }.onFailure { error ->
                _state.update { it.copy(isSearching = false, error = error.message ?: "Search failed") }
            }
        }
    }

    fun updateBrowserSortOption(sortOption: FileSortOption, applyToSubfolders: Boolean) {
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
        if (currentPath.isEmpty()) return

        val invalidChars = listOf('/', '\\', '\u0000')
        if (name.isBlank() || invalidChars.any { name.contains(it) } || name.contains("..")) {
            _state.update { it.copy(error = "Invalid folder name: must not be blank or contain /, \\, or ..") }
            return
        }

        viewModelScope.launch {
            val result = repository.createDirectory(currentPath, name)
            result.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to create folder") }
            }
        }
    }

    fun createFile(name: String) {
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty()) return

        val invalidChars = listOf('/', '\\', '\u0000')
        if (name.isBlank() || invalidChars.any { name.contains(it) } || name.contains("..")) {
            _state.update { it.copy(error = "Invalid file name: must not be blank or contain /, \\, or ..") }
            return
        }

        viewModelScope.launch {
            val result = repository.createFile(currentPath, name)
            result.onSuccess {
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message ?: "Failed to create file") }
            }
        }
    }

    fun moveSelectedToTrash() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.moveToTrash(selectedFiles)
            result.onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to move files to Trash") }
                refresh()
            }
        }
    }

    fun deleteSelectedPermanently() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.deletePermanently(selectedFiles)
            result.onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to delete files") }
                refresh()
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
            val result = repository.renameFile(path, newName)
            result.onSuccess {
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

    // --- Clipboard & Core Transfer System ---

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

            // Detect conflicts before pasting
            val conflictResult = repository.detectCopyConflicts(clipboard.sourcePaths, currentPath)
            conflictResult.onSuccess { conflicts ->
                if (conflicts.isNotEmpty()) {
                    // Show the conflict resolution dialog
                    _state.update {
                        it.copy(
                            isLoading = false,
                            pasteConflicts = conflicts,
                            showConflictDialog = true
                        )
                    }
                } else {
                    // No conflicts — paste immediately
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

    fun shareSelectedFiles(context: Context) {
        val selected = _state.value.selectedFiles.toList()
        if (selected.isEmpty()) return

        try {
            val uris = ArrayList<Uri>()
            for (path in selected) {
                val file = java.io.File(path)
                if (file.exists() && file.isFile) {
                    val uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    uris.add(uri)
                }
            }
            if (uris.isEmpty()) return

            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            
            val chooser = Intent.createChooser(intent, "Share files via")
            chooser.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(chooser)
            
            clearSelection()
        } catch (e: Exception) {
            _state.update { it.copy(error = "Failed to launch share intent: ${e.message}") }
        }
    }
}
