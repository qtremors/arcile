package dev.qtremors.arcile.feature.imagegallery

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.runtime.R as RuntimeR
import dev.qtremors.arcile.core.storage.domain.BrowserPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.storage.domain.FileSortOption
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.shared.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.shared.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.shared.presentation.filterAndSortFiles
import dev.qtremors.arcile.shared.presentation.toUiModel
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataUpdate
import dev.qtremors.arcile.shared.ui.metadata.ImageMetadataWriteResult
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import java.io.File

@androidx.compose.runtime.Immutable
data class ImageGalleryOperationUiState(
    val type: BulkFileOperationType,
    val totalItems: Int,
    val completedItems: Int = 0,
    val currentPath: String? = null,
    val bytesCopied: Long? = null,
    val totalBytes: Long? = null,
    val sourcePaths: List<String> = emptyList(),
    val isCancelling: Boolean = false,
    val terminalStatus: OperationCompletionStatus? = null,
    val startTimeMillis: Long = System.currentTimeMillis()
)

data class ImageGalleryState(
    val volumeId: String? = null,
    val files: PersistentList<FileModel> = persistentListOf(),
    val displayedFiles: PersistentList<FileModel> = persistentListOf(),
    val albums: PersistentList<ImageGalleryAlbum> = persistentListOf(),
    val selectedAlbumPath: String? = null,
    val selectedFiles: PersistentSet<String> = persistentSetOf(),
    val searchQuery: String = "",
    val presentation: BrowserPresentationPreferences = BrowserPresentationPreferences(
        sortOption = BrowserPresentationPreferences.DEFAULT_CATEGORY_SORT_OPTION,
        viewMode = BrowserViewMode.GRID,
        gridMinCellSize = 136f,
        showThumbnails = true
    ),
    val showFileDetails: Boolean = true,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSnapshotStale: Boolean = false,
    val error: UiText? = null,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val isShredChecked: Boolean = false,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: dev.qtremors.arcile.shared.presentation.PropertiesUiModel? = null,
    val isAspectRatio: Boolean = false,
    val isSectioned: Boolean = false,
    val imageGalleryGrouping: ImageGalleryGrouping = ImageGalleryGrouping.MONTH,
    val imageGalleryDefaultTab: ImageGalleryDefaultTab = ImageGalleryDefaultTab.PHOTOS,
    val galleryScrollbarEnabled: Boolean = true,
    val preferencesLoaded: Boolean = false,
    val albumPresentation: BrowserPresentationPreferences = BrowserPresentationPreferences(
        sortOption = FileSortOption.NAME_ASC,
        viewMode = BrowserViewMode.GRID,
        gridMinCellSize = 160f
    ),
    val aspectRatios: PersistentMap<String, Float> = persistentMapOf(),
    val clipboardState: ClipboardState? = null,
    val activeFileOperation: ImageGalleryOperationUiState? = null,
    val pasteConflicts: PersistentList<FileConflict> = persistentListOf(),
    val showConflictDialog: Boolean = false,
    val pasteDestinationAlbumPath: String? = null,
    val favoriteFiles: PersistentSet<String> = persistentSetOf(),
    val pinnedAlbums: PersistentSet<String> = persistentSetOf(),
    val albumCovers: PersistentMap<String, String> = persistentMapOf(),
    val viewerSessionInitialPath: String? = null,
    val viewerCurrentPath: String? = null,
    val viewerMetadataPath: String? = null,
    val viewerMetadataSavingPath: String? = null,
    val viewerMetadataRevision: Long = 0L,
    val viewerUiVisible: Boolean = true,
    val viewerRotationDegrees: PersistentMap<String, Float> = persistentMapOf(),
    val viewerEraseDialogPath: String? = null
)

internal fun ImageGalleryState.withoutGalleryPaths(paths: Collection<String>): ImageGalleryState {
    if (paths.isEmpty()) return this
    val removed = paths.map { it.replace('\\', '/') }.toSet()
    fun isRemoved(path: String): Boolean = path.replace('\\', '/') in removed
    val nextFiles = files.filterNot { isRemoved(it.absolutePath) }
    return copy(
        files = nextFiles.toPersistentList(),
        albums = buildImageGalleryAlbums(nextFiles).toPersistentList(),
        selectedFiles = selectedFiles.filterNot(::isRemoved).toPersistentSet(),
        favoriteFiles = favoriteFiles.filterNot(::isRemoved).toPersistentSet(),
        albumCovers = albumCovers.filterValues { !isRemoved(it) }.toPersistentMap()
    ).withResolvedDisplayedFiles()
}

