package dev.qtremors.arcile.feature.archive

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.ui.R
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
    val isDirectory: Boolean,
    val childCount: Int = 0
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
    val searchQuery: String = "",
    val summary: ArchiveSummary? = null,
    val entries: List<ArchiveEntryModel> = emptyList(),
    val visibleItems: List<ArchiveBrowserItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val passwordRequired: Boolean = false,
    val archivePassword: String? = null,
    val nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
    val pendingConflicts: List<FileConflict> = emptyList(),
    val conflictResolutions: Map<String, ConflictResolution> = emptyMap(),
    val pendingExtractionPrefix: String? = null,
    val pendingExtractionPassword: String? = null,
    val activeOperation: ArchiveOperationUiState? = null,
    val operationStatusMessage: ArchiveOperationStatusMessage? = null,
    val selectedItems: Set<String> = emptySet(),
    val pendingExtractionPrefixes: List<String> = emptyList()
) {
    val archiveFormat: ArchiveFormat? get() = ArchiveFormat.fromPath(archivePath)
    val breadcrumbSegments: List<Pair<String, String?>> get() {
        val prefix = currentPrefix?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return listOf("Root" to null)
        val segments = prefix.split('/').filter { it.isNotBlank() }
        return listOf("Root" to null) + segments.mapIndexed { index, segment ->
            segment to segments.take(index + 1).joinToString("/")
        }
    }
}

