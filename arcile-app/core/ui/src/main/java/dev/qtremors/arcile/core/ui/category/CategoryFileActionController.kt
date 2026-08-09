package dev.qtremors.arcile.core.ui.category

import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.presentation.ClipboardController
import dev.qtremors.arcile.core.presentation.OperationPresentationMapper
import dev.qtremors.arcile.core.presentation.OperationUiState
import dev.qtremors.arcile.core.presentation.PropertiesUiModel
import dev.qtremors.arcile.core.presentation.SelectionPropertiesLoader
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
import dev.qtremors.arcile.core.storage.domain.DeleteDecision
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.normalizeStoragePath
import dev.qtremors.arcile.core.storage.domain.storageParentPath
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import dev.qtremors.arcile.core.ui.fileOperationFeedback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay

data class CategoryFileActionState(
    val clipboardState: ClipboardState? = null,
    val activeOperation: OperationUiState? = null,
    val pasteConflicts: List<dev.qtremors.arcile.core.storage.domain.FileConflict> = emptyList(),
    val pasteDestinationPath: String? = null,
    val showPasteConflictDialog: Boolean = false,
    val showTrashConfirmation: Boolean = false,
    val showPermanentDeleteConfirmation: Boolean = false,
    val showMixedDeleteExplanation: Boolean = false,
    val deleteDecision: DeleteDecision? = null,
    val isPermanentDeleteChecked: Boolean = false,
    val isPermanentDeleteToggleEnabled: Boolean = true,
    val isShredChecked: Boolean = false,
    val isPropertiesVisible: Boolean = false,
    val isPropertiesLoading: Boolean = false,
    val properties: PropertiesUiModel? = null,
    val error: UiText? = null
)

