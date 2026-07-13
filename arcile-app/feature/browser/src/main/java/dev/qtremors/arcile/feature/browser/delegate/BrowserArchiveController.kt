package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.runtime.R as RuntimeR
import dev.qtremors.arcile.core.storage.domain.ArchiveCollisionStyle
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchivePathRequest
import dev.qtremors.arcile.core.storage.domain.ArchivePathResolver
import dev.qtremors.arcile.core.storage.domain.ArchiveExtractionDestinationStyle
import dev.qtremors.arcile.core.storage.domain.ArchiveExtractionPathRequest
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.feature.browser.ArchiveExtractionTarget
import dev.qtremors.arcile.feature.browser.ArchivePasswordAction
import dev.qtremors.arcile.feature.browser.BrowserArchiveContext
import dev.qtremors.arcile.feature.browser.PendingArchiveExtraction
import dev.qtremors.arcile.core.ui.image.ArchiveEntryThumbnailData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class BrowserArchiveWorkflowContext(
    val archiveContext: BrowserArchiveContext?,
    val currentPath: String,
    val selectedPaths: Set<String>
)

internal data class BrowserArchivePasswordPrompt(
    val archivePath: String,
    val password: String? = null,
    val isRequired: Boolean = true,
    val action: ArchivePasswordAction = ArchivePasswordAction.EXTRACT
)

internal data class BrowserArchiveWorkflowState(
    val pendingExtraction: PendingArchiveExtraction? = null,
    val passwordPrompt: BrowserArchivePasswordPrompt? = null
)

