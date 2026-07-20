package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.*
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnlyFilesLocalPickerController(
    private val browser: FileBrowserRepository,
    private val destinations: SaveDestinationBrowser,
    private val volumeRepository: VolumeRepository,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val showError: (Throwable) -> Unit,
    private val import: (List<String>) -> Unit,
    private val export: (List<VaultNodeMetadata>, String, Boolean) -> Unit
) {
    private var volumes = emptyList<StorageVolume>()
    private var exportNodes = emptyList<VaultNodeMetadata>()

    fun openImportFiles() = open(OnlyFilesLocalPickerMode.IMPORT_FILES)
    fun openImportFolder() = open(OnlyFilesLocalPickerMode.IMPORT_FOLDER)
    fun openExport(nodes: List<VaultNodeMetadata>, move: Boolean) {
        exportNodes = nodes
        open(if (move) OnlyFilesLocalPickerMode.MOVE_OUT else OnlyFilesLocalPickerMode.EXPORT)
    }

    fun openDirectory(path: String) = load(path)
    fun toggleFile(path: String) = state.update { current ->
        val picker = current.localPicker ?: return@update current
        val selected = picker.selectedPaths.toMutableSet()
        if (!selected.add(path)) selected.remove(path)
        current.copy(localPicker = picker.copy(selectedPaths = selected))
    }

    fun choose() {
        val picker = state.value.localPicker ?: return
        state.update { it.copy(localPicker = null) }
        when (picker.mode) {
            OnlyFilesLocalPickerMode.IMPORT_FILES -> import(picker.selectedPaths.toList())
            OnlyFilesLocalPickerMode.IMPORT_FOLDER -> picker.current?.path?.let { import(listOf(it)) }
            OnlyFilesLocalPickerMode.EXPORT,
            OnlyFilesLocalPickerMode.MOVE_OUT -> picker.current?.path?.let {
                export(exportNodes, it, picker.mode == OnlyFilesLocalPickerMode.MOVE_OUT)
            }
        }
        exportNodes = emptyList()
    }

    fun up() {
        val picker = state.value.localPicker ?: return
        val current = picker.current ?: return cancel()
        scope.launch {
            destinations.parent(current.path, volumes).fold(
                onSuccess = { parent -> if (parent == null) showRoots(picker.mode) else load(parent.path) },
                onFailure = showError
            )
        }
    }

    fun cancel() {
        exportNodes = emptyList()
        state.update { it.copy(localPicker = null) }
    }

    private fun open(mode: OnlyFilesLocalPickerMode) = scope.launch {
        state.update { it.copy(localPicker = OnlyFilesLocalPickerState(mode, isLoading = true)) }
        volumeRepository.getStorageVolumes().fold(
            onSuccess = { volumes = it; showRoots(mode) },
            onFailure = { cancel(); showError(it) }
        )
    }

    private suspend fun showRoots(mode: OnlyFilesLocalPickerMode) {
        val roots = volumes.mapNotNull { volume ->
            destinations.resolve(volume.path, volumes).getOrNull()?.let { directory ->
                FileModel(directory.name, directory.path, isDirectory = true)
            }
        }
        state.update { it.copy(localPicker = OnlyFilesLocalPickerState(mode, entries = roots)) }
    }

    private fun load(path: String) = scope.launch {
        val previous = state.value.localPicker ?: return@launch
        state.update { it.copy(localPicker = previous.copy(isLoading = true)) }
        val current = destinations.resolve(path, volumes).getOrElse { showError(it); return@launch }
            ?: return@launch
        browser.listFiles(path).fold(
            onSuccess = { entries -> state.update {
                it.copy(localPicker = OnlyFilesLocalPickerState(previous.mode, current, entries.sortedWith(
                    compareByDescending<FileModel> { file -> file.isDirectory }.thenBy { file -> file.name.lowercase() }
                ), previous.selectedPaths))
            } },
            onFailure = { showError(it); state.update { currentState ->
                currentState.copy(localPicker = currentState.localPicker?.copy(isLoading = false))
            } }
        )
    }
}
