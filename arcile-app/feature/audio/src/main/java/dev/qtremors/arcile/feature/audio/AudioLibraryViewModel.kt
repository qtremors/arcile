package dev.qtremors.arcile.feature.audio

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.core.presentation.OperationPresentationMapper
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.AudioLibraryDefaultTab
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.storage.domain.StorageScope
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
    private val fileMutationRepository: FileMutationRepository,
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
    private val clipboardController = ClipboardController(clipboardRepository)

    init {
        viewModelScope.launch {
            clipboardRepository.clipboardState.collectLatest { clipboard ->
                _state.update { it.copy(clipboardState = clipboard) }
            }
        }
        viewModelScope.launch {
            operationCoordinator.events.collect(::handleOperationEvent)
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
                        showFileDetails = preferences.showFileDetails
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
                            error = UiText.Dynamic(error.localizedMessage.orEmpty())
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

    fun toggleSelection(path: String) {
        _state.update { current ->
            val selected = current.selectedPaths.toMutableSet()
            if (!selected.add(path)) selected.remove(path)
            current.copy(selectedPaths = selected)
        }
    }

    fun selectPaths(paths: Collection<String>) {
        _state.update { current ->
            current.copy(selectedPaths = current.selectedPaths + paths)
        }
    }

    fun selectAllVisible() {
        _state.update { current ->
            val paths = when (current.tab) {
                AudioLibraryTab.AUDIO -> current.visibleTracks.map { it.file.absolutePath }
                AudioLibraryTab.FOLDERS -> current.folders.flatMap { folder ->
                    folder.tracks.map { it.file.absolutePath }
                }
            }
            current.copy(selectedPaths = paths.toSet())
        }
    }

    fun invertSelection() {
        _state.update { current ->
            val visible = current.visibleTracks.mapTo(mutableSetOf()) { it.file.absolutePath }
            current.copy(selectedPaths = visible - current.selectedPaths)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedPaths = emptySet()) }
    }

    fun copySelection() = storeSelection(ClipboardOperation.COPY)

    fun cutSelection() = storeSelection(ClipboardOperation.CUT)

    private fun storeSelection(operation: ClipboardOperation) {
        val selected = _state.value.selectedPaths
        val files = _state.value.tracks
            .map { it.file }
            .filter { it.absolutePath in selected }
        if (clipboardController.store(operation, files)) {
            rebuildPresentation {
                it.copy(
                    selectedPaths = emptySet(),
                    tab = AudioLibraryTab.FOLDERS,
                    folderFilter = null
                )
            }
        }
    }

    fun pasteToCurrentFolder() {
        val destination = _state.value.folderFilter?.key ?: return
        val clipboard = _state.value.clipboardState ?: return
        val sources = clipboard.files.map(FileModel::absolutePath)
        if (sources.isEmpty()) return
        viewModelScope.launch {
            _state.update { it.copy(pasteDestinationPath = destination, error = null) }
            clipboardRepository.detectCopyConflicts(sources, destination)
                .onSuccess { conflicts ->
                    if (conflicts.isEmpty()) {
                        executePaste(clipboard, destination, emptyMap())
                    } else {
                        _state.update {
                            it.copy(
                                pasteConflicts = conflicts,
                                pasteDestinationPath = destination,
                                showPasteConflictDialog = true
                            )
                        }
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            pasteDestinationPath = null,
                            error = UiText.Dynamic(error.localizedMessage.orEmpty())
                        )
                    }
                }
        }
    }

    fun resolvePasteConflicts(resolutions: Map<String, ConflictResolution>) {
        val clipboard = _state.value.clipboardState ?: return
        val destination = _state.value.pasteDestinationPath ?: return
        _state.update {
            it.copy(showPasteConflictDialog = false, pasteConflicts = emptyList())
        }
        executePaste(clipboard, destination, resolutions)
    }

    fun dismissPasteConflictDialog() {
        _state.update {
            it.copy(
                showPasteConflictDialog = false,
                pasteConflicts = emptyList(),
                pasteDestinationPath = null
            )
        }
    }

    fun cancelClipboard() {
        operationCoordinator.cancelActiveOperation()
        clipboardController.clear()
        dismissPasteConflictDialog()
    }

    fun clearActiveFileOperation() {
        _state.update { it.copy(activeFileOperation = null) }
    }

    fun renameSelected(newName: String) {
        val path = _state.value.selectedPaths.singleOrNull() ?: return
        if (newName.isBlank() || listOf('/', '\\', '\u0000').any(newName::contains) || ".." in newName) {
            return
        }
        viewModelScope.launch {
            fileMutationRepository.renameFile(path, newName)
                .onSuccess {
                    clearSelection()
                    load(refresh = true)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(error = UiText.Dynamic(error.localizedMessage.orEmpty()))
                    }
                }
        }
    }

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

    private fun executePaste(
        clipboard: ClipboardState,
        destination: String,
        resolutions: Map<String, ConflictResolution>
    ) {
        val type = if (clipboard.operation == ClipboardOperation.CUT) {
            BulkFileOperationType.MOVE
        } else {
            BulkFileOperationType.COPY
        }
        val started = operationCoordinator.startOperation(
            type = type,
            sourcePaths = clipboard.files.map(FileModel::absolutePath),
            destinationPath = destination,
            resolutions = resolutions
        )
        _state.update {
            it.copy(
                pasteDestinationPath = null,
                showPasteConflictDialog = false,
                pasteConflicts = emptyList(),
                error = if (started) it.error else UiText.Dynamic("Another file operation is running")
            )
        }
    }

    private fun handleOperationEvent(event: BulkFileOperationEvent) {
        val request = when (event) {
            is BulkFileOperationEvent.Started -> event.request
            is BulkFileOperationEvent.Progress -> event.request
            is BulkFileOperationEvent.Cancelling -> event.request
            is BulkFileOperationEvent.Completed -> event.request
            is BulkFileOperationEvent.Failed -> event.request
            is BulkFileOperationEvent.Cancelled -> event.request
            else -> null
        } ?: return
        if (request.type !in setOf(BulkFileOperationType.COPY, BulkFileOperationType.MOVE)) return
        when (event) {
            is BulkFileOperationEvent.Started ->
                _state.update {
                    it.copy(activeFileOperation = OperationPresentationMapper.map(event.request))
                }
            is BulkFileOperationEvent.Progress ->
                _state.update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(
                            event.request,
                            event.progress,
                            it.activeFileOperation
                        )
                    )
                }
            is BulkFileOperationEvent.Cancelling ->
                _state.update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(
                            event.request,
                            previous = it.activeFileOperation,
                            isCancelling = true
                        )
                    )
                }
            is BulkFileOperationEvent.Completed -> {
                clipboardController.clear()
                _state.update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(
                            event.request,
                            terminalStatus = OperationCompletionStatus.SUCCESS
                        )
                    )
                }
                load(refresh = true)
            }
            is BulkFileOperationEvent.Failed -> {
                clipboardController.clear()
                _state.update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(
                            event.request,
                            terminalStatus = OperationCompletionStatus.FAILED
                        ),
                        error = UiText.Dynamic(event.message)
                    )
                }
            }
            is BulkFileOperationEvent.Cancelled -> {
                clipboardController.clear()
                _state.update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(
                            request,
                            terminalStatus = OperationCompletionStatus.CANCELLED
                        )
                    )
                }
            }
            else -> Unit
        }
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

private fun AudioLibraryDefaultTab.toFeatureTab(): AudioLibraryTab =
    if (this == AudioLibraryDefaultTab.AUDIO) {
        AudioLibraryTab.AUDIO
    } else {
        AudioLibraryTab.FOLDERS
    }
