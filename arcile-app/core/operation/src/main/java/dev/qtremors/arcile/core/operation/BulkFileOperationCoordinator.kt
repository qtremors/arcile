package dev.qtremors.arcile.core.operation

import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface BulkFileOperationCoordinator {
    val activeRequest: StateFlow<BulkFileOperationRequest?>
    val recoveryRecords: StateFlow<List<OperationRecoveryRecord>>
    val events: SharedFlow<BulkFileOperationEvent>

    fun startOperation(
        type: BulkFileOperationType,
        sourcePaths: List<String>,
        destinationPath: String?,
        resolutions: Map<String, ConflictResolution>,
        fakeFileSize: Long? = null,
        archiveFormat: ArchiveFormat? = null,
        archiveEntryPrefix: String? = null,
        archivePassword: String? = null,
        archiveNameEncoding: ArchiveNameEncoding? = null
    ): Boolean

    fun cancelActiveOperation()
    fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress)
    fun onOperationCancelling(request: BulkFileOperationRequest)
    fun onOperationCompleted(request: BulkFileOperationRequest)
    fun onOperationFailed(request: BulkFileOperationRequest, message: String)
    fun onOperationCancelled(request: BulkFileOperationRequest?)
    fun retryRecoveredOperation(operationId: String): Boolean
    fun cleanupRecoveredOperation(operationId: String)
    fun dismissRecoveredOperation(operationId: String)
}

object NoOpBulkFileOperationCoordinator : BulkFileOperationCoordinator {
    override val activeRequest: StateFlow<BulkFileOperationRequest?> = MutableStateFlow(null)
    override val recoveryRecords: StateFlow<List<OperationRecoveryRecord>> = MutableStateFlow(emptyList())
    override val events: SharedFlow<BulkFileOperationEvent> = MutableSharedFlow()

    override fun startOperation(
        type: BulkFileOperationType,
        sourcePaths: List<String>,
        destinationPath: String?,
        resolutions: Map<String, ConflictResolution>,
        fakeFileSize: Long?,
        archiveFormat: ArchiveFormat?,
        archiveEntryPrefix: String?,
        archivePassword: String?,
        archiveNameEncoding: ArchiveNameEncoding?
    ): Boolean = false

    override fun cancelActiveOperation() = Unit
    override fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress) = Unit
    override fun onOperationCancelling(request: BulkFileOperationRequest) = Unit
    override fun onOperationCompleted(request: BulkFileOperationRequest) = Unit
    override fun onOperationFailed(request: BulkFileOperationRequest, message: String) = Unit
    override fun onOperationCancelled(request: BulkFileOperationRequest?) = Unit
    override fun retryRecoveredOperation(operationId: String): Boolean = false
    override fun cleanupRecoveredOperation(operationId: String) = Unit
    override fun dismissRecoveredOperation(operationId: String) = Unit
}
