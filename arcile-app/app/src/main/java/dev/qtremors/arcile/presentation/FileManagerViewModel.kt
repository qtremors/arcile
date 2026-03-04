package dev.qtremors.arcile.presentation

import android.os.Environment
import androidx.lifecycle.ViewModel
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
    val recentFiles: List<FileModel> = emptyList(),
    val storageInfo: StorageInfo? = null,
    val categoryStorages: List<CategoryStorage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val selectedFiles: Set<String> = emptySet()
)

class FileManagerViewModel(
    private val repository: FileRepository = LocalFileRepository()
) : ViewModel() {

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

            // load category sizes in background (can take a moment)
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

    fun navigateToFolder(path: String) {
        if (_state.value.currentPath.isNotEmpty() && _state.value.currentPath != path) {
            pathHistory.push(_state.value.currentPath)
        }
        loadDirectory(path)
    }

    fun navigateBack(): Boolean {
        if (_state.value.isHomeScreen) {
            return false
        }

        if (pathHistory.isNotEmpty()) {
            val previousPath = pathHistory.pop()
            loadDirectory(previousPath)
            return true
        } else {
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

    fun createFolder(name: String) {
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty()) return

        // validate folder name
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
            // refresh the directory listing first
            refresh()
            // re-apply the error after refresh so the user sees it
            if (failCount > 0) {
                _state.update {
                    it.copy(error = "Failed to delete $failCount of ${selectedFiles.size} file(s)")
                }
            }
        }
    }

    fun renameFile(path: String, newName: String) {
        // validate name
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
