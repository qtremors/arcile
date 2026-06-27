package dev.qtremors.arcile.feature.browser
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.SearchRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageBrowserLocation
import dev.qtremors.arcile.core.storage.domain.NoOpStorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.usecase.GetStorageVolumesUseCase
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.feature.browser.delegate.ArchiveActionDelegate
import dev.qtremors.arcile.feature.browser.delegate.BrowserOperationDelegate
import dev.qtremors.arcile.feature.browser.delegate.ClipboardDelegate
import dev.qtremors.arcile.feature.browser.delegate.NavigationDelegate
import dev.qtremors.arcile.feature.browser.delegate.PropertiesDelegate
import dev.qtremors.arcile.feature.browser.delegate.SearchDelegate
import dev.qtremors.arcile.feature.browser.delegate.UndoDelegate
import dev.qtremors.arcile.shared.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.shared.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import javax.inject.Inject
import java.io.File
@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val searchRepository: SearchRepository,
    private val clipboardRepository: ClipboardRepository,
    private val trashRepository: TrashRepository,
    private val archiveRepository: ArchiveRepository,
    private val volumeRepository: VolumeRepository,
    private val browserPreferencesRepository: BrowserPreferencesStore,
    private val savedStateHandle: SavedStateHandle,
    private val getStorageVolumesUseCase: GetStorageVolumesUseCase,
    private val bulkFileCoordinator: BulkFileOperationCoordinator,
    private val storageMutationNotifier: StorageMutationNotifier = NoOpStorageMutationNotifier
) : ViewModel() {
    private val _state = MutableStateFlow(BrowserState())
    val state: StateFlow<BrowserState> = _state.asStateFlow()
    private val scrollPositions = decodeScrollPositions(
        savedStateHandle.get<Array<String>>(SavedScrollPositionsKey)?.toList().orEmpty()
    ).toMutableMap()
    val navigationState: StateFlow<BrowserNavigationState> = _state
        .map { it.navigationState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.navigationState())
    val listingState: StateFlow<BrowserListingState> = _state
        .map { it.listingState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.listingState())
    val selectionState: StateFlow<BrowserSelectionState> = _state
        .map { it.selectionState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.selectionState())
    val searchState: StateFlow<BrowserSearchState> = _state
        .map { it.searchState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.searchState())
    val dialogState: StateFlow<BrowserDialogState> = _state
        .map { it.dialogState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.dialogState())
    val operationUiState: StateFlow<OperationUiState> = _state
        .map { it.operationUiState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), _state.value.operationUiState())
    private val _nativeRequestFlow = MutableSharedFlow<android.content.IntentSender>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val nativeRequestFlow: SharedFlow<android.content.IntentSender> = _nativeRequestFlow.asSharedFlow()
    private val searchDelegate = SearchDelegate(_state, viewModelScope, searchRepository)
    private val navigationDelegate = NavigationDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        fileBrowserRepository = fileBrowserRepository,
        archiveRepository = archiveRepository,
        searchRepository = searchRepository,
        browserPreferencesRepository = browserPreferencesRepository,
        savedStateHandle = savedStateHandle,
        onClearSearch = { searchDelegate.updateBrowserSearchQuery("") }
    )
    private val clipboardDelegate = ClipboardDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        clipboardRepository = clipboardRepository,
        bulkFileOperationCoordinator = bulkFileCoordinator,
        refreshAction = { navigationDelegate.refresh() }
    )
    private val operationDelegate = BrowserOperationDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        trashRepository = trashRepository,
        bulkFileOperationCoordinator = bulkFileCoordinator,
        refreshAction = { navigationDelegate.refresh() }
    )
    private val archiveActionDelegate = ArchiveActionDelegate(
        state = _state,
        coroutineScope = viewModelScope,
        archiveRepository = archiveRepository,
        bulkFileOperationCoordinator = bulkFileCoordinator,
        clearSelection = { clearSelection() }
    )
    private val propertiesDelegate = PropertiesDelegate(
        state = _state,
        viewModelScope = viewModelScope,
        fileBrowserRepository = fileBrowserRepository,
        archiveRepository = archiveRepository
    )
    private val undoDelegate = UndoDelegate(
        state = _state,
        coroutineScope = viewModelScope,
        fileMutationRepository = fileMutationRepository,
        clipboardRepository = clipboardRepository,
        trashRepository = trashRepository,
        refreshAction = { navigationDelegate.refresh() }
    )
    private val deleteFlowDelegate = DeleteFlowDelegate(
        coroutineScope = viewModelScope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = object : DeleteStateCallbacks {
            override fun getSelectedFiles(): List<String> = _state.value.selectedFiles.toList()
            override fun isPermanentDeleteChecked(): Boolean = _state.value.isPermanentDeleteChecked
            override fun isPermanentDeleteToggleEnabled(): Boolean = _state.value.isPermanentDeleteToggleEnabled
            override fun setLoading(isLoading: Boolean) {
                _state.update { it.copy(isLoading = isLoading) }
            }
            override fun showMixedDeleteExplanation() {
                _state.update { it.copy(showMixedDeleteExplanation = true) }
            }
            override fun showPermanentDeleteConfirmation() {
                _state.update {
                    it.copy(
                        showPermanentDeleteConfirmation = true,
                        isPermanentDeleteChecked = true,
                        isPermanentDeleteToggleEnabled = false
                    )
                }
            }
            override fun showTrashConfirmation() {
                _state.update {
                    it.copy(
                        showTrashConfirmation = true,
                        isPermanentDeleteChecked = false,
                        isPermanentDeleteToggleEnabled = true
                    )
                }
            }
            override fun togglePermanentDeleteChecked() {
                _state.update { it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked) }
            }
            override fun isShredChecked(): Boolean = _state.value.isShredChecked
            override fun toggleShredChecked() {
                _state.update { it.copy(isShredChecked = !it.isShredChecked) }
            }
            override fun dismissDeleteConfirmation() {
                _state.update {
                    it.copy(
                        showTrashConfirmation = false,
                        showPermanentDeleteConfirmation = false,
                        showMixedDeleteExplanation = false,
                        deleteDecision = null,
                        isShredChecked = false
                    )
                }
            }
            override fun setError(error: String) {
                _state.update { it.copy(error = UiText.Dynamic(error)) }
            }
            override fun setError(error: UiText) {
                _state.update { it.copy(error = error) }
            }
            override fun setDeleteDecision(decision: DeleteDecision) {
                _state.update { it.copy(deleteDecision = decision) }
            }
            override fun setPendingNativeAction() {
                _state.update { it.copy(pendingNativeAction = BrowserNativeAction.TRASH) }
            }
            override fun clearSelection() {
                _state.update { it.copy(selectedFiles = persistentSetOf()) }
            }
        },
        startBulkDeleteOperation = { type, selected ->
            bulkFileCoordinator.startOperation(
                type = type,
                sourcePaths = selected,
                destinationPath = null,
                resolutions = emptyMap<String, ConflictResolution>()
            )
        },
        emitNativeRequest = { sender -> _nativeRequestFlow.emit(sender) },
        onSuccess = { navigationDelegate.refresh() }
    )
    private var isInitialized = false
    init {
        operationDelegate.hydrateActiveOperation()
        operationDelegate.observeRecoveryRecords()
        viewModelScope.launch {
            getStorageVolumesUseCase().collectLatest { volumes ->
                _state.update { it.copy(storageVolumes = volumes.toPersistentList()).withUpdatedDisplayState() }
                if (!isInitialized) {
                    isInitialized = true
                    when (val location = navigationDelegate.restoreLocationFromState()) {
                        StorageBrowserLocation.Roots -> navigationDelegate.openVolumeRoots()
                        is StorageBrowserLocation.Directory -> {
                            _state.update {
                                it.copy(
                                    currentPath = location.pathScope.absolutePath,
                                    currentVolumeId = location.pathScope.volumeId,
                                    isVolumeRootScreen = false,
                                    isCategoryScreen = false,
                                    activeCategoryName = ""
                                )
                            }
                            navigationDelegate.refresh()
                        }
                        is StorageBrowserLocation.Category -> {
                            _state.update {
                                it.copy(
                                    currentPath = "",
                                    currentVolumeId = location.categoryScope.volumeId,
                                    isVolumeRootScreen = false,
                                    isCategoryScreen = true,
                                    activeCategoryName = location.categoryScope.categoryName
                                )
                            }
                            navigationDelegate.refresh()
                        }
                        is StorageBrowserLocation.Archive -> {
                            navigationDelegate.openArchive(
                                archivePath = location.archivePath,
                                entryPrefix = location.entryPrefix,
                                seedHistory = false
                            )
                        }
                        null -> navigationDelegate.initializeFromArgs()
                    }
                } else {
                    val currentVolumeId = _state.value.currentVolumeId
                    if (currentVolumeId != null && volumes.none { it.id == currentVolumeId }) {
                        navigationDelegate.openVolumeRoots(UiText.StringResource(R.string.error_selected_storage_removed))
                    } else if (_state.value.isVolumeRootScreen) {
                        _state.update { it.copy(files = navigationDelegate.volumeFiles().toPersistentList()).withUpdatedDisplayState() }
                    }
                }
            }
        }
        viewModelScope.launch {
            fileBrowserRepository.observeFolderStatUpdates().collectLatest { update ->
                _state.update { currentState ->
                    if (currentState.isVolumeRootScreen || currentState.isCategoryScreen) {
                        return@update currentState
                    }
                    val visiblePaths = currentState.files
                        .asSequence()
                        .filter { it.isDirectory }
                        .map { it.absolutePath }
                        .toSet()
                    if (update.path !in visiblePaths) {
                        return@update currentState
                    }
                    currentState.copy(
                        folderStatsByPath = (currentState.folderStatsByPath + (update.path to update.stats)).toPersistentMap(),
                        folderStatsLoadingPaths = (currentState.folderStatsLoadingPaths - update.path).toPersistentSet()
                    ).withUpdatedDisplayState()
                }
            }
        }
        viewModelScope.launch {
            browserPreferencesRepository.preferencesFlow.collectLatest { prefs ->
                _state.update { currentState ->
                    val pathPresentation = if (currentState.isCategoryScreen) {
                        prefs.getPresentationForCategory(currentState.activeCategoryName)
                    } else if (currentState.currentPath.isNotEmpty()) {
                        prefs.getPresentationForPath(currentState.currentPath)
                    } else {
                        prefs.globalPresentation
                    }
                    currentState.copy(
                        browserSortOption = pathPresentation.sortOption,
                        browserViewMode = pathPresentation.viewMode,
                        browserListZoom = pathPresentation.listZoom,
                        browserGridMinCellSize = pathPresentation.gridMinCellSize,
                        browserShowThumbnails = pathPresentation.showThumbnails,
                        browserScrollbarEnabled = prefs.browserScrollbarEnabled,
                        showHiddenFiles = prefs.showHiddenFiles
                    ).withUpdatedDisplayState()
                }
            }
        }
        viewModelScope.launch {
            clipboardRepository.clipboardState.collectLatest { clipboard ->
                _state.update { it.copy(clipboardState = clipboard) }
            }
        }
        viewModelScope.launch {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            storageMutationNotifier.events
                .debounce(300L)
                .collectLatest { event ->
                    if (isInitialized && shouldRefreshForStorageMutation(event.paths)) {
                        navigationDelegate.refresh()
                    }
                }
        }
        operationDelegate.observeOperationEvents()
    }
    fun openFileBrowser(restorePersistentLocation: Boolean = false, errorMessage: String? = null) =
        navigationDelegate.openFileBrowser(restorePersistentLocation, errorMessage?.let(UiText::Dynamic))

    fun savedScrollPosition(key: String): BrowserScrollPosition? = scrollPositions[key]

    fun saveScrollPosition(key: String, position: BrowserScrollPosition) {
        scrollPositions[key] = position
        persistScrollPositions()
    }

    fun clearScrollPosition(key: String) {
        if (scrollPositions.remove(key) != null) {
            persistScrollPositions()
        }
    }

    fun requestOpenedFileReveal(path: String) {
        _state.update {
            it.copy(
                pendingRevealFilePath = path,
                pendingRevealReady = false
            )
        }
    }

    fun armOpenedFileReveal() {
        _state.update {
            if (it.pendingRevealFilePath == null) {
                it
            } else {
                it.copy(pendingRevealReady = true)
            }
        }
    }

    fun consumeOpenedFileReveal(path: String) {
        _state.update {
            if (it.pendingRevealFilePath == path) {
                it.copy(
                    pendingRevealFilePath = null,
                    pendingRevealReady = false
                )
            } else {
                it
            }
        }
    }
    fun navigateToSpecificFolder(path: String, seedInitialPathHistory: Boolean = true) =
        navigationDelegate.navigateToSpecificFolder(path, seedInitialPathHistory)
    fun navigateToCategory(categoryName: String, volumeId: String? = null) = navigationDelegate.navigateToCategory(categoryName, volumeId)
    fun navigateToFolder(path: String) = navigationDelegate.navigateToFolder(path)
    fun openArchive(path: String) = navigationDelegate.openArchive(path)
    fun submitArchivePassword(password: String) = navigationDelegate.submitArchivePassword(password)
    fun navigateBack(allowVolumeRootFallback: Boolean = true): Boolean =
        navigationDelegate.navigateBack(allowVolumeRootFallback)
    fun refresh(pullToRefresh: Boolean = false) = navigationDelegate.refresh(pullToRefresh)
    fun toggleSelection(path: String) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            currentState.reduce(
                BrowserSelectionEvent.Toggle(
                    path = path,
                    files = currentState.files,
                    folderStats = currentState.folderStatsByPath
                )
            )
        }
    }
    fun selectAll(paths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            currentState.reduce(
                BrowserSelectionEvent.SelectAll(
                    paths = paths,
                    files = currentState.files,
                    folderStats = currentState.folderStatsByPath
                )
            )
        }
    }
    fun invertSelection(allPaths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            currentState.reduce(
                BrowserSelectionEvent.Invert(
                    allPaths = allPaths,
                    files = currentState.files,
                    folderStats = currentState.folderStatsByPath
                )
            )
        }
    }
    private fun calculateSelectionSize(selectedPaths: Set<String>, currentFiles: List<FileModel>, folderStats: Map<String, FolderStats>): Long {
        return calculateBrowserSelectionSize(selectedPaths, currentFiles, folderStats)
    }
    fun selectMultiple(paths: List<String>) {
        if (_state.value.isVolumeRootScreen) return
        _state.update { currentState ->
            val updatedSelection = currentState.selectedFiles + paths
            currentState.copy(
                selectedFiles = updatedSelection.toPersistentSet(),
                selectedFilesTotalSize = calculateSelectionSize(updatedSelection, currentState.files, currentState.folderStatsByPath),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }
    fun clearSelection() {
        _state.update { it.reduce(BrowserSelectionEvent.Clear) }
    }
    fun selectFolderTab(path: String?) {
        _state.update { currentState -> currentState.reduce(BrowserNavigationEvent.SelectFolderTab(path)) }
    }
    fun updateBrowserSearchQuery(query: String) = searchDelegate.updateBrowserSearchQuery(query)
    fun updateSearchFilters(filters: SearchFilters) = searchDelegate.updateSearchFilters(filters)
    fun toggleSearchFilterMenu(visible: Boolean) = searchDelegate.toggleSearchFilterMenu(visible)
    fun updateBrowserPresentation(
        presentation: FileListingPreferences,
        applyToSubfolders: Boolean
    ) {
        if (_state.value.isVolumeRootScreen) return
        val normalized = presentation.normalized()
        _state.update {
            it.copy(
                browserSortOption = normalized.sortOption,
                browserViewMode = normalized.viewMode,
                browserListZoom = normalized.listZoom,
                browserGridMinCellSize = normalized.gridMinCellSize,
                browserShowThumbnails = normalized.showThumbnails
            ).withUpdatedDisplayState()
        }
        viewModelScope.launch {
            if (_state.value.isCategoryScreen) {
                browserPreferencesRepository.updatePathPresentation(
                    path = "category_${_state.value.activeCategoryName}",
                    presentation = normalized,
                    applyToSubfolders = false
                )
            } else {
                val path = _state.value.currentPath
                if (path.isNotEmpty()) {
                    browserPreferencesRepository.updatePathPresentation(path, normalized, applyToSubfolders)
                } else if (applyToSubfolders) {
                    browserPreferencesRepository.updateGlobalPresentation(normalized)
                }
            }
        }
    }
    fun createFolder(name: String) {
        if (_state.value.archiveContext != null) return
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty() || _state.value.isVolumeRootScreen) return
        viewModelScope.launch {
            fileMutationRepository.createDirectory(currentPath, name).onSuccess {
                _state.update { state ->
                    state.copy(
                        fileOperationStatusMessage = UiText.StringResource(R.string.file_operation_folder_created),
                        pendingUndoAction = BrowserUndoAction.Created(it.absolutePath)
                    )
                }
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_create_folder_failed)) }
            }
        }
    }
    fun createFile(name: String) {
        if (_state.value.archiveContext != null) return
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty() || _state.value.isVolumeRootScreen) return
        viewModelScope.launch {
            fileMutationRepository.createFile(currentPath, name).onSuccess {
                _state.update { state ->
                    state.copy(
                        fileOperationStatusMessage = UiText.StringResource(R.string.file_operation_file_created),
                        pendingUndoAction = BrowserUndoAction.Created(it.absolutePath)
                    )
                }
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_create_file_failed)) }
            }
        }
    }
    fun createFakeFile(name: String, size: Long) {
        if (_state.value.archiveContext != null) return
        val currentPath = _state.value.currentPath
        if (currentPath.isEmpty() || _state.value.isVolumeRootScreen) return
        bulkFileCoordinator.startOperation(
            type = BulkFileOperationType.CREATE_FAKE,
            sourcePaths = listOf(name),
            destinationPath = currentPath,
            resolutions = emptyMap<String, ConflictResolution>(),
            fakeFileSize = size
        )
    }
    fun extractArchive(target: ArchiveExtractionTarget, customDestination: String?) =
        archiveActionDelegate.extractArchive(target, customDestination)
    fun extractSelectedArchiveEntries(target: ArchiveExtractionTarget, customDestination: String?) =
        archiveActionDelegate.extractSelectedArchiveEntries(target, customDestination)
    fun extractCurrentArchiveFolder(target: ArchiveExtractionTarget, customDestination: String?) =
        archiveActionDelegate.extractCurrentArchiveFolder(target, customDestination)
    fun createArchiveFromSelection(
        archiveName: String,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.STORE,
        password: String? = null,
    ) {
        if (_state.value.archiveContext != null) return
        archiveActionDelegate.createArchiveFromSelection(
            archiveName = archiveName,
            format = format,
            compressionLevel = compressionLevel,
            password = password,
        )
    }
    fun createZipFromSelection() {
        if (_state.value.archiveContext != null) return
        archiveActionDelegate.createZipFromSelection()
    }
    fun requestDeleteSelected() {
        if (_state.value.archiveContext != null) return
        deleteFlowDelegate.requestDeleteSelected()
    }
    fun togglePermanentDelete() = deleteFlowDelegate.togglePermanentDelete()
    fun toggleShred() = deleteFlowDelegate.toggleShred()
    fun confirmDeleteSelected() {
        if (_state.value.archiveContext != null) return
        deleteFlowDelegate.confirmDeleteSelected()
    }
    fun dismissDeleteConfirmation() = deleteFlowDelegate.dismissDeleteConfirmation()
    fun moveSelectedToTrash() {
        if (_state.value.archiveContext != null) return
        deleteFlowDelegate.moveSelectedToTrash()
    }
    fun deleteSelectedPermanently() {
        if (_state.value.archiveContext != null) return
        deleteFlowDelegate.deleteSelectedPermanently()
    }
    fun handleNativeActionResult(confirmed: Boolean) {
        val pendingAction = _state.value.pendingNativeAction ?: return
        _state.update { it.copy(pendingNativeAction = null) }
        if (!confirmed) return
        when (pendingAction) {
            BrowserNativeAction.TRASH -> confirmDeleteSelected()
        }
    }
    fun renameFile(path: String, newName: String) {
        if (_state.value.archiveContext != null) return
        val invalidChars = listOf('/', '\\', '\u0000')
        if (newName.isBlank() || invalidChars.any { newName.contains(it) } || newName.contains("..")) {
            _state.update { it.copy(error = UiText.StringResource(R.string.error_invalid_name)) }
            return
        }
        viewModelScope.launch {
            fileMutationRepository.renameFile(path, newName).onSuccess { renamed ->
                clearSelection()
                _state.update { state ->
                    state.copy(
                        fileOperationStatusMessage = UiText.StringResource(R.string.file_operation_renamed),
                        pendingUndoAction = BrowserUndoAction.Rename(
                            originalPath = path,
                            renamedPath = renamed.absolutePath
                        )
                    )
                }
                refresh()
            }.onFailure { error ->
                _state.update { it.copy(error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_rename_file_failed)) }
            }
        }
    }
    fun clearError() {
        _state.update { it.copy(error = null) }
    }
    fun dismissArchivePasswordPrompt() {
        _state.update {
            it.copy(
                pendingArchiveExtraction = null,
                archiveContext = it.archiveContext?.copy(passwordRequired = false)
            )
        }
    }
    fun clearFileOperationStatusMessage() = operationDelegate.clearStatusMessage()
    fun undoLastTrashMove() = undoDelegate.undoLastTrashMove()
    fun clearPendingTrashUndo() = undoDelegate.clearPendingTrashUndo()
    fun undoLastOperation() = undoDelegate.undoLastOperation()
    fun clearPendingUndo() = undoDelegate.clearPendingUndo()
    fun clearActiveFileOperation() = operationDelegate.clearActiveOperation()
    fun retryRecoveredOperation(operationId: String) = operationDelegate.retryRecoveredOperation(operationId)
    fun cleanupRecoveredOperation(operationId: String) = operationDelegate.cleanupRecoveredOperation(operationId)
    fun dismissRecoveredOperation(operationId: String) = operationDelegate.dismissRecoveredOperation(operationId)
    fun openPropertiesForSelection() = propertiesDelegate.openPropertiesForSelection()
    fun dismissProperties() = propertiesDelegate.dismissProperties()
    fun copySelectedToClipboard() = clipboardDelegate.copySelectedToClipboard()
    fun cutSelectedToClipboard() = clipboardDelegate.cutSelectedToClipboard()
    fun cancelClipboard() = clipboardDelegate.cancelClipboard()
    fun pasteFromClipboard() = clipboardDelegate.pasteFromClipboard()
    fun removeFromClipboard(path: String) = clipboardDelegate.removeFromClipboard(path)
    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) {
        if (_state.value.pendingArchiveExtraction != null) {
            archiveActionDelegate.confirmPendingExtraction(resolutions)
        } else {
            clipboardDelegate.resolveConflicts(resolutions)
        }
    }
    fun dismissConflictDialog() {
        if (_state.value.pendingArchiveExtraction != null) {
            _state.update {
                it.copy(
                    pendingArchiveExtraction = null,
                    pasteConflicts = persistentListOf(),
                    showConflictDialog = false
                )
            }
        } else {
            clipboardDelegate.dismissConflictDialog()
        }
    }
    fun submitArchiveExtractionPassword(password: String) = archiveActionDelegate.retryPendingExtractionWithPassword(password)

    private fun shouldRefreshForStorageMutation(paths: List<String>): Boolean {
        if (paths.isEmpty()) return true
        val stateValue = _state.value
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

    private fun persistScrollPositions() {
        savedStateHandle[SavedScrollPositionsKey] = scrollPositions.entries
            .toList()
            .takeLast(MaxSavedBrowserScrollEntries)
            .map { entry -> encodeScrollEntry(entry.key, entry.value) }
            .toTypedArray()
    }
}

