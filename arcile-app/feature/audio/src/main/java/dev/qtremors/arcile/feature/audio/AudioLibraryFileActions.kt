package dev.qtremors.arcile.feature.audio

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
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.normalizeStoragePath
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.ui.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class AudioLibraryFileActions(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<AudioLibraryState>,
    private val clipboardRepository: ClipboardRepository,
    fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    volumeRepository: VolumeRepository,
    private val archivePathResolver: ArchivePathResolver,
    private val operationCoordinator: BulkFileOperationCoordinator,
    private val playback: AudioPlaybackController,
    private val reload: () -> Unit,
    private val rebuildPresentation: ((AudioLibraryState) -> AudioLibraryState) -> Unit
) {
    private val clipboardController = ClipboardController(clipboardRepository)
    private val propertiesLoader = SelectionPropertiesLoader(
        scope = scope,
        repository = fileBrowserRepository,
        onStateChange = { properties ->
            state.update {
                it.copy(
                    isPropertiesVisible = properties.isVisible,
                    isPropertiesLoading = properties.isLoading,
                    properties = properties.properties
                )
            }
        },
        onError = { error ->
            state.update {
                it.copy(
                    error = error.message?.let(UiText::Dynamic)
                        ?: UiText.StringResource(R.string.error_load_properties_failed)
                )
            }
        }
    )
    private val deleteFlow = DeleteFlowDelegate(
        coroutineScope = scope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = deleteCallbacks(),
        startBulkDeleteOperation = { type, selected ->
            operationCoordinator.startOperation(type, selected, null, emptyMap())
        },
        onFailure = reload
    )

    fun toggleSelection(path: String) {
        propertiesLoader.dismiss()
        state.update { current ->
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
        state.update { current ->
            current.copy(selectedPaths = SelectionReducer.add(current.selectedPaths, paths))
        }
    }

    fun togglePaths(paths: Collection<String>) {
        if (paths.isEmpty()) return
        propertiesLoader.dismiss()
        state.update { current ->
            val selected = current.selectedPaths.toMutableSet()
            if (paths.all(selected::contains)) selected.removeAll(paths.toSet())
            else selected.addAll(paths)
            current.copy(selectedPaths = selected)
        }
    }

    fun selectAllVisible() {
        propertiesLoader.dismiss()
        state.update { current ->
            current.copy(selectedPaths = current.visibleSelectionPaths().toSet())
        }
    }

    fun invertSelection() {
        propertiesLoader.dismiss()
        state.update { current ->
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
        state.update {
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
        val selected = state.value.selectedPaths
        val files = state.value.tracks
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
        propertiesLoader.open(state.value.selectedPaths.toList())
    }

    fun dismissProperties() = propertiesLoader.dismiss()

    fun pasteToCurrentFolder() {
        val destination = state.value.folderFilter?.key ?: return
        pasteToFolder(destination)
    }

    fun pasteToFolder(destination: String) {
        if (destination.isBlank()) return
        val clipboard = state.value.clipboardState ?: return
        val sources = clipboard.files.map(FileModel::absolutePath)
        if (sources.isEmpty()) return
        scope.launch {
            state.update { it.copy(pasteDestinationPath = destination, error = null) }
            clipboardRepository.detectCopyConflicts(sources, destination)
                .onSuccess { conflicts ->
                    if (conflicts.isEmpty()) {
                        executePaste(clipboard, destination, emptyMap())
                    } else {
                        state.update {
                            it.copy(
                                pasteConflicts = conflicts,
                                pasteDestinationPath = destination,
                                showPasteConflictDialog = true
                            )
                        }
                    }
                }
                .onFailure { error ->
                    state.update {
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
        val clipboard = state.value.clipboardState ?: return
        val destination = state.value.pasteDestinationPath ?: return
        state.update {
            it.copy(showPasteConflictDialog = false, pasteConflicts = emptyList())
        }
        executePaste(clipboard, destination, resolutions)
    }

    fun dismissPasteConflictDialog() {
        state.update {
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
        state.update { it.copy(activeFileOperation = null) }
    }

    fun renameSelected(newName: String) {
        val path = state.value.selectedPaths.singleOrNull() ?: return
        val track = state.value.tracks.firstOrNull { it.file.absolutePath == path } ?: return
        if (newName.isBlank() || listOf('/', '\\', '\u0000').any(newName::contains) || ".." in newName) {
            state.update { it.copy(error = UiText.StringResource(R.string.error_invalid_name)) }
            return
        }
        scope.launch {
            fileMutationRepository.renameFile(path, newName)
                .onSuccess { renamedFile ->
                    playback.replaceQueueItem(path, track.copy(file = renamedFile))
                    clearSelection()
                    reload()
                }
                .onFailure { error ->
                    state.update {
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
        val selected = state.value.selectedPaths.toList()
        if (selected.isEmpty()) return
        val parentPath = storageParentPath(selected.first()) ?: return
        scope.launch {
            val destination = archivePathResolver.resolve(
                ArchivePathRequest(
                    sourcePaths = selected,
                    parentPath = parentPath,
                    format = ArchiveFormat.ZIP,
                    collisionStyle = ArchiveCollisionStyle.UNDERSCORE
                )
            ).getOrElse {
                state.update {
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
                state.update {
                    it.copy(
                        error = UiText.StringResource(
                            RuntimeR.string.error_operation_already_running
                        )
                    )
                }
            }
        }
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
        state.update {
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

    fun handleOperationEvent(event: BulkFileOperationEvent) {
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
                    reload()
                }
                is BulkFileOperationEvent.Failed ->
                    state.update {
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
                state.update {
                    it.copy(activeFileOperation = OperationPresentationMapper.map(event.request))
                }
            is BulkFileOperationEvent.Progress ->
                state.update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(
                            event.request,
                            event.progress,
                            it.activeFileOperation
                        )
                    )
                }
            is BulkFileOperationEvent.Cancelling ->
                state.update {
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
                state.update {
                    it.copy(
                        activeFileOperation = OperationPresentationMapper.map(
                            event.request,
                            terminalStatus = OperationCompletionStatus.SUCCESS
                        )
                    )
                }
                reload()
            }
            is BulkFileOperationEvent.Failed -> {
                if (event.request.type in clipboardOperationTypes) {
                    clipboardController.clear()
                }
                state.update {
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
                state.update {
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

    private fun deleteCallbacks() = object : DeleteStateCallbacks {
        override fun getSelectedFiles() = state.value.selectedPaths.toList()
        override fun isPermanentDeleteChecked() = state.value.isPermanentDeleteChecked
        override fun isPermanentDeleteToggleEnabled() =
            state.value.isPermanentDeleteToggleEnabled
        override fun setLoading(isLoading: Boolean) =
            state.update { it.copy(isRefreshing = isLoading) }
        override fun showMixedDeleteExplanation() =
            state.update { it.copy(showMixedDeleteExplanation = true) }
        override fun showPermanentDeleteConfirmation() = state.update {
            it.copy(
                showPermanentDeleteConfirmation = true,
                isPermanentDeleteChecked = true,
                isPermanentDeleteToggleEnabled = false
            )
        }
        override fun showTrashConfirmation() = state.update {
            it.copy(
                showTrashConfirmation = true,
                isPermanentDeleteChecked = false,
                isPermanentDeleteToggleEnabled = true
            )
        }
        override fun togglePermanentDeleteChecked() = state.update {
            it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked)
        }
        override fun isShredChecked() = state.value.isShredChecked
        override fun toggleShredChecked() =
            state.update { it.copy(isShredChecked = !it.isShredChecked) }
        override fun dismissDeleteConfirmation() = state.update {
            it.copy(
                showTrashConfirmation = false,
                showPermanentDeleteConfirmation = false,
                showMixedDeleteExplanation = false,
                deleteDecision = null,
                isShredChecked = false
            )
        }
        override fun setError(error: String) =
            state.update { it.copy(error = UiText.Dynamic(error)) }
        override fun setError(error: UiText) = state.update { it.copy(error = error) }
        override fun setDeleteDecision(
            decision: dev.qtremors.arcile.core.storage.domain.DeleteDecision
        ) = state.update { it.copy(deleteDecision = decision) }
        override fun clearSelection() = this@AudioLibraryFileActions.clearSelection()
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
