package dev.qtremors.arcile.feature.browser
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.BrowserLocationPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.NoOpStorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.feature.browser.delegate.BrowserConflictOwner
import dev.qtremors.arcile.feature.browser.delegate.openArchive
import dev.qtremors.arcile.feature.browser.delegate.submitArchivePassword
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject
@HiltViewModel
internal class BrowserViewModel @Inject constructor(
    private val fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val searchRepository: SearchRepository,
    private val clipboardRepository: ClipboardRepository,
    private val trashRepository: TrashRepository,
    private val archiveRepository: ArchiveRepository,
    private val archivePathResolver: ArchivePathResolver,
    private val volumeRepository: VolumeRepository,
    private val browserPreferencesRepository: BrowserLocationPreferencesStore,
    private val savedStateHandle: SavedStateHandle,
    private val getStorageVolumesUseCase: GetStorageVolumesUseCase,
    private val bulkFileCoordinator: BulkFileOperationCoordinator,
    private val storageMutationNotifier: StorageMutationNotifier = NoOpStorageMutationNotifier
) : ViewModel() {
    private val scrollPositionStore = BrowserScrollPositionStore(savedStateHandle)
    private val controllers = createBrowserControllerGraph(
        scope = viewModelScope,
        fileBrowserRepository = fileBrowserRepository,
        fileMutationRepository = fileMutationRepository,
        searchRepository = searchRepository,
        clipboardRepository = clipboardRepository,
        trashRepository = trashRepository,
        archiveRepository = archiveRepository,
        archivePathResolver = archivePathResolver,
        volumeRepository = volumeRepository,
        browserPreferencesRepository = browserPreferencesRepository,
        savedStateHandle = savedStateHandle,
        bulkFileCoordinator = bulkFileCoordinator
    )
    private val navigationController = controllers.navigation
    private val searchController = controllers.search
    private val propertiesController = controllers.properties
    private val selectionController = controllers.selection
    private val conflictController = controllers.conflicts
    private val operationController = controllers.operation
    private val pasteController = controllers.clipboard
    private val archiveController = controllers.archive
    private val mutationController = controllers.mutation
    private val revealController = controllers.reveal
    private val browserCoordinator = controllers.coordinator
    val uiState: StateFlow<BrowserUiState> = viewModelScope.composeBrowserUiState(
        navigation = navigationController.state,
        transient = controllers.transient.state,
        search = searchController.state,
        selection = selectionController.state,
        properties = propertiesController.state,
        operation = operationController.state,
        conflicts = conflictController.state,
        deletion = mutationController.state,
        archive = archiveController.state,
        reveal = revealController.state
    )
    private val initializer = BrowserInitializer(
        scope = viewModelScope,
        getStorageVolumes = getStorageVolumesUseCase,
        navigation = navigationController
    )
    val initializationState: StateFlow<BrowserInitializationState> = initializer.state

    init {
        operationController.startObserving()
        archiveController.startObserving()
        viewModelScope.launch {
            fileBrowserRepository.observeFolderStatUpdates().collectLatest { update ->
                navigationController.updateFolderStat(update.path, update.stats)
            }
        }
        viewModelScope.launch {
            browserPreferencesRepository.locationPreferencesFlow.collectLatest { prefs ->
                navigationController.applyPreferences(prefs)
            }
        }
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            storageMutationNotifier.events
                .debounce(300L)
                .collectLatest { event ->
                    if (
                        initializationState.value == BrowserInitializationState.Ready &&
                        shouldRefreshForStorageMutation(event.paths)
                    ) {
                        navigationController.refresh()
                    }
                }
        }
    }

    fun initialize(entryRequest: BrowserEntryRequest?) = initializer.initialize(entryRequest)
    fun retryInitialization() = initializer.retry()
    fun openFileBrowser(restorePersistentLocation: Boolean = false, errorMessage: String? = null) =
        navigationController.openFileBrowser(restorePersistentLocation, errorMessage?.let(UiText::Dynamic))

    fun savedScrollPosition(key: String): BrowserScrollPosition? = scrollPositionStore.get(key)

    fun saveScrollPosition(key: String, position: BrowserScrollPosition) {
        scrollPositionStore.save(key, position)
    }

    fun clearScrollPosition(key: String) {
        scrollPositionStore.clear(key)
    }

    fun requestOpenedFileReveal(path: String) = revealController.request(path)
    fun armOpenedFileReveal() = revealController.arm()
    fun consumeOpenedFileReveal(path: String) = revealController.consume(path)
    fun navigateToSpecificFolder(path: String, seedInitialPathHistory: Boolean = true) =
        navigationController.navigateToSpecificFolder(path, seedInitialPathHistory)
    fun navigateToCategory(categoryName: String, volumeId: String? = null) = navigationController.navigateToCategory(categoryName, volumeId)
    fun navigateToFolder(path: String) = navigationController.navigateToFolder(path)
    fun openArchive(path: String) = navigationController.openArchive(path)
    fun submitArchivePassword(password: String) = navigationController.submitArchivePassword(password)
    fun navigateBack(allowVolumeRootFallback: Boolean = true): Boolean =
        browserCoordinator.navigateBack(allowVolumeRootFallback)
    fun refresh(pullToRefresh: Boolean = false) = navigationController.refresh(pullToRefresh)
    fun toggleSelection(path: String) = selectionController.toggle(path)
    fun selectAll(paths: List<String>) = selectionController.selectAll(paths)
    fun invertSelection(allPaths: List<String>) = selectionController.invert(allPaths)
    fun selectMultiple(paths: List<String>) = selectionController.selectMultiple(paths)
    fun clearSelection() = selectionController.clear()
    fun selectFolderTab(path: String?) {
        browserCoordinator.selectFolderTab(path)
    }
    fun updateBrowserSearchQuery(query: String) = searchController.updateQuery(query)
    fun updateSearchFilters(filters: SearchFilters) = searchController.updateFilters(filters)
    fun toggleSearchFilterMenu(visible: Boolean) = searchController.setFilterMenuVisible(visible)
    fun toggleHiddenFiles() {
        val show = !navigationController.state.value.showHiddenFiles
        viewModelScope.launch {
            browserPreferencesRepository.updateShowHiddenFiles(show)
        }
    }
    fun updateBrowserPresentation(
        presentation: FileListingPreferences,
        applyToSubfolders: Boolean
    ) {
        if (navigationController.state.value.isVolumeRootScreen) return
        val normalized = presentation.normalized()
        navigationController.updatePresentation(normalized)
        viewModelScope.launch {
            if (navigationController.state.value.isCategoryScreen) {
                browserPreferencesRepository.updatePathPresentation(
                    path = "category_${navigationController.state.value.activeCategoryName}",
                    presentation = normalized,
                    applyToSubfolders = false
                )
            } else {
                val path = navigationController.state.value.currentPath
                if (path.isNotEmpty()) {
                    browserPreferencesRepository.updatePathPresentation(path, normalized, applyToSubfolders)
                } else if (applyToSubfolders) {
                    browserPreferencesRepository.updateGlobalPresentation(normalized)
                }
            }
        }
    }
    fun createFolder(name: String) = mutationController.createFolder(name)
    fun createFile(name: String) = mutationController.createFile(name)
    fun createFakeFile(name: String, size: Long) = mutationController.createFakeFile(name, size)
    fun extractArchive(target: ArchiveExtractionTarget, customDestination: String?) =
        archiveController.extractArchive(target, customDestination)
    fun extractSelectedArchiveEntries(target: ArchiveExtractionTarget, customDestination: String?) =
        archiveController.extractSelectedEntries(target, customDestination)
    fun extractCurrentArchiveFolder(target: ArchiveExtractionTarget, customDestination: String?) =
        archiveController.extractCurrentFolder(target, customDestination)
    fun createArchiveFromSelection(
        archiveName: String,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.STORE,
        password: String? = null,
    ) {
        if (navigationController.state.value.archiveContext != null) return
        archiveController.createArchive(
            archiveName = archiveName,
            format = format,
            compressionLevel = compressionLevel,
            password = password,
        )
    }
    fun createZipFromSelection() {
        if (navigationController.state.value.archiveContext != null) return
        archiveController.createZip()
    }
    fun requestDeleteSelected() = mutationController.requestDeleteSelected()
    fun togglePermanentDelete() = mutationController.togglePermanentDelete()
    fun toggleShred() = mutationController.toggleShred()
    fun confirmDeleteSelected() = mutationController.confirmDeleteSelected()
    fun dismissDeleteConfirmation() = mutationController.dismissDeleteConfirmation()
    fun moveSelectedToTrash() = mutationController.moveSelectedToTrash()
    fun deleteSelectedPermanently() = mutationController.deleteSelectedPermanently()
    fun handleAuthorizationResult(requestId: String, confirmed: Boolean) =
        operationController.handleAuthorizationResult(requestId, confirmed)

    fun handleAuthorizationUnavailable(requestId: String) =
        operationController.handleAuthorizationUnavailable(requestId)
    fun renameFile(path: String, newName: String) = mutationController.rename(path, newName)
    fun clearError() {
        searchController.clearError()
        controllers.transient.clearError()
        navigationController.clearError()
    }
    fun dismissArchivePasswordPrompt() {
        if (archiveController.state.value.pendingExtraction != null) {
            archiveController.dismissWorkflow()
        } else {
            navigationController.dismissArchivePasswordPrompt()
        }
    }
    fun clearFileOperationStatusMessage() = operationController.clearStatusMessage()
    fun undoLastTrashMove() = operationController.undoLastTrashMove()
    fun clearPendingTrashUndo() = operationController.clearPendingTrashUndo()
    fun undoLastOperation() = operationController.undoLastOperation()
    fun clearPendingUndo() = operationController.clearPendingUndo()
    fun clearActiveFileOperation() = operationController.clearActiveOperation()
    fun retryRecoveredOperation(operationId: String) = operationController.retryRecoveredOperation(operationId)
    fun cleanupRecoveredOperation(operationId: String) = operationController.cleanupRecoveredOperation(operationId)
    fun dismissRecoveredOperation(operationId: String) = operationController.dismissRecoveredOperation(operationId)
    fun openPropertiesForSelection() = propertiesController.openForSelection()
    fun dismissProperties() = propertiesController.dismiss()
    fun copySelectedToClipboard() = pasteController.copySelected()
    fun cutSelectedToClipboard() = pasteController.cutSelected()
    fun cancelClipboard() = pasteController.cancel()
    fun pasteFromClipboard() = pasteController.paste()
    fun removeFromClipboard(path: String) = pasteController.remove(path)
    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) {
        when (conflictController.state.value.owner) {
            BrowserConflictOwner.ARCHIVE -> archiveController.confirmPendingExtraction(resolutions)
            BrowserConflictOwner.PASTE -> pasteController.resolveConflicts(resolutions)
            null -> Unit
        }
    }
    fun dismissConflictDialog() {
        when (conflictController.state.value.owner) {
            BrowserConflictOwner.ARCHIVE -> archiveController.dismissWorkflow()
            BrowserConflictOwner.PASTE, null -> conflictController.dismiss()
        }
    }
    fun submitArchiveExtractionPassword(password: String) = archiveController.retryWithPassword(password)

    private fun shouldRefreshForStorageMutation(paths: List<String>): Boolean {
        if (paths.isEmpty()) return true
        val stateValue = navigationController.state.value
        if (stateValue.isCategoryScreen || stateValue.isVolumeRootScreen) return true
        val archivePath = stateValue.archiveContext?.archivePath
        if (!archivePath.isNullOrBlank()) {
            return paths.any { archivePath.isSameOrAncestorOf(it) }
        }
        val currentPath = stateValue.currentPath.takeIf { it.isNotBlank() } ?: return true
        return paths.any { changed ->
            currentPath.isSameOrAncestorOf(changed)
        }
    }

    private fun String.isSameOrAncestorOf(other: String): Boolean {
        val current = normalizeStoragePath()
        val target = other.normalizeStoragePath()
        return current == target || target.startsWith("$current/")
    }

    private fun String.normalizeStoragePath(): String =
        replace('\\', '/').trimEnd('/')

    override fun onCleared() {
        operationController.stopObserving()
        archiveController.stopObserving()
        super.onCleared()
    }

}
