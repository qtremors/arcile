package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnlyFilesFolderPickerController(
    private val repository: VaultRepository,
    private val destinationBrowser: SaveDestinationBrowser,
    private val volumeRepository: VolumeRepository,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val runBusy: ((suspend () -> Unit) -> Unit),
    private val showError: (Throwable) -> Unit,
    private val selectVault: (VaultId) -> Unit
) {
    private var pending: PendingFolderVault? = null
    private var volumes: List<StorageVolume> = emptyList()
    private var request: Job? = null

    fun beginCreate(name: String, password: String) {
        if (state.value.busy) return
        clearPending()
        pending = PendingFolderVault(name, password.toCharArray())
        open(VaultFolderPickerMode.CREATE)
    }

    fun beginAttach() {
        if (state.value.busy) return
        clearPending()
        open(VaultFolderPickerMode.ATTACH)
    }

    fun choose() {
        val picker = state.value.folderPicker ?: return
        if (!picker.canChoose || state.value.busy) return
        val path = picker.current?.path ?: return
        request?.cancel()
        state.update { it.copy(folderPicker = null) }
        if (picker.mode == VaultFolderPickerMode.ATTACH) attach(path) else finishCreate(path)
    }

    fun cancel() {
        request?.cancel()
        request = null
        state.update { it.copy(folderPicker = null) }
        clearPending()
    }

    fun openPath(path: String) = loadPicker { loadPath(path) }

    fun navigateUp() {
        val picker = state.value.folderPicker ?: return
        val current = picker.current ?: return cancel()
        loadPicker {
            val result = destinationBrowser.parent(current.path, volumes)
            ensureActive()
            result.fold(
                onSuccess = { parent -> if (parent == null) showRoot(picker.mode) else loadPath(parent.path) },
                onFailure = showError
            )
        }
    }

    fun clear() = cancel()

    private fun finishCreate(path: String) {
        val request = pending ?: return
        pending = null
        runBusy {
            try {
                repository.createUserFolderVault(path, request.name, request.password).fold(
                    onSuccess = selectVault, onFailure = showError
                )
            } finally {
                request.password.fill('\u0000')
            }
        }
    }

    private fun attach(path: String) = runBusy {
        repository.attachExistingVault(path).fold(
            onSuccess = { state.update { it.copy(message = null, operationFeedback = operationSuccess(R.string.onlyfiles_vault_added, it.mountGeneration)) } },
            onFailure = showError
        )
    }

    private fun open(mode: VaultFolderPickerMode) = loadPicker {
        state.update { it.copy(folderPicker = VaultFolderPickerState(mode = mode, isLoading = true)) }
        val result = volumeRepository.getStorageVolumes()
        ensureActive()
        result.fold(
            onSuccess = { found -> volumes = found; showRoot(mode) },
            onFailure = { cancel(); showError(it) }
        )
    }

    private suspend fun showRoot(mode: VaultFolderPickerMode) {
        val roots = volumes.mapNotNull { destinationBrowser.resolve(it.path, volumes).getOrNull() }
        currentCoroutineContext().ensureActive()
        state.update { it.copy(folderPicker = VaultFolderPickerState(mode = mode, entries = roots)) }
    }

    private suspend fun loadPath(path: String) {
        val previous = state.value.folderPicker ?: return
        val context = currentCoroutineContext()
        state.update { it.copy(folderPicker = previous.copy(isLoading = true)) }
        val resolved = destinationBrowser.resolve(path, volumes)
        context.ensureActive()
        val current = resolved.getOrNull()
        if (current == null) {
            state.update { it.copy(folderPicker = previous.copy(isLoading = false)) }
            resolved.exceptionOrNull()?.let(showError)
            return
        }
        val children = destinationBrowser.children(current.path, volumes)
        context.ensureActive()
        children.fold(
            onSuccess = { entries ->
                state.update { it.copy(folderPicker = VaultFolderPickerState(previous.mode, current, entries)) }
            },
            onFailure = {
                state.update { currentState -> currentState.copy(folderPicker = previous.copy(isLoading = false)) }
                showError(it)
            }
        )
    }

    private fun loadPicker(block: suspend CoroutineScope.() -> Unit) {
        request?.cancel()
        request = scope.launch(start = CoroutineStart.LAZY, block = block).also { it.start() }
    }

    private fun clearPending() {
        pending?.password?.fill('\u0000')
        pending = null
    }

    private data class PendingFolderVault(val name: String, val password: CharArray)
}
