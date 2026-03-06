package dev.qtremors.arcile.presentation

import android.app.Application
import android.os.Environment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.arcile.data.LocalFileRepository
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque

data class FileManagerState(
    val isHomeScreen: Boolean = true,
    val currentPath: String = "",
    val files: List<FileModel> = emptyList(),
    val searchResults: List<FileModel> = emptyList(),
    val isSearching: Boolean = false,
    val recentFiles: List<FileModel> = emptyList(),
    val browserSearchQuery: String = "",
    val browserSortOption: FileSortOption = FileSortOption.NAME_ASC,
    val homeSearchQuery: String = "",
    val homeSortOption: FileSortOption = FileSortOption.DATE_NEWEST,
    val isGridView: Boolean = false,
    val storageInfo: StorageInfo? = null,
    val categoryStorages: List<CategoryStorage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedFiles: Set<String> = emptySet()
)

class FileManagerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: FileRepository = LocalFileRepository(application)

    private val _state = MutableStateFlow(FileManagerState())
    val state: StateFlow<FileManagerState> = _state.asStateFlow()

    private val pathHistory = ArrayDeque<String>()

    val storageRootPath: String = Environment.getExternalStorageDirectory().absolutePath

    init {
        loadHomeData()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val recentResult = repository.getRecentFiles()
            val storageResult = repository.getStorageInfo()

            _state.update { currentState ->
                currentState.copy(
                    isLoading = false,
                    recentFiles = recentResult.getOrNull() ?: emptyList(),
                    storageInfo = storageResult.getOrNull()
                )
            }

            val categoryResult = repository.getCategoryStorageSizes()
            _state.update { currentState ->
                currentState.copy(
                    categoryStorages = categoryResult.getOrNull() ?: emptyList()
                )
            }
        }
    }

    fun navigateToHome() {
        _state.update { it.copy(isHomeScreen = true, selectedFiles = emptySet()) }
        pathHistory.clear()
        loadHomeData()
    }

    fun openFileBrowser() {
        _state.update { it.copy(isHomeScreen = false) }
        pathHistory.clear()
        loadDirectory(storageRootPath)
    }

    fun navigateToSpecificFolder(path: String) {
        _state.update { it.copy(isHomeScreen = false) }
        pathHistory.clear()
        pathHistory.push(storageRootPath)
        loadDirectory(path)
    }

    fun navigateToCategory(categoryName: String) {
        _state.update { it.copy(isHomeScreen = false) }
        pathHistory.clear()
        loadCategory(categoryName)
    }

    fun navigateToFolder(path: String) {
        if (_state.value.currentPath.isNotEmpty() && _state.value.currentPath != path) {
            pathHistory.push(_state.value.currentPath)
        }
        loadDirectory(path)
    }

    fun navigateBack(): Boolean {
        // If the search query is active, clear the search first before actually going back
        if (_state.value.browserSearchQuery.isNotEmpty() || _state.value.homeSearchQuery.isNotEmpty()) {
            updateBrowserSearchQuery("")
            updateHomeSearchQuery("")
            return true
        }

        if (_state.value.isHomeScreen) {
            return false
        }

        if (pathHistory.isNotEmpty()) {
            val previousPath = pathHistory.pop()
            loadDirectory(previousPath)
            return true
        } else {
            _state.update { it.copy(isHomeScreen = true, selectedFiles = emptySet()) }
            return false
        }
    }

    fun refresh() {
        if (_state.value.isHomeScreen) {
            loadHomeData()
        } else if (_state.value.currentPath.isNotEmpty()) {
            loadDirectory(_state.value.currentPath)
        }
    }

    private fun loadDirectory(path: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, currentPath = path, selectedFiles = emptySet()) }

            val result = repository.listFiles(path)

            result.onSuccess { files ->
                _state.update { it.copy(isLoading = false, files = files) }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load directory") }
                if (pathHistory.isNotEmpty()) {
                    pathHistory.pop()
                }
            }
        }
    }

    private fun loadCategory(categoryName: String) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, currentPath = "Category: $categoryName", selectedFiles = emptySet()) }

            val result = repository.getFilesByCategory(categoryName)

            result.onSuccess { files ->
                _state.update { it.copy(isLoading = false, files = files) }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load category") }
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

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = emptySet()) }
    }

    fun updateBrowserSearchQuery(query: String) {
        _state.update { it.copy(browserSearchQuery = query) }
        debouncedSearch(query)
    }

    fun updateBrowserSortOption(sortOption: FileSortOption) {
        _state.update { it.copy(browserSortOption = sortOption) }
    }

    fun setGridView(enabled: Boolean) {
        _state.update { it.copy(isGridView = enabled) }
    }

    fun updateHomeSearchQuery(query: String) {
        _state.update { it.copy(homeSearchQuery = query) }
        debouncedSearch(query)
    }

    private var searchJob: kotlinx.coroutines.Job? = null

    private fun debouncedSearch(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(searchResults = emptyList(), isSearching = false) }
            return
        }
        searchJob = viewModelScope.launch {
            // Wait for user to stop typing
            kotlinx.coroutines.delay(400)
            _state.update { it.copy(isSearching = true, error = null) }
            val result = repository.searchGlobal(query)
            result.onSuccess { files ->
                _state.update { it.copy(isSearching = false, searchResults = files) }
            }.onFailure { error ->
                _state.update { it.copy(isSearching = false, error = error.message ?: "Search failed") }
            }
        }
    }

    fun updateHomeSortOption(sortOption: FileSortOption) {
        _state.update { it.copy(homeSortOption = sortOption) }
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

    fun deleteSelectedFiles() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            var failCount = 0
            for (path in selectedFiles) {
                repository.deleteFile(path).onFailure { failCount++ }
            }
            refresh()
            if (failCount > 0) {
                _state.update {
                    it.copy(error = "Failed to delete $failCount of ${selectedFiles.size} file(s)")
                }
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
}
