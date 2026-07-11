package dev.qtremors.arcile.feature.imagegallery

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.GalleryPreferences
import dev.qtremors.arcile.core.storage.domain.GalleryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.ImageGalleryDefaultTab
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ImageGalleryViewModel @Inject constructor(
    private val repository: ImageGalleryRepository,
    fileBrowserRepository: FileBrowserRepository,
    fileMutationRepository: FileMutationRepository,
    clipboardRepository: ClipboardRepository,
    volumeRepository: VolumeRepository,
    private val browserPreferencesStore: GalleryPreferencesStore,
    bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    archivePathResolver: ArchivePathResolver,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val _state = MutableStateFlow(
        ImageGalleryState(
            volumeId = savedStateHandle.get<String>("volumeId")?.takeIf(String::isNotBlank)
        )
    )
    val state: StateFlow<ImageGalleryState> = _state.asStateFlow()

    private val fileActions = ImageGalleryFileActionController(
        initialState = _state.value.fileActions,
        scope = viewModelScope,
        fileBrowserRepository = fileBrowserRepository,
        fileMutationRepository = fileMutationRepository,
        clipboardRepository = clipboardRepository,
        volumeRepository = volumeRepository,
        operationCoordinator = bulkFileOperationCoordinator,
        archivePathResolver = archivePathResolver,
        files = { _state.value.files },
        displayedPaths = { _state.value.displayedFiles.map(FileModel::absolutePath) },
        onStateChange = { actions -> _state.update { it.copy(fileActions = actions) } },
        onBusyChange = { busy -> _state.update { it.copy(isRefreshing = busy) } },
        onError = { error -> _state.update { it.copy(error = error) } },
        onPathsRemoved = { paths -> _state.update { it.withoutGalleryPaths(paths) } },
        onRefreshRequested = { loadImages(forceRefresh = true, silent = true) }
    )
    init {
        fileActions.startObserving()
        viewModelScope.launch {
            applyPreferences(browserPreferencesStore.galleryPreferencesFlow.first())
            loadImages(forceRefresh = false)
            browserPreferencesStore.galleryPreferencesFlow.drop(1).collectLatest(::applyPreferences)
        }
        viewModelScope.launch {
            repository.mutationEvents.collect { event ->
                repository.invalidate(event.paths)
                fileActions.removePaths(event.paths)
                _state.update { it.withoutGalleryPaths(event.paths) }
                loadImages(forceRefresh = true, silent = true)
            }
        }
        viewModelScope.launch {
            bulkFileOperationCoordinator.events.collect { event ->
                if (event is BulkFileOperationEvent.Completed && event.request.type in refreshTypes) {
                    val paths = (event.request.sourcePaths + listOfNotNull(event.request.destinationPath)).distinct()
                    repository.invalidate(paths)
                    if (event.request.type != BulkFileOperationType.COPY) {
                        fileActions.removePaths(event.request.sourcePaths)
                        _state.update { it.withoutGalleryPaths(event.request.sourcePaths) }
                    }
                    loadImages(forceRefresh = true, silent = true)
                }
            }
        }
    }

    private fun applyPreferences(preferences: GalleryPreferences) {
        _state.update { state ->
            val persistedPresentation = preferences.imagePresentation
                ?: state.presentation.copy(showThumbnails = preferences.globalShowThumbnails)
            state.copy(
                presentation = persistedPresentation.normalized(),
                showFileDetails = preferences.showFileDetails,
                isAspectRatio = preferences.aspectRatio,
                isSectioned = preferences.sectioned,
                imageGalleryGrouping = preferences.grouping,
                imageGalleryDefaultTab = preferences.defaultTab,
                galleryScrollbarEnabled = preferences.scrollbarEnabled,
                preferencesLoaded = true,
                albumPresentation = preferences.albumPresentation,
                favoriteFiles = preferences.favoriteFiles.toPersistentSet(),
                pinnedAlbums = preferences.pinnedAlbums.toPersistentSet(),
                albumCovers = preferences.albumCovers.toPersistentMap()
            ).withResolvedDisplayedFiles()
        }
    }

    fun loadImages(forceRefresh: Boolean = true, silent: Boolean = false) {
        val volumeId = state.value.volumeId
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
                        it.copy(
                            files = snapshot.files.toPersistentList(),
                            albums = snapshot.albums.toPersistentList(),
                            isLoading = false,
                            isRefreshing = false,
                            isSnapshotStale = snapshot.isStale,
                            aspectRatios = snapshot.aspectRatios.toPersistentMap()
                        ).withResolvedDisplayedFiles()
                    }
                    if (snapshot.isStale) loadImages(forceRefresh = true, silent = true)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.message?.let(UiText::Dynamic)
                                ?: UiText.StringResource(R.string.error_load_category_failed)
                        )
                    }
                }
        }
    }

    fun updateSearchQuery(query: String) =
        _state.update { it.copy(searchQuery = query).withResolvedDisplayedFiles() }

    fun selectAlbum(path: String?) =
        _state.update { it.copy(selectedAlbumPath = path).withResolvedDisplayedFiles() }

    fun updatePresentation(preferences: FileListingPreferences) {
        val normalized = preferences.normalized()
        _state.update { it.copy(presentation = normalized).withResolvedDisplayedFiles() }
        viewModelScope.launch {
            browserPreferencesStore.updateImageGalleryPresentation(normalized)
        }
    }

    fun setShowFileDetails(show: Boolean) {
        _state.update { it.copy(showFileDetails = show) }
        viewModelScope.launch { browserPreferencesStore.updateImageGalleryShowFileDetails(show) }
    }

    fun updateAspectRatio(enabled: Boolean) {
        viewModelScope.launch { browserPreferencesStore.updateImageGalleryAspectRatio(enabled) }
    }

    fun updateSectioned(enabled: Boolean) {
        viewModelScope.launch { browserPreferencesStore.updateImageGallerySectioned(enabled) }
    }

    fun updateGrouping(grouping: ImageGalleryGrouping) {
        viewModelScope.launch { browserPreferencesStore.updateImageGalleryGrouping(grouping) }
    }

    fun updateDefaultTab(tab: ImageGalleryDefaultTab) {
        viewModelScope.launch { browserPreferencesStore.updateImageGalleryDefaultTab(tab) }
    }

    fun updateAlbumPresentation(presentation: FileListingPreferences) {
        viewModelScope.launch { browserPreferencesStore.updateAlbumPresentation(presentation) }
    }

    fun toggleFavorite(path: String) {
        viewModelScope.launch {
            browserPreferencesStore.updateFavorite(path, path !in state.value.favoriteFiles)
        }
    }

    fun togglePinnedAlbum(path: String) {
        viewModelScope.launch {
            browserPreferencesStore.updatePinnedAlbum(path, path !in state.value.pinnedAlbums)
        }
    }

    fun setAlbumCover(albumPath: String, coverPath: String) {
        viewModelScope.launch { browserPreferencesStore.updateAlbumCover(albumPath, coverPath) }
    }

    fun setViewerReturnPath(path: String) = _state.update { it.copy(viewerReturnPath = path) }
    fun clearError() = _state.update { it.copy(error = null) }

    fun toggleSelection(path: String) = fileActions.toggleSelection(path)
    fun selectMultiple(paths: List<String>) = fileActions.selectMultiple(paths)
    fun clearSelection() = fileActions.clearSelection()
    fun selectAll() = fileActions.selectAll()
    fun invertSelection() = fileActions.invertSelection()
    fun requestDeleteSelected() = fileActions.requestDeleteSelected()
    fun confirmDeleteSelected() = fileActions.confirmDeleteSelected()
    fun dismissDeleteConfirmation() = fileActions.dismissDeleteConfirmation()
    fun togglePermanentDelete() = fileActions.togglePermanentDelete()
    fun toggleShred() = fileActions.toggleShred()
    fun openPropertiesForSelection() = fileActions.openProperties()
    fun dismissProperties() = fileActions.dismissProperties()
    fun copySelectedToClipboard() = fileActions.copySelected()
    fun cutSelectedToClipboard() = fileActions.cutSelected()
    fun pasteFromClipboard(path: String?) = fileActions.paste(path)
    fun resolvePasteConflicts(resolutions: Map<String, ConflictResolution>) =
        fileActions.resolveConflicts(resolutions)
    fun dismissPasteConflictDialog() = fileActions.dismissConflictDialog()
    fun cancelClipboard() = fileActions.cancelClipboard()
    fun removeFromClipboard(path: String) = fileActions.removeFromClipboard(path)
    fun clearActiveFileOperation() = fileActions.clearActiveOperation()
    fun createZipFromSelection() = fileActions.createZip()
    fun renameFile(path: String, newName: String) = fileActions.rename(path, newName)

    override fun onCleared() {
        fileActions.stopObserving()
        super.onCleared()
    }

    private companion object {
        val refreshTypes = setOf(
            BulkFileOperationType.MOVE,
            BulkFileOperationType.COPY,
            BulkFileOperationType.TRASH,
            BulkFileOperationType.DELETE,
            BulkFileOperationType.SHRED
        )
    }
}
