package dev.qtremors.arcile.feature.onlyfiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.video.GlobalVideoPlaybackSessions
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultHealthMode
import dev.qtremors.arcile.core.vault.domain.VaultHealthService
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultImportEvent
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultSessionLease
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import dev.qtremors.arcile.core.vault.domain.VaultTransferCoordinator
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
internal class OnlyFilesViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val fileSystem: VaultFileSystem,
    private val transferCoordinator: VaultTransferCoordinator,
    private val importCoordinator: VaultImportCoordinator,
    private val externalAccessManager: VaultExternalAccessManager,
    private val healthService: VaultHealthService,
    private val destinationBrowser: SaveDestinationBrowser,
    private val volumeRepository: VolumeRepository
) : ViewModel() {
    private val _state = MutableStateFlow(OnlyFilesUiState())
    val state: StateFlow<OnlyFilesUiState> = _state.asStateFlow()

    private var selectionLease: VaultSessionLease? = null
    private var selectionVaultId: VaultId? = null
    private var selectionDestination: VaultPath = VaultPath.Root
    private val folderPicker by lazy {
        OnlyFilesFolderPickerController(
            repository, destinationBrowser, volumeRepository, _state, viewModelScope,
            ::runBusy, ::showError, ::selectVault
        )
    }
    private val browser by lazy { OnlyFilesBrowserController(fileSystem, _state, viewModelScope, ::showError) }
    private val transfers by lazy {
        OnlyFilesTransferController(transferCoordinator, _state, viewModelScope, ::reload)
    }

    init {
        viewModelScope.launch {
            repository.vaults.collect { vaults ->
                _state.update { current ->
                    val selectedStillUnlocked = vaults.any { it.id == current.selectedVaultId && it.isUnlocked }
                    current.copy(
                        vaults = vaults,
                        selectedVaultId = current.selectedVaultId.takeIf { selectedStillUnlocked },
                        directoryStack = current.directoryStack.takeIf { selectedStillUnlocked }.orEmpty(),
                        nodes = current.nodes.takeIf { selectedStillUnlocked }.orEmpty(),
                        searchHits = current.searchHits.takeIf { selectedStillUnlocked }.orEmpty(),
                        selectedNodeIds = current.selectedNodeIds.takeIf { selectedStillUnlocked }.orEmpty(),
                        viewer = current.viewer.takeIf { selectedStillUnlocked },
                        clipboard = current.clipboard?.takeIf { clipboard ->
                            vaults.any { it.id == clipboard.sources.firstOrNull()?.vaultId && it.isUnlocked }
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            importCoordinator.activeImports.collect { imports -> _state.update { it.copy(activeImports = imports) } }
        }
        viewModelScope.launch {
            importCoordinator.events.collect { event ->
                when (event) {
                    is VaultImportEvent.Completed -> finishImportMessage(event.vaultId, "Import complete")
                    is VaultImportEvent.Cancelled -> _state.update { it.copy(message = "Import cancelled") }
                    is VaultImportEvent.Failed -> _state.update { it.copy(message = event.message) }
                    is VaultImportEvent.Partial -> finishImportMessage(
                        event.vaultId,
                        "Imported ${event.result.completed.size}; ${event.result.failed.size} failed"
                    )
                    is VaultImportEvent.Started, is VaultImportEvent.Progress -> Unit
                }
            }
        }
        viewModelScope.launch {
            transferCoordinator.progress.collect { progress -> _state.update { it.copy(transferProgress = progress) } }
        }
        viewModelScope.launch { repository.refreshVaults() }
    }

    fun createAppPrivateVault(name: String, password: String) = runBusy {
        repository.createAppPrivateVault(name, password.toCharArray()).fold(
            onSuccess = { selectVault(it) }, onFailure = ::showError
        )
    }

    fun beginFolderVaultCreation(name: String, password: String) = folderPicker.beginCreate(name, password)
    fun beginAttachVault() = folderPicker.beginAttach()
    fun chooseVaultFolder() = folderPicker.choose()
    fun cancelVaultFolderPicker() = folderPicker.cancel()
    fun openVaultFolder(path: String) = folderPicker.openPath(path)
    fun navigateVaultFolderUp() = folderPicker.navigateUp()

    fun unlock(vaultId: VaultId, password: String) = runBusy {
        repository.unlock(vaultId, password.toCharArray()).fold(
            onSuccess = { selectVault(vaultId) }, onFailure = ::showError
        )
    }

    fun openVault(vault: VaultSummary): Boolean {
        if (!vault.isAvailable) {
            _state.update { it.copy(message = "This vault is unavailable. Reconnect its storage and refresh.") }
            return true
        }
        if (!vault.isUnlocked) return false
        selectVault(vault.id)
        return true
    }

    fun open(node: VaultNodeMetadata) {
        if (node.isDirectory) {
            val directoryId = node.ref.directoryId ?: return showError(IllegalStateException("Folder identity is missing"))
            val currentPath = _state.value.currentDirectory?.path ?: VaultPath.Root
            _state.update {
                it.copy(
                    directoryStack = it.directoryStack + VaultDirectoryCrumb(
                        directoryId, node.name, currentPath.resolve(node.name)
                    ),
                    viewer = null,
                    searchQuery = "",
                    searchHits = emptyList(),
                    selectedNodeIds = emptySet()
                )
            }
            reload()
        } else if (node.isViewableImage() || node.isViewableVideo()) {
            _state.update { it.copy(viewer = node, selectedNodeIds = emptySet()) }
        } else {
            _state.update { it.copy(message = "This file type cannot be previewed") }
        }
    }

    fun navigateUp(): Boolean {
        val current = _state.value
        if (current.viewer != null) {
            _state.update { it.copy(viewer = null) }
            return true
        }
        if (current.directoryStack.size <= 1) return false
        _state.update {
            it.copy(
                directoryStack = it.directoryStack.dropLast(1),
                searchQuery = "",
                searchHits = emptyList(),
                selectedNodeIds = emptySet()
            )
        }
        reload()
        return true
    }

    fun refresh() = browser.refresh()
    fun loadNextPage() = browser.loadNextPage()
    fun setSort(field: VaultSortField, direction: VaultSortDirection) = browser.setSort(field, direction)

    fun toggleLayout() = _state.update {
        it.copy(layout = if (it.layout == OnlyFilesLayout.LIST) OnlyFilesLayout.GRID else OnlyFilesLayout.LIST)
    }

    fun updateSearch(query: String) = browser.updateSearch(query)
    fun toggleRecursiveSearch() = browser.toggleRecursiveSearch()

    fun toggleSelection(nodeId: String) = _state.update { state ->
        val selected = state.selectedNodeIds
        state.copy(selectedNodeIds = if (nodeId in selected) selected - nodeId else selected + nodeId)
    }

    fun selectRange(opaqueIds: List<String>) = _state.update { it.copy(selectedNodeIds = it.selectedNodeIds + opaqueIds) }
    fun selectAll() = _state.update { it.copy(selectedNodeIds = it.displayedNodes.map { node -> node.ref.nodeId.value }.toSet()) }
    fun invertSelection() = _state.update { state ->
        val all = state.displayedNodes.map { it.ref.nodeId.value }.toSet()
        state.copy(selectedNodeIds = all - state.selectedNodeIds)
    }
    fun clearSelection() = _state.update { it.copy(selectedNodeIds = emptySet()) }

    fun createFolder(name: String) = currentMutation { vaultId, directoryId ->
        fileSystem.createDirectory(vaultId, directoryId, name)
    }

    fun createEmptyFile(name: String) = currentMutation { vaultId, directoryId ->
        fileSystem.createEmptyFile(vaultId, directoryId, name)
    }

    fun rename(node: VaultNodeMetadata, name: String) = runBusy {
        fileSystem.rename(node.ref, name).fold(onSuccess = { reload() }, onFailure = ::showError)
    }

    fun delete(nodes: List<VaultNodeMetadata>) = runBusy {
        var completed = 0
        var failed = 0
        nodes.distinctBy { it.ref.nodeId }.forEach { node ->
            fileSystem.deletePermanently(node.ref).fold({ completed++ }, { failed++ })
        }
        _state.update {
            it.copy(
                selectedNodeIds = emptySet(),
                message = if (failed == 0) "$completed item(s) permanently deleted" else "$completed deleted; $failed failed"
            )
        }
        reload()
    }

    fun showProperties(nodes: List<VaultNodeMetadata>) = _state.update { it.copy(properties = nodes) }
    fun dismissProperties() = _state.update { it.copy(properties = emptyList()) }

    fun copy(nodes: List<VaultNodeMetadata>) = transfers.setClipboard(VaultClipboardAction.COPY, nodes)
    fun move(nodes: List<VaultNodeMetadata>) = transfers.setClipboard(VaultClipboardAction.MOVE, nodes)
    fun clearClipboard() = _state.update { it.copy(clipboard = null) }

    fun paste() = transfers.paste()
    fun cancelTransfer() = transfers.cancel()
    fun resolveConflict(decision: VaultConflictDecision, applyToAll: Boolean) = transfers.resolveConflict(decision, applyToAll)

    fun issueExternalAccess(
        nodes: List<VaultNodeMetadata>,
        onReady: (List<VaultExternalGrant>) -> Unit
    ) {
        val files = nodes.filterNot(VaultNodeMetadata::isDirectory)
        if (files.isEmpty()) return
        viewModelScope.launch {
            val issued = mutableListOf<VaultExternalGrant>()
            for (node in files) {
                val grant = externalAccessManager.issue(node.ref).getOrElse { error ->
                    issued.forEach { externalAccessManager.revoke(it.token) }
                    showError(error)
                    return@launch
                }
                issued += grant
            }
            onReady(issued)
        }
    }

    fun revokeExternalAccess(grants: List<VaultExternalGrant>) {
        grants.forEach { externalAccessManager.revoke(it.token) }
    }

    fun verifyHealth(mode: VaultHealthMode) {
        val id = _state.value.selectedVaultId ?: return
        runBusy {
            healthService.verify(id, mode).fold(
                onSuccess = { report -> _state.update { it.copy(healthReport = report) } },
                onFailure = ::showError
            )
        }
    }

    fun dismissHealthReport() = _state.update { it.copy(healthReport = null) }

    fun lockCurrent() {
        val id = _state.value.selectedVaultId ?: return
        GlobalVideoPlaybackSessions.removeSecurityScope(vaultSecurityScope(id))
        clearSensitiveUiState()
        viewModelScope.launch { repository.lock(id) }
    }

    fun lockAll() {
        _state.value.vaults.forEach { GlobalVideoPlaybackSessions.removeSecurityScope(vaultSecurityScope(it.id)) }
        clearSensitiveUiState()
        viewModelScope.launch { repository.lockAll() }
    }

    fun beginImportSelection(): Boolean {
        cancelImportSelection()
        val state = _state.value
        val id = state.selectedVaultId ?: return false
        val lease = repository.holdSession(id).getOrElse { showError(it); return false }
        selectionLease = lease
        selectionVaultId = id
        selectionDestination = state.currentDirectory?.path ?: VaultPath.Root
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
            lease.close()
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
    fun openReader(ref: VaultNodeRef): Result<VaultSeekableReader> = fileSystem.openReader(ref)

    private fun selectVault(vaultId: VaultId) {
        _state.update {
            it.copy(
                selectedVaultId = vaultId,
                directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "", VaultPath.Root)),
                nodes = emptyList(),
                searchHits = emptyList(),
                searchQuery = "",
                selectedNodeIds = emptySet(),
                viewer = null
            )
        }
        reload()
    }

    private fun reload() = browser.reload()

    private fun currentMutation(block: suspend (VaultId, DirectoryId) -> Result<*>) {
        val snapshot = _state.value
        val id = snapshot.selectedVaultId ?: return
        val directory = snapshot.currentDirectory?.id ?: return
        runBusy { block(id, directory).fold(onSuccess = { reload() }, onFailure = ::showError) }
    }

    private fun clearSensitiveUiState() {
        transfers.clear()
        browser.cancel()
        cancelImportSelection()
        _state.update {
            it.copy(
                selectedVaultId = null,
                directoryStack = emptyList(),
                nodes = emptyList(),
                searchHits = emptyList(),
                searchQuery = "",
                selectedNodeIds = emptySet(),
                clipboard = null,
                viewer = null,
                properties = emptyList(),
                pendingConflict = null,
                healthReport = null
            )
        }
    }

    private fun finishImportMessage(vaultId: VaultId, message: String) {
        _state.update { it.copy(message = message) }
        if (_state.value.selectedVaultId == vaultId) reload()
    }

    private fun runBusy(block: suspend () -> Unit) {
        if (_state.value.busy) return
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try { block() } finally { _state.update { it.copy(busy = false) } }
        }
    }

    private fun showError(error: Throwable) {
        _state.update { it.copy(message = error.message ?: "Vault operation failed") }
    }

    override fun onCleared() {
        transfers.clear()
        cancelImportSelection()
        folderPicker.clear()
        super.onCleared()
    }

}
