package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.*
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
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
    private val export: (List<VaultNodeMetadata>, String, Boolean) -> Unit,
    private val moveIn: (List<String>) -> Unit = {}
) {
    private var volumes = emptyList<StorageVolume>()
    private var exportNodes = emptyList<VaultNodeMetadata>()
    private var request: Job? = null

    fun openImportFiles() = open(OnlyFilesLocalPickerMode.IMPORT_FILES)
    fun openMoveIn() = open(OnlyFilesLocalPickerMode.MOVE_FILES_IN)
    fun openImportFolder() = open(OnlyFilesLocalPickerMode.IMPORT_FOLDER)
    fun openExport(nodes: List<VaultNodeMetadata>, move: Boolean) {
        exportNodes = nodes
        open(if (move) OnlyFilesLocalPickerMode.MOVE_OUT else OnlyFilesLocalPickerMode.EXPORT)
    }

    fun openDirectory(path: String) = load(path)
    fun toggleFile(path: String) = state.update { current ->
        val picker = current.localPicker ?: return@update current
        if (picker.isLoading || !picker.mode.selectsFiles ||
            picker.entries.none { it.reference == path && !it.isDirectory }
        ) return@update current
        val selected = picker.selectedPaths.toMutableSet()
        if (!selected.add(path)) selected.remove(path)
        current.copy(localPicker = picker.copy(selectedPaths = selected))
    }

    fun choose() {
        val picker = state.value.localPicker ?: return
        if (!picker.canChoose || state.value.busy) return
        request?.cancel()
        state.update { it.copy(localPicker = null) }
        when (picker.mode) {
            OnlyFilesLocalPickerMode.IMPORT_FILES -> import(picker.selectedPaths.toList())
            OnlyFilesLocalPickerMode.MOVE_FILES_IN -> moveIn(picker.selectedPaths.toList())
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
        loadPicker {
            val result = destinations.parent(current.path, volumes)
            ensureActive()
            result.fold(
                onSuccess = { parent ->
                    if (parent == null) showRoots(picker.mode, picker.selectedPaths) else loadDirectory(parent.path)
                },
                onFailure = showError
            )
        }
    }

    fun cancel() {
        request?.cancel()
        exportNodes = emptyList()
        state.update { it.copy(localPicker = null) }
    }

    private fun open(mode: OnlyFilesLocalPickerMode) = loadPicker {
        if (state.value.selectedVaultId == null) return@loadPicker
        state.update { it.copy(localPicker = OnlyFilesLocalPickerState(mode, isLoading = true)) }
        val result = volumeRepository.getStorageVolumes()
        ensureActive()
        result.fold(
            onSuccess = { volumes = it; showRoots(mode) },
            onFailure = { cancel(); showError(it) }
        )
    }

    private suspend fun showRoots(mode: OnlyFilesLocalPickerMode, selectedPaths: Set<String> = emptySet()) {
        val roots = volumes.mapNotNull { volume ->
            destinations.resolve(volume.path, volumes).getOrNull()?.let { directory ->
                FileModel(directory.name, directory.path, isDirectory = true)
            }
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        state.update { it.copy(localPicker = OnlyFilesLocalPickerState(mode, entries = roots, selectedPaths = selectedPaths)) }
    }

    private fun load(path: String) = loadPicker { loadDirectory(path) }

    private suspend fun loadDirectory(path: String) {
        val previous = state.value.localPicker ?: return
        state.update { it.copy(localPicker = previous.copy(isLoading = true)) }
        val result = destinations.resolve(path, volumes)
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val current = result.getOrElse {
            state.update { it.copy(localPicker = previous.copy(isLoading = false)) }
            showError(it)
            return
        } ?: run {
            state.update { it.copy(localPicker = previous.copy(isLoading = false)) }
            return
        }
        val entriesResult = if (previous.mode.selectsFiles) {
            browser.listFiles(current.path)
        } else {
            destinations.children(current.path, volumes).map { children ->
                children.map { FileModel(it.name, it.path, isDirectory = true) }
            }
        }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        entriesResult.fold(
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

    private fun loadPicker(block: suspend CoroutineScope.() -> Unit) {
        request?.cancel()
        request = scope.launch(start = CoroutineStart.LAZY, block = block).also { it.start() }
    }
}
