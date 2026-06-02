package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationRecoveryRecord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBulkFileOperationCoordinator : BulkFileOperationCoordinator {
    private val _activeRequest = MutableStateFlow<BulkFileOperationRequest?>(null)
    override val activeRequest = _activeRequest
    private val _recoveryRecords = MutableStateFlow<List<OperationRecoveryRecord>>(emptyList())
    override val recoveryRecords = _recoveryRecords
    private val _events = MutableSharedFlow<BulkFileOperationEvent>(replay = 1, extraBufferCapacity = 16)
    override val events = _events

    val startedRequests = mutableListOf<BulkFileOperationRequest>()
    val cleanupRequests = mutableListOf<String>()
    val dismissedRequests = mutableListOf<String>()
    var startResult = true

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
        if (!startResult) return false
        val request = BulkFileOperationRequest("test-op-${startedRequests.size}", type, sourcePaths, destinationPath, resolutions, fakeFileSize, archiveFormat, archiveEntryPrefix, archivePassword, archiveNameEncoding, archiveCompressionLevel)
        startedRequests += request
        _activeRequest.value = request
        _events.tryEmit(BulkFileOperationEvent.Started(request))
        return true
    }

    override fun cancelActiveOperation() {
        val request = _activeRequest.value
        _activeRequest.value = null
        _events.tryEmit(BulkFileOperationEvent.Cancelled(request))
    }

    override fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress) {
        _events.tryEmit(BulkFileOperationEvent.Progress(request, progress))
    }

    override fun onOperationCancelling(request: BulkFileOperationRequest) {
        _events.tryEmit(BulkFileOperationEvent.Cancelling(request))
    }

    override fun onOperationCompleted(request: BulkFileOperationRequest) {
        _activeRequest.value = null
        _events.tryEmit(BulkFileOperationEvent.Completed(request))
    }

    override fun onOperationFailed(request: BulkFileOperationRequest, message: String) {
        _activeRequest.value = null
        _events.tryEmit(BulkFileOperationEvent.Failed(request, message))
    }

    override fun onOperationCancelled(request: BulkFileOperationRequest?) {
        _activeRequest.value = null
        _events.tryEmit(BulkFileOperationEvent.Cancelled(request))
    }

    override fun retryRecoveredOperation(operationId: String): Boolean {
        if (_activeRequest.value != null || !startResult) return false
        val record = _recoveryRecords.value.firstOrNull { it.request.operationId == operationId } ?: return false
        _recoveryRecords.value = _recoveryRecords.value.filterNot { it.request.operationId == operationId }
        startedRequests += record.request
        _activeRequest.value = record.request
        _events.tryEmit(BulkFileOperationEvent.Started(record.request))
        return true
    }

    override fun cleanupRecoveredOperation(operationId: String) {
        cleanupRequests += operationId
        _recoveryRecords.value = _recoveryRecords.value.filterNot { it.request.operationId == operationId }
        _events.tryEmit(BulkFileOperationEvent.RecoveryCleanupCompleted(operationId))
    }

    override fun dismissRecoveredOperation(operationId: String) {
        dismissedRequests += operationId
        _recoveryRecords.value = _recoveryRecords.value.filterNot { it.request.operationId == operationId }
        _events.tryEmit(BulkFileOperationEvent.RecoveryDismissed(operationId))
    }

    fun seedRecovery(record: OperationRecoveryRecord) {
        _recoveryRecords.value = _recoveryRecords.value.filterNot {
            it.request.operationId == record.request.operationId
        } + record
        _events.tryEmit(BulkFileOperationEvent.RecoveryAvailable(record))
    }
}
