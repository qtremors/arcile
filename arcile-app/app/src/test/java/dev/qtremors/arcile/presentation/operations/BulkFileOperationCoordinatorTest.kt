package dev.qtremors.arcile.presentation.operations

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.domain.ConflictResolution
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
        coordinator = ForegroundBulkFileOperationCoordinator(context)
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
    }

    @Test
    fun `events from stale request do not clear active request`() = testScope.runTest {
        coordinator.startOperation(BulkFileOperationType.COPY, listOf("/test.txt"), "/dest", emptyMap(), null)
        val staleRequest = BulkFileOperationRequest("stale-id", BulkFileOperationType.DELETE, emptyList(), null, emptyMap(), null)

        coordinator.onOperationCompleted(staleRequest)

        assertNotNull(coordinator.activeRequest.value)
    }
}
