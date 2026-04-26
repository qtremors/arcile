package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.presentation.operations.BulkFileOperationCoordinator
import dev.qtremors.arcile.presentation.operations.BulkFileOperationEvent
import dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress
import dev.qtremors.arcile.presentation.operations.BulkFileOperationRequest
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeBulkFileOperationCoordinator : BulkFileOperationCoordinator {
    private val _activeRequest = MutableStateFlow<BulkFileOperationRequest?>(null)
    override val activeRequest = _activeRequest
    private val _events = MutableSharedFlow<BulkFileOperationEvent>(replay = 1, extraBufferCapacity = 16)
    override val events = _events

    val startedRequests = mutableListOf<BulkFileOperationRequest>()
    var startResult = true

    override fun startOperation(
        type: BulkFileOperationType,
        sourcePaths: List<String>,
        destinationPath: String?,
        resolutions: Map<String, ConflictResolution>
    ): Boolean {
        if (!startResult) return false
        val request = BulkFileOperationRequest("test-op-${startedRequests.size}", type, sourcePaths, destinationPath, resolutions)
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
}