@HiltViewModel
class ArchiveViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: ArchiveRepository,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    @ApplicationContext private val context: Context
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
            val encoding = _state.value.nameEncoding
            val entriesResult = repository.listArchiveEntries(archivePath, password, encoding)
            val metadataResult = repository.getArchiveMetadata(archivePath, password, encoding)
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
                    visibleItems = buildVisibleItems(entries, it.currentPrefix, it.searchQuery),
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

    fun selectNameEncoding(encoding: ArchiveNameEncoding) {
        if (_state.value.nameEncoding == encoding) return
        _state.update { it.copy(nameEncoding = encoding, currentPrefix = null, pendingConflicts = emptyList(), conflictResolutions = emptyMap(), selectedItems = emptySet()) }
        load()
    }

    fun openFolder(path: String) {
        _state.update {
            it.copy(
                currentPrefix = path,
                visibleItems = buildVisibleItems(it.entries, path, it.searchQuery),
                selectedItems = emptySet()
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _state.update {
            it.copy(
                searchQuery = query,
                visibleItems = buildVisibleItems(it.entries, it.currentPrefix, query),
                selectedItems = emptySet()
            )
        }
    }

    fun toggleItemSelection(path: String) {
        _state.update { state ->
            val next = if (state.selectedItems.contains(path)) state.selectedItems - path else state.selectedItems + path
            state.copy(selectedItems = next)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedItems = emptySet()) }
    }

    fun selectAllVisible() {
        _state.update { state ->
            state.copy(selectedItems = state.visibleItems.map { it.path }.toSet())
        }
    }

    fun navigateBack(): Boolean {
        val prefix = _state.value.currentPrefix ?: return false
        val parent = prefix.trimEnd('/').substringBeforeLast('/', missingDelimiterValue = "").takeIf { it.isNotBlank() }
        _state.update {
            it.copy(
                currentPrefix = parent,
                visibleItems = buildVisibleItems(it.entries, parent, it.searchQuery),
                selectedItems = emptySet()
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

    fun extractSelected(password: String? = null) {
        val selected = _state.value.selectedItems.toList()
        if (selected.isEmpty()) return
        _state.update {
            it.copy(
                pendingExtractionPrefixes = selected,
                selectedItems = emptySet()
            )
        }
        startNextSequentialExtraction(password)
    }

    private fun startNextSequentialExtraction(password: String? = null) {
        val next = _state.value.pendingExtractionPrefixes.firstOrNull() ?: return
        startExtract(next, password)
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

    fun setConflictResolution(sourcePath: String, resolution: ConflictResolution) {
        _state.update { state ->
            state.copy(conflictResolutions = state.conflictResolutions + (sourcePath to resolution))
        }
    }

    fun applyConflictResolutionToAll(resolution: ConflictResolution) {
        _state.update { state ->
            state.copy(conflictResolutions = state.pendingConflicts.associate { it.sourcePath to resolution })
        }
    }

    fun dismissConflicts() {
        _state.update {
            it.copy(
                pendingConflicts = emptyList(),
                conflictResolutions = emptyMap(),
                pendingExtractionPrefix = null,
                pendingExtractionPassword = null
            )
        }
    }

    fun confirmConflictResolutions() {
        val state = _state.value
        if (state.pendingConflicts.isEmpty()) return
        val unresolved = state.pendingConflicts.any { state.conflictResolutions[it.sourcePath] == null }
        if (unresolved) return
        beginExtractOperation(
            prefix = state.pendingExtractionPrefix,
            password = state.pendingExtractionPassword,
            resolutions = state.conflictResolutions
        )
        dismissConflicts()
    }

    private fun startExtract(prefix: String?, password: String?) {
        viewModelScope.launch {
            val archive = File(archivePath)
            val destination = File(archive.parentFile ?: return@launch, archive.archiveBaseName()).absolutePath
            val effectivePassword = password ?: _state.value.archivePassword
            val encoding = _state.value.nameEncoding
            val result = repository.detectArchiveConflicts(
                archivePath = archivePath,
                destinationPath = destination,
                entryPrefix = prefix,
                password = effectivePassword,
                nameEncoding = encoding
            )
            result.fold(
                onSuccess = { conflicts ->
                    if (conflicts.isEmpty()) {
                        beginExtractOperation(prefix, effectivePassword, emptyMap())
                    } else {
                        _state.update {
                            it.copy(
                                pendingConflicts = conflicts,
                                conflictResolutions = emptyMap(),
                                pendingExtractionPrefix = prefix,
                                pendingExtractionPassword = effectivePassword
                            )
                        }
                    }
                },
                onFailure = { error ->
                    _state.update {
                        it.copy(error = error.message ?: context.getString(R.string.archive_conflict_inspection_failed))
                    }
                }
            )
        }
    }

    private fun beginExtractOperation(prefix: String?, password: String?, resolutions: Map<String, ConflictResolution>) {
        val archive = File(archivePath)
        val destination = File(archive.parentFile ?: return, archive.archiveBaseName()).absolutePath
        bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf(archivePath),
            destinationPath = destination,
            resolutions = resolutions,
            archiveEntryPrefix = prefix,
            archivePassword = password ?: _state.value.archivePassword,
            archiveNameEncoding = _state.value.nameEncoding
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
                        val pending = _state.value.pendingExtractionPrefixes
                        if (pending.isNotEmpty()) {
                            val nextPending = pending.drop(1)
                            _state.update { it.copy(pendingExtractionPrefixes = nextPending) }
                            if (nextPending.isNotEmpty()) {
                                startNextSequentialExtraction(event.request.archivePassword)
                            } else {
                                _state.update {
                                    it.copy(
                                        activeOperation = it.activeOperation?.copy(terminalStatus = OperationCompletionStatus.SUCCESS),
                                        operationStatusMessage = ArchiveOperationStatusMessage.ExtractionComplete
                                    )
                                }
                            }
                        } else {
                            _state.update {
                                it.copy(
                                    activeOperation = it.activeOperation?.copy(terminalStatus = OperationCompletionStatus.SUCCESS),
                                    operationStatusMessage = ArchiveOperationStatusMessage.ExtractionComplete
                                )
                            }
                        }
                    }
                    is BulkFileOperationEvent.Failed -> {
                        if (!event.request.isCurrentArchiveExtraction()) return@collectLatest
                        _state.update {
                            it.copy(
                                activeOperation = it.activeOperation?.copy(terminalStatus = OperationCompletionStatus.FAILED),
                                error = event.message,
                                pendingExtractionPrefixes = emptyList()
                            )
                        }
                    }
                    is BulkFileOperationEvent.Cancelled -> {
                        val request = event.request ?: return@collectLatest
                        if (!request.isCurrentArchiveExtraction()) return@collectLatest
                        _state.update {
                            it.copy(
                                activeOperation = it.activeOperation?.copy(terminalStatus = OperationCompletionStatus.CANCELLED),
                                operationStatusMessage = ArchiveOperationStatusMessage.ExtractionCancelled,
                                pendingExtractionPrefixes = emptyList()
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

    private fun buildVisibleItems(entries: List<ArchiveEntryModel>, prefix: String?, searchQuery: String): List<ArchiveBrowserItem> {
        val normalizedPrefix = prefix?.trimEnd('/')?.takeIf { it.isNotBlank() }
        val normalizedQuery = searchQuery.trim().lowercase().takeIf { it.isNotBlank() }
        val children = linkedMapOf<String, ArchiveBrowserItem>()
        entries.forEach { entry ->
            if (normalizedQuery != null && !entry.path.lowercase().contains(normalizedQuery)) return@forEach
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
            val childCount = if (isFolder) {
                entries.count { candidate ->
                    val candidatePath = candidate.path.trim('/')
                    candidatePath.startsWith("$childPath/") && candidatePath.removePrefix("$childPath/").trim('/').isNotBlank()
                }
            } else {
                0
            }
            val existing = children[childPath]
            if (existing == null || (!isFolder && existing.isDirectory)) {
                children[childPath] = ArchiveBrowserItem(
                    name = childName,
                    path = childPath,
                    size = if (isFolder) 0L else entry.size,
                    lastModified = entry.lastModified,
                    isDirectory = isFolder,
                    childCount = childCount
                )
            }
        }
        return children.values.sortedWith(compareBy<ArchiveBrowserItem> { !it.isDirectory }.thenBy { it.name.lowercase() })
    }

    private fun File.archiveBaseName(): String {
        val format = ArchiveFormat.fromPath(name) ?: return nameWithoutExtension
        return name.removeSuffix(".${format.extension}").ifBlank { nameWithoutExtension }
    }
}
