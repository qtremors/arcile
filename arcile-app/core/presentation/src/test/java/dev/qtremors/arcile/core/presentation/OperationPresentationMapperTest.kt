package dev.qtremors.arcile.core.presentation

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class OperationPresentationMapperTest {
    @Test
    fun `request maps source count and current path`() {
        val request = request(sourcePaths = listOf("/a", "/b"))

        val result = OperationPresentationMapper.map(request)

        assertEquals(BulkFileOperationType.COPY, result.type)
        assertEquals(2, result.totalItems)
        assertEquals("/a", result.currentPath)
        assertEquals(listOf("/a", "/b"), result.sourcePaths)
    }

    @Test
    fun `progress maps transfer values and preserves start time`() {
        val previous = OperationUiState(
            type = BulkFileOperationType.COPY,
            totalItems = 2,
            startTimeMillis = 42L
        )
        val progress = BulkFileOperationProgress(
            completedItems = 1,
            totalItems = 2,
            currentPath = "/b",
            bytesCopied = 10L,
            totalBytes = 20L
        )

        val result = OperationPresentationMapper.map(
            request = request(sourcePaths = listOf("/a", "/b")),
            progress = progress,
            previous = previous
        )

        assertEquals(1, result.completedItems)
        assertEquals("/b", result.currentPath)
        assertEquals(10L, result.bytesCopied)
        assertEquals(20L, result.totalBytes)
        assertEquals(42L, result.startTimeMillis)
        assertFalse(result.isIndeterminate)
    }

    @Test
    fun `terminal state is mapped without losing previous start time`() {
        val previous = OperationUiState(
            type = BulkFileOperationType.COPY,
            totalItems = 1,
            startTimeMillis = 7L
        )

        val result = OperationPresentationMapper.map(
            request = request(sourcePaths = listOf("/a")),
            previous = previous,
            completedItems = 1,
            terminalStatus = OperationCompletionStatus.SUCCESS
        )

        assertEquals(1, result.completedItems)
        assertEquals(OperationCompletionStatus.SUCCESS, result.terminalStatus)
        assertEquals(7L, result.startTimeMillis)
    }

    private fun request(sourcePaths: List<String>) = BulkFileOperationRequest(
        operationId = "operation-id",
        type = BulkFileOperationType.COPY,
        sourcePaths = sourcePaths,
        destinationPath = "/destination"
    )
}