class CategoryFileActionController<S>(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<S>,
    private val clipboardRepository: ClipboardRepository,
    fileBrowserRepository: FileBrowserRepository,
    private val fileMutationRepository: FileMutationRepository,
    volumeRepository: VolumeRepository,
    private val archivePathResolver: ArchivePathResolver,
    private val operationCoordinator: BulkFileOperationCoordinator,
    private val operationOwnerId: String? = null,
    private val files: (S) -> List<FileModel>,
    private val selectedPaths: (S) -> Set<String>,
    private val actionState: (S) -> CategoryFileActionState,
    private val withSelection: (S, Set<String>) -> S,
    private val withActions: (S, CategoryFileActionState) -> S,
    private val withoutPaths: (S, Set<String>) -> S,
    private val onOperationFeedback: (ArcileFeedbackEvent) -> Unit = {},
    private val reload: () -> Unit
) {
    private var terminalClearJob: Job? = null
    private val clipboardController = ClipboardController(clipboardRepository)
    private val propertiesLoader = SelectionPropertiesLoader(
        scope = scope,
        repository = fileBrowserRepository,
        onStateChange = { properties ->
            updateActions {
                it.copy(
                    isPropertiesVisible = properties.isVisible,
                    isPropertiesLoading = properties.isLoading,
                    properties = properties.properties
                )
            }
        },
        onError = {
            setError(
                it.message?.let(UiText::Dynamic)
                    ?: UiText.StringResource(R.string.error_load_properties_failed)
            )
        }
    )
    private val deleteFlow = DeleteFlowDelegate(
        coroutineScope = scope,
        volumeRepository = volumeRepository,
        fileBrowserRepository = fileBrowserRepository,
        callbacks = deleteCallbacks(),
        startBulkDeleteOperation = { type, paths ->
            operationCoordinator.startOperation(
                type,
                paths,
                null,
                emptyMap(),
                presentationOwnerId = operationOwnerId
            )
        },
        onFailure = reload
    )

    fun syncActiveRequest(
        request: dev.qtremors.arcile.core.operation.BulkFileOperationRequest?
    ) {
        val ownedRequest = request?.takeIf {
            it.presentationOwnerId == operationOwnerId && it.type in trackedOperationTypes
        }
        if (ownedRequest != null) {
            terminalClearJob?.cancel()
            updateActions { it.copy(activeOperation = OperationPresentationMapper.map(ownedRequest)) }
        } else if (actionState(state.value).activeOperation?.terminalStatus == null) {
            updateActions { it.copy(activeOperation = null) }
        }
    }

    fun syncClipboard(clipboard: ClipboardState?) =
        updateActions { it.copy(clipboardState = clipboard) }

    fun copySelection(): Int = storeSelection(ClipboardOperation.COPY)
    fun cutSelection(): Int = storeSelection(ClipboardOperation.CUT)
    fun removeFromClipboard(path: String) = clipboardController.remove(path)
    fun requestDelete() = deleteFlow.requestDeleteSelected()
    fun confirmDelete() = deleteFlow.confirmDeleteSelected()
    fun dismissDelete() = deleteFlow.dismissDeleteConfirmation()
    fun togglePermanentDelete() = deleteFlow.togglePermanentDelete()
    fun toggleShred() = deleteFlow.toggleShred()
    fun openProperties() = propertiesLoader.open(selectedPaths(state.value).toList())
    fun dismissProperties() = propertiesLoader.dismiss()

    fun pasteToFolder(destination: String) {
        if (destination.isBlank()) return
        val clipboard = actionState(state.value).clipboardState ?: return
        val sources = clipboard.files.map(FileModel::absolutePath)
        if (sources.isEmpty()) return
        scope.launch {
            updateActions { it.copy(pasteDestinationPath = destination, error = null) }
            clipboardRepository.detectCopyConflicts(sources, destination)
                .onSuccess { conflicts ->
                    if (conflicts.isEmpty()) {
                        executePaste(clipboard, destination, emptyMap())
                    } else {
                        updateActions {
                            it.copy(
                                pasteConflicts = conflicts,
                                pasteDestinationPath = destination,
                                showPasteConflictDialog = true
                            )
                        }
                    }
                }
                .onFailure {
                    updateActions { current ->
                        current.copy(
                            pasteDestinationPath = null,
                            error = UiText.StringResource(R.string.error_check_conflicts_failed)
                        )
                    }
                }
        }
    }

    fun resolvePasteConflicts(resolutions: Map<String, ConflictResolution>) {
        val actions = actionState(state.value)
        val clipboard = actions.clipboardState ?: return
        val destination = actions.pasteDestinationPath ?: return
        updateActions { it.copy(showPasteConflictDialog = false, pasteConflicts = emptyList()) }
        executePaste(clipboard, destination, resolutions)
    }

    fun dismissPasteConflictDialog() = updateActions {
        it.copy(
            showPasteConflictDialog = false,
            pasteConflicts = emptyList(),
            pasteDestinationPath = null
        )
    }

    fun cancelClipboard() {
        val activeRequest = operationCoordinator.activeRequest.value
            ?.takeIf {
                it.presentationOwnerId == operationOwnerId &&
                    it.clipboardSessionId != null &&
                    it.type in clipboardOperationTypes
            }
        if (activeRequest != null) {
            operationCoordinator.cancelActiveOperation()
            activeRequest.clipboardSessionId?.let(clipboardController::clear)
        } else {
            clipboardController.clear()
        }
        dismissPasteConflictDialog()
    }

    fun clearActiveOperation() = updateActions { it.copy(activeOperation = null) }
    fun clearError() = updateActions { it.copy(error = null) }

    fun renameSelected(newName: String) {
        val path = selectedPaths(state.value).singleOrNull() ?: return
        if (newName.isBlank() || listOf('/', '\\', '\u0000').any(newName::contains) || ".." in newName) {
            setError(UiText.StringResource(R.string.error_invalid_name))
            return
        }
        scope.launch {
            fileMutationRepository.renameFile(path, newName)
                .onSuccess {
                    clearSelection()
                    reload()
                }
                .onFailure { setError(UiText.StringResource(R.string.error_rename_file_failed)) }
        }
    }

    fun createZip() {
        val selected = selectedPaths(state.value).toList()
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
                setError(UiText.StringResource(R.string.error_file_operation_failed))
                return@launch
            }
            if (
                operationCoordinator.startOperation(
                    type = BulkFileOperationType.CREATE_ARCHIVE,
                    sourcePaths = selected,
                    destinationPath = destination,
                    resolutions = emptyMap(),
                    archiveFormat = ArchiveFormat.ZIP,
                    archiveCompressionLevel = ArchiveCompressionLevel.STORE,
                    presentationOwnerId = operationOwnerId
                )
            ) {
                clearSelection()
            } else {
                setError(UiText.StringResource(RuntimeR.string.error_operation_already_running))
            }
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
        if (event is BulkFileOperationEvent.Completed) {
            if (request.type in deleteOperationTypes || request.type == BulkFileOperationType.MOVE) {
                removePaths(request.sourcePaths)
            }
            if (request.type in deleteOperationTypes || request.type in trackedOperationTypes) reload()
        }
        if (request.presentationOwnerId != operationOwnerId) return
        if (request.type in deleteOperationTypes) {
            when (event) {
                is BulkFileOperationEvent.Failed ->
                    setError(UiText.StringResource(R.string.error_file_operation_failed))
                else -> Unit
            }
            return
        }
        if (request.type !in trackedOperationTypes) return
        when (event) {
            is BulkFileOperationEvent.Started ->
                updateActions {
                    terminalClearJob?.cancel()
                    it.copy(activeOperation = OperationPresentationMapper.map(event.request))
                }
            is BulkFileOperationEvent.Progress ->
                updateActions {
                    it.copy(
                        activeOperation = OperationPresentationMapper.map(
                            event.request,
                            event.progress,
                            it.activeOperation
                        )
                    )
                }
            is BulkFileOperationEvent.Cancelling ->
                updateActions {
                    it.copy(
                        activeOperation = OperationPresentationMapper.map(
                            event.request,
                            previous = it.activeOperation,
                            isCancelling = true
                        )
                    )
            }
            is BulkFileOperationEvent.Completed -> {
                request.clipboardSessionId?.let(clipboardController::clear)
                updateActions {
                    it.copy(
                        activeOperation = OperationPresentationMapper.map(
                            request,
                            terminalStatus = OperationCompletionStatus.SUCCESS
                        )
                    )
                }
                publishTerminalFeedback(request, OperationCompletionStatus.SUCCESS)
            }
            is BulkFileOperationEvent.Failed -> {
                request.clipboardSessionId?.let(clipboardController::clear)
                updateActions {
                    it.copy(
                        activeOperation = OperationPresentationMapper.map(
                            request,
                            terminalStatus = OperationCompletionStatus.FAILED
                        )
                    )
                }
                publishTerminalFeedback(
                    request,
                    OperationCompletionStatus.FAILED,
                    UiText.StringResource(R.string.error_file_operation_failed)
                )
            }
            is BulkFileOperationEvent.Cancelled -> {
                request.clipboardSessionId?.let(clipboardController::clear)
                updateActions {
                    it.copy(
                        activeOperation = OperationPresentationMapper.map(
                            request,
                            terminalStatus = OperationCompletionStatus.CANCELLED
                        )
                    )
                }
                publishTerminalFeedback(request, OperationCompletionStatus.CANCELLED)
            }
            else -> Unit
        }
    }

    private fun storeSelection(operation: ClipboardOperation): Int {
        val current = state.value
        val selection = selectedPaths(current)
        val selectedFiles = files(current).filter { it.absolutePath in selection }
        return if (clipboardController.store(operation, selectedFiles)) {
            clearSelection()
            selectedFiles.size
        } else {
            0
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
            resolutions = resolutions,
            presentationOwnerId = operationOwnerId,
            clipboardSessionId = clipboard.sessionId
        )
        updateActions {
            it.copy(
                pasteDestinationPath = null,
                showPasteConflictDialog = false,
                pasteConflicts = emptyList(),
                error = if (started) it.error else {
                    UiText.StringResource(RuntimeR.string.error_operation_already_running)
                }
            )
        }
    }

    private fun publishTerminalFeedback(
        request: dev.qtremors.arcile.core.operation.BulkFileOperationRequest,
        status: OperationCompletionStatus,
        error: UiText? = null
    ) {
        onOperationFeedback(fileOperationFeedback(request, status, error))
        terminalClearJob?.cancel()
        terminalClearJob = scope.launch {
            delay(TERMINAL_OPERATION_HOLD_MS)
            clearActiveOperation()
        }
    }

    private fun clearSelection() {
        propertiesLoader.dismiss()
        state.update { withSelection(it, emptySet()) }
    }

    private fun removePaths(paths: Collection<String>) {
        if (paths.isEmpty()) return
        val normalized = paths.mapTo(mutableSetOf(), ::normalizeStoragePath)
        state.update { withoutPaths(it, normalized) }
    }

    private fun updateActions(transform: (CategoryFileActionState) -> CategoryFileActionState) {
        state.update { withActions(it, transform(actionState(it))) }
    }

    private fun setError(error: UiText) = updateActions { it.copy(error = error) }

    private fun deleteCallbacks() = object : DeleteStateCallbacks {
        override fun getSelectedFiles() = selectedPaths(state.value).toList()
        override fun isPermanentDeleteChecked() = actionState(state.value).isPermanentDeleteChecked
        override fun isPermanentDeleteToggleEnabled() =
            actionState(state.value).isPermanentDeleteToggleEnabled
        override fun setLoading(isLoading: Boolean) = Unit
        override fun showMixedDeleteExplanation() =
            updateActions { it.copy(showMixedDeleteExplanation = true) }
        override fun showPermanentDeleteConfirmation() = updateActions {
            it.copy(
                showPermanentDeleteConfirmation = true,
                isPermanentDeleteChecked = true,
                isPermanentDeleteToggleEnabled = false
            )
        }
        override fun showTrashConfirmation() = updateActions {
            it.copy(
                showTrashConfirmation = true,
                isPermanentDeleteChecked = false,
                isPermanentDeleteToggleEnabled = true
            )
        }
        override fun togglePermanentDeleteChecked() =
            updateActions { it.copy(isPermanentDeleteChecked = !it.isPermanentDeleteChecked) }
        override fun isShredChecked() = actionState(state.value).isShredChecked
        override fun toggleShredChecked() =
            updateActions { it.copy(isShredChecked = !it.isShredChecked) }
        override fun dismissDeleteConfirmation() = updateActions {
            it.copy(
                showTrashConfirmation = false,
                showPermanentDeleteConfirmation = false,
                showMixedDeleteExplanation = false,
                deleteDecision = null,
                isShredChecked = false
            )
        }
        override fun setError(error: String) = setError(UiText.Dynamic(error))
        override fun setError(error: UiText) = this@CategoryFileActionController.setError(error)
        override fun setDeleteDecision(decision: DeleteDecision) =
            updateActions { it.copy(deleteDecision = decision) }
        override fun clearSelection() = this@CategoryFileActionController.clearSelection()
    }

    private companion object {
        const val TERMINAL_OPERATION_HOLD_MS = 800L
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
            BulkFileOperationType.DELETE,
            BulkFileOperationType.TRASH,
            BulkFileOperationType.SHRED
        )
    }
}
