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
import dev.qtremors.arcile.core.presentation.SelectionPropertiesLoader
import dev.qtremors.arcile.core.presentation.SelectionReducer
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.core.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.core.runtime.R as RuntimeR
import dev.qtremors.arcile.core.storage.domain.ArchiveCollisionStyle
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchivePathRequest
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.AudioLibraryRepository
import dev.qtremors.arcile.core.storage.domain.AudioLibraryDefaultTab
import dev.qtremors.arcile.core.storage.domain.AudioLibraryPreferencesStore
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileListingPreferences
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.ImageGalleryGrouping
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.normalizeStoragePath
import dev.qtremors.arcile.core.storage.domain.storageParentPath
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
    private val clipboardController = ClipboardController(clipboardRepository)
    private val propertiesLoader = SelectionPropertiesLoader(
        scope = viewModelScope,
        repository = fileBrowserRepository,
        onStateChange = { properties ->
            _state.update {
                it.copy(
                    isPropertiesVisible = properties.isVisible,
                    isPropertiesLoading = properties.isLoading,
                    properties = properties.properties
                )
            }
        },
        onError = { error ->
            _state.update {
                it.copy(
                    error = error.message?.let(UiText::Dynamic)
                        ?: UiText.StringResource(R.string.error_load_properties_failed)
                )
            }
        }
    )
    private val deleteFlow = DeleteFlowDelegate(
        coroutineScope = viewModelScope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = audioDeleteCallbacks(),
        startBulkDeleteOperation = { type, selected ->
            operationCoordinator.startOperation(type, selected, null, emptyMap())
        },
        onFailure = { load(refresh = true) }
    )

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

    fun toggleSelection(path: String) {
        propertiesLoader.dismiss()
        _state.update { current ->
            current.copy(
                selectedPaths = SelectionReducer.toggle(current.selectedPaths, path),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun selectPaths(paths: Collection<String>) {
        propertiesLoader.dismiss()
        _state.update { current ->
            current.copy(selectedPaths = SelectionReducer.add(current.selectedPaths, paths))
        }
    }

    fun togglePaths(paths: Collection<String>) {
        if (paths.isEmpty()) return
        propertiesLoader.dismiss()
        _state.update { current ->
            val selected = current.selectedPaths.toMutableSet()
            if (paths.all(selected::contains)) selected.removeAll(paths.toSet())
            else selected.addAll(paths)
            current.copy(selectedPaths = selected)
        }
    }

    fun selectAllVisible() {
        propertiesLoader.dismiss()
        _state.update { current ->
            current.copy(selectedPaths = current.visibleSelectionPaths().toSet())
        }
    }

    fun invertSelection() {
        propertiesLoader.dismiss()
        _state.update { current ->
            current.copy(
                selectedPaths = SelectionReducer.invert(
                    current.selectedPaths,
                    current.visibleSelectionPaths()
                )
            )
        }
    }

    fun clearSelection() {
        propertiesLoader.dismiss()
        _state.update {
            it.copy(
                selectedPaths = emptySet(),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
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

    fun removeFromClipboard(path: String) = clipboardController.remove(path)

    fun requestDeleteSelected() = deleteFlow.requestDeleteSelected()
    fun confirmDeleteSelected() = deleteFlow.confirmDeleteSelected()
    fun dismissDeleteConfirmation() = deleteFlow.dismissDeleteConfirmation()
    fun togglePermanentDelete() = deleteFlow.togglePermanentDelete()
    fun toggleShred() = deleteFlow.toggleShred()

    fun openPropertiesForSelection() {
        propertiesLoader.open(_state.value.selectedPaths.toList())
    }

    fun dismissProperties() = propertiesLoader.dismiss()

    fun pasteToCurrentFolder() {
        val destination = _state.value.folderFilter?.key ?: return
        pasteToFolder(destination)
    }

    fun pasteToFolder(destination: String) {
        if (destination.isBlank()) return
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
                            error = error.localizedMessage
                                ?.takeIf(String::isNotBlank)
                                ?.let(UiText::Dynamic)
                                ?: UiText.StringResource(R.string.error_check_conflicts_failed)
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
        val track = _state.value.tracks.firstOrNull { it.file.absolutePath == path } ?: return
        if (newName.isBlank() || listOf('/', '\\', '\u0000').any(newName::contains) || ".." in newName) {
            _state.update { it.copy(error = UiText.StringResource(R.string.error_invalid_name)) }
            return
        }
        viewModelScope.launch {
            fileMutationRepository.renameFile(path, newName)
                .onSuccess { renamedFile ->
                    playback.replaceQueueItem(path, track.copy(file = renamedFile))
                    clearSelection()
                    load(refresh = true)
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            error = error.localizedMessage
                                ?.takeIf(String::isNotBlank)
                                ?.let(UiText::Dynamic)
                                ?: UiText.StringResource(R.string.error_rename_file_failed)
                        )
                    }
                }
        }
    }

    fun createZipFromSelection() {
        val selected = _state.value.selectedPaths.toList()
        if (selected.isEmpty()) return
        val parentPath = storageParentPath(selected.first()) ?: return
        viewModelScope.launch {
            val destination = archivePathResolver.resolve(
                ArchivePathRequest(
                    sourcePaths = selected,
                    parentPath = parentPath,
                    format = ArchiveFormat.ZIP,
                    collisionStyle = ArchiveCollisionStyle.UNDERSCORE
                )
            ).getOrElse {
                _state.update {
                    it.copy(error = UiText.StringResource(R.string.error_file_operation_failed))
                }
                return@launch
            }
            val started = operationCoordinator.startOperation(
                type = BulkFileOperationType.CREATE_ARCHIVE,
                sourcePaths = selected,
                destinationPath = destination,
                resolutions = emptyMap(),
                archiveFormat = ArchiveFormat.ZIP,
                archiveCompressionLevel = ArchiveCompressionLevel.STORE
            )
            if (started) {
                clearSelection()
            } else {
                _state.update {
                    it.copy(
                        error = UiText.StringResource(
                            RuntimeR.string.error_operation_already_running
                        )
                    )
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
                error = if (started) {
                    it.error
                } else {
                    UiText.StringResource(RuntimeR.string.error_operation_already_running)
                }
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
        if (request.type in deleteOperationTypes) {
            when (event) {
                is BulkFileOperationEvent.Completed -> {
                    playback.removeQueueItems(event.request.sourcePaths)
                    removePaths(event.request.sourcePaths)
                    load(refresh = true)
                }
                is BulkFileOperationEvent.Failed ->
                    _state.update {
                        it.copy(
                            error = event.message
                                .takeIf(String::isNotBlank)
                                ?.let(UiText::Dynamic)
                                ?: UiText.StringResource(R.string.error_file_operation_failed)
                        )
                    }
                else -> Unit
            }
            return
        }
        if (request.type !in trackedOperationTypes) return
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
                if (event.request.type in clipboardOperationTypes) {
                    clipboardController.clear()
                }
                if (event.request.type == BulkFileOperationType.MOVE) {
                    playback.removeQueueItems(event.request.sourcePaths)
                    removePaths(event.request.sourcePaths)
                }
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
                if (event.request.type in clipboardOperationTypes) {
                    clipboardController.clear()
                }
                _state.update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(
                            event.request,
                            terminalStatus = OperationCompletionStatus.FAILED
                        ),
                        error = event.message
                            .takeIf(String::isNotBlank)
                            ?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_file_operation_failed)
                    )
                }
            }
            is BulkFileOperationEvent.Cancelled -> {
                if (request.type in clipboardOperationTypes) {
                    clipboardController.clear()
                }
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

    private fun removePaths(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val removed = paths.mapTo(mutableSetOf(), ::normalizeStoragePath)
        rebuildPresentation { current ->
            current.copy(
                tracks = current.tracks.filterNot {
                    normalizeStoragePath(it.file.absolutePath) in removed
                },
                selectedPaths = current.selectedPaths.filterNot {
                    normalizeStoragePath(it) in removed
                }.toSet()
            )
        }
    }

    private fun audioDeleteCallbacks() = object : DeleteStateCallbacks {
        override fun getSelectedFiles() = _state.value.selectedPaths.toList()
        override fun isPermanentDeleteChecked() = _state.value.isPermanentDeleteChecked
        override fun isPermanentDeleteToggleEnabled() =
            _state.value.isPermanentDeleteToggleEnabled
        override fun setLoading(isLoading: Boolean) =
            _state.update { it.copy(isRefreshing = isLoading) }
        override fun showMixedDeleteExplanation() =
            _state.update { it.copy(showMixedDeleteExplanation = true) }
        override fun showPermanentDeleteConfirmation() = _state.update {
            it.copy(
                showPermanentDeleteConfirmation = true,
                isPermanentDeleteChecked = true,
                isPermanentDeleteToggleEnabled = false
            )
        }
        override fun showTrashConfirmation() = _state.update {
            it.copy(
                showTrashConfirmation = true,
                isPermanentDeleteChecked = false,
                isPermanentDeleteToggleEnabled = true
            )
        }
        override fun togglePermanentDeleteChecked() = _state.update {
            it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked)
        }
        override fun isShredChecked() = _state.value.isShredChecked
        override fun toggleShredChecked() =
            _state.update { it.copy(isShredChecked = !it.isShredChecked) }
        override fun dismissDeleteConfirmation() = _state.update {
            it.copy(
                showTrashConfirmation = false,
                showPermanentDeleteConfirmation = false,
                showMixedDeleteExplanation = false,
                deleteDecision = null,
                isShredChecked = false
            )
        }
        override fun setError(error: String) =
            _state.update { it.copy(error = UiText.Dynamic(error)) }
        override fun setError(error: UiText) = _state.update { it.copy(error = error) }
        override fun setDeleteDecision(
            decision: dev.qtremors.arcile.core.storage.domain.DeleteDecision
        ) = _state.update { it.copy(deleteDecision = decision) }
        override fun clearSelection() = this@AudioLibraryViewModel.clearSelection()
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

    private companion object {
        val trackedOperationTypes = setOf(
            BulkFileOperationType.COPY,
            BulkFileOperationType.MOVE,
            BulkFileOperationType.CREATE_ARCHIVE
        )
        val clipboardOperationTypes = setOf(
            BulkFileOperationType.COPY,
            BulkFileOperationType.MOVE
        )
        val deleteOperationTypes = setOf(
            BulkFileOperationType.TRASH,
            BulkFileOperationType.DELETE,
            BulkFileOperationType.SHRED
        )
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
