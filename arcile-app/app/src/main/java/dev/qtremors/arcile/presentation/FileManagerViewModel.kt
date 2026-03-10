package dev.qtremors.arcile.presentation

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.qtremors.arcile.data.LocalFileRepository
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.SearchFilters
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque

enum class ClipboardOperation { COPY, CUT }

data class ClipboardState(
    val operation: ClipboardOperation,
    val sourcePaths: List<String>
)

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
    val selectedFiles: Set<String> = emptySet(),
    val clipboardState: ClipboardState? = null,
    val isTrashScreen: Boolean = false,
    val trashFiles: List<TrashMetadata> = emptyList(),
    val isRecentFilesScreen: Boolean = false,
    val activeSearchFilters: SearchFilters = SearchFilters(),
    val isSearchFilterMenuVisible: Boolean = false
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
            val oneWeekAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
            val recentResult = repository.getRecentFiles(limit = Int.MAX_VALUE, minTimestamp = oneWeekAgo)
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
        _state.update { it.copy(isHomeScreen = true, isTrashScreen = false, isRecentFilesScreen = false, selectedFiles = emptySet()) }
        pathHistory.clear()
        loadHomeData()
    }

    fun openFileBrowser() {
        _state.update { it.copy(isHomeScreen = false, isTrashScreen = false, isRecentFilesScreen = false) }
        pathHistory.clear()
        loadDirectory(storageRootPath)
    }

    fun navigateToSpecificFolder(path: String) {
        _state.update { it.copy(isHomeScreen = false, isTrashScreen = false, isRecentFilesScreen = false) }
        pathHistory.clear()
        pathHistory.push(storageRootPath)
        loadDirectory(path)
    }

    fun navigateToCategory(categoryName: String) {
        _state.update { it.copy(isHomeScreen = false, isTrashScreen = false, isRecentFilesScreen = false) }
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
        
        if (_state.value.isTrashScreen || _state.value.isRecentFilesScreen) {
            navigateToHome()
            return true
        }

        if (pathHistory.isNotEmpty()) {
            val previousPath = pathHistory.pop()
            loadDirectory(previousPath)
            return true
        } else {
            _state.update { it.copy(isHomeScreen = true, isTrashScreen = false, isRecentFilesScreen = false, selectedFiles = emptySet()) }
            return false
        }
    }

    fun refresh() {
        when {
            _state.value.isHomeScreen -> loadHomeData()
            _state.value.isTrashScreen -> loadTrashFiles()
            _state.value.currentPath.isNotEmpty() -> loadDirectory(_state.value.currentPath)
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
            
            val stateVal = _state.value
            val pathScope = if (stateVal.isHomeScreen) null else stateVal.currentPath
            val filters = stateVal.activeSearchFilters
            
            val result = repository.searchFiles(query, pathScope, filters)
            result.onSuccess { files ->
                _state.update { it.copy(isSearching = false, searchResults = files) }
            }.onFailure { error ->
                _state.update { it.copy(isSearching = false, error = error.message ?: "Search failed") }
            }
        }
    }

    fun updateSearchFilters(filters: SearchFilters) {
        _state.update { it.copy(activeSearchFilters = filters) }
        val stateVal = _state.value
        val currentQuery = if (stateVal.isHomeScreen) stateVal.homeSearchQuery else stateVal.browserSearchQuery
        if (currentQuery.isNotBlank()) {
            debouncedSearch(currentQuery)
        }
    }
    
    fun toggleSearchFilterMenu(visible: Boolean) {
        _state.update { it.copy(isSearchFilterMenuVisible = visible) }
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

    fun deleteSelectedFiles() {
        val selectedFiles = _state.value.selectedFiles.toList()
        if (selectedFiles.isEmpty()) return

        // New pipeline routes explicit deletions smoothly into the underlying trash protocol
        moveToTrashSelected()
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
        if (currentPath.isEmpty() || _state.value.isHomeScreen || _state.value.isTrashScreen) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = if (clipboard.operation == ClipboardOperation.COPY) {
                repository.copyFiles(clipboard.sourcePaths, currentPath)
            } else {
                repository.moveFiles(clipboard.sourcePaths, currentPath)
            }

            result.onSuccess {
                _state.update { it.copy(clipboardState = null) }
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to paste files") }
            }
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

    // --- Trash Subsystem Implementations ---

    fun navigateToTrash() {
        _state.update { it.copy(isHomeScreen = false, isTrashScreen = true, isRecentFilesScreen = false, selectedFiles = emptySet()) }
        pathHistory.clear()
        loadTrashFiles()
    }

    fun navigateToRecentFiles() {
        _state.update { it.copy(isHomeScreen = false, isTrashScreen = false, isRecentFilesScreen = true, selectedFiles = emptySet()) }
        pathHistory.clear()
        // Always reload to ensure fresh data
        loadHomeData()
    }

    private fun loadTrashFiles() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, currentPath = "Trash Bin", selectedFiles = emptySet()) }
            val result = repository.getTrashFiles()
            result.onSuccess { trashItems ->
                _state.update { it.copy(isLoading = false, trashFiles = trashItems) }
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to load Trash Bin") }
            }
        }
    }

    fun moveToTrashSelected() {
        val selected = _state.value.selectedFiles.toList()
        if (selected.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.moveToTrash(selected)
            result.onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to move files to Trash") }
                refresh()
            }
        }
    }

    fun restoreSelectedTrash() {
        val selectedTrashIds = _state.value.selectedFiles.toList()
        if (selectedTrashIds.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.restoreFromTrash(selectedTrashIds)
            result.onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to restore files") }
                refresh()
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true) }
            val result = repository.emptyTrash()
            result.onSuccess {
                clearSelection()
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(isLoading = false, error = error.message ?: "Failed to empty Trash Bin") }
                refresh()
            }
        }
    }
}