internal fun ImageGalleryState.withResolvedDisplayedFiles(): ImageGalleryState {
    val albumFiltered = when (selectedAlbumPath) {
        "__favorites__" -> files.filter { it.absolutePath in favoriteFiles }
        null -> files
        else -> files.filter { galleryParentPath(it.absolutePath) == selectedAlbumPath }
    }

    return copy(displayedFiles = filterAndSortFiles(albumFiltered, searchQuery, presentation.sortOption).toPersistentList())
}

@HiltViewModel
class ImageGalleryViewModel @Inject constructor(
    private val repository: ImageGalleryRepository,
    private val fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val clipboardRepository: ClipboardRepository,
    private val volumeRepository: VolumeRepository,
    private val browserPreferencesStore: BrowserPreferencesStore,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(
        ImageGalleryState(
            volumeId = savedStateHandle.get<String>("volumeId")?.takeIf { it.isNotBlank() },
            viewerSessionInitialPath = savedStateHandle[KEY_VIEWER_SESSION_INITIAL_PATH],
            viewerCurrentPath = savedStateHandle[KEY_VIEWER_CURRENT_PATH],
            viewerMetadataPath = savedStateHandle[KEY_VIEWER_METADATA_PATH],
            viewerUiVisible = savedStateHandle[KEY_VIEWER_UI_VISIBLE] ?: true,
            viewerRotationDegrees = restoreViewerRotations(savedStateHandle[KEY_VIEWER_ROTATIONS]),
            viewerEraseDialogPath = savedStateHandle[KEY_VIEWER_ERASE_DIALOG_PATH]
        )
    )
    val state: StateFlow<ImageGalleryState> = _state.asStateFlow()

    private val _nativeRequestFlow = MutableSharedFlow<IntentSender>()
    val nativeRequestFlow: SharedFlow<IntentSender> = _nativeRequestFlow.asSharedFlow()

    private val deleteFlowDelegate = DeleteFlowDelegate(
        coroutineScope = viewModelScope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = object : DeleteStateCallbacks {
            override fun getSelectedFiles(): List<String> = _state.value.selectedFiles.toList()
            override fun isPermanentDeleteChecked(): Boolean = _state.value.isPermanentDeleteChecked
            override fun isPermanentDeleteToggleEnabled(): Boolean = _state.value.isPermanentDeleteToggleEnabled
            override fun setLoading(isLoading: Boolean) {
                _state.update { it.copy(isRefreshing = isLoading) }
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
            override fun setPendingNativeAction() = Unit
            override fun clearSelection() {
                _state.update { it.copy(selectedFiles = persistentSetOf()) }
            }
        },
        startBulkDeleteOperation = { type, selected ->
            bulkFileOperationCoordinator.startOperation(
                type = type,
                sourcePaths = selected,
                destinationPath = null,
                resolutions = emptyMap()
            )
        },
        emitNativeRequest = { _nativeRequestFlow.emit(it) },
        onSuccess = {
            _state.update { it.withoutGalleryPaths(pendingDeletePaths) }
            pendingDeletePaths = emptyList()
            loadImages(forceRefresh = true)
        },
        onFailure = { loadImages(forceRefresh = true) }
    )
    private var pendingDeletePaths: List<String> = emptyList()

    init {
        viewModelScope.launch {
            applyPreferences(browserPreferencesStore.preferencesFlow.first())
            loadImages(forceRefresh = false)
            browserPreferencesStore.preferencesFlow.drop(1).collectLatest { preferences ->
                applyPreferences(preferences)
            }
        }
        viewModelScope.launch {
            clipboardRepository.clipboardState.collectLatest { clipboard ->
                _state.update { it.copy(clipboardState = clipboard) }
            }
        }
        viewModelScope.launch {
            repository.mutationEvents.collect { event ->
                repository.invalidate(event.paths)
                _state.update { it.withoutGalleryPaths(event.paths) }
                loadImages(forceRefresh = true, silent = true)
            }
        }
        viewModelScope.launch {
            bulkFileOperationCoordinator.events.collect { event ->
                handleOperationEvent(event)
                if (event is BulkFileOperationEvent.Completed && event.request.type in refreshTypes) {
                    val invalidationPaths = (event.request.sourcePaths + listOfNotNull(event.request.destinationPath)).distinct()
                    repository.invalidate(invalidationPaths)
                    if (event.request.type != BulkFileOperationType.COPY) {
                        _state.update { it.withoutGalleryPaths(event.request.sourcePaths) }
                    }
                    loadImages(forceRefresh = true, silent = true)
                }
            }
        }
    }

    private fun handleOperationEvent(event: BulkFileOperationEvent) {
        when (event) {
            is BulkFileOperationEvent.Started -> {
                if (event.request.type in trackedOperationTypes) {
                    _state.update {
                        it.copy(
                            isRefreshing = true,
                            error = null,
                            activeFileOperation = event.request.toGalleryOperationUiState()
                        )
                    }
                }
            }
            is BulkFileOperationEvent.Progress -> {
                if (event.request.type in trackedOperationTypes) {
                    _state.update { state ->
                        state.copy(
                            isRefreshing = true,
                            activeFileOperation = event.request.toGalleryOperationUiState(
                                progress = event.progress,
                                startTimeMillis = state.activeFileOperation?.startTimeMillis
                            )
                        )
                    }
                }
            }
            is BulkFileOperationEvent.Cancelling -> {
                if (event.request.type in trackedOperationTypes) {
                    _state.update { state ->
                        state.copy(
                            activeFileOperation = state.activeFileOperation?.copy(isCancelling = true)
                                ?: event.request.toGalleryOperationUiState(isCancelling = true)
                        )
                    }
                }
            }
            is BulkFileOperationEvent.Completed -> {
                if (event.request.type in trackedOperationTypes) {
                    val clearsClipboard = event.request.type in clipboardOperationTypes
                    if (event.request.type in clipboardOperationTypes) {
                        clipboardRepository.clearClipboardState()
                    }
                    _state.update { state ->
                        state.copy(
                            isRefreshing = false,
                            clipboardState = if (clearsClipboard) null else state.clipboardState,
                            activeFileOperation = state.activeFileOperation
                                ?.copy(terminalStatus = OperationCompletionStatus.SUCCESS)
                                ?: event.request.toGalleryOperationUiState(
                                    completedItems = event.request.operationItemCount(),
                                    terminalStatus = OperationCompletionStatus.SUCCESS
                                )
                        )
                    }
                }
            }
            is BulkFileOperationEvent.Failed -> {
                if (event.request.type in trackedOperationTypes) {
                    val clearsClipboard = event.request.type in clipboardOperationTypes
                    if (event.request.type in clipboardOperationTypes) {
                        clipboardRepository.clearClipboardState()
                    }
                    _state.update { state ->
                        state.copy(
                            isRefreshing = false,
                            clipboardState = if (clearsClipboard) null else state.clipboardState,
                            activeFileOperation = state.activeFileOperation
                                ?.copy(terminalStatus = OperationCompletionStatus.FAILED)
                                ?: event.request.toGalleryOperationUiState(terminalStatus = OperationCompletionStatus.FAILED)
                        )
                    }
                }
            }
            is BulkFileOperationEvent.Cancelled -> {
                val request = event.request
                if (request == null || request.type in trackedOperationTypes) {
                    val clearsClipboard = request == null || request.type in clipboardOperationTypes
                    if (clearsClipboard) {
                        clipboardRepository.clearClipboardState()
                    }
                    _state.update { state ->
                        state.copy(
                            isRefreshing = false,
                            clipboardState = if (clearsClipboard) null else state.clipboardState,
                            activeFileOperation = state.activeFileOperation
                                ?.copy(terminalStatus = OperationCompletionStatus.CANCELLED)
                                ?: request?.toGalleryOperationUiState(terminalStatus = OperationCompletionStatus.CANCELLED)
                        )
                    }
                }
            }
            is BulkFileOperationEvent.RecoveryAvailable,
            is BulkFileOperationEvent.RecoveryCleanupCompleted,
            is BulkFileOperationEvent.RecoveryDismissed -> Unit
        }
    }

    private fun applyPreferences(preferences: BrowserPreferences) {
        _state.update { state ->
            val persistedPresentation = preferences.exactPathPresentationOptions[IMAGE_GALLERY_PREF_KEY]
                ?: state.presentation.copy(showThumbnails = preferences.globalPresentation.showThumbnails)
            state.copy(
                presentation = persistedPresentation.normalized(),
                showFileDetails = preferences.imageGalleryShowFileDetails,
                isAspectRatio = preferences.imageGalleryAspectRatio,
                isSectioned = preferences.imageGallerySectioned,
                imageGalleryGrouping = preferences.imageGalleryGrouping,
                imageGalleryDefaultTab = preferences.imageGalleryDefaultTab,
                galleryScrollbarEnabled = preferences.galleryScrollbarEnabled,
                preferencesLoaded = true,
                albumPresentation = preferences.albumPresentation,
                favoriteFiles = preferences.favoriteFiles.toPersistentSet(),
                pinnedAlbums = preferences.pinnedAlbums.toPersistentSet(),
                albumCovers = preferences.albumCovers.toPersistentMap()
            ).withDisplayedFiles()
        }
    }

    fun loadImages(forceRefresh: Boolean = true, silent: Boolean = false) {
        val volumeId = _state.value.volumeId
        _state.update {
            it.copy(
                isLoading = !silent && it.files.isEmpty(),
                isRefreshing = !silent && it.files.isNotEmpty(),
                error = null
            )
        }
        viewModelScope.launch {
            runCatching { repository.loadImages(volumeId, forceRefresh) }
                .onSuccess { snapshot ->
                    _state.update {
                        val next = it.copy(
                            files = snapshot.files.toPersistentList(),
                            albums = snapshot.albums.toPersistentList(),
                            isLoading = false,
                            isRefreshing = false,
                            isSnapshotStale = snapshot.isStale,
                            aspectRatios = snapshot.aspectRatios.toPersistentMap()
                        )
                        next.withDisplayedFiles()
                    }
                    if (snapshot.isStale) {
                        loadImages(forceRefresh = true, silent = true)
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_category_failed)
                        )
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update { it.copy(searchQuery = query).withDisplayedFiles() }
    }

    fun selectAlbum(path: String?) {
        _state.update { it.copy(selectedAlbumPath = path).withDisplayedFiles() }
    }

    fun updatePresentation(preferences: BrowserPresentationPreferences) {
        val normalized = preferences.normalized()
        _state.update { it.copy(presentation = normalized).withDisplayedFiles() }
        viewModelScope.launch {
            browserPreferencesStore.updatePathPresentation(
                path = IMAGE_GALLERY_PREF_KEY,
                presentation = normalized,
                applyToSubfolders = false
            )
        }
    }

    fun setShowFileDetails(show: Boolean) {
        _state.update { it.copy(showFileDetails = show) }
        viewModelScope.launch {
            browserPreferencesStore.updateImageGalleryShowFileDetails(show)
        }
    }

    fun toggleSelection(path: String) {
        _state.update {
            val nextSelection = if (path in it.selectedFiles) it.selectedFiles - path else it.selectedFiles + path
            it.copy(selectedFiles = nextSelection.toPersistentSet(), isPropertiesVisible = false, properties = null)
        }
    }

    fun selectMultiple(paths: List<String>) {
        _state.update { it.copy(selectedFiles = (it.selectedFiles + paths).toPersistentSet()) }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = persistentSetOf(), isPropertiesVisible = false, properties = null) }
    }

    fun selectAll() {
        _state.update { it.copy(selectedFiles = it.displayedFiles.map(FileModel::absolutePath).toPersistentSet()) }
    }

    fun invertSelection() {
        _state.update {
            val allPaths = it.displayedFiles.map(FileModel::absolutePath).toSet()
            val nextSelection = allPaths - it.selectedFiles
            it.copy(selectedFiles = nextSelection.toPersistentSet(), isPropertiesVisible = false, properties = null)
        }
    }

    fun requestDeleteSelected() {
        pendingDeletePaths = _state.value.selectedFiles.toList()
        deleteFlowDelegate.requestDeleteSelected()
    }
    fun confirmDeleteSelected() {
        pendingDeletePaths = _state.value.selectedFiles.toList().ifEmpty { pendingDeletePaths }
        deleteFlowDelegate.confirmDeleteSelected()
    }
    fun dismissDeleteConfirmation() = deleteFlowDelegate.dismissDeleteConfirmation()
    fun togglePermanentDelete() = deleteFlowDelegate.togglePermanentDelete()
    fun toggleShred() = deleteFlowDelegate.toggleShred()

    fun openPropertiesForSelection() {
        val selected = _state.value.selectedFiles.toList()
        if (selected.isEmpty()) return
        _state.update { it.copy(isPropertiesVisible = true, isPropertiesLoading = true, properties = null) }
        viewModelScope.launch {
            fileBrowserRepository.getSelectionProperties(selected)
                .onSuccess { properties ->
                    _state.update { it.copy(isPropertiesVisible = true, isPropertiesLoading = false, properties = properties.toUiModel()) }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isPropertiesVisible = false,
                            isPropertiesLoading = false,
                            error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_load_properties_failed)
                        )
                    }
                }
        }
    }

    fun dismissProperties() {
        _state.update { it.copy(isPropertiesVisible = false, isPropertiesLoading = false, properties = null) }
    }

    fun updateAspectRatio(enabled: Boolean) {
        viewModelScope.launch {
            browserPreferencesStore.updateImageGalleryAspectRatio(enabled)
        }
    }

    fun updateSectioned(enabled: Boolean) {
        viewModelScope.launch {
            browserPreferencesStore.updateImageGallerySectioned(enabled)
        }
    }

    fun updateGrouping(grouping: ImageGalleryGrouping) {
        viewModelScope.launch {
            browserPreferencesStore.updateImageGalleryGrouping(grouping)
        }
    }

    fun updateDefaultTab(tab: ImageGalleryDefaultTab) {
        viewModelScope.launch {
            browserPreferencesStore.updateImageGalleryDefaultTab(tab)
        }
    }

    fun updateAlbumPresentation(presentation: BrowserPresentationPreferences) {
        viewModelScope.launch {
            browserPreferencesStore.updateAlbumPresentation(presentation)
        }
    }

    fun eraseMetadata(filePath: String) {
        setViewerEraseDialogPath(null)
        viewModelScope.launch {
            _state.update { it.copy(viewerMetadataSavingPath = filePath) }
            val result = withContext(Dispatchers.IO) {
                ExifMetadataReader.eraseMetadataResult(filePath, context)
            }
            if (result == ImageMetadataWriteResult.Success) {
                repository.invalidate(listOf(filePath))
                _state.update {
                    it.copy(
                        viewerMetadataSavingPath = null,
                        viewerMetadataRevision = it.viewerMetadataRevision + 1
                    )
                }
                loadImages(forceRefresh = true, silent = true)
            } else {
                _state.update {
                    it.copy(
                        viewerMetadataSavingPath = null,
                        error = metadataWriteError(result)
                    )
                }
            }
        }
    }

    fun updateMetadata(filePath: String, update: ImageMetadataUpdate) {
        viewModelScope.launch {
            _state.update { it.copy(viewerMetadataSavingPath = filePath) }
            val result = withContext(Dispatchers.IO) {
                ExifMetadataReader.updateMetadata(filePath, update, context)
            }
            if (result == ImageMetadataWriteResult.Success) {
                repository.invalidate(listOf(filePath))
                _state.update {
                    it.copy(
                        viewerMetadataSavingPath = null,
                        viewerMetadataRevision = it.viewerMetadataRevision + 1
                    )
                }
                loadImages(forceRefresh = true, silent = true)
            } else {
                _state.update {
                    it.copy(
                        viewerMetadataSavingPath = null,
                        error = metadataWriteError(result)
                    )
                }
            }
        }
    }

    fun copySelectedToClipboard() {
        val selectedPaths = _state.value.selectedFiles
        val selectedFiles = _state.value.files.filter { it.absolutePath in selectedPaths }
        if (selectedFiles.isNotEmpty()) {
            clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, selectedFiles))
            clearSelection()
        }
    }

    fun cutSelectedToClipboard() {
        val selectedPaths = _state.value.selectedFiles
        val selectedFiles = _state.value.files.filter { it.absolutePath in selectedPaths }
        if (selectedFiles.isNotEmpty()) {
            clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.CUT, selectedFiles))
            clearSelection()
        }
    }

    fun pasteFromClipboard(destinationAlbumPath: String?) {
        if (!isPasteDestinationAlbumPath(destinationAlbumPath)) return
        val clipboard = _state.value.clipboardState ?: return
        val sourcePaths = clipboard.files.map { it.absolutePath }
        if (sourcePaths.isEmpty()) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isRefreshing = true,
                    error = null,
                    pasteDestinationAlbumPath = destinationAlbumPath
                )
            }
            clipboardRepository.detectCopyConflicts(sourcePaths, destinationAlbumPath!!).onSuccess { conflicts ->
                if (conflicts.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            isRefreshing = false,
                            pasteConflicts = conflicts.toPersistentList(),
                            showConflictDialog = true,
                            pasteDestinationAlbumPath = destinationAlbumPath
                        )
                    }
                } else {
                    executePaste(clipboard, destinationAlbumPath, emptyMap())
                }
            }.onFailure { error ->
                _state.update {
                    it.copy(
                        isRefreshing = false,
                        pasteDestinationAlbumPath = null,
                        error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_check_conflicts_failed)
                    )
                }
            }
        }
    }

    fun resolvePasteConflicts(resolutions: Map<String, ConflictResolution>) {
        val clipboard = _state.value.clipboardState ?: return
        val destination = _state.value.pasteDestinationAlbumPath ?: return
        if (!isPasteDestinationAlbumPath(destination)) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    showConflictDialog = false,
                    pasteConflicts = persistentListOf(),
                    isRefreshing = true
                )
            }
            executePaste(clipboard, destination, resolutions)
        }
    }

    fun dismissPasteConflictDialog() {
        _state.update {
            it.copy(
                showConflictDialog = false,
                pasteConflicts = persistentListOf(),
                pasteDestinationAlbumPath = null,
                isRefreshing = false
            )
        }
    }

    fun cancelClipboard() {
        bulkFileOperationCoordinator.cancelActiveOperation()
        clipboardRepository.clearClipboardState()
        dismissPasteConflictDialog()
    }

    fun removeFromClipboard(path: String) {
        val clipboard = clipboardRepository.clipboardState.value ?: return
        val updatedFiles = clipboard.files.filter { it.absolutePath != path }
        if (updatedFiles.isEmpty()) {
            clipboardRepository.clearClipboardState()
        } else {
            clipboardRepository.setClipboardState(clipboard.copy(files = updatedFiles))
        }
    }

    fun clearActiveFileOperation() {
        _state.update { it.copy(activeFileOperation = null) }
    }

    private fun executePaste(
        clipboard: ClipboardState,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>
    ) {
        val operationType = if (clipboard.operation == ClipboardOperation.CUT) {
            BulkFileOperationType.MOVE
        } else {
            BulkFileOperationType.COPY
        }
        val started = bulkFileOperationCoordinator.startOperation(
            type = operationType,
            sourcePaths = clipboard.files.map { it.absolutePath },
            destinationPath = destinationPath,
            resolutions = resolutions
        )

        if (started) {
            _state.update {
                it.copy(
                    isRefreshing = false,
                    pasteDestinationAlbumPath = null,
                    showConflictDialog = false,
                    pasteConflicts = persistentListOf()
                )
            }
        } else {
            _state.update {
                it.copy(
                    isRefreshing = false,
                    pasteDestinationAlbumPath = null,
                    error = UiText.StringResource(RuntimeR.string.error_operation_already_running)
                )
            }
        }
    }

    fun createZipFromSelection() {
        val selected = _state.value.selectedFiles.toList()
        if (selected.isEmpty()) return
        val firstFile = File(selected.first())
        val parentPath = firstFile.parent ?: return
        val defaultBaseName = if (selected.size == 1) {
            firstFile.nameWithoutExtension.ifBlank { "Archive" }
        } else {
            "Archive"
        }
        val targetZipPath = File(parentPath, "$defaultBaseName.zip").absolutePath.replace('\\', '/')

        viewModelScope.launch {
            val uniqueZipPath = withContext(Dispatchers.IO) {
                var file = File(targetZipPath)
                var counter = 1
                var uniquePath = targetZipPath
                while (file.exists()) {
                    uniquePath = File(parentPath, "${defaultBaseName}_$counter.zip").absolutePath.replace('\\', '/')
                    file = File(uniquePath)
                    counter++
                }
                uniquePath
            }

            bulkFileOperationCoordinator.startOperation(
                type = BulkFileOperationType.CREATE_ARCHIVE,
                sourcePaths = selected,
                destinationPath = uniqueZipPath,
                resolutions = emptyMap(),
                archiveFormat = ArchiveFormat.ZIP,
                archiveCompressionLevel = ArchiveCompressionLevel.STORE
            )
            clearSelection()
        }
    }

    fun renameFile(path: String, newName: String) {
        val invalidChars = listOf('/', '\\', '\u0000')
        if (newName.isBlank() || invalidChars.any { newName.contains(it) } || newName.contains("..")) {
            _state.update { it.copy(error = UiText.StringResource(R.string.error_invalid_name)) }
            return
        }
        viewModelScope.launch {
            fileMutationRepository.renameFile(path, newName).onSuccess { renamed ->
                clearSelection()
                loadImages(forceRefresh = true, silent = true)
            }.onFailure { error ->
                _state.update { it.copy(error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_rename_file_failed)) }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    fun toggleFavorite(path: String) {
        val isFav = path in _state.value.favoriteFiles
        viewModelScope.launch {
            browserPreferencesStore.updateFavorite(path, !isFav)
        }
    }

    fun togglePinnedAlbum(path: String) {
        val isPinned = path in _state.value.pinnedAlbums
        viewModelScope.launch {
            browserPreferencesStore.updatePinnedAlbum(path, !isPinned)
        }
    }

    fun startViewerSession(initialPath: String) {
        if (_state.value.viewerSessionInitialPath == initialPath) return
        savedStateHandle[KEY_VIEWER_SESSION_INITIAL_PATH] = initialPath
        savedStateHandle[KEY_VIEWER_CURRENT_PATH] = initialPath
        savedStateHandle[KEY_VIEWER_UI_VISIBLE] = true
        savedStateHandle.remove<String>(KEY_VIEWER_METADATA_PATH)
        savedStateHandle.remove<String>(KEY_VIEWER_ERASE_DIALOG_PATH)
        _state.update {
            it.copy(
                viewerSessionInitialPath = initialPath,
                viewerCurrentPath = initialPath,
                viewerMetadataPath = null,
                viewerUiVisible = true,
                viewerEraseDialogPath = null
            )
        }
    }

    fun setViewerCurrentPath(path: String?) {
        savedStateHandle[KEY_VIEWER_CURRENT_PATH] = path
        _state.update { it.copy(viewerCurrentPath = path) }
    }

    fun setViewerMetadataVisible(path: String?, visible: Boolean) {
        val metadataPath = if (visible) path else null
        savedStateHandle[KEY_VIEWER_METADATA_PATH] = metadataPath
        _state.update { it.copy(viewerMetadataPath = metadataPath) }
    }

    fun setViewerUiVisible(visible: Boolean) {
        savedStateHandle[KEY_VIEWER_UI_VISIBLE] = visible
        _state.update { it.copy(viewerUiVisible = visible) }
    }

    fun toggleViewerUi() {
        setViewerUiVisible(!_state.value.viewerUiVisible)
    }

    fun rotateViewerImage(path: String) {
        val currentRotation = _state.value.viewerRotationDegrees[path] ?: 0f
        val nextRotations = (_state.value.viewerRotationDegrees + (path to ((currentRotation + 90f) % 360f))).toPersistentMap()
        savedStateHandle[KEY_VIEWER_ROTATIONS] = encodeViewerRotations(nextRotations)
        _state.update { it.copy(viewerRotationDegrees = nextRotations) }
    }

    fun setViewerEraseDialogPath(path: String?) {
        savedStateHandle[KEY_VIEWER_ERASE_DIALOG_PATH] = path
        _state.update { it.copy(viewerEraseDialogPath = path) }
    }

    fun setAlbumCover(albumPath: String, coverPath: String) {
        viewModelScope.launch {
            browserPreferencesStore.updateAlbumCover(albumPath, coverPath)
        }
    }

    private fun ImageGalleryState.withDisplayedFiles(): ImageGalleryState = withResolvedDisplayedFiles()

    companion object {
        private const val IMAGE_GALLERY_PREF_KEY = "image_gallery"
        private const val KEY_VIEWER_SESSION_INITIAL_PATH = "image_viewer.session_initial_path"
        private const val KEY_VIEWER_CURRENT_PATH = "image_viewer.current_path"
        private const val KEY_VIEWER_METADATA_PATH = "image_viewer.metadata_path"
        private const val KEY_VIEWER_UI_VISIBLE = "image_viewer.ui_visible"
        private const val KEY_VIEWER_ROTATIONS = "image_viewer.rotations"
        private const val KEY_VIEWER_ERASE_DIALOG_PATH = "image_viewer.erase_dialog_path"
        private val refreshTypes = setOf(
            BulkFileOperationType.MOVE,
            BulkFileOperationType.COPY,
            BulkFileOperationType.TRASH,
            BulkFileOperationType.DELETE,
            BulkFileOperationType.SHRED
        )
        private val trackedOperationTypes = setOf(
            BulkFileOperationType.MOVE,
            BulkFileOperationType.COPY,
            BulkFileOperationType.CREATE_ARCHIVE
        )
        private val clipboardOperationTypes = setOf(
            BulkFileOperationType.MOVE,
            BulkFileOperationType.COPY
        )

        private fun restoreViewerRotations(encoded: ArrayList<String>?): PersistentMap<String, Float> =
            encoded.orEmpty()
                .mapNotNull { value ->
                    val separator = value.indexOf('\t')
                    if (separator <= 0) return@mapNotNull null
                    val rotation = value.substring(0, separator).toFloatOrNull() ?: return@mapNotNull null
                    value.substring(separator + 1) to rotation
                }
                .toMap()
                .toPersistentMap()

        private fun encodeViewerRotations(rotations: Map<String, Float>): ArrayList<String> =
            ArrayList(rotations.map { (path, rotation) -> "$rotation\t$path" })
    }

    private fun metadataWriteError(result: ImageMetadataWriteResult): UiText = when (result) {
        ImageMetadataWriteResult.NotWritable ->
            UiText.StringResource(R.string.image_gallery_metadata_not_writable)
        ImageMetadataWriteResult.UnsupportedFormat ->
            UiText.StringResource(R.string.image_gallery_metadata_unsupported_format)
        is ImageMetadataWriteResult.Failure ->
            UiText.StringResource(R.string.image_gallery_metadata_save_failed)
        ImageMetadataWriteResult.Success ->
            UiText.StringResource(R.string.image_gallery_metadata_save_failed)
    }
}

private fun BulkFileOperationRequest.toGalleryOperationUiState(
    progress: BulkFileOperationProgress? = null,
    completedItems: Int = progress?.completedItems ?: 0,
    terminalStatus: OperationCompletionStatus? = null,
    isCancelling: Boolean = false,
    startTimeMillis: Long? = null
): ImageGalleryOperationUiState = ImageGalleryOperationUiState(
    type = type,
    totalItems = progress?.totalItems ?: operationItemCount(),
    completedItems = completedItems,
    currentPath = progress?.currentPath ?: operationCurrentPath(),
    bytesCopied = progress?.bytesCopied,
    totalBytes = progress?.totalBytes,
    sourcePaths = sourcePaths,
    isCancelling = isCancelling,
    terminalStatus = terminalStatus,
    startTimeMillis = startTimeMillis ?: System.currentTimeMillis()
)

private fun BulkFileOperationRequest.operationItemCount(): Int =
    when {
        importItems.isNotEmpty() -> importItems.size
        sourcePaths.isNotEmpty() -> sourcePaths.size
        else -> 1
    }

private fun BulkFileOperationRequest.operationCurrentPath(): String? =
    sourcePaths.firstOrNull() ?: importItems.firstOrNull()?.displayName
