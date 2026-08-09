package dev.qtremors.arcile.core.operation.android

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationRecoveryRecord
import dev.qtremors.arcile.core.operation.SaveToArcileImportItem
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import dev.qtremors.arcile.core.storage.domain.ActivityLogOperationStatus
import dev.qtremors.arcile.core.storage.domain.ActivityLogStore
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.NoOpClipboardRepository
import dev.qtremors.arcile.core.storage.domain.toArcileError
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val PROGRESS_MIN_INTERVAL_MS = 500L
private const val PROGRESS_HEARTBEAT_INTERVAL_MS = 1_500L
private const val PROGRESS_BYTE_STEP = 4L * 1024 * 1024

@Singleton
class ForegroundBulkFileOperationCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val operationJournal: OperationJournal = DefaultOperationJournal(context),
    private val mutationJournal: MutationJournal = NoOpMutationJournal(),
    private val activityLogStore: ActivityLogStore? = null,
    @param:ApplicationScope private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    @param:DeferOperationJournalRecovery private val deferJournalRecovery: Boolean = false,
    private val clipboardRepository: ClipboardRepository = NoOpClipboardRepository
) : BulkFileOperationCoordinator {
    private val json = Json { ignoreUnknownKeys = true }
    private val _activeRequest = MutableStateFlow<BulkFileOperationRequest?>(null)
    override val activeRequest: StateFlow<BulkFileOperationRequest?> = _activeRequest.asStateFlow()
    private val _recoveryRecords = MutableStateFlow<List<OperationRecoveryRecord>>(emptyList())
    override val recoveryRecords: StateFlow<List<OperationRecoveryRecord>> = _recoveryRecords.asStateFlow()
    private val activityLogWriteLock = Any()
    private val latestActivityLogSequence = mutableMapOf<String, Long>()
    private var activityLogSequence = 0L
    private var activityLogWriteJob: Job? = null
    private val progressLock = Any()
    private var progressState: ProgressState? = null
    internal var progressClock: () -> Long = System::currentTimeMillis

    private val _events = MutableSharedFlow<BulkFileOperationEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<BulkFileOperationEvent> = _events.asSharedFlow()

    init {
        if (deferJournalRecovery) {
            applicationScope.launch { hydrateRecoveredOperations() }
        } else {
            hydrateRecoveredOperations()
        }
    }

    private fun hydrateRecoveredOperations() {
        val recoveredRecords = operationJournal.recoverInterrupted().map { it.toRecoveryRecord() }
        if (_activeRequest.value == null) {
            _activeRequest.value = operationJournal.activeRecord()?.request
        }
        _recoveryRecords.value = recoveredRecords
        recoveredRecords.forEach { record ->
            _events.tryEmit(BulkFileOperationEvent.RecoveryAvailable(record))
        }
    }

    override fun startOperation(
        type: BulkFileOperationType,
        sourcePaths: List<String>,
        destinationPath: String?,
        resolutions: Map<String, ConflictResolution>,
        fakeFileSize: Long?,
        archiveFormat: ArchiveFormat?,
        archiveEntryPrefix: String?,
        archivePassword: String?,
        archiveNameEncoding: ArchiveNameEncoding?,
        archiveCompressionLevel: ArchiveCompressionLevel?,
        importItems: List<SaveToArcileImportItem>,
        presentationOwnerId: String?,
        clipboardSessionId: String?
    ): Boolean {
        if (_activeRequest.value != null) return false

        val request = BulkFileOperationRequest(
            operationId = UUID.randomUUID().toString(),
            presentationOwnerId = presentationOwnerId,
            clipboardSessionId = clipboardSessionId,
            type = type,
            sourcePaths = sourcePaths,
            destinationPath = destinationPath,
            resolutions = resolutions,
            fakeFileSize = fakeFileSize,
            archiveFormat = archiveFormat,
            archiveEntryPrefix = archiveEntryPrefix,
            archivePassword = archivePassword?.takeIf { it.isNotEmpty() },
            archiveNameEncoding = archiveNameEncoding,
            archiveCompressionLevel = archiveCompressionLevel,
            importItems = importItems
        )
        return startRequest(request)
    }

    override fun cancelActiveOperation() {
        val request = _activeRequest.value ?: return
        flushProgress(request)
        operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.CANCELLING) }
        _events.tryEmit(BulkFileOperationEvent.Cancelling(request))
        val intent = Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_CANCEL
            putExtra(BulkFileOperationService.EXTRA_OPERATION_ID, request.operationId)
        }
        context.startService(intent)
    }

    override fun onOperationProgress(
        request: BulkFileOperationRequest,
        progress: BulkFileOperationProgress
    ): Boolean = synchronized(progressLock) {
        if (_activeRequest.value?.operationId != request.operationId) return false
        val current = progressState
            ?.takeIf { it.operationId == request.operationId }
            ?: ProgressState(operationId = request.operationId)
        if (!progress.isMonotonicAfter(current.latest)) return false
        val now = progressClock()
        val next = current.copy(latest = progress)
        progressState = next
        if (!next.shouldPublish(progress, now)) return false
        publishProgressLocked(request, progress, now)
        true
    }

    override fun onOperationCheckpoint(
        request: BulkFileOperationRequest,
        stagedPaths: List<String>,
        finalizedPaths: List<String>,
        rollbackHints: List<String>,
        trashResultIds: List<String>
    ) {
        if (_activeRequest.value?.operationId != request.operationId) return
        flushProgress(request)
        operationJournal.update(request.operationId) { record ->
            record.copy(
                stagedPaths = (record.stagedPaths + stagedPaths).distinct(),
                finalizedPaths = (record.finalizedPaths + finalizedPaths).distinct(),
                rollbackHints = (record.rollbackHints + rollbackHints).distinct(),
                trashResultIds = (record.trashResultIds + trashResultIds).distinct()
            )
        }
    }

    override fun onOperationCancelling(request: BulkFileOperationRequest) {
        if (_activeRequest.value?.operationId == request.operationId) {
            flushProgress(request)
            operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.CANCELLING) }
            _events.tryEmit(BulkFileOperationEvent.Cancelling(request))
        }
    }

    override fun onOperationCompleted(request: BulkFileOperationRequest) {
        flushProgress(request)
        if (_activeRequest.value?.operationId == request.operationId) {
            _activeRequest.value = null
        }
        operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.COMPLETED) }
        operationJournal.clearActive(request.operationId)
        clearProgress(request.operationId)
        recordOperation(request, ActivityLogOperationStatus.COMPLETED)
        settleClipboard(request)
        _events.tryEmit(BulkFileOperationEvent.Completed(request))
    }

    override fun onOperationFailed(request: BulkFileOperationRequest, message: String) {
        flushProgress(request)
        if (_activeRequest.value?.operationId == request.operationId) {
            _activeRequest.value = null
        }
        val error = Exception(message).toArcileError()
        operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.FAILED, error = message) }
        operationJournal.clearActive(request.operationId)
        clearProgress(request.operationId)
        recordOperation(request, ActivityLogOperationStatus.FAILED, message)
        settleClipboard(request)
        _events.tryEmit(BulkFileOperationEvent.Failed(request, message, error))
    }

    override fun onOperationCancelled(request: BulkFileOperationRequest?) {
        request?.let(::flushProgress)
        if (request == null || _activeRequest.value?.operationId == request.operationId) {
            _activeRequest.value = null
        }
        request?.let {
            operationJournal.update(it.operationId) { record -> record.copy(phase = OperationPhase.CANCELLED) }
            operationJournal.clearActive(it.operationId)
            clearProgress(it.operationId)
            recordOperation(it, ActivityLogOperationStatus.CANCELLED)
            settleClipboard(it)
        }
        _events.tryEmit(BulkFileOperationEvent.Cancelled(request))
    }

    override fun retryRecoveredOperation(operationId: String): Boolean {
        if (_activeRequest.value != null) return false
        val record = _recoveryRecords.value.firstOrNull { it.request.operationId == operationId } ?: return false
        operationJournal.dismissRecovery(operationId)
        _recoveryRecords.value = operationJournal.recoveryRecords().map { it.toRecoveryRecord() }
        return startRequest(record.request)
    }

    override fun cleanupRecoveredOperation(operationId: String) {
        val record = _recoveryRecords.value.firstOrNull { it.request.operationId == operationId } ?: return
        applicationScope.launch {
            cleanupCheckpointPaths(record.stagedPaths)
            mutationJournal.cleanupAbandonedMutations()
            operationJournal.dismissRecovery(operationId)
            _recoveryRecords.value = operationJournal.recoveryRecords().map { it.toRecoveryRecord() }
            _events.tryEmit(BulkFileOperationEvent.RecoveryCleanupCompleted(operationId))
        }
    }

    override fun dismissRecoveredOperation(operationId: String) {
        operationJournal.dismissRecovery(operationId)
        _recoveryRecords.value = operationJournal.recoveryRecords().map { it.toRecoveryRecord() }
        _events.tryEmit(BulkFileOperationEvent.RecoveryDismissed(operationId))
    }

    private fun startRequest(request: BulkFileOperationRequest): Boolean {
        if (_activeRequest.value != null) return false
        _activeRequest.value = request
        synchronized(progressLock) {
            progressState = ProgressState(operationId = request.operationId)
        }
        operationJournal.upsertActive(request.toJournalRecord(OperationPhase.QUEUED))
        _events.tryEmit(BulkFileOperationEvent.Started(request))

        val intent = Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_START
            putExtra(BulkFileOperationService.EXTRA_REQUEST_JSON, json.encodeToString(request))
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.RUNNING) }
            recordOperation(request, ActivityLogOperationStatus.RUNNING)
            true
        } catch (e: Exception) {
            _activeRequest.value = null
            clearProgress(request.operationId)
            operationJournal.update(request.operationId) {
                it.copy(phase = OperationPhase.FAILED, error = e.message)
            }
            settleClipboard(request)
            _events.tryEmit(
                BulkFileOperationEvent.Failed(
                    request,
                    e.message ?: "Failed to start file operation",
                    e.toArcileError()
                )
            )
            false
        }
    }

    private fun settleClipboard(request: BulkFileOperationRequest) {
        if (request.type != BulkFileOperationType.COPY && request.type != BulkFileOperationType.MOVE) return
        request.clipboardSessionId?.let(clipboardRepository::clearClipboardState)
    }

    private fun publishProgressLocked(
        request: BulkFileOperationRequest,
        progress: BulkFileOperationProgress,
        nowMillis: Long
    ) {
        operationJournal.update(request.operationId) {
            it.copy(phase = OperationPhase.RUNNING, progress = progress)
        }
        _events.tryEmit(BulkFileOperationEvent.Progress(request, progress))
        progressState = requireNotNull(progressState).copy(
            lastPublished = progress,
            lastPublishedAtMillis = nowMillis
        )
    }

    private fun flushProgress(request: BulkFileOperationRequest) = synchronized(progressLock) {
        val state = progressState?.takeIf { it.operationId == request.operationId } ?: return
        val latest = state.latest ?: return
        if (latest != state.lastPublished) {
            publishProgressLocked(request, latest, progressClock())
        }
    }

    private fun clearProgress(operationId: String) = synchronized(progressLock) {
        if (progressState?.operationId == operationId) progressState = null
    }

    private fun recordOperation(
        request: BulkFileOperationRequest,
        status: ActivityLogOperationStatus,
        errorMessage: String? = null
    ) {
        val entry = ActivityLogEntry.FileOperation(
            id = "operation:${request.operationId}",
            timestampMillis = System.currentTimeMillis(),
            operationId = request.operationId,
            operationType = request.type.name,
            status = status,
            sourceCount = if (request.type == BulkFileOperationType.SAVE_TO_ARCILE_IMPORT) {
                request.importItems.size
            } else {
                request.sourcePaths.size
            },
            destinationPath = request.destinationPath,
            errorMessage = errorMessage
        )
        val terminal = status != ActivityLogOperationStatus.RUNNING
        val sequence: Long
        val previousJob: Job?

        synchronized(activityLogWriteLock) {
            sequence = ++activityLogSequence
            latestActivityLogSequence[request.operationId] = sequence
            previousJob = activityLogWriteJob
            activityLogWriteJob = applicationScope.launch {
                previousJob?.join()
                val shouldWrite = synchronized(activityLogWriteLock) {
                    latestActivityLogSequence[request.operationId] == sequence
                }
                if (!shouldWrite) return@launch

                activityLogStore?.upsertFileOperation(entry)

                if (terminal) {
                    synchronized(activityLogWriteLock) {
                        if (latestActivityLogSequence[request.operationId] == sequence) {
                            latestActivityLogSequence.remove(request.operationId)
                        }
                    }
                }
            }
        }
    }

    private fun cleanupCheckpointPaths(paths: List<String>) {
        paths.forEach { path ->
            runCatching {
                val file = File(path)
                if (file.exists() && isKnownOperationTemp(file.name)) {
                    if (file.isDirectory) file.deleteRecursively() else file.delete()
                }
            }
        }
    }

    private fun isKnownOperationTemp(name: String): Boolean =
        name.contains(".arcile-transfer-") ||
            name.contains(".arcile-replace-") ||
            name.contains(".arcile-archive-") ||
            name.contains(".arcile-import-")
}

