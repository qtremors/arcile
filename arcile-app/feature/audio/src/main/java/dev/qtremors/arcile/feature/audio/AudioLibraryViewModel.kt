package dev.qtremors.arcile.feature.audio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.AudioLibraryDefaultTab
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.ui.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.collectLatest
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
    private val initialPath = savedStateHandle.get<String>("initialPath")?.takeIf(String::isNotBlank)
    private var initialPlaybackHandled = false
    private var loadJob: Job? = null
    private var presentationJob: Job? = null
    private var presentationGeneration = 0L
    private var preferencesApplied = false
    private val _state = MutableStateFlow(AudioLibraryState(playerExpanded = initialPath != null))
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
            preferencesStore.audioLibraryPreferencesFlow.collectLatest { preferences ->
                rebuildPresentation { current ->
                    val defaultTab = preferences.defaultTab.toFeatureTab()
                    current.copy(
                        audioPresentation = preferences.audioPresentation,
                        folderPresentation = preferences.folderPresentation,
                        grouping = preferences.grouping,
                        defaultTab = defaultTab,
                        tab = if (preferencesApplied) current.tab else defaultTab,
                        showFileDetails = preferences.showFileDetails,
                        scrollbarEnabled = preferences.scrollbarEnabled
                    )
                }
                preferencesApplied = true
            }
        }
        load()
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
                    if (!initialPlaybackHandled && initialPath != null) {
                        initialPlaybackHandled = true
                        val queue = _state.value.tracks
                        if (queue.any { it.file.absolutePath == initialPath }) {
                            playback.playQueue(queue, initialPath)
                        }
                    }
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

    fun selectTab(tab: AudioLibraryTab) {
        rebuildPresentation { it.copy(tab = tab, folderFilter = null) }
    }

    fun selectFolder(folder: AudioFolder) {
        rebuildPresentation {
            it.copy(
                tab = AudioLibraryTab.AUDIO,
                folderFilter = folder,
                query = ""
            )
        }
    }

    fun clearFolderFilter() {
        rebuildPresentation { it.copy(folderFilter = null) }
    }

    fun updatePresentation(tab: AudioLibraryTab, presentation: FileListingPreferences) {
        viewModelScope.launch {
            when (tab) {
                AudioLibraryTab.AUDIO ->
                    preferencesStore.updateAudioPresentation(presentation.normalized())
                AudioLibraryTab.FOLDERS ->
                    preferencesStore.updateAudioFolderPresentation(presentation.normalized())
            }
        }
        rebuildPresentation { current ->
            when (tab) {
                AudioLibraryTab.AUDIO -> current.copy(
                    audioPresentation = presentation.normalized()
                )
                AudioLibraryTab.FOLDERS -> current.copy(
                    folderPresentation = presentation.normalized()
                )
            }
        }
    }

    fun updateGrouping(grouping: ImageGalleryGrouping) {
        viewModelScope.launch { preferencesStore.updateAudioGrouping(grouping) }
        _state.update { it.copy(grouping = grouping) }
    }

    fun updateShowFileDetails(show: Boolean) {
        viewModelScope.launch { preferencesStore.updateAudioShowFileDetails(show) }
        _state.update { it.copy(showFileDetails = show) }
    }

    fun updateDefaultTab(tab: AudioLibraryTab) {
        viewModelScope.launch {
            preferencesStore.updateAudioDefaultTab(
                if (tab == AudioLibraryTab.AUDIO) {
                    AudioLibraryDefaultTab.AUDIO
                } else {
                    AudioLibraryDefaultTab.FOLDERS
                }
            )
        }
        _state.update { it.copy(defaultTab = tab) }
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

    fun play(trackPath: String) {
        val queue = _state.value.visibleTracks.takeIf { it.isNotEmpty() } ?: _state.value.tracks
        playback.playQueue(queue, trackPath)
    }

    fun playSelection(paths: Collection<String>) {
        val selected = paths.toSet()
        val queue = _state.value.tracks.filter { it.file.absolutePath in selected }
        queue.firstOrNull()?.let { playback.playQueue(queue, it.file.absolutePath) }
    }

    fun expandPlayer() {
        _state.update { it.copy(playerExpanded = true) }
    }

    fun collapsePlayer() {
        _state.update { it.copy(playerExpanded = false) }
    }

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
    when (tab) {
        AudioLibraryTab.AUDIO -> visibleTracks.map { it.file.absolutePath }
        AudioLibraryTab.FOLDERS -> folders.flatMap { folder ->
            folder.tracks.map { it.file.absolutePath }
        }
    }

private fun AudioLibraryDefaultTab.toFeatureTab(): AudioLibraryTab =
    if (this == AudioLibraryDefaultTab.AUDIO) {
        AudioLibraryTab.AUDIO
    } else {
        AudioLibraryTab.FOLDERS
    }
