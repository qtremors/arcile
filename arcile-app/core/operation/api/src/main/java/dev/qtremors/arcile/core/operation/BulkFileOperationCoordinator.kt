package dev.qtremors.arcile.core.operation

import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
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
        archiveNameEncoding: ArchiveNameEncoding? = null,
        archiveCompressionLevel: ArchiveCompressionLevel? = null,
        importItems: List<SaveToArcileImportItem> = emptyList(),
        presentationOwnerId: String? = null,
        clipboardSessionId: String? = null
    ): Boolean

    fun startNodeOperation(
        type: BulkFileOperationType,
        sourceNodes: List<StorageNodeRef>,
        destinationNode: StorageNodeRef?,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        presentationOwnerId: String? = null,
        clipboardSessionId: String? = null
    ): Boolean = startOperation(
        type = type,
        sourcePaths = sourceNodes.map { it.displayPath.absolutePath },
        destinationPath = destinationNode?.displayPath?.absolutePath,
        resolutions = resolutions,
        presentationOwnerId = presentationOwnerId,
        clipboardSessionId = clipboardSessionId
    )

    fun startCreateFakeNodeOperation(
        parent: StorageNodeRef,
        name: String,
        size: Long,
        presentationOwnerId: String? = null
    ): Boolean = startOperation(
        type = BulkFileOperationType.CREATE_FAKE,
        sourcePaths = listOf(name),
        destinationPath = parent.displayPath.absolutePath,
        resolutions = emptyMap(),
        fakeFileSize = size,
        presentationOwnerId = presentationOwnerId
    )

    fun startImportOperation(
        destinationPath: String,
        importItems: List<SaveToArcileImportItem>
    ): Boolean =
        startOperation(
            type = BulkFileOperationType.SAVE_TO_ARCILE_IMPORT,
            sourcePaths = emptyList(),
            destinationPath = destinationPath,
            resolutions = emptyMap(),
            importItems = importItems
        )

    fun cancelActiveOperation()
    /** Returns true when this update should also be presented to the user. */
    fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress): Boolean
    fun onOperationCheckpoint(
        request: BulkFileOperationRequest,
        stagedPaths: List<String> = emptyList(),
        finalizedPaths: List<String> = emptyList(),
        rollbackHints: List<String> = emptyList(),
        trashResultIds: List<String> = emptyList()
    )
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
        archiveNameEncoding: ArchiveNameEncoding?,
        archiveCompressionLevel: ArchiveCompressionLevel?,
        importItems: List<SaveToArcileImportItem>,
        presentationOwnerId: String?,
        clipboardSessionId: String?
    ): Boolean = false

    override fun cancelActiveOperation() = Unit
    override fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress) = false
    override fun onOperationCheckpoint(
        request: BulkFileOperationRequest,
        stagedPaths: List<String>,
        finalizedPaths: List<String>,
        rollbackHints: List<String>,
        trashResultIds: List<String>
    ) = Unit
    override fun onOperationCancelling(request: BulkFileOperationRequest) = Unit
    override fun onOperationCompleted(request: BulkFileOperationRequest) = Unit
    override fun onOperationFailed(request: BulkFileOperationRequest, message: String) = Unit
    override fun onOperationCancelled(request: BulkFileOperationRequest?) = Unit
    override fun retryRecoveredOperation(operationId: String): Boolean = false
    override fun cleanupRecoveredOperation(operationId: String) = Unit
    override fun dismissRecoveredOperation(operationId: String) = Unit
}
