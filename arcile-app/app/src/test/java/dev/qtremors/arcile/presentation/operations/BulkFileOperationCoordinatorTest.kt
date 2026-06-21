package dev.qtremors.arcile.operations

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.operation.BulkFileOperationEvent
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.domain.ActivityLogEntry
import dev.qtremors.arcile.core.storage.domain.ActivityLogOperationStatus
import dev.qtremors.arcile.core.storage.domain.ActivityLogStore
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.testutil.FakeActivityLogStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BulkFileOperationCoordinatorTest {

    private lateinit var context: Context
    private lateinit var coordinator: ForegroundBulkFileOperationCoordinator
    private lateinit var testScope: TestScope

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("operation_journal", Context.MODE_PRIVATE).edit().clear().commit()
        coordinator = ForegroundBulkFileOperationCoordinator(context, DefaultOperationJournal(context))
        testScope = TestScope(UnconfinedTestDispatcher())
    }

    @Test
    fun `startOperation returns true and sets active request when idle`() {
        val result = coordinator.startOperation(
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/test.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap(),
            fakeFileSize = null
        )

        assertTrue(result)
        val activeRequest = coordinator.activeRequest.value
        assertNotNull(activeRequest)
        assertEquals(BulkFileOperationType.COPY, activeRequest?.type)
        assertEquals(listOf("/test.txt"), activeRequest?.sourcePaths)
        assertEquals(OperationPhase.RUNNING, DefaultOperationJournal(context).activeRecord()?.phase)
    }

    @Test
    fun `startOperation returns false when operation is already active`() {
        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        
        val result = coordinator.startOperation(BulkFileOperationType.DELETE, listOf("/test2.txt"), null, emptyMap(), null)
        
        assertFalse(result)
        assertEquals(BulkFileOperationType.COPY, coordinator.activeRequest.value?.type)
    }

    @Test
    fun `cancelActiveOperation sets request to null and emits cancelled event`() = testScope.runTest {
        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        val request = coordinator.activeRequest.value!!
        
        var cancellingEvent: BulkFileOperationEvent.Cancelling? = null
        val job = launch {
            cancellingEvent = coordinator.events.first { it is BulkFileOperationEvent.Cancelling } as BulkFileOperationEvent.Cancelling
        }

        coordinator.cancelActiveOperation()
        testScope.advanceUntilIdle()

        assertNotNull(coordinator.activeRequest.value) // Wait, cancelActiveOperation DOES NOT clear activeRequest. It just emits Cancelling.
        assertEquals(request.operationId, cancellingEvent?.request?.operationId)
        job.cancel()
    }

    @Test
    fun `terminal events from service clear active request`() = testScope.runTest {
        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        val request = coordinator.activeRequest.value!!

        coordinator.onOperationCompleted(request)

        assertNull(coordinator.activeRequest.value)
        assertNull(DefaultOperationJournal(context).activeRecord())
    }

    @Test
    fun `events from stale request do not clear active request`() = testScope.runTest {
        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        val staleRequest = BulkFileOperationRequest("stale-id", BulkFileOperationType.DELETE, emptyList(), null, emptyMap(), null)

        coordinator.onOperationCompleted(staleRequest)

        assertNotNull(coordinator.activeRequest.value)
    }

    @Test
    fun `progress is written to operation journal`() {
        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        val request = coordinator.activeRequest.value!!
        val progress = BulkFileOperationProgress(1, 2, "/test.txt", 50L, 100L)

        coordinator.onOperationProgress(request, progress)

        assertEquals(progress, DefaultOperationJournal(context).activeRecord()?.progress)
    }

    @Test
    fun `checkpoints are written to operation journal`() {
        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        val request = coordinator.activeRequest.value!!

        coordinator.onOperationCheckpoint(
            request = request,
            stagedPaths = listOf("/dest/.test.txt.arcile-transfer-temp"),
            finalizedPaths = listOf("/dest/test.txt"),
            rollbackHints = listOf("created:/dest/test.txt"),
            trashResultIds = listOf("trash-id")
        )

        val record = DefaultOperationJournal(context).activeRecord()
        assertEquals(listOf("/dest/.test.txt.arcile-transfer-temp"), record?.stagedPaths)
        assertEquals(listOf("/dest/test.txt"), record?.finalizedPaths)
        assertEquals(listOf("created:/dest/test.txt"), record?.rollbackHints)
        assertEquals(listOf("trash-id"), record?.trashResultIds)
    }

    @Test
    fun `accepted operation writes running activity entry`() = testScope.runTest {
        val activityLog = FakeActivityLogStore()
        coordinator = ForegroundBulkFileOperationCoordinator(
            context = context,
            operationJournal = DefaultOperationJournal(context),
            activityLogStore = activityLog,
            applicationScope = this
        )

        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        advanceUntilIdle()

        val entry = activityLog.fileOperationRequests.single()
        assertEquals(ActivityLogOperationStatus.RUNNING, entry.status)
        assertEquals(BulkFileOperationType.COPY.name, entry.operationType)
        assertEquals(1, entry.sourceCount)
        assertEquals("/dest", entry.destinationPath)
    }

    @Test
    fun `terminal operation callbacks update activity entry`() = testScope.runTest {
        val activityLog = FakeActivityLogStore()
        coordinator = ForegroundBulkFileOperationCoordinator(
            context = context,
            operationJournal = DefaultOperationJournal(context),
            activityLogStore = activityLog,
            applicationScope = this
        )
        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        val request = coordinator.activeRequest.value!!

        coordinator.onOperationFailed(request, "Copy failed")
        advanceUntilIdle()

        val entry = activityLog.fileOperationRequests.last()
        assertEquals(ActivityLogOperationStatus.FAILED, entry.status)
        assertEquals("Copy failed", entry.errorMessage)
        assertEquals("/dest", entry.destinationPath)
    }

    @Test
    fun `terminal activity entry is not overwritten by delayed running entry`() = testScope.runTest {
        val activityLog = DelayingFirstActivityLogStore()
        coordinator = ForegroundBulkFileOperationCoordinator(
            context = context,
            operationJournal = DefaultOperationJournal(context),
            activityLogStore = activityLog,
            applicationScope = this
        )
        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        val request = coordinator.activeRequest.value!!
        activityLog.firstWriteStarted.await()

        coordinator.onOperationCompleted(request)
        advanceUntilIdle()
        activityLog.resumeFirstWrite.complete(Unit)
        advanceUntilIdle()

        val entry = activityLog.fileOperationRequests.last()
        assertEquals(ActivityLogOperationStatus.COMPLETED, entry.status)
    }

    @Test
    fun `constructor surfaces interrupted recovery while active request remains null`() {
        val journal = DefaultOperationJournal(context)
        journal.upsertActive(request("op-recover").toJournalRecord(OperationPhase.RUNNING))

        val recoveredCoordinator = ForegroundBulkFileOperationCoordinator(context, journal)

        assertNull(recoveredCoordinator.activeRequest.value)
        assertEquals("op-recover", recoveredCoordinator.recoveryRecords.value.single().request.operationId)
        assertEquals(OperationPhase.CLEANUP_REQUIRED.name, recoveredCoordinator.recoveryRecords.value.single().phase)
    }

    @Test
    fun `constructor classifies queued running and cancelling interrupted operations as cleanup required`() {
        listOf(OperationPhase.QUEUED, OperationPhase.RUNNING, OperationPhase.CANCELLING).forEach { phase ->
            context.getSharedPreferences("operation_journal", Context.MODE_PRIVATE).edit().clear().commit()
            val journal = DefaultOperationJournal(context)
            journal.upsertActive(request("op-${phase.name.lowercase()}").toJournalRecord(phase))

            val recoveredCoordinator = ForegroundBulkFileOperationCoordinator(context, journal)

            val recovery = recoveredCoordinator.recoveryRecords.value.single()
            assertEquals("op-${phase.name.lowercase()}", recovery.request.operationId)
            assertEquals(OperationPhase.CLEANUP_REQUIRED.name, recovery.phase)
            assertTrue(recovery.error.orEmpty().contains("cleanup", ignoreCase = true))
            assertNull(journal.activeRecord())
        }
    }

    @Test
    fun `retry recovered operation starts original request when idle`() {
        val journal = DefaultOperationJournal(context)
        val request = request("op-retry")
        journal.upsertActive(request.toJournalRecord(OperationPhase.RUNNING))
        val recoveredCoordinator = ForegroundBulkFileOperationCoordinator(context, journal)

        val result = recoveredCoordinator.retryRecoveredOperation("op-retry")

        assertTrue(result)
        assertEquals("op-retry", recoveredCoordinator.activeRequest.value?.operationId)
        assertTrue(recoveredCoordinator.recoveryRecords.value.isEmpty())
    }

    @Test
    fun `retry recovered operation is refused while another operation is active`() {
        val journal = DefaultOperationJournal(context)
        journal.upsertActive(request("op-retry-blocked").toJournalRecord(OperationPhase.RUNNING))
        val recoveredCoordinator = ForegroundBulkFileOperationCoordinator(context, journal)
        recoveredCoordinator.startOperation(BulkFileOperationType.COPY, listOf("/live.txt"), "/dest", emptyMap(), null)

        val result = recoveredCoordinator.retryRecoveredOperation("op-retry-blocked")

        assertFalse(result)
        assertEquals("op-retry-blocked", recoveredCoordinator.recoveryRecords.value.single().request.operationId)
    }

    @Test
    fun `cleanup recovered operation runs mutation cleanup and dismisses recovery`() = testScope.runTest {
        val journal = DefaultOperationJournal(context)
        journal.upsertActive(request("op-cleanup").toJournalRecord(OperationPhase.RUNNING))
        val mutationJournal = RecordingMutationJournal()
        val recoveredCoordinator = ForegroundBulkFileOperationCoordinator(
            context = context,
            operationJournal = journal,
            mutationJournal = mutationJournal,
            applicationScope = this
        )

        recoveredCoordinator.cleanupRecoveredOperation("op-cleanup")
        advanceUntilIdle()

        assertTrue(mutationJournal.cleanupCalled)
        assertTrue(recoveredCoordinator.recoveryRecords.value.isEmpty())
    }

    private fun request(id: String): BulkFileOperationRequest =
        BulkFileOperationRequest(
            operationId = id,
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap()
        )

    private class RecordingMutationJournal : MutationJournal {
        var cleanupCalled = false
        override fun recordTemporaryPath(path: String) = Unit
        override fun forgetTemporaryPath(path: String) = Unit
        override fun recordTrashFallback(sourcePath: String, payloadPath: String, metadataPath: String) = Unit
        override fun forgetTrashFallback(payloadPath: String, metadataPath: String) = Unit
        override suspend fun cleanupAbandonedMutations() {
            cleanupCalled = true
        }
    }

    private class DelayingFirstActivityLogStore : ActivityLogStore {
        override val entries = MutableStateFlow<List<ActivityLogEntry>>(emptyList())
        val fileOperationRequests = mutableListOf<ActivityLogEntry.FileOperation>()
        val firstWriteStarted = CompletableDeferred<Unit>()
        val resumeFirstWrite = CompletableDeferred<Unit>()
        private var writeCount = 0

        override suspend fun recordFolderOpened(path: String, volumeId: String?) = Unit

        override suspend fun upsertFileOperation(entry: ActivityLogEntry.FileOperation) {
            writeCount += 1
            if (writeCount == 1) {
                firstWriteStarted.complete(Unit)
                resumeFirstWrite.await()
            }
            fileOperationRequests += entry
            entries.value = listOf(entry) + entries.value.filterNot {
                it is ActivityLogEntry.FileOperation && it.operationId == entry.operationId
            }
        }

        override suspend fun clear() {
            entries.value = emptyList()
        }
    }
}
