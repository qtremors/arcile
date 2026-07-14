package dev.qtremors.arcile.feature.onlyfiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultImportEvent
import dev.qtremors.arcile.core.vault.domain.VaultImportState
import dev.qtremors.arcile.core.vault.domain.VaultNode
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultSessionLease
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class OnlyFilesUiState(
    val vaults: List<VaultSummary> = emptyList(),
    val selectedVaultId: VaultId? = null,
    val path: VaultPath = VaultPath.Root,
    val nodes: List<VaultNode> = emptyList(),
    val viewer: VaultNode? = null,
    val activeImports: Map<VaultId, VaultImportState> = emptyMap(),
    val busy: Boolean = false,
    val message: String? = null
) {
    val selectedVault: VaultSummary?
        get() = vaults.firstOrNull { it.id == selectedVaultId }
}

@HiltViewModel
internal class OnlyFilesViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val importCoordinator: VaultImportCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(OnlyFilesUiState())
    val state: StateFlow<OnlyFilesUiState> = _state.asStateFlow()

    private var selectionLease: VaultSessionLease? = null
    private var selectionVaultId: VaultId? = null
    private var selectionDestination: VaultPath = VaultPath.Root

    init {
        viewModelScope.launch {
            repository.vaults.collect { vaults ->
                _state.update { current ->
                    val selectedStillUnlocked = vaults.any {
                        it.id == current.selectedVaultId && it.isUnlocked
                    }
                    current.copy(
                        vaults = vaults,
                        selectedVaultId = current.selectedVaultId.takeIf { selectedStillUnlocked },
                        path = current.path.takeIf { selectedStillUnlocked } ?: VaultPath.Root,
                        nodes = current.nodes.takeIf { selectedStillUnlocked }.orEmpty(),
                        viewer = current.viewer.takeIf { selectedStillUnlocked }
                    )
                }
            }
        }
        viewModelScope.launch {
            importCoordinator.activeImports.collect { imports ->
                _state.update { it.copy(activeImports = imports) }
            }
        }
        viewModelScope.launch {
            importCoordinator.events.collect { event ->
                when (event) {
                    is VaultImportEvent.Completed -> {
                        _state.update { it.copy(message = "Import complete") }
                        if (_state.value.selectedVaultId == event.vaultId) reload()
                    }
                    is VaultImportEvent.Cancelled -> _state.update { it.copy(message = "Import cancelled") }
                    is VaultImportEvent.Failed -> _state.update { it.copy(message = event.message) }
                    is VaultImportEvent.Started,
                    is VaultImportEvent.Progress -> Unit
                }
            }
        }
        viewModelScope.launch { repository.refreshVaults() }
    }

    fun createVault(name: String, password: String) = runBusy {
        repository.createAppPrivateVault(name, password.toCharArray()).fold(
            onSuccess = { id ->
                _state.update { it.copy(selectedVaultId = id, path = VaultPath.Root, nodes = emptyList()) }
                reload()
            },
            onFailure = ::showError
        )
    }

    fun unlock(vaultId: VaultId, password: String) = runBusy {
        repository.unlock(vaultId, password.toCharArray()).fold(
            onSuccess = {
                _state.update { it.copy(selectedVaultId = vaultId, path = VaultPath.Root, nodes = emptyList()) }
                reload()
            },
            onFailure = ::showError
        )
    }

    fun openVault(vault: VaultSummary): Boolean {
        if (!vault.isUnlocked) return false
        _state.update { it.copy(selectedVaultId = vault.id, path = VaultPath.Root, viewer = null) }
        reload()
        return true
    }

    fun open(node: VaultNode) {
        if (node.isDirectory) {
            _state.update { it.copy(path = node.path, viewer = null) }
            reload()
        } else if (node.isViewableImage() || node.isViewableVideo()) {
            _state.update { it.copy(viewer = node) }
        } else {
            _state.update { it.copy(message = "This file type cannot be previewed yet") }
        }
    }

    fun navigateUp(): Boolean {
        val current = _state.value
        if (current.viewer != null) {
            _state.update { it.copy(viewer = null) }
            return true
        }
        val parent = current.path.parent ?: return false
        _state.update { it.copy(path = parent) }
        reload()
        return true
    }

    fun createFolder(name: String) = mutate { id, path -> repository.createDirectory(id, path, name) }

    fun rename(node: VaultNode, name: String) = mutate { id, _ -> repository.rename(id, node.path, name) }

    fun delete(node: VaultNode) = mutate { id, _ -> repository.delete(id, node.path) }

    fun lockCurrent() {
        val id = _state.value.selectedVaultId ?: return
        _state.update { it.copy(selectedVaultId = null, path = VaultPath.Root, nodes = emptyList(), viewer = null) }
        viewModelScope.launch { repository.lock(id) }
    }

    fun lockAll() {
        _state.update { it.copy(selectedVaultId = null, path = VaultPath.Root, nodes = emptyList(), viewer = null) }
        viewModelScope.launch { repository.lockAll() }
    }

    fun beginImportSelection(): Boolean {
        cancelImportSelection()
        val id = _state.value.selectedVaultId ?: return false
        val lease = repository.holdSession(id).getOrElse {
            showError(it)
            return false
        }
        selectionLease = lease
        selectionVaultId = id
        selectionDestination = _state.value.path
        return true
    }

    fun finishImportSelection(sourceUris: List<String>) {
        val lease = selectionLease
        val id = selectionVaultId
        val destination = selectionDestination
        selectionLease = null
        selectionVaultId = null
        if (sourceUris.isEmpty() || lease == null || id == null) {
            lease?.close()
            return
        }
        if (!importCoordinator.startImport(id, destination, sourceUris, lease)) {
            _state.update { it.copy(message = "Another import is already running") }
        }
    }

    fun cancelImportSelection() {
        selectionLease?.close()
        selectionLease = null
        selectionVaultId = null
    }

    fun cancelImport(vaultId: VaultId) = importCoordinator.cancelImport(vaultId)

    fun clearMessage() = _state.update { it.copy(message = null) }

    suspend fun readViewerImage(node: VaultNode): Result<ByteArray> {
        val id = _state.value.selectedVaultId
            ?: return Result.failure(IllegalStateException("Vault is locked"))
        return repository.readBytes(id, node.path, MAX_IMAGE_BYTES)
    }

    fun openReader(vaultId: VaultId, path: VaultPath): Result<VaultSeekableReader> =
        repository.openReader(vaultId, path)

    private fun reload() {
        val id = _state.value.selectedVaultId ?: return
        val path = _state.value.path
        viewModelScope.launch {
            repository.list(id, path).fold(
                onSuccess = { nodes ->
                    if (_state.value.selectedVaultId == id && _state.value.path == path) {
                        _state.update { it.copy(nodes = nodes) }
                    }
                },
                onFailure = ::showError
            )
        }
    }

    private fun mutate(block: suspend (VaultId, VaultPath) -> Result<*>) {
        val id = _state.value.selectedVaultId ?: return
        val path = _state.value.path
        runBusy {
            block(id, path).fold(
                onSuccess = { reload() },
                onFailure = ::showError
            )
        }
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
            } finally {
                _state.update { it.copy(busy = false) }
            }
        }
    }

    private fun showError(error: Throwable) {
        _state.update { it.copy(message = error.message ?: "Vault operation failed") }
    }

    override fun onCleared() {
        cancelImportSelection()
        super.onCleared()
    }

    private companion object {
        const val MAX_IMAGE_BYTES = 128L * 1024L * 1024L
    }
}
