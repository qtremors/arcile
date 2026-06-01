package dev.qtremors.arcile.feature.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class ArchiveBrowserItem(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long?,
    val isDirectory: Boolean
)

data class ArchiveOperationUiState(
    val totalItems: Int,
    val completedItems: Int = 0,
    val currentPath: String? = null,
    val isCancelling: Boolean = false,
    val terminalStatus: OperationCompletionStatus? = null
)

enum class ArchiveOperationStatusMessage {
    ExtractionComplete,
    ExtractionCancelled
}

data class ArchiveViewerState(
    val archivePath: String = "",
    val currentPrefix: String? = null,
    val summary: ArchiveSummary? = null,
    val entries: List<ArchiveEntryModel> = emptyList(),
    val visibleItems: List<ArchiveBrowserItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val passwordRequired: Boolean = false,
    val archivePassword: String? = null,
    val activeOperation: ArchiveOperationUiState? = null,
    val operationStatusMessage: ArchiveOperationStatusMessage? = null
)

@HiltViewModel
class ArchiveViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ArchiveRepository,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator
) : ViewModel() {
    private val archivePath = savedStateHandle.get<String>("archivePath").orEmpty()

    private val _state = MutableStateFlow(ArchiveViewerState(archivePath = archivePath))
    val state: StateFlow<ArchiveViewerState> = _state.asStateFlow()

    init {
        load()
        observeArchiveOperations()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val password = _state.value.archivePassword
            val entriesResult = repository.listArchiveEntries(archivePath, password)
            val metadataResult = repository.getArchiveMetadata(archivePath, password)
            if (entriesResult.isFailure || metadataResult.isFailure) {
                val message = entriesResult.exceptionOrNull()?.message
                    ?: metadataResult.exceptionOrNull()?.message
                    ?: "Failed to read archive"
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = message,
                        passwordRequired = message.contains("password", ignoreCase = true)
                    )
                }
                return@launch
            }
            val entries = entriesResult.getOrThrow()
            _state.update {
                it.copy(
                    entries = entries,
                    summary = metadataResult.getOrThrow(),
                    visibleItems = buildVisibleItems(entries, it.currentPrefix),
                    isLoading = false,
                    passwordRequired = false
                )
            }
        }
    }

    fun submitPassword(password: String) {
        _state.update { it.copy(archivePassword = password.takeIf { value -> value.isNotEmpty() }, passwordRequired = false, error = null) }
        load()
    }

    fun openFolder(path: String) {
        _state.update {
            it.copy(
                currentPrefix = path,
                visibleItems = buildVisibleItems(it.entries, path)
            )
        }
    }

    fun navigateBack(): Boolean {
        val prefix = _state.value.currentPrefix ?: return false
        val parent = prefix.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        _state.update {
            it.copy(
                currentPrefix = parent,
                visibleItems = buildVisibleItems(it.entries, parent)
            )
        }
        return true
    }

    fun extractAll(password: String? = null) {
        startExtract(null, password)
    }

    fun extractCurrentFolder(password: String? = null) {
        startExtract(_state.value.currentPrefix, password)
    }

    fun cancelExtraction() {
        bulkFileOperationCoordinator.cancelActiveOperation()
    }

    fun clearOperationStatusMessage() {
        _state.update { it.copy(operationStatusMessage = null) }
    }

    fun clearActiveOperation() {
        _state.update { it.copy(activeOperation = null) }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun startExtract(prefix: String?, password: String?) {
        val archive = File(archivePath)
        val destination = File(archive.parentFile ?: return, archive.nameWithoutExtension).absolutePath
        bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf(archivePath),
            destinationPath = destination,
            resolutions = emptyMap<String, ConflictResolution>(),
            archiveEntryPrefix = prefix,
            archivePassword = password ?: _state.value.archivePassword
        )
    }

    private fun observeArchiveOperations() {
        viewModelScope.launch {
            bulkFileOperationCoordinator.events.collectLatest { event ->
                when (event) {
                    is BulkFileOperationEvent.Started -> {
                        if (!event.request.isCurrentArchiveExtraction()) return@collectLatest
                        _state.update {
                            it.copy(
                                activeOperation = ArchiveOperationUiState(
                                    totalItems = event.request.sourcePaths.size,
                                    currentPath = event.request.archiveEntryPrefix ?: event.request.sourcePaths.firstOrNull()
                                ),
                                operationStatusMessage = null
                            )
                        }
                    }
                    is BulkFileOperationEvent.Progress -> {
                        if (!event.request.isCurrentArchiveExtraction()) return@collectLatest
                        _state.update {
                            it.copy(
                                activeOperation = ArchiveOperationUiState(
                                    totalItems = event.progress.totalItems,
                                    completedItems = event.progress.completedItems,
                                    currentPath = event.progress.currentPath,
                                    isCancelling = false
                                )
                            )
                        }
                    }
                    is BulkFileOperationEvent.Cancelling -> {
                        if (!event.request.isCurrentArchiveExtraction()) return@collectLatest
                        _state.update { currentState ->
                            currentState.copy(
                                activeOperation = currentState.activeOperation?.copy(isCancelling = true)
                                    ?: ArchiveOperationUiState(
                                        totalItems = event.request.sourcePaths.size,
                                        currentPath = event.request.archiveEntryPrefix,
                                        isCancelling = true
                                    )
                            )
                        }
                    }
                    is BulkFileOperationEvent.Completed -> {
                        if (!event.request.isCurrentArchiveExtraction()) return@collectLatest
                        _state.update {
                            it.copy(
                                activeOperation = it.activeOperation?.copy(terminalStatus = OperationCompletionStatus.SUCCESS),
                                operationStatusMessage = ArchiveOperationStatusMessage.ExtractionComplete
                            )
                        }
                    }
                    is BulkFileOperationEvent.Failed -> {
                        if (!event.request.isCurrentArchiveExtraction()) return@collectLatest
                        _state.update {
                            it.copy(
                                activeOperation = it.activeOperation?.copy(terminalStatus = OperationCompletionStatus.FAILED),
                                error = event.message
                            )
                        }
                    }
                    is BulkFileOperationEvent.Cancelled -> {
                        val request = event.request ?: return@collectLatest
                        if (!request.isCurrentArchiveExtraction()) return@collectLatest
                        _state.update {
                            it.copy(
                                activeOperation = it.activeOperation?.copy(terminalStatus = OperationCompletionStatus.CANCELLED),
                                operationStatusMessage = ArchiveOperationStatusMessage.ExtractionCancelled
                            )
                        }
                    }
                    is BulkFileOperationEvent.RecoveryAvailable,
                    is BulkFileOperationEvent.RecoveryDismissed,
                    is BulkFileOperationEvent.RecoveryCleanupCompleted -> Unit
                }
            }
        }
    }

    private fun dev.qtremors.arcile.core.operation.BulkFileOperationRequest.isCurrentArchiveExtraction(): Boolean =
        type == BulkFileOperationType.EXTRACT_ARCHIVE && sourcePaths.firstOrNull() == archivePath

    private fun buildVisibleItems(entries: List<ArchiveEntryModel>, prefix: String?): List<ArchiveBrowserItem> {
        val normalizedPrefix = prefix?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val children = linkedMapOf<String, ArchiveBrowserItem>()
        entries.forEach { entry ->
            val path = entry.path.trim('/')
            val remainder = if (normalizedPrefix == null) {
                path
            } else if (path == normalizedPrefix) {
                ""
            } else if (path.startsWith("$normalizedPrefix/")) {
                path.removePrefix("$normalizedPrefix/")
            } else {
                return@forEach
            }
            if (remainder.isBlank()) return@forEach
            val childName = remainder.substringBefore('/')
            val childPath = if (normalizedPrefix == null) childName else "$normalizedPrefix/$childName"
            val isFolder = remainder.contains('/') || entries.any { it.isDirectory && it.path.trimEnd('/') == childPath }
            val existing = children[childPath]
            if (existing == null || (!isFolder && existing.isDirectory)) {
                children[childPath] = ArchiveBrowserItem(
                    name = childName,
                    path = childPath,
                    size = if (isFolder) 0L else entry.size,
                    lastModified = entry.lastModified,
                    isDirectory = isFolder
                )
            }
        }
        return children.values.sortedWith(compareBy<ArchiveBrowserItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }
}