private data class ProgressState(
    val operationId: String,
    val latest: BulkFileOperationProgress? = null,
    val lastPublished: BulkFileOperationProgress? = null,
    val lastPublishedAtMillis: Long = Long.MIN_VALUE
) {
    fun shouldPublish(progress: BulkFileOperationProgress, nowMillis: Long): Boolean {
        val previous = lastPublished ?: return true
        if (progress.isFinished) return true
        val elapsed = if (lastPublishedAtMillis == Long.MIN_VALUE) {
            Long.MAX_VALUE
        } else {
            (nowMillis - lastPublishedAtMillis).coerceAtLeast(0L)
        }
        if (elapsed < PROGRESS_MIN_INTERVAL_MS) return false
        val itemChanged = progress.completedItems != previous.completedItems ||
            progress.currentPath != previous.currentPath
        val currentBytes = progress.bytesCopied
        val previousBytes = previous.bytesCopied
        val byteStepReached = currentBytes != null && previousBytes != null &&
            currentBytes - previousBytes >= PROGRESS_BYTE_STEP
        return itemChanged || byteStepReached || elapsed >= PROGRESS_HEARTBEAT_INTERVAL_MS
    }
}

private val BulkFileOperationProgress.isFinished: Boolean
    get() = totalItems > 0 && completedItems >= totalItems

private fun BulkFileOperationProgress.isMonotonicAfter(previous: BulkFileOperationProgress?): Boolean {
    if (previous == null) return true
    if (completedItems < previous.completedItems) return false
    val previousBytes = previous.bytesCopied
    val currentBytes = bytesCopied
    return previousBytes == null || currentBytes == null || currentBytes >= previousBytes
}
