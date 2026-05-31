package dev.qtremors.arcile.core.operation

import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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

object NoOpBulkFileOperationCoordinator : BulkFileOperationCoordinator {
    override val activeRequest: StateFlow<BulkFileOperationRequest?> = MutableStateFlow(null)
    override val events: SharedFlow<BulkFileOperationEvent> = MutableSharedFlow()

    override fun startOperation(
        type: BulkFileOperationType,
        sourcePaths: List<String>,
        destinationPath: String?,
        resolutions: Map<String, ConflictResolution>,
        fakeFileSize: Long?,
        archiveFormat: ArchiveFormat?,
        archiveEntryPrefix: String?,
        archivePassword: String?
    ): Boolean = false

    override fun cancelActiveOperation() = Unit
    override fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress) = Unit
    override fun onOperationCancelling(request: BulkFileOperationRequest) = Unit
    override fun onOperationCompleted(request: BulkFileOperationRequest) = Unit
    override fun onOperationFailed(request: BulkFileOperationRequest, message: String) = Unit
    override fun onOperationCancelled(request: BulkFileOperationRequest?) = Unit
}
