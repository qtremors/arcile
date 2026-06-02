package dev.qtremors.arcile.presentation.operations

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
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.toArcileError
import dev.qtremors.arcile.di.ApplicationScope
import dev.qtremors.arcile.di.DeferOperationJournalRecovery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundBulkFileOperationCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val operationJournal: OperationJournal = DefaultOperationJournal(context),
    private val mutationJournal: MutationJournal = NoOpMutationJournal(),
    @param:ApplicationScope private val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    @param:DeferOperationJournalRecovery private val deferJournalRecovery: Boolean = false
) : BulkFileOperationCoordinator {
    private val json = Json { ignoreUnknownKeys = true }
    private val _activeRequest = MutableStateFlow<BulkFileOperationRequest?>(null)
    override val activeRequest: StateFlow<BulkFileOperationRequest?> = _activeRequest.asStateFlow()
    private val _recoveryRecords = MutableStateFlow<List<OperationRecoveryRecord>>(emptyList())
    override val recoveryRecords: StateFlow<List<OperationRecoveryRecord>> = _recoveryRecords.asStateFlow()

    private val _events = MutableSharedFlow<BulkFileOperationEvent>(
        replay = 1,
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
        archiveCompressionLevel: ArchiveCompressionLevel?
    ): Boolean {
        if (_activeRequest.value != null) return false

        val request = BulkFileOperationRequest(
            operationId = UUID.randomUUID().toString(),
            type = type,
            sourcePaths = sourcePaths,
            destinationPath = destinationPath,
            resolutions = resolutions,
            fakeFileSize = fakeFileSize,
            archiveFormat = archiveFormat,
            archiveEntryPrefix = archiveEntryPrefix,
            archivePassword = archivePassword?.takeIf { it.isNotEmpty() },
            archiveNameEncoding = archiveNameEncoding,
            archiveCompressionLevel = archiveCompressionLevel
        )
        return startRequest(request)
    }

    override fun cancelActiveOperation() {
        val request = _activeRequest.value ?: return
        operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.CANCELLING) }
        _events.tryEmit(BulkFileOperationEvent.Cancelling(request))
        val intent = Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_CANCEL
            putExtra(BulkFileOperationService.EXTRA_OPERATION_ID, request.operationId)
        }
        context.startService(intent)
    }

    override fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress) {
        if (_activeRequest.value?.operationId == request.operationId) {
            operationJournal.update(request.operationId) {
                it.copy(phase = OperationPhase.RUNNING, progress = progress)
            }
            _events.tryEmit(BulkFileOperationEvent.Progress(request, progress))
        }
    }

    override fun onOperationCancelling(request: BulkFileOperationRequest) {
        if (_activeRequest.value?.operationId == request.operationId) {
            operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.CANCELLING) }
            _events.tryEmit(BulkFileOperationEvent.Cancelling(request))
        }
    }

    override fun onOperationCompleted(request: BulkFileOperationRequest) {
        if (_activeRequest.value?.operationId == request.operationId) {
            _activeRequest.value = null
        }
        operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.COMPLETED) }
        operationJournal.clearActive(request.operationId)
        _events.tryEmit(BulkFileOperationEvent.Completed(request))
    }

    override fun onOperationFailed(request: BulkFileOperationRequest, message: String) {
        if (_activeRequest.value?.operationId == request.operationId) {
            _activeRequest.value = null
        }
        val error = Exception(message).toArcileError()
        operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.FAILED, error = message) }
        operationJournal.clearActive(request.operationId)
        _events.tryEmit(BulkFileOperationEvent.Failed(request, message, error))
    }

    override fun onOperationCancelled(request: BulkFileOperationRequest?) {
        if (request == null || _activeRequest.value?.operationId == request.operationId) {
            _activeRequest.value = null
        }
        request?.let {
            operationJournal.update(it.operationId) { record -> record.copy(phase = OperationPhase.CANCELLED) }
            operationJournal.clearActive(it.operationId)
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
        if (_recoveryRecords.value.none { it.request.operationId == operationId }) return
        applicationScope.launch {
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
        operationJournal.upsertActive(request.toJournalRecord(OperationPhase.QUEUED))
        _events.tryEmit(BulkFileOperationEvent.Started(request))

        val intent = Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_START
            putExtra(BulkFileOperationService.EXTRA_REQUEST_JSON, json.encodeToString(request))
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            operationJournal.update(request.operationId) { it.copy(phase = OperationPhase.RUNNING) }
            true
        } catch (e: Exception) {
            _activeRequest.value = null
            operationJournal.update(request.operationId) {
                it.copy(phase = OperationPhase.FAILED, error = e.message)
            }
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
}
