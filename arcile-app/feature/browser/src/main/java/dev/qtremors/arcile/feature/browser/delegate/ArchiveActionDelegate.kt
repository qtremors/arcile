package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ArchiveActionDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val coroutineScope: CoroutineScope,
    private val bulkFileOperationCoordinator: BulkFileOperationCoordinator,
    private val clearSelection: () -> Unit
) {
    private val pendingArchives = ArrayDeque<PendingArchiveCreation>()
    private var shouldDeleteSourcesAfterCompletion = false
    private val sourcesToDelete = mutableListOf<String>()
    private var archiveToDeleteAfterExtraction: String? = null

    init {
        coroutineScope.launch {
            bulkFileOperationCoordinator.events.collect { event ->
                handleOperationEvent(event)
            }
        }
    }

    private fun handleOperationEvent(event: BulkFileOperationEvent) {
        val currentRequest = pendingArchives.firstOrNull()
        when (event) {
            is BulkFileOperationEvent.Completed -> {
                if (currentRequest != null &&
                    event.request.type == BulkFileOperationType.CREATE_ARCHIVE &&
                    event.request.destinationPath == currentRequest.archivePath) {
                    pendingArchives.removeFirst()
                    if (pendingArchives.isNotEmpty()) {
                        startNextArchiveCreation()
                    } else {
                        if (shouldDeleteSourcesAfterCompletion && sourcesToDelete.isNotEmpty()) {
                            val sources = sourcesToDelete.toList()
                            sourcesToDelete.clear()
                            shouldDeleteSourcesAfterCompletion = false
                            bulkFileOperationCoordinator.startOperation(
                                type = BulkFileOperationType.DELETE,
                                sourcePaths = sources,
                                destinationPath = null,
                                resolutions = emptyMap()
                            )
                        }
                    }
                } else if (event.request.type == BulkFileOperationType.EXTRACT_ARCHIVE &&
                    event.request.sourcePaths.firstOrNull() == archiveToDeleteAfterExtraction) {
                    val toDelete = archiveToDeleteAfterExtraction
                    archiveToDeleteAfterExtraction = null
                    if (toDelete != null) {
                        bulkFileOperationCoordinator.startOperation(
                            type = BulkFileOperationType.DELETE,
                            sourcePaths = listOf(toDelete),
                            destinationPath = null,
                            resolutions = emptyMap()
                        )
                    }
                }
            }
            is BulkFileOperationEvent.Failed -> {
                if (currentRequest != null &&
                    event.request.type == BulkFileOperationType.CREATE_ARCHIVE &&
                    event.request.destinationPath == currentRequest.archivePath) {
                    pendingArchives.clear()
                    sourcesToDelete.clear()
                    shouldDeleteSourcesAfterCompletion = false
                } else if (event.request.type == BulkFileOperationType.EXTRACT_ARCHIVE &&
                    event.request.sourcePaths.firstOrNull() == archiveToDeleteAfterExtraction) {
                    archiveToDeleteAfterExtraction = null
                }
            }
            is BulkFileOperationEvent.Cancelled -> {
                if (currentRequest != null &&
                    event.request?.type == BulkFileOperationType.CREATE_ARCHIVE &&
                    event.request?.destinationPath == currentRequest.archivePath) {
                    pendingArchives.clear()
                    sourcesToDelete.clear()
                    shouldDeleteSourcesAfterCompletion = false
                } else if (event.request?.type == BulkFileOperationType.EXTRACT_ARCHIVE &&
                    event.request?.sourcePaths?.firstOrNull() == archiveToDeleteAfterExtraction) {
                    archiveToDeleteAfterExtraction = null
                }
            }
            else -> {}
        }
    }

    fun extractArchive(
        password: String?,
        createSubfolder: Boolean,
        deleteArchive: Boolean
    ) {
        val archivePath = state.value.selectedFiles.singleOrNull() ?: return
        val currentPath = state.value.currentPath
        if (!ArchiveFormat.isSupported(archivePath) || currentPath.isEmpty()) {
            state.update { it.copy(error = UiText.StringResource(R.string.error_unsupported_archive)) }
            return
        }

        val destination = if (createSubfolder) {
            val archive = File(archivePath)
            File(currentPath, archive.archiveBaseName()).absolutePath
        } else {
            currentPath
        }

        if (deleteArchive) {
            archiveToDeleteAfterExtraction = archivePath
        } else {
            archiveToDeleteAfterExtraction = null
        }

        bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf(archivePath),
            destinationPath = destination,
            resolutions = emptyMap<String, ConflictResolution>(),
            archivePassword = password
        )
        clearSelection()
    }

    fun createArchiveFromSelection(
        archiveName: String,
        format: ArchiveFormat,
        password: String? = null,
        deleteSources: Boolean = false,
        separateArchives: Boolean = false
    ) {
        val selected = state.value.selectedFiles.toList()
        val currentPath = state.value.currentPath
        if (selected.isEmpty() || currentPath.isEmpty()) return

        pendingArchives.clear()
        shouldDeleteSourcesAfterCompletion = deleteSources
        sourcesToDelete.clear()
        if (deleteSources) {
            sourcesToDelete.addAll(selected)
        }

        if (separateArchives && selected.size > 1) {
            selected.forEach { path ->
                val singleItemName = File(path).nameWithoutExtension.ifBlank { DEFAULT_ARCHIVE_NAME }
                val archivePath = nextArchivePath(currentPath, listOf(path), singleItemName, format)
                pendingArchives.addLast(
                    PendingArchiveCreation(
                        sourcePaths = listOf(path),
                        archivePath = archivePath,
                        format = format,
                        password = password
                    )
                )
            }
        } else {
            val archivePath = nextArchivePath(currentPath, selected, archiveName, format)
            pendingArchives.addLast(
                PendingArchiveCreation(
                    sourcePaths = selected,
                    archivePath = archivePath,
                    format = format,
                    password = password
                )
            )
        }

        startNextArchiveCreation()
        clearSelection()
    }

    fun createZipFromSelection() {
        val selected = state.value.selectedFiles.toList()
        val defaultName = if (selected.size == 1) {
            File(selected.first()).nameWithoutExtension.ifBlank { DEFAULT_ARCHIVE_NAME }
        } else {
            DEFAULT_ARCHIVE_NAME
        }
        createArchiveFromSelection(defaultName, ArchiveFormat.ZIP)
    }

    private fun startNextArchiveCreation() {
        val next = pendingArchives.firstOrNull() ?: return
        val success = bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.CREATE_ARCHIVE,
            sourcePaths = next.sourcePaths,
            destinationPath = next.archivePath,
            resolutions = emptyMap(),
            archiveFormat = next.format,
            archivePassword = next.password
        )
        if (!success) {
            pendingArchives.clear()
            sourcesToDelete.clear()
            shouldDeleteSourcesAfterCompletion = false
            state.update { it.copy(error = UiText.StringResource(R.string.error_file_operation_failed)) }
        }
    }

    private fun nextArchivePath(
        currentPath: String,
        selected: List<String>,
        requestedName: String? = null,
        format: ArchiveFormat = ArchiveFormat.ZIP
    ): String {
        val defaultBaseName = if (selected.size == 1) {
            File(selected.first()).nameWithoutExtension.ifBlank { DEFAULT_ARCHIVE_NAME }
        } else {
            DEFAULT_ARCHIVE_NAME
        }
        val extension = format.extension
        val cleanedName = requestedName
            ?.removeArchiveSuffix(format)
            ?.replace('/', '_')
            ?.replace('\\', '_')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: defaultBaseName
        var candidate = File(currentPath, "$cleanedName.$extension")
        var index = 1
        while (candidate.exists()) {
            candidate = File(currentPath, "$cleanedName ($index).$extension")
            index += 1
        }
        return candidate.absolutePath
    }

    private fun File.archiveBaseName(): String {
        val format = ArchiveFormat.fromPath(name) ?: return nameWithoutExtension
        return name.removeArchiveSuffix(format).ifBlank { nameWithoutExtension }
    }

    private fun String.removeArchiveSuffix(format: ArchiveFormat): String =
        removeSuffix(".${format.extension}")

    private companion object {
        const val DEFAULT_ARCHIVE_NAME = "Archive"
    }
}

private data class PendingArchiveCreation(
    val sourcePaths: List<String>,
    val archivePath: String,
    val format: ArchiveFormat,
    val password: String?
)
