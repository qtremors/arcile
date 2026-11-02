package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.BuiltInFileOpen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.SaveDestinationBrowser
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.video.GlobalVideoPlaybackSessions
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultExternalAccessManager
import dev.qtremors.arcile.core.vault.domain.VaultExternalGrant
import dev.qtremors.arcile.core.vault.domain.VaultFileSystem
import dev.qtremors.arcile.core.vault.domain.VaultHealthMode
import dev.qtremors.arcile.core.vault.domain.VaultHealthService
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultOperationRecovery
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryQueue
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultRepository
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultSecurityPreferences
import dev.qtremors.arcile.core.vault.domain.VaultThumbnailCache
import dev.qtremors.arcile.core.vault.domain.VaultCatalog
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultBackupService
import dev.qtremors.arcile.core.ui.security.SensitiveMemory
import dev.qtremors.arcile.core.vault.domain.VaultViewOptions
import dev.qtremors.arcile.core.vault.domain.VaultFolderViewOptions
import dev.qtremors.arcile.core.vault.domain.VaultTransferProgress
import dev.qtremors.arcile.core.vault.domain.VaultRenameItem
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
    private val backupService: VaultBackupService,
    private val destinationBrowser: SaveDestinationBrowser,
    private val localFileBrowser: FileBrowserRepository,
    private val volumeRepository: VolumeRepository,
    private val securityPreferences: VaultSecurityPreferences,
    private val catalog: VaultCatalog,
    private val sessionManager: VaultSessionManager,
    private val boundaryTransferCoordinator: VaultBoundaryTransferCoordinator,
    private val vaultThumbnailCache: VaultThumbnailCache,
    operationRecovery: VaultOperationRecovery
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
        OnlyFilesBrowserController(fileSystem, _state, viewModelScope, ::showError, ::runBusy, ::onFoldersDeleted)
    }
    private val archives by lazy {
        OnlyFilesArchiveController(fileSystem, _state, viewModelScope, ::reload, ::showError, ::onFoldersDeleted)
    }
    private val undo by lazy {
        OnlyFilesUndoController(fileSystem, _state, ::runBusy, ::reload, ::onFoldersDeleted, { locations.navigate(it) })
    }
    private val locations: OnlyFilesLocationController by lazy {
        OnlyFilesLocationController(
            fileSystem, securityPreferences, _state, viewModelScope, ::reload,
            { browser.updateSearch(_state.value.searchQuery, debounceMillis = 0L) }, ::showError,
            { workspace.onDirectoryChanged() }
        )
    }
    private val workspace: OnlyFilesWorkspaceController by lazy {
        OnlyFilesWorkspaceController(
            fileSystem, securityPreferences, _state, viewModelScope,
            stopListing = { locations.cancelNavigation(); locations.closePicker(); browser.cancel() },
            reload = ::reload,
            showError = ::showError
        )
    }
    private val transfers by lazy {
        OnlyFilesTransferController(transferCoordinator, _state, viewModelScope, ::reload, ::onFoldersDeleted)
    }
    private val administration by lazy {
        OnlyFilesAdministrationController(
            catalog, sessionManager, _state, viewModelScope, ::runBusy, ::showError, ::selectVault, ::onVaultRemoved
        )
    }
    private val boundary by lazy {
        OnlyFilesBoundaryController(boundaryTransferCoordinator, _state, viewModelScope, ::reload, ::onFoldersDeleted)
    }
    private val recovery = OnlyFilesRecoveryController(operationRecovery, _state, viewModelScope, ::reload)
    private val externalAccess by lazy {
        OnlyFilesExternalAccessController(externalAccessManager, viewModelScope, ::showError)
    }
    private val backups = OnlyFilesBackupController(backupService, _state, viewModelScope, ::showError)
    private val imports = OnlyFilesImportController(
        importCoordinator, _state, viewModelScope, ::showError, ::reload
    )
    private val browserImports by lazy {
        OnlyFilesBrowserImportController(_state, imports, locations::closePicker)
    }
    private val localPicker by lazy {
        OnlyFilesLocalPickerController(
            localFileBrowser, destinationBrowser, volumeRepository, _state, viewModelScope,
            ::showError, ::importLocalPaths, ::exportLocal,
            moveIn = { paths -> _state.update { it.copy(pendingMoveIntoVault = paths) } },
            chooseBackupFolder = backups::chooseFolder
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
                }
                _state.update { current ->
                    current.copy(
                        vaults = vaults,
                        biometricVaultIds = biometricVaultIds,
                        clipboard = current.clipboard?.takeIf { clipboard ->
                            val unlocked = repository.unlockedVaultIds.value
                            clipboard.sources.all { it.vaultId in unlocked } &&
                                clipboard.mergeRetries.values.all { it.destinationVault in unlocked }
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
                        numericPasswordKeyboard = settings.numericPasswordKeyboard,
                        backgroundLockDelay = settings.backgroundLockDelay,
                        lockOnScreenOff = settings.lockOnScreenOff,
                        pinnedDirectoryIds = it.selectedVaultId?.let(settings.pinnedDirectories::get).orEmpty(),
                        pinnedVaultIds = settings.pinnedVaults,
                        workspaceTabsEnabled = settings.workspaceTabsEnabled
                    )
                }
                browser.applyViewOptions(settings)
                workspace.restorePins(_state.value.selectedVaultId?.let(settings.pinnedWorkspaceTabs::get).orEmpty())
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
    fun acceptBrowserImport(requestId: String) = browserImports.accept(requestId)
    fun chooseBrowserImportDestination(move: Boolean = false) = browserImports.choose(move)
    fun resolveBrowserMoveIntoVault(confirmed: Boolean) = browserImports.confirmMove(confirmed)

    fun beginLocalMoveIntoVault() = localPicker.openMoveIn()

    fun resolveLocalMoveIntoVault(confirmed: Boolean) {
        val pending = _state.value.pendingMoveIntoVault
        _state.update { it.copy(pendingMoveIntoVault = emptyList()) }
        if (confirmed && pending.isNotEmpty() && imports.begin()) imports.finishSelection(pending, move = true)
    }
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
            _state.update { it.copy(message = null, operationFeedback = operationNotice(R.string.onlyfiles_operation_storage_missing, it.mountGeneration, error = true)) }
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

    fun open(node: VaultNodeMetadata, keepSelection: Boolean = false) {
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
                    viewerQueue = emptyList(),
                    viewerSelectionEnabled = keepSelection,
                    selectedNodeIds = if (keepSelection) it.selectedNodeIds else emptySet()
                ).withCurrentMediaQueue()
            }
        }
    }

    fun navigateUp(): Boolean {
        val current = _state.value
        if (current.workspace.switching) {
            workspace.cancelSwitch()
            return true
        }
        if (current.viewer != null) {
            _state.update { it.copy(viewer = null, viewerQueue = emptyList(), viewerSelectionEnabled = false) }
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
    fun resetVaultFolderViews() {
        val vault = _state.value.selectedVaultId ?: return
        viewModelScope.launch {
            try {
                securityPreferences.resetVaultFolderViews(vault)
            } catch (error: java.io.IOException) {
                showError(error)
            }
        }
    }

    private suspend fun onFoldersDeleted(vault: VaultId, folders: Set<DirectoryId>) {
        _state.update { current ->
            if (current.selectedVaultId != vault) return@update current
            val workspace = current.workspace.capture(current)
            val tabs = workspace.tabs.map { tab ->
                if (tab.pinned && tab.location.directory.id in folders) tab.copy(pinned = false) else tab
            }
            current.copy(workspace = workspace.copy(tabs = tabs.sortedBy { if (it.primary) 0 else if (it.pinned) 1 else 2 }))
        }
        try {
            securityPreferences.removeDirectoryPreferences(vault, folders)
        } catch (_: java.io.IOException) {
            // The files are already deleted. Stale shortcuts are checked when next opened.
        }
    }

    private suspend fun onVaultRemoved(vault: VaultId) {
        if (_state.value.selectedVaultId == vault) dismissVaultSession()
        try {
            securityPreferences.removeVaultPreferences(vault)
        } catch (_: java.io.IOException) {
            // The vault operation already completed.
        }
    }
    fun setVaultPinned(id: VaultId, pinned: Boolean) {
        if (_state.value.vaults.none { it.id == id }) return
        viewModelScope.launch {
            try {
                securityPreferences.setVaultPinned(id, pinned)
            } catch (error: java.io.IOException) {
                showError(error)
            }
        }
    }
    fun setWorkspaceTabsEnabled(enabled: Boolean) = workspace.setEnabled(enabled)
    fun addWorkspaceTab() = workspace.addTab()
    fun duplicateWorkspaceTab(id: Int) = workspace.duplicateTab(id)
    fun selectWorkspaceTab(id: Int) = workspace.selectTab(id)
    fun pinWorkspaceTab(id: Int, pinned: Boolean) = workspace.setPinned(id, pinned)
    fun moveWorkspaceTab(id: Int, offset: Int) = workspace.moveTab(id, offset)
    fun closeWorkspaceTab(id: Int) = workspace.closeTab(id)
    fun closeOtherWorkspaceTabs(id: Int) = workspace.closeOtherTabs(id)
    fun toggleFolderPin(directory: DirectoryId) = locations.togglePin(directory)
    fun resetFolderView() = locations.resetCurrentFolderView()
    fun openLocationFolder(folder: VaultNodeMetadata) = locations.openPickerFolder(folder)
    fun navigateLocationPicker(id: DirectoryId) = locations.pickerToDirectory(id)
    fun loadMoreLocationFolders() = locations.loadMoreFolders()
    fun retryLocationPicker() = locations.retryPicker()
    fun applySearchScope() = locations.applyScope()
    fun closeLocationPicker() {
        if (_state.value.locationPicker?.mode == OnlyFilesLocationMode.IMPORT_DESTINATION) clearBrowserImport()
        clearPendingExtraction()
        locations.closePicker()
    }

    private fun clearBrowserImport() = browserImports.clear()
    fun openPinnedFolder(id: DirectoryId) {
        locations.closePicker()
        locations.navigate(id)
    }

    fun createZip(nodes: List<VaultNodeMetadata>, name: String, compression: Int, successMessage: String, password: CharArray?) =
        archives.create(nodes, name, compression, successMessage, password)

    fun requestZipExtraction(request: VaultZipExtraction, chooseDestination: Boolean) {
        if (_state.value.busy || _state.value.selectedVaultId != request.node.ref.vaultId) {
            request.password?.fill('\u0000')
            return
        }
        if (chooseDestination) {
            clearPendingExtraction()
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
        pendingExtraction = null
        closeLocationPicker()
        extractZip(request, directory)
    }

    fun cancelArchiveOperation() = archives.cancel()

    private fun clearPendingExtraction() {
        pendingExtraction?.password?.fill('\u0000')
        pendingExtraction = null
    }

    private fun extractZip(request: VaultZipExtraction, parent: DirectoryId) =
        archives.extract(
            request.node, parent, request.selection, request.name, request.password, request.intoExisting
        )

    fun showViewerImage(ref: VaultNodeRef) {
        _state.update { current ->
            if (current.viewer?.isViewableImage() != true) return@update current
            val image = current.viewerQueue.firstOrNull { it.ref == ref } ?: return@update current
            current.copy(viewer = image)
        }
    }

    fun stepViewerAudio(offset: Int) {
        _state.update { current ->
            val track = current.viewer ?: return@update current
            if (track.builtInOpen() != BuiltInFileOpen.AUDIO) return@update current
            val index = current.viewerQueue.indexOfFirst { it.ref == track.ref }
            val next = current.viewerQueue.getOrNull(index + offset) ?: return@update current
            current.copy(viewer = next)
        }
    }

    fun showViewerAudio(ref: VaultNodeRef) {
        _state.update { current ->
            if (current.viewer?.builtInOpen() != BuiltInFileOpen.AUDIO) return@update current
            val next = current.viewerQueue.firstOrNull { it.ref == ref } ?: return@update current
            current.copy(viewer = next)
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

    fun toggleSelection(nodeId: String) {
        browser.cancelSelection()
        _state.update { state ->
            val selected = state.selectedNodeIds
            state.copy(selectedNodeIds = if (nodeId in selected) selected - nodeId else selected + nodeId)
        }
    }

    fun selectRange(opaqueIds: List<String>) {
        browser.cancelSelection()
        _state.update { it.copy(selectedNodeIds = it.selectedNodeIds + opaqueIds) }
    }
    fun selectAll() = browser.selectRows(invert = false)
    fun invertSelection() = browser.selectRows(invert = true)
    fun cancelSelection() = browser.cancelSelection()
    fun clearSelection() {
        browser.cancelSelection()
        _state.update { it.copy(selectedNodeIds = emptySet()) }
    }

    fun createFolder(name: String) = browser.createFolder(name)
    fun createEmptyFile(name: String) = browser.createEmptyFile(name)
    fun createSyntheticFile(name: String, size: Long) = browser.createSyntheticFile(name, size)
    fun rename(node: VaultNodeMetadata, name: String, onRenamed: (VaultNodeMetadata) -> Unit = {}) =
        browser.rename(node, name, onRenamed)
    fun batchRename(items: List<VaultRenameItem>) = browser.batchRename(items)
    suspend fun batchRenameSiblingNames(items: List<VaultRenameItem>) = fileSystem.batchRenameSiblingNames(items)
    fun delete(nodes: List<VaultNodeMetadata>, onDeleted: (Set<String>) -> Unit = {}) = browser.delete(nodes, onDeleted)

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
                displayNodes = clipboard.displayNodes - removed,
                mergeRetries = clipboard.mergeRetries - removed.ref.backendIdentity
            ).takeIf { remaining.isNotEmpty() })
        }
    }

    fun paste() = transfers.paste()
    fun cancelTransfer() { transfers.cancel(); boundary.cancel(); recovery.cancel() }
    fun resolveConflict(id: Long, response: VaultConflictResponse) {
        transfers.conflictReview.resolve(id, response)
        boundary.conflictReview.resolve(id, response)
        recovery.conflictReview.resolve(id, response)
        archives.conflictReview.resolve(id, response)
    }
    fun openRecovery() = recovery.open()
    fun closeRecovery() = recovery.close()
    fun discardRecovery(queue: VaultRecoveryQueue) = recovery.discard(queue)
    fun resumeRecovery(queue: VaultRecoveryQueue) = recovery.resume(queue)
    fun beginExportSelection(nodes: List<VaultNodeMetadata>): Boolean = boundary.prepare(nodes)
    fun resumeLocalExport() = boundary.resume()
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

    fun moveVaultItemsToTrash(nodes: List<VaultNodeMetadata>) {
        val vault = _state.value.selectedVaultId ?: return
        if (nodes.isEmpty() || nodes.any { it.ref.vaultId != vault || !it.ref.capabilities.canExport || !it.ref.capabilities.canDeletePermanently }) return
        if (boundary.prepare(nodes)) boundary.startTrash()
    }

    fun issueExternalAccess(
        nodes: List<VaultNodeMetadata>,
        onReady: (List<VaultExternalGrant>) -> Unit
    ) = issueVaultHandoff(nodes, plaintextFallback = false, onReady)

    fun issuePlaintextFallbackAccess(
        nodes: List<VaultNodeMetadata>,
        onReady: (List<VaultExternalGrant>) -> Unit
    ) = issueVaultHandoff(nodes, plaintextFallback = true, onReady)

    fun issuePackageAccess(
        node: VaultNodeMetadata,
        onReady: (VaultExternalGrant) -> Unit,
        onFailure: () -> Unit
    ) = issueVaultHandoff(
        listOf(node), plaintextFallback = false, onReady = { onReady(it.single()) }, onFailure = onFailure
    )

    private fun issueVaultHandoff(
        nodes: List<VaultNodeMetadata>,
        plaintextFallback: Boolean,
        onReady: (List<VaultExternalGrant>) -> Unit,
        onFailure: () -> Unit = {}
    ) {
        val current = _state.value
        val vault = current.selectedVaultId
        if (vault == null || nodes.any { it.ref.vaultId != vault }) return onFailure()
        externalAccess.issue(nodes, plaintextFallback, isCurrentMount = {
            _state.value.mountGeneration == current.mountGeneration && vault in repository.unlockedVaultIds.value
        }, onReady, onFailure)
    }

    fun revokeExternalAccess(grants: List<VaultExternalGrant>) {
        externalAccess.revoke(grants)
    }

    suspend fun vaultExternalGrants(vault: VaultId, mount: Long): List<VaultExternalGrant> {
        if (!isCurrentVault(vault, mount)) return emptyList()
        val grants = withContext(Dispatchers.IO) { externalAccessManager.activeGrants(vault) }
        return grants.takeIf { isCurrentVault(vault, mount) }.orEmpty()
    }

    suspend fun revokeVaultExternalGrant(vault: VaultId, mount: Long, token: String) {
        withContext(Dispatchers.IO) {
            if (isCurrentVault(vault, mount) && externalAccessManager.activeGrants(vault).any { it.token == token }) {
                externalAccessManager.revoke(token)
            }
        }
    }

    private fun isCurrentVault(vault: VaultId, mount: Long): Boolean = _state.value.let {
        it.selectedVaultId == vault && it.mountGeneration == mount && vault in repository.unlockedVaultIds.value
    }

    suspend fun vaultStorageUsage(vault: VaultId) = fileSystem.storageUsage(vault)

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

    fun beginVaultBackup() = localPicker.openBackupDestination()
    fun beginRestoreBackup() = localPicker.openBackupSource()
    fun dismissBackupRequest() = backups.dismiss()
    fun submitBackup(label: String, password: String, weakConfirmed: Boolean) = backups.submit(label, password, weakConfirmed)
    fun cancelBackup() = backups.cancel()

    fun recoverVaultTransactions() {
        val mount = _state.value
        val id = mount.selectedVaultId ?: return
        if (mount.healthReport?.issues?.none { it.code == "pending_transaction" } != false) return
        runBusy {
            healthService.recoverTransactions(id).fold(
                onSuccess = {
                    if (_state.value.mountGeneration == mount.mountGeneration) {
                        reload()
                        healthService.verify(id, VaultHealthMode.QUICK).fold(
                            onSuccess = { report ->
                                _state.update {
                                    if (it.mountGeneration == mount.mountGeneration) it.copy(healthReport = report) else it
                                }
                            },
                            onFailure = ::showError
                        )
                    }
                },
                onFailure = ::showError
            )
        }
    }

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
    fun clearMessage() = _state.update { it.copy(message = null, completedArchive = null, operationFeedback = null) }
    fun undoOperationFeedback(feedback: OnlyFilesOperationFeedback) = undo.undo(feedback)
    fun dismissOperationFeedback(id: String) = _state.update {
        if (it.operationFeedback?.id == id) it.copy(operationFeedback = null) else it
    }
    fun revealOperationDestination(feedback: OnlyFilesOperationFeedback, openLocal: (String?) -> Unit) {
        val current = _state.value
        if (!feedback.belongsTo(current) || current.operationFeedback?.id != feedback.id) return
        dismissOperationFeedback(feedback.id)
        when (val target = feedback.destination) {
            is OnlyFilesOperationDestination.VaultDirectory -> {
                if (current.selectedVaultId == target.vaultId) locations.navigate(target.directoryId)
            }
            is OnlyFilesOperationDestination.LocalDirectory -> openLocal(target.path)
            OnlyFilesOperationDestination.MainTrash -> openLocal(null)
            null -> Unit
        }
    }
    fun openReader(ref: VaultNodeRef): Result<VaultSeekableReader> = fileSystem.openReader(ref)
    suspend fun folderTotals(ref: VaultNodeRef) = fileSystem.folderTotals(ref)
    suspend fun selectionTotals(refs: List<VaultNodeRef>) = fileSystem.selectionTotals(refs)

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
        workspace.clear()
        recovery.cancel()
        recovery.close()
        val requested = pendingEntry?.takeIf { it.vaultId == vaultId }
        pendingEntry = null
        if (requested != null) consumedEntry = requested
        val settings = securityPreferences.settings.value
        _state.update {
            it.copy(
                selectedVaultId = vaultId,
                mountGeneration = it.mountGeneration + 1L,
                exportRetries = it.exportRetries.filter { retry -> retry.source.vaultId == vaultId },
                workspace = VaultWorkspace.mounted(),
                workspaceTabsEnabled = settings.workspaceTabsEnabled,
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
                viewerQueue = emptyList(),
                viewerSelectionEnabled = false,
                operationFeedback = null
            )
        }
        if (browserImports.hasPending) {
            reload()
            locations.openImportDestination()
        } else if (requested != null) locations.navigate(requested.directoryId, requested.focusNodeId)
        else locations.restore(settings.lastDirectories[vaultId])
        workspace.restorePins(settings.pinnedWorkspaceTabs[vaultId].orEmpty())
        recovery.notifyAfterUnlock()
    }

    private fun reload() = browser.reload()

    private fun clearSensitiveUiState(keepClipboard: Boolean = false) {
        transfers.clear()
        boundary.clear()
        dismissVaultSession(keepClipboard)
    }

    private fun dismissVaultSession(keepClipboard: Boolean = false) {
        recovery.cancel()
        recovery.close()
        pendingEntry = null
        clearPendingExtraction()
        clearBrowserImport()
        archives.cancel()
        workspace.clear()
        locations.cancelNavigation()
        locations.closePicker()
        browser.cancel()
        localPicker.cancel()
        backups.cancel()
        boundary.cancelSelection()
        cancelImportSelection()
        _state.update {
            it.copy(
                selectedVaultId = null,
                pendingMoveIntoVault = emptyList(),
                mountGeneration = it.mountGeneration + 1L,
                workspace = VaultWorkspace(),
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
                exportRetries = emptyList(),
                viewer = null,
                viewerQueue = emptyList(),
                viewerSelectionEnabled = false,
                properties = emptyList(),
                pendingConflict = null,
                healthReport = null,
                message = null,
                batchProgress = null,
                archiveProgress = null,
                completedArchive = null,
                operationFeedback = null
            )
        }
        transfers.conflictReview.dismiss()
        boundary.conflictReview.dismiss()
        archives.conflictReview.dismiss()
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
        _state.update { it.copy(message = null, operationFeedback = vaultOperationFailure(error, R.string.onlyfiles_error_operation, it.mountGeneration)) }
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

    fun setNumericPasswordKeyboard(enabled: Boolean) {
        viewModelScope.launch { securityPreferences.setNumericPasswordKeyboard(enabled) }
    }
    fun setBackgroundLockDelay(delay: dev.qtremors.arcile.core.vault.domain.VaultBackgroundLockDelay) {
        viewModelScope.launch { securityPreferences.setBackgroundLockDelay(delay) }
    }
    fun setLockOnScreenOff(enabled: Boolean) {
        viewModelScope.launch { securityPreferences.setLockOnScreenOff(enabled) }
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
        clearPendingExtraction()
        transfers.clear()
        boundary.clear()
        cancelImportSelection()
        folderPicker.clear()
    }
}
