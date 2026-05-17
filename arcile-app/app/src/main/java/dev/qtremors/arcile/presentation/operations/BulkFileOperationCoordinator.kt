package dev.qtremors.arcile.presentation.operations

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.ArchiveFormat
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
    @ApplicationContext private val context: Context
) : BulkFileOperationCoordinator {
    private val json = Json { ignoreUnknownKeys = true }
    private val _activeRequest = MutableStateFlow<BulkFileOperationRequest?>(null)
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
        _events.tryEmit(BulkFileOperationEvent.Started(request))

        val intent = Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_START
            putExtra(BulkFileOperationService.EXTRA_REQUEST_JSON, json.encodeToString(request))
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (e: Exception) {
            _activeRequest.value = null
            _events.tryEmit(BulkFileOperationEvent.Failed(request, e.message ?: "Failed to start file operation"))
            false
        }
    }

    override fun cancelActiveOperation() {
        val request = _activeRequest.value ?: return
        _events.tryEmit(BulkFileOperationEvent.Cancelling(request))
        val intent = Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_CANCEL
            putExtra(BulkFileOperationService.EXTRA_OPERATION_ID, request.operationId)
        }
        context.startService(intent)
    }

    override fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress) {
        if (_activeRequest.value?.operationId == request.operationId) {
            _events.tryEmit(BulkFileOperationEvent.Progress(request, progress))
        }
    }

    override fun onOperationCancelling(request: BulkFileOperationRequest) {
        if (_activeRequest.value?.operationId == request.operationId) {
            _events.tryEmit(BulkFileOperationEvent.Cancelling(request))
        }
    }

    override fun onOperationCompleted(request: BulkFileOperationRequest) {
        if (_activeRequest.value?.operationId == request.operationId) {
            _activeRequest.value = null
        }
        _events.tryEmit(BulkFileOperationEvent.Completed(request))
    }

    override fun onOperationFailed(request: BulkFileOperationRequest, message: String) {
        if (_activeRequest.value?.operationId == request.operationId) {
            _activeRequest.value = null
        }
        _events.tryEmit(BulkFileOperationEvent.Failed(request, message))
    }

    override fun onOperationCancelled(request: BulkFileOperationRequest?) {
        if (request == null || _activeRequest.value?.operationId == request.operationId) {
            _activeRequest.value = null
        }
        _events.tryEmit(BulkFileOperationEvent.Cancelled(request))
    }
}
