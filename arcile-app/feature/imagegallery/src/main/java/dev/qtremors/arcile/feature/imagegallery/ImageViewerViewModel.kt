package dev.qtremors.arcile.feature.imagegallery

import android.content.IntentSender
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.presentation.SelectionReducer
import dev.qtremors.arcile.core.storage.domain.GalleryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.core.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataUpdate
import dev.qtremors.arcile.core.ui.metadata.ImageMetadataWriteResult
import javax.inject.Inject
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
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

internal data class ImageViewerState(
    val files: PersistentList<FileModel> = persistentListOf(),
    val displayedFiles: PersistentList<FileModel> = persistentListOf(),
    val favoriteFiles: PersistentSet<String> = persistentSetOf(),
    val selectedFiles: PersistentSet<String> = persistentSetOf(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val error: UiText? = null,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val isShredChecked: Boolean = false,
    val viewerSessionInitialPath: String? = null,
    val viewerCurrentPath: String? = null,
    val viewerMetadataPath: String? = null,
    val viewerMetadataSavingPath: String? = null,
    val viewerMetadataRevision: Long = 0L,
    val viewerUiVisible: Boolean = true,
    val viewerRotationDegrees: PersistentMap<String, Float> = persistentMapOf(),
    val viewerEraseDialogPath: String? = null
)

@HiltViewModel
internal class ImageViewerViewModel @Inject constructor(
    private val browserPreferencesStore: GalleryPreferencesStore,
    private val fileBrowserRepository: FileBrowserRepository,
    private val volumeRepository: VolumeRepository,
    private val operationCoordinator: BulkFileOperationCoordinator,
    private val imageMetadataRepository: ImageMetadataRepository,
    private val galleryRepository: ImageGalleryRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(
        ImageViewerState(
            viewerSessionInitialPath = savedStateHandle[KEY_SESSION_INITIAL_PATH],
            viewerCurrentPath = savedStateHandle[KEY_CURRENT_PATH],
            viewerMetadataPath = savedStateHandle[KEY_METADATA_PATH],
            viewerUiVisible = savedStateHandle[KEY_UI_VISIBLE] ?: true,
            viewerRotationDegrees = restoreRotations(savedStateHandle[KEY_ROTATIONS]),
            viewerEraseDialogPath = savedStateHandle[KEY_ERASE_DIALOG_PATH]
        )
    )
    val state: StateFlow<ImageViewerState> = _state.asStateFlow()

    private val _nativeRequestFlow = MutableSharedFlow<IntentSender>()
    val nativeRequestFlow: SharedFlow<IntentSender> = _nativeRequestFlow.asSharedFlow()
    private var pendingDeletePaths: List<String> = emptyList()

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
            operationCoordinator.startOperation(
                type = type,
                sourcePaths = selected,
                destinationPath = null,
                resolutions = emptyMap()
            )
        },
        emitNativeRequest = { _nativeRequestFlow.emit(it) },
        onSuccess = {
            val removed = pendingDeletePaths.toSet()
            _state.update {
                val files = it.files.filterNot { file -> file.absolutePath in removed }
                    .toPersistentList()
                it.copy(
                    files = files,
                    displayedFiles = files,
                    selectedFiles = persistentSetOf(),
                    isRefreshing = false
                )
            }
            galleryRepository.invalidate(pendingDeletePaths)
            pendingDeletePaths = emptyList()
        },
        onFailure = { _state.update { it.copy(isRefreshing = false) } }
    )

    init {
        viewModelScope.launch {
            browserPreferencesStore.galleryPreferencesFlow.collectLatest { preferences ->
                _state.update {
                    it.copy(favoriteFiles = preferences.favoriteFiles.toPersistentSet())
                }
            }
        }
    }

    fun initialize(initialPath: String, contextPaths: List<String>) {
        val paths = (contextPaths + initialPath).distinct()
        val files = paths.map(::fileModelFromPath).toPersistentList()
        _state.update {
            it.copy(
                files = files,
                displayedFiles = files
            )
        }
        startViewerSession(initialPath)
    }

    fun toggleSelection(path: String) {
        _state.update {
            it.copy(
                selectedFiles = SelectionReducer.toggle(it.selectedFiles, path).toPersistentSet()
            )
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedFiles = persistentSetOf()) }
    }

    fun requestDeleteSelected() {
        pendingDeletePaths = state.value.selectedFiles.toList()
        deleteFlow.requestDeleteSelected()
    }

    fun confirmDeleteSelected() {
        pendingDeletePaths = state.value.selectedFiles.toList().ifEmpty {
            pendingDeletePaths
        }
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

    suspend fun readImageMetadata(
        filePath: String,
        mimeType: String?
    ): GalleryFileMetadata = imageMetadataRepository.read(filePath, mimeType)

    fun eraseMetadata(filePath: String) {
        setViewerEraseDialogPath(null)
        viewModelScope.launch {
            _state.update { it.copy(viewerMetadataSavingPath = filePath) }
            finishMetadataWrite(filePath, imageMetadataRepository.erase(filePath))
        }
    }

    fun updateMetadata(filePath: String, update: ImageMetadataUpdate) {
        viewModelScope.launch {
            _state.update { it.copy(viewerMetadataSavingPath = filePath) }
            finishMetadataWrite(
                filePath,
                imageMetadataRepository.update(filePath, update)
            )
        }
    }

    private fun finishMetadataWrite(
        filePath: String,
        result: ImageMetadataWriteResult
    ) {
        if (result == ImageMetadataWriteResult.Success) {
            galleryRepository.invalidate(listOf(filePath))
            _state.update {
                it.copy(
                    viewerMetadataSavingPath = null,
                    viewerMetadataRevision = it.viewerMetadataRevision + 1
                )
            }
        } else {
            _state.update {
                it.copy(
                    viewerMetadataSavingPath = null,
                    error = metadataWriteError(result)
                )
            }
        }
    }

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

    fun rotateViewerImage(path: String) {
        val current = state.value.viewerRotationDegrees[path] ?: 0f
        val rotations = (
            state.value.viewerRotationDegrees + (path to ((current + 90f) % 360f))
            ).toPersistentMap()
        savedStateHandle[KEY_ROTATIONS] = encodeRotations(rotations)
        _state.update { it.copy(viewerRotationDegrees = rotations) }
    }

    fun setViewerEraseDialogPath(path: String?) {
        savedStateHandle[KEY_ERASE_DIALOG_PATH] = path
        _state.update { it.copy(viewerEraseDialogPath = path) }
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

    private companion object {
        const val KEY_SESSION_INITIAL_PATH = "image_viewer.session_initial_path"
        const val KEY_CURRENT_PATH = "image_viewer.current_path"
        const val KEY_METADATA_PATH = "image_viewer.metadata_path"
        const val KEY_UI_VISIBLE = "image_viewer.ui_visible"
        const val KEY_ROTATIONS = "image_viewer.rotations"
        const val KEY_ERASE_DIALOG_PATH = "image_viewer.erase_dialog_path"

        fun restoreRotations(encoded: ArrayList<String>?): PersistentMap<String, Float> =
            encoded.orEmpty()
                .mapNotNull { value ->
                    val separator = value.indexOf('\t')
                    if (separator <= 0) return@mapNotNull null
                    val rotation = value.substring(0, separator).toFloatOrNull()
                        ?: return@mapNotNull null
                    value.substring(separator + 1) to rotation
                }
                .toMap()
                .toPersistentMap()

        fun encodeRotations(rotations: Map<String, Float>): ArrayList<String> =
            ArrayList(rotations.map { (path, rotation) -> "$rotation\t$path" })
    }
}
