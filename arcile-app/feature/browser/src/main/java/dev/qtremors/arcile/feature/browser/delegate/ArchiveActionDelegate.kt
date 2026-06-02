package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.feature.browser.ArchiveExtractionTarget
import dev.qtremors.arcile.feature.browser.ArchivePasswordAction
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.feature.browser.BrowserState
import dev.qtremors.arcile.feature.browser.PendingArchiveExtraction
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import java.io.File
import kotlinx.collections.immutable.toPersistentList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ArchiveActionDelegate(
    private val state: MutableStateFlow<BrowserState>,
    private val coroutineScope: CoroutineScope,
    private val archiveRepository: ArchiveRepository,
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
        target: ArchiveExtractionTarget,
        customDestination: String?
    ) {
        val archivePath = state.value.archiveContext?.archivePath ?: state.value.selectedFiles.singleOrNull() ?: return
        val currentPath = state.value.currentPath
        val parentPath = File(archivePath).parent.orEmpty().ifBlank { currentPath }
        if (!ArchiveFormat.isSupported(archivePath) || parentPath.isEmpty()) {
            state.update { it.copy(error = UiText.StringResource(R.string.error_unsupported_archive)) }
            return
        }

        val archive = File(archivePath)
        val destination = when (target) {
            ArchiveExtractionTarget.NAMED_FOLDER -> File(parentPath, archive.archiveBaseName()).absolutePath
            ArchiveExtractionTarget.SAME_FOLDER -> parentPath
            ArchiveExtractionTarget.CUSTOM_FOLDER -> customDestination?.takeIf { it.isNotBlank() } ?: parentPath
        }
        val archiveContext = state.value.archiveContext
        val request = PendingArchiveExtraction(
            archivePath = archivePath,
            destinationPath = destination,
            entryPrefix = null,
            password = archiveContext?.password,
            nameEncoding = archiveContext?.nameEncoding ?: ArchiveNameEncoding.UTF_8
        )

        coroutineScope.launch {
            archiveRepository.detectArchiveConflicts(
                archivePath = archivePath,
                destinationPath = destination,
                entryPrefix = null,
                password = request.password,
                nameEncoding = request.nameEncoding
            ).onSuccess { conflicts ->
                if (conflicts.isEmpty()) {
                    beginExtraction(request, emptyMap())
                } else {
                    state.update {
                        it.copy(
                            pendingArchiveExtraction = request,
                            pasteConflicts = conflicts.toPersistentList(),
                            showConflictDialog = true
                        )
                    }
                }
            }.onFailure { error ->
                if (error.isArchivePasswordError()) {
                    state.update {
                        it.copy(
                            pendingArchiveExtraction = request,
                            archiveContext = archiveContext ?: BrowserArchiveContext(archivePath = archivePath),
                        ).copy(
                            archiveContext = (it.archiveContext ?: BrowserArchiveContext(archivePath = archivePath)).copy(
                                passwordRequired = true,
                                pendingPasswordAction = ArchivePasswordAction.EXTRACT
                            )
                        )
                    }
                } else {
                    state.update { it.copy(error = error.message?.let(UiText::Dynamic) ?: UiText.StringResource(R.string.error_file_operation_failed)) }
                }
            }
        }
    }

    fun confirmPendingExtraction(resolutions: Map<String, ConflictResolution>) {
        val request = state.value.pendingArchiveExtraction ?: return
        beginExtraction(request, resolutions)
        state.update {
            it.copy(
                pendingArchiveExtraction = null,
                pasteConflicts = kotlinx.collections.immutable.persistentListOf(),
                showConflictDialog = false
            )
        }
    }

    fun retryPendingExtractionWithPassword(password: String) {
        val request = state.value.pendingArchiveExtraction ?: return
        val updated = request.copy(password = password)
        state.update {
            it.copy(
                pendingArchiveExtraction = updated,
                archiveContext = (it.archiveContext ?: BrowserArchiveContext(archivePath = request.archivePath)).copy(
                    password = password,
                    passwordRequired = false
                )
            )
        }
        coroutineScope.launch {
            archiveRepository.detectArchiveConflicts(
                archivePath = updated.archivePath,
                destinationPath = updated.destinationPath,
                entryPrefix = updated.entryPrefix,
                password = updated.password,
                nameEncoding = updated.nameEncoding
            ).onSuccess { conflicts ->
                if (conflicts.isEmpty()) {
                    beginExtraction(updated, emptyMap())
                    state.update { it.copy(pendingArchiveExtraction = null) }
                } else {
                    state.update {
                        it.copy(
                            pasteConflicts = conflicts.toPersistentList(),
                            showConflictDialog = true
                        )
                    }
                }
            }.onFailure { error ->
                state.update {
                    it.copy(
                        archiveContext = (it.archiveContext ?: BrowserArchiveContext(archivePath = request.archivePath)).copy(
                            passwordRequired = error.isArchivePasswordError(),
                            pendingPasswordAction = ArchivePasswordAction.EXTRACT
                        ),
                        error = if (error.isArchivePasswordError()) null else error.message?.let(UiText::Dynamic)
                    )
                }
            }
        }
    }

    private fun beginExtraction(request: PendingArchiveExtraction, resolutions: Map<String, ConflictResolution>) {
        archiveToDeleteAfterExtraction = null
        bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf(request.archivePath),
            destinationPath = request.destinationPath,
            resolutions = resolutions,
            archiveEntryPrefix = request.entryPrefix,
            archivePassword = request.password,
            archiveNameEncoding = request.nameEncoding
        )
        clearSelection()
    }

    fun createArchiveFromSelection(
        archiveName: String,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel,
        password: String? = null,
    ) {
        val selected = state.value.selectedFiles.toList()
        val currentPath = state.value.currentPath
        if (selected.isEmpty() || currentPath.isEmpty()) return

        pendingArchives.clear()
        shouldDeleteSourcesAfterCompletion = false
        sourcesToDelete.clear()

        coroutineScope.launch {
            val archivePath = withContext(Dispatchers.IO) {
                nextArchivePath(currentPath, selected, archiveName, format)
            }
            pendingArchives.addLast(
                PendingArchiveCreation(
                    sourcePaths = selected,
                    archivePath = archivePath,
                    format = format,
                    compressionLevel = compressionLevel,
                    password = password
                )
            )
            clearSelection()
            startNextArchiveCreation()
        }
    }

    fun createZipFromSelection() {
        val selected = state.value.selectedFiles.toList()
        val defaultName = if (selected.size == 1) {
            File(selected.first()).nameWithoutExtension.ifBlank { DEFAULT_ARCHIVE_NAME }
        } else {
            DEFAULT_ARCHIVE_NAME
        }
        createArchiveFromSelection(defaultName, ArchiveFormat.ZIP, ArchiveCompressionLevel.STORE)
    }

    private fun startNextArchiveCreation() {
        val next = pendingArchives.firstOrNull() ?: return
        val success = bulkFileOperationCoordinator.startOperation(
            type = BulkFileOperationType.CREATE_ARCHIVE,
            sourcePaths = next.sourcePaths,
            destinationPath = next.archivePath,
            resolutions = emptyMap(),
            archiveFormat = next.format,
            archivePassword = next.password,
            archiveCompressionLevel = next.compressionLevel
        )
        if (!success) {
            pendingArchives.clear()
            sourcesToDelete.clear()
            shouldDeleteSourcesAfterCompletion = false
            state.update { it.copy(error = UiText.StringResource(R.string.error_file_operation_failed)) }
        }
    }

    private fun Throwable.isArchivePasswordError(): Boolean =
        message.orEmpty().contains("password", ignoreCase = true) ||
            message.orEmpty().contains("encrypted", ignoreCase = true)

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
    val compressionLevel: ArchiveCompressionLevel,
    val password: String?
)
