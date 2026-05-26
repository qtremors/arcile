package dev.qtremors.arcile.presentation.operations

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OperationJournalTest {

    private lateinit var context: Context
    private lateinit var journal: DefaultOperationJournal

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("operation_journal", Context.MODE_PRIVATE).edit().clear().commit()
        journal = DefaultOperationJournal(context)
    }

    @Test
    fun `upsert and hydrate active operation`() {
        val request = request("op-1")
        journal.upsert(request.toJournalRecord(OperationPhase.RUNNING))

        val hydrated = DefaultOperationJournal(context).activeRecord()

        assertEquals("op-1", hydrated?.request?.operationId)
        assertEquals(OperationPhase.RUNNING, hydrated?.phase)
    }

    @Test
    fun `update persists progress and cancellation state`() {
        val request = request("op-2")
        journal.upsert(request.toJournalRecord(OperationPhase.RUNNING))
        val progress = BulkFileOperationProgress(
            completedItems = 1,
            totalItems = 2,
            currentPath = "/a.txt",
            bytesCopied = 10L,
            totalBytes = 20L
        )

        journal.update("op-2") { it.copy(phase = OperationPhase.CANCELLING, progress = progress) }

        val record = journal.activeRecord()
        assertEquals(OperationPhase.CANCELLING, record?.phase)
        assertEquals(progress, record?.progress)
    }

    @Test
    fun `recoverInterrupted marks running operation cleanup required`() {
        journal.upsert(request("op-3").toJournalRecord(OperationPhase.RUNNING))

        val recovered = journal.recoverInterrupted()

        assertEquals(OperationPhase.CLEANUP_REQUIRED, recovered?.phase)
        assertTrue(recovered?.error?.contains("interrupted") == true)
    }

    @Test
    fun `clearActive removes matching operation`() {
        journal.upsert(request("op-4").toJournalRecord(OperationPhase.COMPLETED))

        journal.clearActive("op-4")

        assertNull(journal.activeRecord())
    }

    private fun request(id: String): BulkFileOperationRequest =
        BulkFileOperationRequest(
            operationId = id,
            type = BulkFileOperationType.TRASH,
            sourcePaths = listOf("/a.txt"),
            destinationPath = null
        )
}