internal class BrowserArchiveController(
    initialState: BrowserArchiveWorkflowState,
    private val scope: CoroutineScope,
    private val archiveRepository: ArchiveRepository,
    private val archivePathResolver: ArchivePathResolver,
    private val operationCoordinator: BulkFileOperationCoordinator,
    private val contextProvider: () -> BrowserArchiveWorkflowContext,
    private val clearSelection: () -> Unit,
    private val onWorkflowChanged: (BrowserArchiveWorkflowState) -> Unit,
    private val onConflicts: (List<FileConflict>) -> Unit,
    private val onDismissConflicts: () -> Unit,
    private val onError: (UiText) -> Unit
) {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<BrowserArchiveWorkflowState> = _state.asStateFlow()
    private var observationJob: Job? = null
    private var pendingCreation: PendingArchiveCreation? = null
    private val pendingExtractionSteps = ArrayDeque<PendingExtractionStep>()

    fun startObserving() {
        observationJob?.cancel()
        observationJob = scope.launch {
            operationCoordinator.events.collect(::handleOperationEvent)
        }
    }

    fun stopObserving() {
        observationJob?.cancel()
        observationJob = null
    }

    private fun handleOperationEvent(event: BulkFileOperationEvent) {
        val request = when (event) {
            is BulkFileOperationEvent.Completed -> event.request
            is BulkFileOperationEvent.Failed -> event.request
            is BulkFileOperationEvent.Cancelled -> event.request
            else -> null
        } ?: return

        val creation = pendingCreation
        if (request.type == BulkFileOperationType.CREATE_ARCHIVE &&
            request.destinationPath == creation?.archivePath
        ) {
            pendingCreation = null
            return
        }

        val extraction = pendingExtractionSteps.firstOrNull() ?: return
        if (request.type != BulkFileOperationType.EXTRACT_ARCHIVE ||
            request.sourcePaths.firstOrNull() != extraction.request.archivePath ||
            request.archiveEntryPrefix != extraction.request.entryPrefix
        ) {
            return
        }
        when (event) {
            is BulkFileOperationEvent.Completed -> {
                pendingExtractionSteps.removeFirst()
                pendingExtractionSteps.firstOrNull()?.let {
                    startExtraction(it, clearSelectionAfterStart = false)
                }
            }
            is BulkFileOperationEvent.Failed,
            is BulkFileOperationEvent.Cancelled -> pendingExtractionSteps.clear()
            else -> Unit
        }
    }

    fun extractArchive(target: ArchiveExtractionTarget, customDestination: String?) {
        val context = contextProvider()
        val path = context.archiveContext?.archivePath
            ?: context.selectedPaths.singleOrNull()
            ?: return
        scope.launch {
            val destination = resolveExtractionDestination(path, target, customDestination, context.currentPath)
                ?: return@launch
            inspectAndBegin(
                PendingArchiveExtraction(
                    archivePath = path,
                    destinationPath = destination,
                    password = context.archiveContext?.password,
                    nameEncoding = context.archiveContext?.nameEncoding ?: ArchiveNameEncoding.UTF_8
                )
            )
        }
    }

    fun extractCurrentFolder(target: ArchiveExtractionTarget, customDestination: String?) {
        val context = contextProvider()
        val archive = context.archiveContext ?: return
        val prefix = archive.entryPrefix?.takeIf(String::isNotBlank) ?: return
        scope.launch {
            val destination = resolveExtractionDestination(
                archive.archivePath, target, customDestination, context.currentPath
            ) ?: return@launch
            inspectAndBegin(
                PendingArchiveExtraction(
                    archivePath = archive.archivePath,
                    destinationPath = destination,
                    entryPrefix = prefix,
                    password = archive.password,
                    nameEncoding = archive.nameEncoding
                )
            )
        }
    }

    fun extractSelectedEntries(target: ArchiveExtractionTarget, customDestination: String?) {
        val context = contextProvider()
        val archive = context.archiveContext ?: return
        val prefixes = context.selectedPaths
            .mapNotNull(ArchiveEntryThumbnailData::entryPathFromVirtualPath)
            .distinct()
        if (prefixes.isEmpty()) return
        scope.launch {
            val destination = resolveExtractionDestination(
                archive.archivePath, target, customDestination, context.currentPath
            ) ?: return@launch
            inspectAndBegin(
                PendingArchiveExtraction(
                    archivePath = archive.archivePath,
                    destinationPath = destination,
                    entryPrefix = prefixes.singleOrNull(),
                    entryPrefixes = prefixes,
                    password = archive.password,
                    nameEncoding = archive.nameEncoding
                )
            )
        }
    }

    private fun inspectAndBegin(request: PendingArchiveExtraction) {
        scope.launch {
            detectConflicts(request).fold(
                onSuccess = { conflicts ->
                    if (conflicts.isEmpty()) {
                        beginExtraction(request, emptyMap())
                    } else {
                        update { it.copy(pendingExtraction = request) }
                        onConflicts(conflicts)
                    }
                },
                onFailure = { error ->
                    if (error.isArchivePasswordError()) {
                        update {
                            it.copy(
                                pendingExtraction = request,
                                passwordPrompt = BrowserArchivePasswordPrompt(request.archivePath)
                            )
                        }
                    } else {
                        onError(
                            error.message?.let(UiText::Dynamic)
                                ?: UiText.StringResource(R.string.error_file_operation_failed)
                        )
                    }
                }
            )
        }
    }

    fun confirmPendingExtraction(resolutions: Map<String, ConflictResolution>) {
        val request = state.value.pendingExtraction ?: return
        onDismissConflicts()
        if (beginExtraction(request, resolutions)) {
            update { it.copy(pendingExtraction = null) }
        }
    }

    fun retryWithPassword(password: String) {
        val request = state.value.pendingExtraction ?: return
        val updatedRequest = request.copy(password = password)
        update {
            it.copy(
                pendingExtraction = updatedRequest,
                passwordPrompt = BrowserArchivePasswordPrompt(
                    archivePath = request.archivePath,
                    password = password,
                    isRequired = false
                )
            )
        }
        scope.launch {
            detectConflicts(updatedRequest).fold(
                onSuccess = { conflicts ->
                    if (conflicts.isEmpty()) {
                        if (beginExtraction(updatedRequest, emptyMap())) {
                            update { it.copy(pendingExtraction = null, passwordPrompt = null) }
                        }
                    } else {
                        onConflicts(conflicts)
                    }
                },
                onFailure = { error ->
                    val passwordError = error.isArchivePasswordError()
                    update {
                        it.copy(
                            passwordPrompt = BrowserArchivePasswordPrompt(
                                archivePath = request.archivePath,
                                password = password,
                                isRequired = passwordError
                            )
                        )
                    }
                    if (!passwordError) {
                        onError(
                            error.message?.let(UiText::Dynamic)
                                ?: UiText.StringResource(R.string.error_file_operation_failed)
                        )
                    }
                }
            )
        }
    }

    fun dismissWorkflow() {
        onDismissConflicts()
        update { BrowserArchiveWorkflowState() }
    }

    private suspend fun detectConflicts(
        request: PendingArchiveExtraction
    ): Result<List<FileConflict>> {
        val conflicts = mutableListOf<FileConflict>()
        request.entryPrefixes.ifEmpty { listOf(request.entryPrefix) }.forEach { prefix ->
            archiveRepository.detectArchiveConflicts(
                archivePath = request.archivePath,
                destinationPath = request.destinationPath,
                entryPrefix = prefix,
                password = request.password,
                nameEncoding = request.nameEncoding
            ).onSuccess(conflicts::addAll)
                .onFailure { return Result.failure(it) }
        }
        return Result.success(conflicts)
    }

    private fun beginExtraction(
        request: PendingArchiveExtraction,
        resolutions: Map<String, ConflictResolution>
    ): Boolean {
        val steps = request.entryPrefixes.ifEmpty { listOf(request.entryPrefix) }
            .map { prefix ->
                PendingExtractionStep(
                    request.copy(entryPrefix = prefix, entryPrefixes = emptyList()),
                    resolutions
                )
            }
        pendingExtractionSteps.clear()
        pendingExtractionSteps.addAll(steps)
        val first = pendingExtractionSteps.firstOrNull() ?: return false
        return startExtraction(first, clearSelectionAfterStart = true)
    }

    private fun startExtraction(
        step: PendingExtractionStep,
        clearSelectionAfterStart: Boolean
    ): Boolean {
        val request = step.request
        val started = operationCoordinator.startOperation(
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf(request.archivePath),
            destinationPath = request.destinationPath,
            resolutions = step.resolutions,
            archiveEntryPrefix = request.entryPrefix,
            archivePassword = request.password,
            archiveNameEncoding = request.nameEncoding
        )
        if (started) {
            if (clearSelectionAfterStart) clearSelection()
        } else {
            pendingExtractionSteps.clear()
            onError(UiText.StringResource(RuntimeR.string.error_operation_already_running))
        }
        return started
    }

    fun createArchive(
        archiveName: String,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel,
        password: String?
    ) {
        queueArchiveCreation(archiveName, format, compressionLevel, password)
    }

    private fun queueArchiveCreation(
        archiveName: String?,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel,
        password: String?
    ) {
        val context = contextProvider()
        val selected = context.selectedPaths.toList()
        if (selected.isEmpty() || context.currentPath.isEmpty()) return
        scope.launch {
            val path = archivePathResolver.resolve(
                ArchivePathRequest(
                    sourcePaths = selected,
                    parentPath = context.currentPath,
                    requestedName = archiveName,
                    format = format,
                    collisionStyle = ArchiveCollisionStyle.PARENTHESIZED
                )
            ).getOrElse {
                onError(UiText.StringResource(R.string.error_file_operation_failed))
                return@launch
            }
            pendingCreation = PendingArchiveCreation(
                sourcePaths = selected,
                archivePath = path,
                format = format,
                compressionLevel = compressionLevel,
                password = password
            )
            if (startArchiveCreation()) clearSelection()
        }
    }

    fun createZip() {
        queueArchiveCreation(null, ArchiveFormat.ZIP, ArchiveCompressionLevel.STORE, null)
    }

    private fun startArchiveCreation(): Boolean {
        val creation = pendingCreation ?: return false
        val started = operationCoordinator.startOperation(
            type = BulkFileOperationType.CREATE_ARCHIVE,
            sourcePaths = creation.sourcePaths,
            destinationPath = creation.archivePath,
            resolutions = emptyMap(),
            archiveFormat = creation.format,
            archivePassword = creation.password,
            archiveCompressionLevel = creation.compressionLevel
        )
        if (!started) {
            pendingCreation = null
            onError(UiText.StringResource(RuntimeR.string.error_operation_already_running))
        }
        return started
    }

    private suspend fun resolveExtractionDestination(
        archivePath: String,
        target: ArchiveExtractionTarget,
        customDestination: String?,
        currentPath: String
    ): String? = archivePathResolver.resolveExtraction(
        ArchiveExtractionPathRequest(
            archivePath = archivePath,
            style = when (target) {
                ArchiveExtractionTarget.NAMED_FOLDER -> ArchiveExtractionDestinationStyle.NAMED_FOLDER
                ArchiveExtractionTarget.SAME_FOLDER -> ArchiveExtractionDestinationStyle.SAME_FOLDER
                ArchiveExtractionTarget.CUSTOM_FOLDER -> ArchiveExtractionDestinationStyle.CUSTOM_FOLDER
            },
            currentPath = currentPath,
            customDestination = customDestination
        )
    ).getOrElse {
        onUnsupportedArchive()
        return null
    }

    private fun onUnsupportedArchive() =
        onError(UiText.StringResource(R.string.error_unsupported_archive))

    private fun Throwable.isArchivePasswordError(): Boolean =
        message.orEmpty().contains("password", ignoreCase = true) ||
            message.orEmpty().contains("encrypted", ignoreCase = true)

    private inline fun update(
        transform: (BrowserArchiveWorkflowState) -> BrowserArchiveWorkflowState
    ) {
        _state.update(transform)
        onWorkflowChanged(_state.value)
    }

}

private data class PendingArchiveCreation(
    val sourcePaths: List<String>,
    val archivePath: String,
    val format: ArchiveFormat,
    val compressionLevel: ArchiveCompressionLevel,
    val password: String?
)

private data class PendingExtractionStep(
    val request: PendingArchiveExtraction,
    val resolutions: Map<String, ConflictResolution>
)
