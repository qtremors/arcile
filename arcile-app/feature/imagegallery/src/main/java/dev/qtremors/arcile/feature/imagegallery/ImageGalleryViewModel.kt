package dev.qtremors.arcile.feature.imagegallery

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.BrowserPreferencesStore
import dev.qtremors.arcile.core.storage.domain.BrowserPresentationPreferences
import dev.qtremors.arcile.core.storage.domain.BrowserViewMode
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.shared.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.shared.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.shared.presentation.filterAndSortFiles
import dev.qtremors.arcile.shared.presentation.toUiModel
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

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
    val properties: dev.qtremors.arcile.shared.presentation.PropertiesUiModel? = null
)

@HiltViewModel
class ImageGalleryViewModel @Inject constructor(
    private val repository: ImageGalleryRepository,
    private val fileBrowserRepository: FileBrowserRepository,
    private val volumeRepository: VolumeRepository,
    private val browserPreferencesStore: BrowserPreferencesStore,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(
        ImageGalleryState(volumeId = savedStateHandle.get<String>("volumeId")?.takeIf { it.isNotBlank() })
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
        onSuccess = { loadImages(forceRefresh = true) },
        onFailure = { loadImages(forceRefresh = true) }
    )

    init {
        loadImages(forceRefresh = false)
        viewModelScope.launch {
            browserPreferencesStore.preferencesFlow.collectLatest { preferences ->
                _state.update { state ->
                    val persistedPresentation = preferences.exactPathPresentationOptions[IMAGE_GALLERY_PREF_KEY]
                        ?: state.presentation.copy(showThumbnails = preferences.globalPresentation.showThumbnails)
                    state.copy(
                        presentation = persistedPresentation.normalized(),
                        showFileDetails = preferences.imageGalleryShowFileDetails
                    ).withDisplayedFiles()
                }
            }
        }
        viewModelScope.launch {
            repository.mutationEvents.collect { event ->
                repository.invalidate(event.paths)
                loadImages(forceRefresh = true, silent = true)
            }
        }
        viewModelScope.launch {
            bulkFileOperationCoordinator.events.collect { event ->
                if (event is BulkFileOperationEvent.Completed && event.request.type in refreshTypes) {
                    repository.invalidate(event.request.sourcePaths)
                    loadImages(forceRefresh = true, silent = true)
                }
            }
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
                            isSnapshotStale = snapshot.isStale
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

    fun requestDeleteSelected() = deleteFlowDelegate.requestDeleteSelected()
    fun confirmDeleteSelected() = deleteFlowDelegate.confirmDeleteSelected()
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

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun ImageGalleryState.withDisplayedFiles(): ImageGalleryState {
        val albumFiltered = selectedAlbumPath?.let { albumPath ->
            files.filter { java.io.File(it.absolutePath).parent == albumPath }
        } ?: files
        return copy(displayedFiles = filterAndSortFiles(albumFiltered, searchQuery, presentation.sortOption).toPersistentList())
    }

    companion object {
        private const val IMAGE_GALLERY_PREF_KEY = "image_gallery"
        private val refreshTypes = setOf(
            BulkFileOperationType.MOVE,
            BulkFileOperationType.TRASH,
            BulkFileOperationType.DELETE,
            BulkFileOperationType.SHRED
        )
    }
}
