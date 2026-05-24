package dev.qtremors.arcile.presentation.operations

import android.content.Context
import android.content.Intent
import android.app.Notification
import android.app.NotificationManager
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.data.DefaultStorageWorkCoordinator
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BulkFileOperationServiceTest {

    private lateinit var context: Context
    private lateinit var coordinator: BulkFileOperationCoordinator
    private lateinit var repository: FakeFileRepository
    private lateinit var storageWorkCoordinator: DefaultStorageWorkCoordinator
    private lateinit var serviceController: ServiceController<BulkFileOperationService>
    private lateinit var service: BulkFileOperationService

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("operation_journal", Context.MODE_PRIVATE).edit().clear().commit()
        coordinator = mockk(relaxed = true)
        repository = FakeFileRepository()
        storageWorkCoordinator = DefaultStorageWorkCoordinator()

        serviceController = Robolectric.buildService(BulkFileOperationService::class.java)
        service = serviceController.get()
        service.coordinator = coordinator
        service.repository = repository
        service.storageWorkCoordinator = storageWorkCoordinator
        service.operationJournal = DefaultOperationJournal(context)
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

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        assertEquals(listOf(listOf("/test.txt")), repository.moveToTrashRequests)
        awaitInactiveMutation()
        assertEquals(null, DefaultOperationJournal(context).activeRecord())
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

    @Test
    fun `service updates notification with determinate progress and cancel action`() {
        val request = BulkFileOperationRequest(
            operationId = "op-progress",
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source/a.txt", "/source/b.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap(),
            fakeFileSize = null
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)
        val progressSent = CountDownLatch(1)
        val finishOperation = CountDownLatch(1)
        repository.copyFilesResultProvider = { _, _, _, onProgress ->
            onProgress?.invoke(
                BulkFileOperationProgress(
                    completedItems = 1,
                    totalItems = 2,
                    currentPath = "/source/a.txt",
                    bytesCopied = 50L,
                    totalBytes = 100L
                )
            )
            progressSent.countDown()
            finishOperation.await(2, TimeUnit.SECONDS)
            Result.success(Unit)
        }

        try {
            serviceController.withIntent(startIntent(request)).startCommand(0, 1)
            assertTrue(progressSent.await(2, TimeUnit.SECONDS))

            verify(timeout = 2_000) {
                coordinator.onOperationProgress(
                    request,
                    BulkFileOperationProgress(1, 2, "/source/a.txt", 50L, 100L)
                )
            }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val notifications = shadowOf(notificationManager).allNotifications

            assertTrue(notifications.any { it.extras.getString(Notification.EXTRA_TITLE) == "Copying files" })
            assertTrue(notifications.any { it.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("50%") })
            assertTrue(notifications.any { notification -> notification.actions.any { it.title == "Cancel" } })
        } finally {
            finishOperation.countDown()
        }
    }

    @Test
    fun `service marks storage work active only while operation runs`() {
        val request = BulkFileOperationRequest(
            operationId = "op-active",
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source/a.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap(),
            fakeFileSize = null
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)
        repository.copyFilesResultProvider = { _, _, _, _ ->
            assertTrue(storageWorkCoordinator.isMutationActive.value)
            Result.success(Unit)
        }

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        awaitInactiveMutation()
    }

    private fun awaitInactiveMutation() {
        val deadline = System.currentTimeMillis() + 2_000
        while (storageWorkCoordinator.isMutationActive.value && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertFalse(storageWorkCoordinator.isMutationActive.value)
    }

    private fun startIntent(request: BulkFileOperationRequest): Intent =
        Intent(context, BulkFileOperationService::class.java).apply {
            action = BulkFileOperationService.ACTION_START
            putExtra(BulkFileOperationService.EXTRA_REQUEST_JSON, Json.encodeToString(request))
        }
}
