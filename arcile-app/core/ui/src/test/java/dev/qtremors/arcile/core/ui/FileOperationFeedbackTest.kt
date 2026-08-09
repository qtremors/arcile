package dev.qtremors.arcile.core.ui

import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import org.junit.Assert.assertEquals
import org.junit.Test

class FileOperationFeedbackTest {
    @Test
    fun `clipboard feedback describes the initiating action`() {
        assertEquals(
            UiText.PluralResource(R.plurals.clipboard_copied_items, 2, listOf(2)),
            clipboardStoredFeedback(ClipboardOperation.COPY, 2).message
        )
        assertEquals(
            UiText.PluralResource(R.plurals.clipboard_cut_items, 1, listOf(1)),
            clipboardStoredFeedback(ClipboardOperation.CUT, 1).message
        )
    }

    @Test
    fun `terminal feedback uses status appropriate severity`() {
        val request = BulkFileOperationRequest(
            operationId = "operation",
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source.jpg"),
            destinationPath = "/Pictures"
        )

        assertEquals(
            ArcileFeedbackSeverity.Success,
            fileOperationFeedback(request, OperationCompletionStatus.SUCCESS).severity
        )
        assertEquals(
            ArcileFeedbackSeverity.Error,
            fileOperationFeedback(request, OperationCompletionStatus.FAILED).severity
        )
        assertEquals(
            ArcileFeedbackSeverity.Info,
            fileOperationFeedback(request, OperationCompletionStatus.CANCELLED).severity
        )
    }
}
