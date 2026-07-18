package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import kotlinx.coroutines.CoroutineScope
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

    fun beginCreate(name: String, password: String) {
        if (state.value.busy) return
        clearPending()
        pending = PendingFolderVault(name, password.toCharArray())
        open(VaultFolderPickerMode.CREATE)
    }

    fun beginAttach() {
        clearPending()
        open(VaultFolderPickerMode.ATTACH)
    }

    fun choose() {
        val picker = state.value.folderPicker ?: return
        val path = picker.current?.path ?: return
        state.update { it.copy(folderPicker = null) }
        if (picker.mode == VaultFolderPickerMode.ATTACH) attach(path) else finishCreate(path)
    }

    fun cancel() {
        state.update { it.copy(folderPicker = null) }
        clearPending()
    }

    fun openPath(path: String) = loadPath(path)

    fun navigateUp() {
        val picker = state.value.folderPicker ?: return
        val current = picker.current ?: return cancel()
        scope.launch {
            destinationBrowser.parent(current.path, volumes).fold(
                onSuccess = { parent -> if (parent == null) showRoot(picker.mode) else loadPath(parent.path) },
                onFailure = showError
            )
        }
    }

    fun clear() = clearPending()

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
            onSuccess = { state.update { it.copy(message = "Vault added. Enter its password to unlock it.") } },
            onFailure = showError
        )
    }

    private fun open(mode: VaultFolderPickerMode) {
        scope.launch {
            state.update { it.copy(folderPicker = VaultFolderPickerState(mode = mode, isLoading = true)) }
            volumeRepository.getStorageVolumes().fold(
                onSuccess = { found -> volumes = found; showRoot(mode) },
                onFailure = { cancel(); showError(it) }
            )
        }
    }

    private suspend fun showRoot(mode: VaultFolderPickerMode) {
        val roots = volumes.mapNotNull { destinationBrowser.resolve(it.path, volumes).getOrNull() }
        state.update { it.copy(folderPicker = VaultFolderPickerState(mode = mode, entries = roots)) }
    }

    private fun loadPath(path: String) {
        val mode = state.value.folderPicker?.mode ?: return
        scope.launch {
            state.update { it.copy(folderPicker = it.folderPicker?.copy(isLoading = true)) }
            val current = destinationBrowser.resolve(path, volumes).getOrElse {
                state.update { currentState -> currentState.copy(folderPicker = currentState.folderPicker?.copy(isLoading = false)) }
                showError(it)
                return@launch
            } ?: return@launch
            destinationBrowser.children(path, volumes).fold(
                onSuccess = { children -> state.update { it.copy(folderPicker = VaultFolderPickerState(mode, current, children)) } },
                onFailure = {
                    state.update { currentState -> currentState.copy(folderPicker = currentState.folderPicker?.copy(isLoading = false)) }
                    showError(it)
                }
            )
        }
    }

    private fun clearPending() {
        pending?.password?.fill('\u0000')
        pending = null
    }

    private data class PendingFolderVault(val name: String, val password: CharArray)
}
