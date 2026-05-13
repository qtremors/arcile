package dev.qtremors.arcile.presentation.archive

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.qtremors.arcile.domain.ArchiveEntryModel
import dev.qtremors.arcile.domain.ArchiveSummary
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.navigation.AppRoutes
import dev.qtremors.arcile.presentation.operations.BulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
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

data class ArchiveViewerState(
    val archivePath: String = "",
    val currentPrefix: String? = null,
    val summary: ArchiveSummary? = null,
    val entries: List<ArchiveEntryModel> = emptyList(),
    val visibleItems: List<ArchiveBrowserItem> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class ArchiveViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: FileRepository,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator
) : ViewModel() {
    private val archivePath = savedStateHandle.toRoute<AppRoutes.ArchiveViewer>().archivePath

    private val _state = MutableStateFlow(ArchiveViewerState(archivePath = archivePath))
    val state: StateFlow<ArchiveViewerState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val entriesResult = repository.listArchiveEntries(archivePath)
            val metadataResult = repository.getArchiveMetadata(archivePath)
            if (entriesResult.isFailure || metadataResult.isFailure) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = entriesResult.exceptionOrNull()?.message
                            ?: metadataResult.exceptionOrNull()?.message
                            ?: "Failed to read archive"
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
                    isLoading = false
                )
            }
        }
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

    fun extractAll() {
        startExtract(null)
    }

    fun extractCurrentFolder() {
        startExtract(_state.value.currentPrefix)
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun startExtract(prefix: String?) {
        val archive = File(archivePath)
        val destination = File(archive.parentFile ?: return, archive.nameWithoutExtension).absolutePath
        bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf(archivePath),
            destinationPath = destination,
            resolutions = emptyMap<String, ConflictResolution>(),
            archiveEntryPrefix = prefix
        )
    }

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
