package dev.qtremors.arcile.feature.audio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferences
import dev.qtremors.arcile.core.storage.domain.CategoryLibraryPage
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.FileViewMode
import dev.qtremors.arcile.core.storage.domain.CategoryGrouping
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
internal class AudioLibraryViewModel @Inject constructor(
    private val repository: AudioLibraryRepository,
    private val preferencesStore: AudioLibraryPreferencesStore,
    private val clipboardRepository: ClipboardRepository,
    private val fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val volumeRepository: VolumeRepository,
    private val archivePathResolver: ArchivePathResolver,
    private val operationCoordinator: BulkFileOperationCoordinator,
    internal val playback: AudioPlaybackController,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    private val volumeId = savedStateHandle.get<String>("volumeId")?.takeIf(String::isNotBlank)
    private var loadJob: Job? = null
    private var presentationJob: Job? = null
    private var presentationGeneration = 0L
    private var preferencesApplied = false
    private val _state = MutableStateFlow(AudioLibraryState())
    val state: StateFlow<AudioLibraryState> = _state.asStateFlow()
    private val fileActions = AudioLibraryFileActions(
        scope = viewModelScope,
        state = _state,
        clipboardRepository = clipboardRepository,
        fileBrowserRepository = fileBrowserRepository,
        fileMutationRepository = fileMutationRepository,
        volumeRepository = volumeRepository,
        archivePathResolver = archivePathResolver,
        operationCoordinator = operationCoordinator,
        playback = playback,
        reload = { load(refresh = true) },
        rebuildPresentation = ::rebuildPresentation
    )

    init {
        viewModelScope.launch {
            clipboardRepository.clipboardState.collectLatest { clipboard ->
                _state.update { it.copy(clipboardState = clipboard) }
            }
        }
        viewModelScope.launch {
            operationCoordinator.events.collect(fileActions::handleOperationEvent)
        }
        viewModelScope.launch {
            val preferencesFlow = preferencesStore.audioLibraryPreferencesFlow
            applyPreferences(preferencesFlow.first())
            load()
            preferencesFlow.drop(1).collectLatest(::applyPreferences)
        }
    }

    private fun applyPreferences(preferences: AudioLibraryPreferences) {
        rebuildPresentation { current ->
            val defaultPage = preferences.defaultPage
            current.copy(
                audioPresentation = preferences.audioPresentation,
                folderPresentation = preferences.folderPresentation.copy(
                    viewMode = FileViewMode.GRID
                ),
                grouping = preferences.grouping,
                defaultPage = defaultPage,
                tab = if (preferencesApplied) current.tab else defaultPage,
                showFileDetails = preferences.showFileDetails,
                scrollbarEnabled = preferences.scrollbarEnabled,
                favoritePaths = preferences.favoriteFiles,
                pinnedFolderPaths = preferences.pinnedFolders,
                folderCoverPaths = preferences.folderCovers
            )
        }
        preferencesApplied = true
    }

    fun load(refresh: Boolean = false) {
        loadJob?.cancel()
        _state.update {
            it.copy(
                isLoading = !refresh && it.tracks.isEmpty(),
                isRefreshing = refresh,
                error = null
            )
        }
        loadJob = viewModelScope.launch {
            val scope = volumeId?.let(StorageScope::Volume) ?: StorageScope.AllStorage
            repository.getTracks(scope)
                .onSuccess { tracks ->
                    presentationGeneration += 1L
                    presentationJob?.cancel()
                    val current = _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = null,
                        selectedPaths = _state.value.selectedPaths.intersect(
                            tracks.mapTo(mutableSetOf()) { it.file.absolutePath }
                        )
                    )
                    val presented = withContext(Dispatchers.Default) {
                        buildAudioLibraryState(current, tracks)
                    }
                    _state.value = presented
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = error.localizedMessage
                                ?.takeIf(String::isNotBlank)
                                ?.let(UiText::Dynamic)
                                ?: UiText.StringResource(R.string.error_load_category_failed)
                        )
                    }
                }
        }
    }

    fun updateQuery(query: String) {
        rebuildPresentation { it.copy(query = query) }
    }

    fun updateSearchFilters(filters: SearchFilters) {
        rebuildPresentation { it.copy(searchFilters = filters) }
    }

    fun selectTab(tab: CategoryLibraryPage) {
        rebuildPresentation {
            it.copy(tab = tab, folderFilter = null)
                .withPresentedVisibleTracks()
        }
    }

    fun selectFolder(folder: AudioFolder) {
        rebuildPresentation {
            it.copy(
                tab = CategoryLibraryPage.FOLDERS,
                folderFilter = folder,
                query = ""
            ).withPresentedVisibleTracks()
        }
    }

    fun clearFolderFilter() {
        rebuildPresentation {
            it.copy(folderFilter = null)
                .withPresentedVisibleTracks()
        }
    }

    fun updatePresentation(tab: CategoryLibraryPage, presentation: FileListingPreferences) {
        viewModelScope.launch {
            when (tab) {
                CategoryLibraryPage.ITEMS ->
                    preferencesStore.updateAudioPresentation(presentation.normalized())
                CategoryLibraryPage.FOLDERS ->
                    preferencesStore.updateAudioFolderPresentation(
                        presentation.normalized().copy(viewMode = FileViewMode.GRID)
                    )
            }
        }
        rebuildPresentation { current ->
            when (tab) {
                CategoryLibraryPage.ITEMS -> current.copy(
                    audioPresentation = presentation.normalized()
                )
                CategoryLibraryPage.FOLDERS -> current.copy(
                    folderPresentation = presentation.normalized().copy(
                        viewMode = FileViewMode.GRID
                    )
                )
            }
        }
    }

    fun updateGrouping(grouping: CategoryGrouping) {
        viewModelScope.launch { preferencesStore.updateAudioGrouping(grouping) }
        _state.update { it.copy(grouping = grouping) }
    }

    fun updateShowFileDetails(show: Boolean) {
        viewModelScope.launch { preferencesStore.updateAudioShowFileDetails(show) }
        _state.update { it.copy(showFileDetails = show) }
    }

    fun updateDefaultPage(tab: CategoryLibraryPage) {
        viewModelScope.launch { preferencesStore.updateAudioDefaultPage(tab) }
        _state.update { it.copy(defaultPage = tab) }
    }

    fun toggleFavoriteSelection() {
        val selected = _state.value.selectedPaths
        if (selected.isEmpty()) return
        val makeFavorite = !selected.all(_state.value.favoritePaths::contains)
        viewModelScope.launch {
            selected.forEach { path ->
                preferencesStore.updateFavorite(path, makeFavorite)
            }
        }
        rebuildPresentation { current ->
            current.copy(
                favoritePaths = if (makeFavorite) {
                    current.favoritePaths + selected
                } else {
                    current.favoritePaths - selected
                }
            )
        }
    }

    fun togglePinnedFolder(folder: AudioFolder) {
        if (folder.isFavorites) return
        val makePinned = folder.key !in _state.value.pinnedFolderPaths
        viewModelScope.launch {
            preferencesStore.updatePinnedFolder(folder.key, makePinned)
        }
        rebuildPresentation { current ->
            current.copy(
                pinnedFolderPaths = if (makePinned) {
                    current.pinnedFolderPaths + folder.key
                } else {
                    current.pinnedFolderPaths - folder.key
                }
            )
        }
    }

    fun updateFolderCover(folder: AudioFolder, trackPath: String?) {
        if (folder.isFavorites) return
        val validPath = trackPath?.takeIf { candidate ->
            folder.tracks.any { it.file.absolutePath == candidate }
        }
        viewModelScope.launch {
            preferencesStore.updateFolderCover(folder.key, validPath)
        }
        rebuildPresentation { current ->
            current.copy(
                folderCoverPaths = if (validPath == null) {
                    current.folderCoverPaths - folder.key
                } else {
                    current.folderCoverPaths + (folder.key to validPath)
                }
            )
        }
    }

    fun toggleSelection(path: String) = fileActions.toggleSelection(path)
    fun selectPaths(paths: Collection<String>) = fileActions.selectPaths(paths)
    fun togglePaths(paths: Collection<String>) = fileActions.togglePaths(paths)
    fun selectAllVisible() = fileActions.selectAllVisible()
    fun invertSelection() = fileActions.invertSelection()
    fun clearSelection() = fileActions.clearSelection()
    fun copySelection() = fileActions.copySelection()
    fun cutSelection() = fileActions.cutSelection()
    fun removeFromClipboard(path: String) = fileActions.removeFromClipboard(path)
    fun requestDeleteSelected() = fileActions.requestDeleteSelected()
    fun confirmDeleteSelected() = fileActions.confirmDeleteSelected()
    fun dismissDeleteConfirmation() = fileActions.dismissDeleteConfirmation()
    fun togglePermanentDelete() = fileActions.togglePermanentDelete()
    fun toggleShred() = fileActions.toggleShred()
    fun openPropertiesForSelection() = fileActions.openPropertiesForSelection()
    fun dismissProperties() = fileActions.dismissProperties()
    fun pasteToCurrentFolder() = fileActions.pasteToCurrentFolder()
    fun pasteToFolder(destination: String) = fileActions.pasteToFolder(destination)
    fun resolvePasteConflicts(resolutions: Map<String, ConflictResolution>) =
        fileActions.resolvePasteConflicts(resolutions)
    fun dismissPasteConflictDialog() = fileActions.dismissPasteConflictDialog()
    fun cancelClipboard() = fileActions.cancelClipboard()
    fun clearActiveFileOperation() = fileActions.clearActiveFileOperation()
    fun renameSelected(newName: String) = fileActions.renameSelected(newName)
    fun createZipFromSelection() = fileActions.createZipFromSelection()

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun rebuildPresentation(
        transform: (AudioLibraryState) -> AudioLibraryState
    ) {
        val generation = ++presentationGeneration
        val snapshot = transform(_state.value)
        _state.value = snapshot
        presentationJob?.cancel()
        presentationJob = viewModelScope.launch {
            val presented = withContext(Dispatchers.Default) {
                buildAudioLibraryState(snapshot)
            }
            if (generation == presentationGeneration) {
                _state.value = presented
            }
        }
    }

}

internal fun AudioLibraryState.visibleSelectionPaths(): List<String> =
    if (tab == CategoryLibraryPage.ITEMS || folderFilter != null) {
        visibleTracks.map { it.file.absolutePath }
    } else {
        folders.flatMap { folder ->
            folder.tracks.map { it.file.absolutePath }
        }
    }
