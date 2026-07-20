package dev.qtremors.arcile.feature.onlyfiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
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
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import dev.qtremors.arcile.core.vault.domain.VaultCatalog
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.ui.security.SensitiveMemory
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
    private val localFileBrowser: FileBrowserRepository,
    private val volumeRepository: VolumeRepository,
    private val securityPreferences: VaultSecurityPreferences,
    private val catalog: VaultCatalog,
    private val sessionManager: VaultSessionManager,
    private val boundaryTransferCoordinator: VaultBoundaryTransferCoordinator
) : ViewModel() {
    private val _state = MutableStateFlow(OnlyFilesUiState())
    val state: StateFlow<OnlyFilesUiState> = _state.asStateFlow()

    private val folderPicker by lazy {
        OnlyFilesFolderPickerController(
            repository, destinationBrowser, volumeRepository, _state, viewModelScope,
            ::runBusy, ::showError, ::selectVault
        )
    }
    private val browser by lazy {
        OnlyFilesBrowserController(fileSystem, _state, viewModelScope, ::showError, ::runBusy)
    }
    private val transfers by lazy {
        OnlyFilesTransferController(transferCoordinator, _state, viewModelScope, ::reload)
    }
    private val administration by lazy {
        OnlyFilesAdministrationController(
            catalog, sessionManager, _state, viewModelScope, ::runBusy, ::showError, ::selectVault
        )
    }
    private val boundary by lazy {
        OnlyFilesBoundaryController(boundaryTransferCoordinator, _state, viewModelScope, ::reload)
    }
    private val externalAccess by lazy {
        OnlyFilesExternalAccessController(externalAccessManager, viewModelScope, ::showError)
    }
    private val imports = OnlyFilesImportController(
        importCoordinator, _state, viewModelScope, ::showError, ::reload
    )
    private val localPicker by lazy {
        OnlyFilesLocalPickerController(
            localFileBrowser, destinationBrowser, volumeRepository, _state, viewModelScope,
            ::showError, ::importLocalPaths, ::exportLocal
        )
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
            transferCoordinator.progress.collect { progress -> _state.update { it.copy(transferProgress = progress) } }
        }
        viewModelScope.launch {
            boundaryTransferCoordinator.progress.collect { progress -> _state.update { it.copy(transferProgress = progress) } }
        }
        viewModelScope.launch { repository.refreshVaults() }
        viewModelScope.launch {
            securityPreferences.settings.collect { settings ->
                _state.update { it.copy(screenshotProtectionEnabled = settings.screenshotProtectionEnabled) }
            }
        }
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
    fun beginLocalFileImport() = localPicker.openImportFiles()
    fun beginLocalFolderImport() = localPicker.openImportFolder()
    fun beginLocalExport(nodes: List<VaultNodeMetadata>, move: Boolean) = localPicker.openExport(nodes, move)
    fun openLocalPickerDirectory(path: String) = localPicker.openDirectory(path)
    fun toggleLocalPickerFile(path: String) = localPicker.toggleFile(path)
    fun chooseLocalPicker() = localPicker.choose()
    fun navigateLocalPickerUp() = localPicker.up()
    fun cancelLocalPicker() = localPicker.cancel()

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

    fun navigateViewer(offset: Int) {
        if (offset == 0) return
        val snapshot = _state.value
        val current = snapshot.viewer ?: return
        val siblings = snapshot.displayedNodes.filter(VaultNodeMetadata::isViewableImage)
        val index = siblings.indexOfFirst { it.ref.nodeId == current.ref.nodeId }
        val destination = index.takeIf { it >= 0 }?.plus(offset)?.takeIf { it in siblings.indices } ?: return
        _state.update { it.copy(viewer = siblings[destination]) }
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

    fun createFolder(name: String) = browser.createFolder(name)
    fun createEmptyFile(name: String) = browser.createEmptyFile(name)
    fun rename(node: VaultNodeMetadata, name: String) = browser.rename(node, name)
    fun delete(nodes: List<VaultNodeMetadata>) = browser.delete(nodes)

    fun showProperties(nodes: List<VaultNodeMetadata>) = _state.update { it.copy(properties = nodes) }
    fun dismissProperties() = _state.update { it.copy(properties = emptyList()) }

    fun copy(nodes: List<VaultNodeMetadata>) = transfers.setClipboard(VaultClipboardAction.COPY, nodes)
    fun move(nodes: List<VaultNodeMetadata>) = transfers.setClipboard(VaultClipboardAction.MOVE, nodes)
    fun clearClipboard() = _state.update { it.copy(clipboard = null) }

    fun paste() = transfers.paste()
    fun cancelTransfer() { transfers.cancel(); boundary.cancel() }
    fun resolveConflict(decision: VaultConflictDecision, applyToAll: Boolean) {
        transfers.resolveConflict(decision, applyToAll); boundary.resolve(decision, applyToAll)
    }
    fun beginExportSelection(nodes: List<VaultNodeMetadata>): Boolean = boundary.prepare(nodes)
    fun finishExportSelection(destinationPath: String?, move: Boolean) {
        if (destinationPath == null) boundary.cancelSelection()
        else boundary.start(destinationPath, move)
    }

    private fun importLocalPaths(paths: List<String>) {
        if (imports.begin()) imports.finishSelection(paths)
    }

    private fun exportLocal(nodes: List<VaultNodeMetadata>, destinationPath: String, move: Boolean) {
        if (boundary.prepare(nodes)) boundary.start(destinationPath, move)
    }

    fun issueExternalAccess(
        nodes: List<VaultNodeMetadata>,
        onReady: (List<VaultExternalGrant>) -> Unit
    ) = externalAccess.issue(nodes, plaintextFallback = false, onReady)

    fun issuePlaintextFallbackAccess(
        nodes: List<VaultNodeMetadata>,
        onReady: (List<VaultExternalGrant>) -> Unit
    ) = externalAccess.issue(nodes, plaintextFallback = true, onReady)

    fun revokeExternalAccess(grants: List<VaultExternalGrant>) {
        externalAccess.revoke(grants)
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

    fun changePassword(current: String, replacement: String, weakConfirmed: Boolean) =
        administration.changePassword(current, replacement, weakConfirmed)
    fun prepareBiometricEnrollment(password: String, onReady: (VaultBiometricChallenge) -> Unit) =
        administration.prepareBiometricEnrollment(password, onReady)
    fun prepareBiometricUnlock(vaultId: VaultId, onReady: (VaultBiometricChallenge) -> Unit) =
        administration.prepareBiometricUnlock(vaultId, onReady)
    fun biometricCompleted(vaultId: VaultId) = administration.biometricCompleted(vaultId)
    fun removeBiometric() = administration.removeBiometric()
    fun removeRegistration() = administration.removeRegistration()
    fun deleteVault(confirmation: String) = administration.deleteVault(confirmation)

    fun lockCurrent() {
        val id = _state.value.selectedVaultId ?: return
        GlobalVideoPlaybackSessions.removeSecurityScope(vaultSecurityScope(id))
        SensitiveMemory.clear()
        clearSensitiveUiState()
        viewModelScope.launch { repository.lock(id) }
    }

    fun lockAll() {
        _state.value.vaults.forEach { GlobalVideoPlaybackSessions.removeSecurityScope(vaultSecurityScope(it.id)) }
        SensitiveMemory.clear()
        clearSensitiveUiState()
        viewModelScope.launch { repository.lockAll() }
    }

    fun beginImportSelection(): Boolean = imports.begin()
    fun finishImportSelection(sourceUris: List<String>) = imports.finishSelection(sourceUris)
    fun cancelImportSelection() = imports.cancelSelection()
    fun cancelImport(vaultId: VaultId) = imports.cancel(vaultId)
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

    private fun clearSensitiveUiState() {
        transfers.clear()
        boundary.clear()
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
        boundary.clear()
        cancelImportSelection()
        folderPicker.clear()
        super.onCleared()
    }

}
