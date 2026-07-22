package dev.qtremors.arcile.feature.videoplayer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.presentation.SelectionReducer
import dev.qtremors.arcile.core.storage.domain.GalleryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.core.presentation.delegate.DeleteStateCallbacks
import javax.inject.Inject
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
internal class VideoViewerViewModel @Inject constructor(
    private val browserPreferencesStore: GalleryPreferencesStore,
    private val fileBrowserRepository: FileBrowserRepository,
    private val volumeRepository: VolumeRepository,
    private val operationCoordinator: BulkFileOperationCoordinator,
    private val videoMetadataRepository: VideoMetadataRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private var siblingLoadJob: Job? = null
    private var selectionBeforeCurrentDelete: PersistentSet<String>? = null
    private var currentDeleteTarget: String? = null

    private val _state = MutableStateFlow(
        VideoViewerState(
            viewerSessionInitialPath = savedStateHandle[KEY_SESSION_INITIAL_PATH],
            viewerCurrentPath = savedStateHandle[KEY_CURRENT_PATH],
            viewerMetadataPath = savedStateHandle[KEY_METADATA_PATH],
            viewerUiVisible = savedStateHandle[KEY_UI_VISIBLE] ?: true,
            viewerEraseDialogPath = savedStateHandle[KEY_ERASE_DIALOG_PATH]
        )
    )
    val state: StateFlow<VideoViewerState> = _state.asStateFlow()

    private val deleteFlow = DeleteFlowDelegate(
        coroutineScope = viewModelScope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = object : DeleteStateCallbacks {
            override fun getSelectedFiles(): List<String> = state.value.selectedFiles.toList()
            override fun isPermanentDeleteChecked() = state.value.isPermanentDeleteChecked
            override fun isPermanentDeleteToggleEnabled() =
                state.value.isPermanentDeleteToggleEnabled
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
                _state.update {
                    it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked)
                }
            }
            override fun isShredChecked() = state.value.isShredChecked
            override fun toggleShredChecked() {
                _state.update { it.copy(isShredChecked = !it.isShredChecked) }
            }
            override fun dismissDeleteConfirmation() {
                _state.update { it.withDeleteDialogsHidden(selectionBeforeCurrentDelete ?: it.selectedFiles) }
                selectionBeforeCurrentDelete = null
                currentDeleteTarget = null
            }
            override fun hideDeleteConfirmationForOperation() {
                _state.update(VideoViewerState::withDeleteDialogsHidden)
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
            override fun clearSelection() {
                val originalSelection = selectionBeforeCurrentDelete
                val restoredSelection = currentDeleteTarget
                    ?.let { target -> originalSelection?.remove(target) }
                    ?: originalSelection
                    ?: persistentSetOf()
                _state.update { it.copy(selectedFiles = restoredSelection) }
                selectionBeforeCurrentDelete = null
                currentDeleteTarget = null
            }
        },
        startBulkDeleteOperation = { type, selected ->
            operationCoordinator.startOperation(
                type = type,
                sourcePaths = selected,
                destinationPath = null,
                resolutions = emptyMap()
            )
        },
        onFailure = {
            _state.update {
                it.copy(
                    isRefreshing = false,
                    selectedFiles = selectionBeforeCurrentDelete ?: it.selectedFiles
                )
            }
            selectionBeforeCurrentDelete = null
            currentDeleteTarget = null
        }
    )

    init {
        viewModelScope.launch {
            browserPreferencesStore.galleryPreferencesFlow.collectLatest { preferences ->
                _state.update {
                    it.copy(favoriteFiles = preferences.favoriteFiles.toPersistentSet())
                }
            }
        }
        viewModelScope.launch {
            operationCoordinator.events.collect { event ->
                if (event is BulkFileOperationEvent.Completed &&
                    event.request.type in viewerRemovalOperationTypes
                ) {
                    val removedPaths = event.request.sourcePaths.toSet()
                    _state.update { current ->
                        current.copy(
                            files = current.files
                                .filterNot { it.absolutePath in removedPaths }
                                .toPersistentList(),
                            displayedFiles = current.displayedFiles
                                .filterNot { it.absolutePath in removedPaths }
                                .toPersistentList(),
                            selectedFiles = current.selectedFiles
                                .filterNot { it in removedPaths }
                                .toPersistentSet(),
                            viewerMetadataPath = current.viewerMetadataPath
                                ?.takeUnless { it in removedPaths }
                        )
                    }
                }
            }
        }
    }

    fun initialize(
        initialPath: String,
        contextFiles: List<FileModel>,
        selectedPaths: List<String> = emptyList(),
        discoverSiblings: Boolean = true
    ) {
        siblingLoadJob?.cancel()
        val files = (contextFiles + fileModelFromPath(initialPath))
            .distinctBy(FileModel::absolutePath).toPersistentList()
        _state.update {
            it.copy(
                isInitialized = true,
                files = files,
                displayedFiles = files,
                selectedFiles = selectedPaths.toPersistentSet()
            )
        }
        startViewerSession(initialPath)

        if (discoverSiblings && contextFiles.distinctBy(FileModel::absolutePath).size <= 1) {
            siblingLoadJob = viewModelScope.launch {
                val parentPath = viewerParentPath(initialPath) ?: return@launch
                val siblings = fileBrowserRepository.listFiles(parentPath).getOrNull()
                    ?.filter { file ->
                        !file.isDirectory &&
                            FileCategories.getCategoryForFile(file.extension, file.mimeType) ==
                            FileCategories.Videos
                    }
                    .orEmpty()
                if (siblings.none { it.absolutePath == initialPath }) return@launch
                if (_state.value.viewerSessionInitialPath != initialPath) return@launch
                val siblingFiles = siblings.toPersistentList()
                _state.update {
                    it.copy(files = siblingFiles, displayedFiles = siblingFiles)
                }
            }
        }
    }

    fun toggleSelection(path: String) {
        _state.update {
            it.copy(
                selectedFiles = SelectionReducer.toggle(it.selectedFiles, path).toPersistentSet()
            )
        }
    }

    fun clearSelection() {
        selectionBeforeCurrentDelete = null
        currentDeleteTarget = null
        _state.update { it.copy(selectedFiles = persistentSetOf()) }
    }

    fun requestDeleteCurrent(path: String) {
        selectionBeforeCurrentDelete = _state.value.selectedFiles
        currentDeleteTarget = path
        _state.update { it.copy(selectedFiles = persistentSetOf(path)) }
        deleteFlow.requestDeleteSelected()
    }

    fun requestDeleteSelected() {
        deleteFlow.requestDeleteSelected()
    }

    fun confirmDeleteSelected() {
        deleteFlow.confirmDeleteSelected()
    }

    fun dismissDeleteConfirmation() = deleteFlow.dismissDeleteConfirmation()
    fun togglePermanentDelete() = deleteFlow.togglePermanentDelete()
    fun toggleShred() = deleteFlow.toggleShred()

    fun toggleFavorite(path: String) {
        val isFavorite = path in state.value.favoriteFiles
        viewModelScope.launch {
            browserPreferencesStore.updateFavorite(path, !isFavorite)
        }
    }

    suspend fun readVideoMetadata(
        filePath: String,
        mimeType: String?
    ): VideoFileMetadata = videoMetadataRepository.read(filePath, mimeType)

    fun startViewerSession(initialPath: String) {
        if (state.value.viewerSessionInitialPath == initialPath) return
        savedStateHandle[KEY_SESSION_INITIAL_PATH] = initialPath
        savedStateHandle[KEY_CURRENT_PATH] = initialPath
        savedStateHandle[KEY_UI_VISIBLE] = true
        savedStateHandle.remove<String>(KEY_METADATA_PATH)
        savedStateHandle.remove<String>(KEY_ERASE_DIALOG_PATH)
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
        savedStateHandle[KEY_CURRENT_PATH] = path
        _state.update { it.copy(viewerCurrentPath = path) }
    }

    fun setViewerMetadataVisible(path: String?, visible: Boolean) {
        val metadataPath = if (visible) path else null
        savedStateHandle[KEY_METADATA_PATH] = metadataPath
        _state.update { it.copy(viewerMetadataPath = metadataPath) }
    }

    fun toggleViewerUi() {
        val visible = !state.value.viewerUiVisible
        savedStateHandle[KEY_UI_VISIBLE] = visible
        _state.update { it.copy(viewerUiVisible = visible) }
    }

    fun setViewerEraseDialogPath(path: String?) {
        savedStateHandle[KEY_ERASE_DIALOG_PATH] = path
        _state.update { it.copy(viewerEraseDialogPath = path) }
    }

    private companion object {
        val viewerRemovalOperationTypes = setOf(
            BulkFileOperationType.MOVE,
            BulkFileOperationType.TRASH,
            BulkFileOperationType.DELETE,
            BulkFileOperationType.SHRED
        )
        const val KEY_SESSION_INITIAL_PATH = "video_viewer.session_initial_path"
        const val KEY_CURRENT_PATH = "video_viewer.current_path"
        const val KEY_METADATA_PATH = "video_viewer.metadata_path"
        const val KEY_UI_VISIBLE = "video_viewer.ui_visible"
        const val KEY_ERASE_DIALOG_PATH = "video_viewer.erase_dialog_path"
    }
}