private const val SavedScrollPositionsKey = "browserScrollPositions"
private const val MaxSavedBrowserScrollEntries = 32

private fun decodeScrollPositions(entries: List<String>): Map<String, BrowserScrollPosition> =
    entries.mapNotNull { entry ->
        val key = entry.scrollEntryKey() ?: return@mapNotNull null
        val position = entry.decodeScrollEntryPosition() ?: return@mapNotNull null
        key to position
    }.toMap()

private fun encodeScrollEntry(
    key: String,
    position: BrowserScrollPosition
): String = buildString {
    append(key.length)
    append(':')
    append(key)
    append(':')
    append(position.listIndex)
    append(':')
    append(position.listOffset)
    append(':')
    append(position.gridIndex)
    append(':')
    append(position.gridOffset)
}

private fun String.scrollEntryKey(): String? {
    val separatorIndex = indexOf(':')
    if (separatorIndex <= 0) return null
    val keyLength = substring(0, separatorIndex).toIntOrNull() ?: return null
    val keyStart = separatorIndex + 1
    val keyEnd = keyStart + keyLength
    if (keyEnd > length) return null
    return substring(keyStart, keyEnd)
}

private fun String.decodeScrollEntryPosition(): BrowserScrollPosition? {
    val key = scrollEntryKey() ?: return null
    val valuesStart = indexOf(':') + 1 + key.length
    if (valuesStart >= length || this[valuesStart] != ':') return null
    val values = substring(valuesStart + 1).split(':')
    if (values.size != 4) return null
    return BrowserScrollPosition(
        listIndex = values[0].toIntOrNull() ?: return null,
        listOffset = values[1].toIntOrNull() ?: return null,
        gridIndex = values[2].toIntOrNull() ?: return null,
        gridOffset = values[3].toIntOrNull() ?: return null
    )
}
