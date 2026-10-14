package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SearchFilters

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
import dev.qtremors.arcile.core.vault.domain.VaultThumbnailCache
import dev.qtremors.arcile.core.vault.domain.VaultCatalog
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.ui.security.SensitiveMemory
import dev.qtremors.arcile.core.vault.domain.VaultViewOptions
import dev.qtremors.arcile.core.vault.domain.VaultFolderViewOptions
import dev.qtremors.arcile.core.vault.domain.VaultTransferProgress
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import dev.qtremors.arcile.core.vault.domain.VaultTransferCoordinator
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val boundaryTransferCoordinator: VaultBoundaryTransferCoordinator,
    private val vaultThumbnailCache: VaultThumbnailCache
) : ViewModel() {
    private val _state = MutableStateFlow(OnlyFilesUiState())
    val state: StateFlow<OnlyFilesUiState> = _state.asStateFlow()
    private var exitInProgress = false
    private var pendingEntry: OnlyFilesEntry? = null
    private var consumedEntry: OnlyFilesEntry? = null
    private var pendingExtraction: VaultZipExtraction? = null

    private val folderPicker by lazy {
        OnlyFilesFolderPickerController(
            repository, destinationBrowser, volumeRepository, _state, viewModelScope,
            ::runBusy, ::showError, ::selectVault
        )
    }
    private val browser by lazy {
        OnlyFilesBrowserController(fileSystem, _state, viewModelScope, ::showError, ::runBusy)
    }
    private val archives by lazy {
        OnlyFilesArchiveController(fileSystem, _state, viewModelScope, ::reload, ::showError)
    }
    private val locations by lazy {
        OnlyFilesLocationController(
            fileSystem, securityPreferences, _state, viewModelScope, ::reload,
            { browser.updateSearch(_state.value.searchQuery, debounceMillis = 0L) }, ::showError
        )
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
            combine(repository.vaults, repository.unlockedVaultIds) { vaults, unlockedVaultIds ->
                vaults.withCurrentUnlockState(unlockedVaultIds)
            }.collect { vaults ->
                val biometricVaultIds = vaults.filter { sessionManager.hasBiometricEnrollment(it.id) }
                    .mapTo(linkedSetOf(), VaultSummary::id)
                val activeVault = _state.value.selectedVaultId
                if (activeVault != null && vaults.none { it.id == activeVault && it.isUnlocked }) {
                    GlobalVideoPlaybackSessions.removeSecurityScope(vaultSecurityScope(activeVault))
                    dismissVaultSession()
                    resolveConflict(VaultConflictDecision.SKIP, true)
                }
                _state.update { current ->
                    current.copy(
                        vaults = vaults,
                        biometricVaultIds = biometricVaultIds,
                        clipboard = current.clipboard?.takeIf { clipboard ->
                            vaults.any { it.id == clipboard.sources.firstOrNull()?.vaultId && it.isUnlocked }
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            transferCoordinator.progress.collect(::updateTransferProgress)
        }
        viewModelScope.launch {
            boundaryTransferCoordinator.progress.collect(::updateTransferProgress)
        }
        viewModelScope.launch { repository.refreshVaults() }
        viewModelScope.launch {
            securityPreferences.settings.collect { settings ->
                _state.update {
                    it.copy(
                        screenshotProtectionEnabled = settings.screenshotProtectionEnabled,
                        pinnedDirectoryIds = it.selectedVaultId?.let(settings.pinnedDirectories::get).orEmpty()
                    )
                }
                browser.applyViewOptions(settings)
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
        val currentVault = _state.value.vaults.firstOrNull { it.id == vault.id } ?: vault
        if (!currentVault.isAvailable) {
            _state.update { it.copy(message = "This vault is unavailable. Reconnect its storage and refresh.") }
            return true
        }
        if (vault.id !in repository.unlockedVaultIds.value) return false
        selectVault(vault.id)
        return true
    }

    fun openEntry(entry: OnlyFilesEntry, vault: VaultSummary): Boolean {
        if (entry == consumedEntry) return true
        pendingEntry = entry
        return openVault(vault)
    }

    fun open(node: VaultNodeMetadata) {
        if (node.isDirectory) {
            val directoryId = node.ref.directoryId ?: return showError(IllegalStateException("Folder identity is missing"))
            val snapshot = _state.value
            val hit = snapshot.searchHits.firstOrNull { it.metadata.ref == node.ref }
            val stack = (if (hit != null) snapshot.searchScope ?: snapshot.directoryStack else snapshot.directoryStack)
                .toMutableList()
            var path = stack.lastOrNull()?.path ?: VaultPath.Root
            for ((id, name) in hit?.parentDirectoryIds.orEmpty().zip(hit?.parentNames.orEmpty())) {
                path = path.resolve(name)
                stack += VaultDirectoryCrumb(id, name, path)
            }
            locations.changeDirectory(stack + VaultDirectoryCrumb(directoryId, node.name, path.resolve(node.name)))
        } else {
            _state.update {
                it.copy(
                    viewer = node,
                    viewerQueue = if (node.isViewableImage()) it.displayedNodes.filter(VaultNodeMetadata::isViewableImage)
                    else emptyList(),
                    selectedNodeIds = emptySet()
                )
            }
        }
    }

    fun navigateUp(): Boolean {
        val current = _state.value
        if (current.viewer != null) {
            _state.update { it.copy(viewer = null, viewerQueue = emptyList()) }
            return true
        }
        if (current.directoryStack.size <= 1) {
            if (current.selectedVaultId != null) {
                clearSensitiveUiState(keepClipboard = current.transferMountGeneration == null)
                return true
            }
            return false
        }
        navigateToDirectory(current.directoryStack[current.directoryStack.lastIndex - 1].id.value)
        return true
    }

    fun navigateToDirectory(id: String) {
        val stack = _state.value.directoryStack
        val index = stack.indexOfFirst { it.id.value == id }
        if (index !in 0 until stack.lastIndex) return
        locations.changeDirectory(stack.take(index + 1))
    }

    fun revealInParent(node: VaultNodeMetadata) {
        if (_state.value.selectedVaultId == node.ref.vaultId) {
            locations.navigate(node.ref.parentId, node.ref.nodeId.value)
        }
    }

    fun finishReveal(unavailableMessage: String? = null) = _state.update {
        it.copy(revealNodeId = null, message = unavailableMessage ?: it.message)
    }
    fun openSearchScope() = locations.openScopePicker()
    fun clearSearchScope() = locations.clearScope()
    fun openPinnedFolders() = locations.openPinnedFolders()
    fun toggleFolderPin(directory: DirectoryId) = locations.togglePin(directory)
    fun resetFolderView() = locations.resetCurrentFolderView()
    fun openLocationFolder(folder: VaultNodeMetadata) = locations.openPickerFolder(folder)
    fun navigateLocationPicker(id: DirectoryId) = locations.pickerToDirectory(id)
    fun loadMoreLocationFolders() = locations.loadMoreFolders()
    fun retryLocationPicker() = locations.retryPicker()
    fun applySearchScope() = locations.applyScope()
    fun closeLocationPicker() {
        pendingExtraction = null
        locations.closePicker()
    }
    fun openPinnedFolder(id: DirectoryId) {
        locations.closePicker()
        locations.navigate(id)
    }

    fun createZip(nodes: List<VaultNodeMetadata>, name: String, compression: Int, successMessage: String) =
        archives.create(nodes, name, compression, successMessage)

    fun requestZipExtraction(request: VaultZipExtraction, chooseDestination: Boolean) {
        if (_state.value.busy || _state.value.selectedVaultId != request.node.ref.vaultId) return
        if (chooseDestination) {
            pendingExtraction = request
            locations.openArchiveDestination()
        } else {
            extractZip(request, request.node.ref.parentId)
        }
    }

    fun chooseArchiveDestination() {
        val picker = _state.value.locationPicker ?: return
        if (picker.mode != OnlyFilesLocationMode.ARCHIVE_DESTINATION) return
        val directory = picker.stack.lastOrNull()?.id ?: return
        val request = pendingExtraction ?: return
        closeLocationPicker()
        extractZip(request, directory)
    }

    fun cancelArchiveOperation() = archives.cancel()

    private fun extractZip(request: VaultZipExtraction, parent: DirectoryId) =
        archives.extract(request.node, parent, request.selection, request.name, request.successMessage, request.keepBoth)

    fun showViewerImage(ref: VaultNodeRef) {
        _state.update { current ->
            if (current.viewer?.isViewableImage() != true) return@update current
            val image = current.viewerQueue.firstOrNull { it.ref == ref } ?: return@update current
            current.copy(viewer = image)
        }
    }

    fun refresh() = browser.refresh()
    fun loadNextPage() = browser.loadNextPage()
    private fun setDefaultViewOptions(options: VaultViewOptions) = viewModelScope.launch {
        try {
            securityPreferences.setViewOptions(options)
        } catch (error: java.io.IOException) {
            showError(error)
        }
    }

    fun setFolderViewOptions(options: VaultViewOptions, applyToSubfolders: Boolean) {
        val snapshot = _state.value
        val vault = snapshot.selectedVaultId ?: return
        val directory = snapshot.currentDirectory?.id ?: return
        viewModelScope.launch {
            try {
                securityPreferences.setFolderViewOptions(vault, directory, VaultFolderViewOptions(options, applyToSubfolders))
            } catch (error: java.io.IOException) {
                showError(error)
            }
        }
    }

    fun setRecursiveSearch(recursive: Boolean) {
        setDefaultViewOptions(_state.value.defaultViewOptions.copy(recursiveSearch = recursive))
    }

    fun updateSearch(query: String) = browser.updateSearch(query)
    fun updateSearchFilters(filters: SearchFilters) = browser.updateSearch(_state.value.searchQuery, filters, 0L)
    fun clearSearch() {
        _state.update { it.copy(searchScope = null) }
        browser.updateSearch("", SearchFilters(), 0L)
    }
    fun setGridCellSize(size: Float) {
        val snapshot = _state.value
        setFolderViewOptions(snapshot.viewOptions.copy(gridMinCellSize = size), snapshot.viewOptionsApplyToSubfolders)
    }

    fun toggleHiddenFiles() {
        val options = _state.value.defaultViewOptions
        setDefaultViewOptions(options.copy(showHiddenFiles = !options.showHiddenFiles))
    }

    fun toggleSelection(nodeId: String) = _state.update { state ->
        val selected = state.selectedNodeIds
        state.copy(selectedNodeIds = if (nodeId in selected) selected - nodeId else selected + nodeId)
    }

    fun selectRange(opaqueIds: List<String>) = _state.update { it.copy(selectedNodeIds = it.selectedNodeIds + opaqueIds) }
    fun selectAll(loadedOnlyMessage: String) = _state.update {
        it.copy(
            selectedNodeIds = it.displayedNodes.map { node -> node.ref.nodeId.value }.toSet(),
            message = loadedOnlyMessage.takeIf { _ -> it.hasMoreRows } ?: it.message
        )
    }
    fun invertSelection(loadedOnlyMessage: String) = _state.update { state ->
        val all = state.displayedNodes.map { it.ref.nodeId.value }.toSet()
        state.copy(
            selectedNodeIds = all - state.selectedNodeIds,
            message = loadedOnlyMessage.takeIf { state.hasMoreRows } ?: state.message
        )
    }
    fun clearSelection() = _state.update { it.copy(selectedNodeIds = emptySet()) }

    fun createFolder(name: String) = browser.createFolder(name)
    fun createEmptyFile(name: String) = browser.createEmptyFile(name)
    fun createSyntheticFile(name: String, size: Long) = browser.createSyntheticFile(name, size)
    fun rename(node: VaultNodeMetadata, name: String) = browser.rename(node, name)
    fun delete(nodes: List<VaultNodeMetadata>) = browser.delete(nodes)

    fun showProperties(nodes: List<VaultNodeMetadata>) = _state.update { it.copy(properties = nodes) }
    fun dismissProperties() = _state.update { it.copy(properties = emptyList()) }

    fun copy(nodes: List<VaultNodeMetadata>) = transfers.setClipboard(VaultClipboardAction.COPY, nodes)
    fun move(nodes: List<VaultNodeMetadata>) = transfers.setClipboard(VaultClipboardAction.MOVE, nodes)
    fun clearClipboard() = _state.update { it.copy(clipboard = null) }

    fun removeClipboardItem(reference: String) {
        _state.update { current ->
            val clipboard = current.clipboard ?: return@update current
            val removed = clipboard.displayNodes.firstOrNull { it.toSharedFileModel().reference == reference }
                ?: return@update current
            val remaining = clipboard.sources - removed.ref
            current.copy(clipboard = clipboard.copy(
                sources = remaining,
                displayNodes = clipboard.displayNodes - removed
            ).takeIf { remaining.isNotEmpty() })
        }
    }

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

    fun reportExternalLaunchFailure(message: String) {
        _state.update { it.copy(message = message) }
    }

    fun verifyHealth(mode: VaultHealthMode) {
        val mount = _state.value
        val id = mount.selectedVaultId ?: return
        runBusy {
            healthService.verify(id, mode).fold(
                onSuccess = { report ->
                    _state.update {
                        if (it.mountGeneration == mount.mountGeneration) it.copy(healthReport = report) else it
                    }
                },
                onFailure = ::showError
            )
        }
    }

    fun dismissHealthReport() = _state.update { it.copy(healthReport = null) }

    fun changePassword(current: String, replacement: String, weakConfirmed: Boolean) =
        administration.changePassword(current, replacement, weakConfirmed)
    fun prepareBiometricEnrollment(password: String, onReady: (VaultBiometricChallenge) -> Unit) =
        administration.prepareBiometricEnrollment(password, onReady)
    fun prepareBiometricUnlock(vaultId: VaultId, onReady: (VaultBiometricChallenge) -> Unit) {
        if (vaultId in repository.unlockedVaultIds.value) {
            selectVault(vaultId)
            return
        }
        administration.prepareBiometricUnlock(vaultId, onReady)
    }
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
        prepareForLockAll()
        viewModelScope.launch { repository.lockAll() }
    }

    fun lockAllAndExit(onLocked: () -> Unit) {
        if (exitInProgress) return
        exitInProgress = true
        prepareForLockAll()
        viewModelScope.launch {
            try {
                repository.lockAll()
                onLocked()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                exitInProgress = false
                showError(error)
            }
        }
    }

    fun beginImportSelection(): Boolean = imports.begin()
    fun finishImportSelection(sourceUris: List<String>) = imports.finishSelection(sourceUris)
    fun cancelImportSelection() = imports.cancelSelection()
    fun cancelImport(vaultId: VaultId) = imports.cancel(vaultId)
    fun clearMessage() = _state.update { it.copy(message = null, completedArchive = null) }
    fun openReader(ref: VaultNodeRef): Result<VaultSeekableReader> = fileSystem.openReader(ref)
    suspend fun folderTotals(ref: VaultNodeRef) = fileSystem.folderTotals(ref)

    suspend fun saveVaultText(ref: VaultNodeRef, revision: Long, text: String): Result<VaultNodeMetadata> {
        val mount = _state.value.mountGeneration
        val bytes = text.encodeToByteArray()
        val result = try {
            if (bytes.size > MAX_VAULT_EDITOR_BYTES) {
                Result.failure(IllegalArgumentException("This file exceeds the text editor limit."))
            } else {
                fileSystem.replaceFileContents(ref, revision, bytes)
            }
        } finally {
            bytes.fill(0)
        }
        result.getOrNull()?.let { saved ->
            withContext(Dispatchers.Main.immediate) {
                if (_state.value.mountGeneration == mount && _state.value.selectedVaultId == ref.vaultId) {
                    _state.update { it.withUpdatedViewerNode(saved) }
                    browser.refresh()
                }
            }
        }
        return result
    }

    private fun selectVault(vaultId: VaultId) {
        val requested = pendingEntry?.takeIf { it.vaultId == vaultId }
        pendingEntry = null
        if (requested != null) consumedEntry = requested
        val settings = securityPreferences.settings.value
        _state.update {
            it.copy(
                selectedVaultId = vaultId,
                mountGeneration = it.mountGeneration + 1L,
                directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "", VaultPath.Root)),
                nodes = emptyList(),
                searchHits = emptyList(),
                searchQuery = "",
                searchFilters = SearchFilters(),
                searchScope = null,
                revealNodeId = null,
                revealHiddenNodeId = null,
                pinnedDirectoryIds = settings.pinnedDirectories[vaultId].orEmpty(),
                selectedNodeIds = emptySet(),
                viewer = null,
                viewerQueue = emptyList()
            )
        }
        if (requested != null) locations.navigate(requested.directoryId, requested.focusNodeId)
        else locations.restore(settings.lastDirectories[vaultId])
    }

    private fun reload() = browser.reload()

    private fun clearSensitiveUiState(keepClipboard: Boolean = false) {
        transfers.clear()
        boundary.clear()
        dismissVaultSession(keepClipboard)
    }

    private fun dismissVaultSession(keepClipboard: Boolean = false) {
        pendingEntry = null
        pendingExtraction = null
        archives.cancel()
        locations.cancelNavigation()
        locations.closePicker()
        browser.cancel()
        localPicker.cancel()
        boundary.cancelSelection()
        cancelImportSelection()
        _state.update {
            it.copy(
                selectedVaultId = null,
                mountGeneration = it.mountGeneration + 1L,
                directoryStack = emptyList(),
                nodes = emptyList(),
                nextPageToken = null,
                searchHits = emptyList(),
                searchNextPageToken = null,
                searchQuery = "",
                searchFilters = SearchFilters(),
                searchScope = null,
                revealNodeId = null,
                revealHiddenNodeId = null,
                pinnedDirectoryIds = emptySet(),
                isSearching = false,
                isListing = false,
                selectedNodeIds = emptySet(),
                clipboard = it.clipboard?.takeIf { clipboard ->
                    keepClipboard && clipboard.sources.all { ref -> ref.vaultId in repository.unlockedVaultIds.value }
                },
                pasteQueueNames = emptyList(),
                viewer = null,
                viewerQueue = emptyList(),
                properties = emptyList(),
                pendingConflict = null,
                healthReport = null,
                message = null,
                batchProgress = null,
                archiveProgress = null,
                completedArchive = null
            )
        }
    }

    private fun updateTransferProgress(progress: VaultTransferProgress) {
        _state.update {
            if (it.transferMountGeneration == null) return@update it
            it.copy(transferProgress = progress.copy(currentName = null))
        }
    }

    private fun prepareForLockAll() {
        _state.value.vaults.forEach { GlobalVideoPlaybackSessions.removeSecurityScope(vaultSecurityScope(it.id)) }
        SensitiveMemory.clear()
        clearSensitiveUiState()
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

    fun openSettings() {
        _state.update { it.copy(showSettingsSheet = true) }
        refreshSettings()
    }

    fun closeSettings() {
        _state.update { it.copy(showSettingsSheet = false) }
    }

    fun openSecurityDisclosure() {
        _state.update { it.copy(showSecurityDisclosure = true) }
    }

    fun closeSecurityDisclosure() {
        _state.update { it.copy(showSecurityDisclosure = false) }
    }

    fun refreshSettings() {
        viewModelScope.launch {
            _state.update { it.copy(isSettingsBusy = true) }
            val stats = vaultThumbnailCache.stats().getOrNull()
            _state.update {
                it.copy(
                    encryptedThumbnailFiles = stats?.encryptedFileCount ?: 0,
                    encryptedThumbnailBytes = stats?.encryptedBytes ?: 0L,
                    activeExternalGrants = externalAccessManager.activeGrants().size,
                    isSettingsBusy = false
                )
            }
        }
    }

    fun setScreenshotProtection(enabled: Boolean) {
        viewModelScope.launch { securityPreferences.setScreenshotProtectionEnabled(enabled) }
    }

    fun clearVaultThumbnails() {
        if (_state.value.isSettingsBusy) return
        viewModelScope.launch {
            _state.update { it.copy(isSettingsBusy = true) }
            vaultThumbnailCache.clear()
            refreshSettings()
        }
    }

    fun revokeAllVaultExternalAccess() {
        externalAccessManager.revokeAll()
        refreshSettings()
    }

    override fun onCleared() {
        transfers.clear()
        boundary.clear()
        cancelImportSelection()
        folderPicker.clear()
    }
}
