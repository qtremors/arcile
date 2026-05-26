package dev.qtremors.arcile.presentation.operations

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.toArcileError
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

interface BulkFileOperationCoordinator {
    val activeRequest: StateFlow<BulkFileOperationRequest?>
    val events: SharedFlow<BulkFileOperationEvent>

    fun startOperation(
        type: BulkFileOperationType,
        sourcePaths: List<String>,
        destinationPath: String?,
        resolutions: Map<String, ConflictResolution>,
        fakeFileSize: Long? = null,
        archiveFormat: ArchiveFormat? = null,
        archiveEntryPrefix: String? = null,
        archivePassword: String? = null
    ): Boolean

    fun cancelActiveOperation()
    fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress)
    fun onOperationCancelling(request: BulkFileOperationRequest)
    fun onOperationCompleted(request: BulkFileOperationRequest)
    fun onOperationFailed(request: BulkFileOperationRequest, message: String)
    fun onOperationCancelled(request: BulkFileOperationRequest?)
}

@Singleton
class ForegroundBulkFileOperationCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val operationJournal: OperationJournal = DefaultOperationJournal(context)
) : BulkFileOperationCoordinator {
    private val json = Json { ignoreUnknownKeys = true }
    private val _activeRequest = MutableStateFlow(
        operationJournal.recoverInterrupted()
            ?.takeUnless { it.phase.isTerminal }
            ?.request
    )
    override val activeRequest: StateFlow<BulkFileOperationRequest?> = _activeRequest.asStateFlow()

    private val _events = MutableSharedFlow<BulkFileOperationEvent>(
        replay = 1,
        extraBufferCapacity = 64,
        onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
    )
    override val events: SharedFlow<BulkFileOperationEvent> = _events.asSharedFlow()

    override fun startOperation(
        type: BulkFileOperationType,
        sourcePaths: List<String>,
        destinationPath: String?,
        resolutions: Map<String, ConflictResolution>,
        fakeFileSize: Long?,
        archiveFormat: ArchiveFormat?,
        archiveEntryPrefix: String?,
        archivePassword: String?
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
            archivePassword = archivePassword?.takeIf { it.isNotEmpty() }
        )
        _activeRequest.value = request
        operationJournal.upsert(request.toJournalRecord(OperationPhase.QUEUED))
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
}
