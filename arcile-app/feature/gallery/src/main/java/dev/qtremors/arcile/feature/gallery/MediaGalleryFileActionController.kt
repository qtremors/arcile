package dev.qtremors.arcile.feature.gallery

import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.core.presentation.OperationPresentationMapper
import dev.qtremors.arcile.core.presentation.SelectionPropertiesLoader
import dev.qtremors.arcile.core.presentation.SelectionReducer
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.presentation.userMessage
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
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.fileOperationFeedback
import dev.qtremors.arcile.core.presentation.delegate.DeleteFlowDelegate
import dev.qtremors.arcile.core.presentation.delegate.DeleteStateCallbacks
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.storage.domain.normalizeStoragePath
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

internal class MediaGalleryFileActionController(
    initialState: MediaGalleryFileActionState,
    private val scope: CoroutineScope,
    private val fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    private val clipboardRepository: ClipboardRepository,
    private val volumeRepository: VolumeRepository,
    private val operationCoordinator: BulkFileOperationCoordinator,
    private val operationOwnerId: String? = null,
    private val archivePathResolver: ArchivePathResolver,
    private val files: () -> List<FileModel>,
    private val displayedPaths: () -> List<String>,
    private val onStateChange: (MediaGalleryFileActionState) -> Unit,
    private val onBusyChange: (Boolean) -> Unit,
    private val onError: (UiText?) -> Unit,
    private val onOperationFeedback: (ArcileFeedbackEvent) -> Unit = {},
    private val onPathsRemoved: (List<String>) -> Unit,
    private val onRefreshRequested: () -> Unit
) {
    private val clipboardController = ClipboardController(clipboardRepository)
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<MediaGalleryFileActionState> = _state.asStateFlow()
    private val observationJobs = mutableListOf<Job>()
    private var terminalClearJob: Job? = null
    private val propertiesLoader = SelectionPropertiesLoader(
        scope = scope,
        repository = fileBrowserRepository,
        onStateChange = { properties ->
            update {
                it.copy(
                    isPropertiesVisible = properties.isVisible,
                    isPropertiesLoading = properties.isLoading,
                    properties = properties.properties
                )
            }
        },
        onError = { error ->
            onError(
                error.message?.let(UiText::Dynamic)
                    ?: UiText.StringResource(R.string.error_load_properties_failed)
            )
        }
    )

    private val deleteFlow = DeleteFlowDelegate(
        coroutineScope = scope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = deleteCallbacks(),
        startBulkDeleteOperation = { type, selected ->
            operationCoordinator.startOperation(
                type,
                selected,
                null,
                emptyMap(),
                presentationOwnerId = operationOwnerId
            )
        },
        onFailure = onRefreshRequested
    )

    fun startObserving() {
        stopObserving()
        observationJobs += scope.launch(start = CoroutineStart.UNDISPATCHED) {
            operationCoordinator.activeRequest.collectLatest(::syncActiveRequest)
        }
        observationJobs += scope.launch {
            clipboardRepository.clipboardState.collectLatest { clipboard ->
                update { it.copy(clipboardState = clipboard) }
            }
        }
        observationJobs += scope.launch {
            operationCoordinator.events.collect(::handleOperationEvent)
        }
    }

    fun stopObserving() {
        observationJobs.forEach(Job::cancel)
        observationJobs.clear()
        terminalClearJob?.cancel()
    }

    private fun syncActiveRequest(
        request: dev.qtremors.arcile.core.operation.BulkFileOperationRequest?
    ) {
        val ownedRequest = request?.takeIf {
            it.presentationOwnerId == operationOwnerId && it.type in galleryTrackedOperationTypes
        }
        if (ownedRequest != null) {
            terminalClearJob?.cancel()
            onBusyChange(true)
            update { it.copy(activeFileOperation = OperationPresentationMapper.map(ownedRequest)) }
        } else if (_state.value.activeFileOperation?.terminalStatus == null) {
            onBusyChange(false)
            update { it.copy(activeFileOperation = null) }
        }
    }

    fun toggleSelection(path: String) {
        propertiesLoader.dismiss()
        update {
            it.copy(
                selectedFiles = SelectionReducer.toggle(it.selectedFiles, path).toPersistentSet(),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun selectMultiple(paths: List<String>) {
        propertiesLoader.dismiss()
        update {
            it.copy(
                selectedFiles = SelectionReducer.add(it.selectedFiles, paths).toPersistentSet(),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun clearSelection() {
        propertiesLoader.dismiss()
        update {
            it.copy(
                selectedFiles = persistentSetOf(),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun selectAll() {
        propertiesLoader.dismiss()
        update {
            it.copy(
                selectedFiles = SelectionReducer.all(displayedPaths()).toPersistentSet(),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun invertSelection() {
        propertiesLoader.dismiss()
        update {
            it.copy(
                selectedFiles = SelectionReducer.invert(it.selectedFiles, displayedPaths()).toPersistentSet(),
                isPropertiesVisible = false,
                isPropertiesLoading = false,
                properties = null
            )
        }
    }

    fun removePaths(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val normalized = paths.mapTo(mutableSetOf(), ::normalizeStoragePath)
        propertiesLoader.dismiss()
        update {
            it.copy(
                selectedFiles = it.selectedFiles
                    .filterNot { path -> normalizeStoragePath(path) in normalized }
                    .toPersistentSet()
            )
        }
    }

    fun requestDeleteSelected() {
        deleteFlow.requestDeleteSelected()
    }

    fun replaceSelection(paths: Collection<String>) {
        val displayed = displayedPaths().toHashSet()
        update {
            it.copy(
                selectedFiles = paths
                    .filterTo(linkedSetOf()) { path -> path in displayed }
                    .toPersistentSet()
            )
        }
    }

    fun confirmDeleteSelected() {
        deleteFlow.confirmDeleteSelected()
    }

    fun dismissDeleteConfirmation() = deleteFlow.dismissDeleteConfirmation()
    fun togglePermanentDelete() = deleteFlow.togglePermanentDelete()
    fun toggleShred() = deleteFlow.toggleShred()

    fun openProperties() {
        val selected = state.value.selectedFiles.toList()
        propertiesLoader.open(selected)
    }

    fun dismissProperties() = propertiesLoader.dismiss()

    fun copySelected(): Int = storeSelection(ClipboardOperation.COPY)
    fun cutSelected(): Int = storeSelection(ClipboardOperation.CUT)

    private fun storeSelection(operation: ClipboardOperation): Int {
        val selected = state.value.selectedFiles
        val selectedFiles = files().filter { it.reference in selected }
        return if (clipboardController.store(operation, selectedFiles)) {
            clearSelection()
            selectedFiles.size
        } else {
            0
        }
    }

    fun paste(destinationPath: String?) {
        if (!isPasteDestinationFolderPath(destinationPath)) return
        val clipboard = state.value.clipboardState ?: return
        val sources = clipboard.files.map(FileModel::reference)
        if (sources.isEmpty()) return
        scope.launch {
            onBusyChange(true)
            onError(null)
            update { it.copy(pasteDestinationFolderPath = destinationPath) }
            clipboardRepository.detectTransferConflicts(sources, destinationPath!!).onSuccess { conflicts ->
                if (conflicts.isEmpty()) {
                    executePaste(clipboard, destinationPath, emptyMap())
                } else {
                    onBusyChange(false)
                    update {
                        it.copy(
                            pasteConflicts = conflicts.toPersistentList(),
                            showConflictDialog = true,
                            pasteDestinationFolderPath = destinationPath
                        )
                    }
                }
            }.onFailure { error ->
                onBusyChange(false)
                update { it.copy(pasteDestinationFolderPath = null) }
                onError(
                    error.message?.let(UiText::Dynamic)
                        ?: UiText.StringResource(R.string.error_check_conflicts_failed)
                )
            }
        }
    }

    fun resolveConflicts(resolutions: Map<String, ConflictResolution>) {
        val clipboard = state.value.clipboardState ?: return
        val destination = state.value.pasteDestinationFolderPath ?: return
        if (!isPasteDestinationFolderPath(destination)) return
        onBusyChange(true)
        update { it.copy(showConflictDialog = false, pasteConflicts = persistentListOf()) }
        executePaste(clipboard, destination, resolutions)
    }

    fun dismissConflictDialog() {
        onBusyChange(false)
        update {
            it.copy(
                showConflictDialog = false,
                pasteConflicts = persistentListOf(),
                pasteDestinationFolderPath = null
            )
        }
    }

    fun cancelClipboard() {
        val activeRequest = operationCoordinator.activeRequest.value
            ?.takeIf {
                it.presentationOwnerId == operationOwnerId &&
                    it.clipboardSessionId != null &&
                    it.type in galleryClipboardOperationTypes
            }
        if (activeRequest != null) {
            operationCoordinator.cancelActiveOperation()
            activeRequest.clipboardSessionId?.let(clipboardController::clear)
        } else {
            clipboardController.clear()
        }
        dismissConflictDialog()
    }

    fun removeFromClipboard(path: String) = clipboardController.remove(path)
    fun clearActiveOperation() = update { it.copy(activeFileOperation = null) }

    fun createZip() {
        val selected = state.value.selectedFiles.toList()
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
                onError(UiText.StringResource(R.string.error_file_operation_failed))
                return@launch
            }
            operationCoordinator.startOperation(
                type = BulkFileOperationType.CREATE_ARCHIVE,
                sourcePaths = selected,
                destinationPath = destination,
                resolutions = emptyMap(),
                archiveFormat = ArchiveFormat.ZIP,
                archiveCompressionLevel = ArchiveCompressionLevel.STORE,
                presentationOwnerId = operationOwnerId
            )
            clearSelection()
        }
    }

    fun rename(path: String, newName: String) {
        if (newName.isBlank() || listOf('/', '\\', '\u0000').any(newName::contains) || ".." in newName) {
            onError(UiText.StringResource(R.string.error_invalid_name))
            return
        }
        scope.launch {
            fileMutationRepository.renameFile(path, newName)
                .onSuccess {
                    clearSelection()
                    onRefreshRequested()
                }
                .onFailure { error ->
                    onError(
                        error.message?.let(UiText::Dynamic)
                            ?: UiText.StringResource(R.string.error_rename_file_failed)
                    )
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
        if (operationCoordinator.startOperation(
                type = type,
                sourcePaths = clipboard.files.map(FileModel::reference),
                destinationPath = destination,
                resolutions = resolutions,
                presentationOwnerId = operationOwnerId,
                clipboardSessionId = clipboard.sessionId
            )
        ) {
            onBusyChange(false)
            update {
                it.copy(
                    pasteDestinationFolderPath = null,
                    showConflictDialog = false,
                    pasteConflicts = persistentListOf()
                )
            }
        } else {
            onBusyChange(false)
            update { it.copy(pasteDestinationFolderPath = null) }
            onError(UiText.StringResource(RuntimeR.string.error_operation_already_running))
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
        if (request.presentationOwnerId != operationOwnerId) return
        if (request.type !in galleryTrackedOperationTypes) return
        when (event) {
            is BulkFileOperationEvent.Started -> {
                terminalClearJob?.cancel()
                onBusyChange(true)
                onError(null)
                update { it.copy(activeFileOperation = OperationPresentationMapper.map(event.request)) }
            }
            is BulkFileOperationEvent.Progress -> update {
                it.copy(
                    activeFileOperation = OperationPresentationMapper.map(
                        event.request,
                        event.progress,
                        it.activeFileOperation
                    )
                )
            }
            is BulkFileOperationEvent.Cancelling -> update {
                it.copy(
                    activeFileOperation = it.activeFileOperation?.copy(isCancelling = true)
                        ?: OperationPresentationMapper.map(event.request, isCancelling = true)
                )
            }
            is BulkFileOperationEvent.Completed -> finishOperation(
                event.request.type,
                OperationCompletionStatus.SUCCESS,
                event.request
            )
            is BulkFileOperationEvent.Failed -> finishOperation(
                event.request.type,
                OperationCompletionStatus.FAILED,
                event.request,
                event.error?.userMessage
                    ?: event.message.takeIf(String::isNotBlank)?.let(UiText::Dynamic)
            )
            is BulkFileOperationEvent.Cancelled -> {
                finishOperation(request.type, OperationCompletionStatus.CANCELLED, request)
            }
            is BulkFileOperationEvent.RecoveryAvailable,
            is BulkFileOperationEvent.RecoveryCleanupCompleted,
            is BulkFileOperationEvent.RecoveryDismissed -> Unit
        }
    }

    private fun finishOperation(
        type: BulkFileOperationType?,
        status: OperationCompletionStatus,
        request: dev.qtremors.arcile.core.operation.BulkFileOperationRequest?,
        error: UiText? = null
    ) {
        onBusyChange(false)
        val clipboardSessionId = request?.clipboardSessionId
        if (type != null && type in galleryClipboardOperationTypes && clipboardSessionId != null) {
            clipboardController.clear(clipboardSessionId)
        }
        update {
            it.copy(
                clipboardState = if (it.clipboardState?.sessionId == clipboardSessionId) {
                    null
                } else {
                    it.clipboardState
                },
                activeFileOperation = it.activeFileOperation?.copy(terminalStatus = status)
                    ?: request?.let { operation ->
                        OperationPresentationMapper.map(operation, terminalStatus = status)
                    }
            )
        }
        request?.let { onOperationFeedback(fileOperationFeedback(it, status, error)) }
        terminalClearJob?.cancel()
        terminalClearJob = scope.launch {
            delay(TERMINAL_OPERATION_HOLD_MS)
            clearActiveOperation()
        }
    }

    private fun deleteCallbacks() = object : DeleteStateCallbacks {
        override fun getSelectedFiles() = state.value.selectedFiles.toList()
        override fun isPermanentDeleteChecked() = state.value.isPermanentDeleteChecked
        override fun isPermanentDeleteToggleEnabled() = state.value.isPermanentDeleteToggleEnabled
        override fun setLoading(isLoading: Boolean) = onBusyChange(isLoading)
        override fun showMixedDeleteExplanation() =
            update { it.copy(showMixedDeleteExplanation = true) }
        override fun showPermanentDeleteConfirmation() = update {
            it.copy(
                showPermanentDeleteConfirmation = true,
                isPermanentDeleteChecked = true,
                isPermanentDeleteToggleEnabled = false
            )
        }
        override fun showTrashConfirmation() = update {
            it.copy(
                showTrashConfirmation = true,
                isPermanentDeleteChecked = false,
                isPermanentDeleteToggleEnabled = true
            )
        }
        override fun togglePermanentDeleteChecked() =
            update { it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked) }
        override fun isShredChecked() = state.value.isShredChecked
        override fun toggleShredChecked() = update { it.copy(isShredChecked = !it.isShredChecked) }
        override fun dismissDeleteConfirmation() = update {
            it.copy(
                showTrashConfirmation = false,
                showPermanentDeleteConfirmation = false,
                showMixedDeleteExplanation = false,
                deleteDecision = null,
                isShredChecked = false
            )
        }
        override fun setError(error: String) = onError(UiText.Dynamic(error))
        override fun setError(error: UiText) = onError(error)
        override fun setDeleteDecision(decision: dev.qtremors.arcile.core.storage.domain.DeleteDecision) =
            update { it.copy(deleteDecision = decision) }
        override fun clearSelection() = this@MediaGalleryFileActionController.clearSelection()
    }

    private inline fun update(
        transform: (MediaGalleryFileActionState) -> MediaGalleryFileActionState
    ) {
        _state.update(transform)
        onStateChange(_state.value)
    }

    fun batchRename(renames: List<Pair<FileModel, String>>) {
        if (renames.isEmpty()) return
        val selected = state.value.selectedFiles
        if (renames.any { (file, _) -> file.reference !in selected }) return
        scope.launch {
            fileMutationRepository.batchRenameFiles(
                renames.map { (file, newName) -> file.reference to newName }
            ).onSuccess {
                clearSelection()
                onRefreshRequested()
            }.onFailure { error ->
                onError(
                    error.message?.let(UiText::Dynamic)
                        ?: UiText.StringResource(R.string.error_rename_file_failed)
                )
            }
        }
    }

    private companion object {
        const val TERMINAL_OPERATION_HOLD_MS = 800L
    }
}
