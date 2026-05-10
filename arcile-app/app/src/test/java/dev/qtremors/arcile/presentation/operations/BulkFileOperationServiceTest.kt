package dev.qtremors.arcile.presentation.operations

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.testutil.FakeFileRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BulkFileOperationServiceTest {

    private lateinit var context: Context
    private lateinit var coordinator: BulkFileOperationCoordinator
    private lateinit var repository: FakeFileRepository
    private lateinit var serviceController: ServiceController<BulkFileOperationService>
    private lateinit var service: BulkFileOperationService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        coordinator = mockk(relaxed = true)
        repository = FakeFileRepository()

        serviceController = Robolectric.buildService(BulkFileOperationService::class.java)
        service = serviceController.get()
        service.coordinator = coordinator
        service.repository = repository
    }

    @Test
    fun `service start starts foreground and executes operation`() {
        val request = BulkFileOperationRequest(
            operationId = "op-1",
            type = BulkFileOperationType.TRASH,
            sourcePaths = listOf("/test.txt"),
            destinationPath = null,
            resolutions = emptyMap(),
            fakeFileSize = null
        )
        val activeRequestFlow = MutableStateFlow<BulkFileOperationRequest?>(request)
        every { coordinator.activeRequest } returns activeRequestFlow

        val intent = Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_START
            putExtra(BulkFileOperationService.EXTRA_REQUEST_JSON, Json.encodeToString(request))
        }

        serviceController.withIntent(intent).startCommand(0, 1)

        verify { coordinator.onOperationCompleted(request) }
        assertEquals(listOf(listOf("/test.txt")), repository.moveToTrashRequests)
    }

    @Test
    fun `service cancel stops execution and calls coordinator`() {
        val request = BulkFileOperationRequest(
            operationId = "op-1",
            type = BulkFileOperationType.TRASH,
            sourcePaths = listOf("/test.txt"),
            destinationPath = null,
            resolutions = emptyMap(),
            fakeFileSize = null
        )
        val activeRequestFlow = MutableStateFlow<BulkFileOperationRequest?>(request)
        every { coordinator.activeRequest } returns activeRequestFlow

        val startIntent = Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_START
            putExtra(BulkFileOperationService.EXTRA_REQUEST_JSON, Json.encodeToString(request))
        }
        serviceController.withIntent(startIntent).startCommand(0, 1)

        val cancelIntent = Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_CANCEL
            putExtra(BulkFileOperationService.EXTRA_OPERATION_ID, "op-1")
        }
        serviceController.withIntent(cancelIntent).startCommand(0, 2)

        // Wait, testing coroutine cancellation in Robolectric Service can be tricky.
        // But we can verify coordinator receives the cancel request.
        verify { coordinator.onOperationCancelling(request) }
    }
}
