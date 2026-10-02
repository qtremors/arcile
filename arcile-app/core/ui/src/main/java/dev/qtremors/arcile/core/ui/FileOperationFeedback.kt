package dev.qtremors.arcile.core.ui

import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.presentation.OperationPresentationMapper
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation

fun clipboardStoredFeedback(
    operation: ClipboardOperation,
    itemCount: Int
): ArcileFeedbackEvent = ArcileFeedbackEvent(
    message = UiText.PluralResource(
        resId = when (operation) {
            ClipboardOperation.COPY -> R.plurals.clipboard_copied_items
            ClipboardOperation.CUT -> R.plurals.clipboard_cut_items
        },
        quantity = itemCount,
        args = listOf(itemCount)
    ),
    severity = ArcileFeedbackSeverity.Info
)

fun fileOperationFeedback(
    request: BulkFileOperationRequest,
    status: OperationCompletionStatus,
    error: UiText? = null
): ArcileFeedbackEvent = ArcileFeedbackEvent(
    message = when (status) {
        OperationCompletionStatus.SUCCESS -> completedFileOperationMessage(request)
        OperationCompletionStatus.FAILED ->
            error ?: UiText.StringResource(R.string.error_file_operation_failed)
        OperationCompletionStatus.CANCELLED ->
            UiText.StringResource(R.string.file_operation_cancelled)
    },
    severity = when (status) {
        OperationCompletionStatus.SUCCESS -> ArcileFeedbackSeverity.Success
        OperationCompletionStatus.FAILED -> ArcileFeedbackSeverity.Error
        OperationCompletionStatus.CANCELLED -> ArcileFeedbackSeverity.Info
    }
)

fun completedFileOperationMessage(request: BulkFileOperationRequest): UiText {
    val count = OperationPresentationMapper.itemCount(request)
    val pluralRes = when (request.type) {
        BulkFileOperationType.COPY -> R.plurals.file_operation_copied_items
        BulkFileOperationType.MOVE -> R.plurals.file_operation_moved_items
        BulkFileOperationType.TRASH -> R.plurals.file_operation_trashed_items
        BulkFileOperationType.DELETE -> R.plurals.file_operation_deleted_items
        BulkFileOperationType.SHRED -> R.plurals.file_operation_shredded_items
        BulkFileOperationType.CREATE_SYNTHETIC -> R.plurals.file_operation_created_items
        BulkFileOperationType.EXTRACT_ARCHIVE -> R.plurals.file_operation_extracted_items
        BulkFileOperationType.CREATE_ARCHIVE -> R.plurals.file_operation_archived_items
        BulkFileOperationType.SAVE_TO_ARCILE_IMPORT -> R.plurals.file_operation_copied_items
    }
    return UiText.PluralResource(pluralRes, count, listOf(count))
}
