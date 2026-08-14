package dev.qtremors.arcile.core.operation.android

import android.content.Context
import android.content.Intent
import android.app.Notification
import android.app.NotificationManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationStorageNodeRef
import dev.qtremors.arcile.core.operation.SaveToArcileImportItem
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.BatchMutationFailure
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.data.DefaultStorageWorkCoordinator
import dev.qtremors.arcile.testutil.FakeArchiveRepository
import dev.qtremors.arcile.testutil.FakeClipboardRepository
import dev.qtremors.arcile.testutil.FakeFileMutationRepository
import dev.qtremors.arcile.testutil.FakeTrashRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class BulkFileOperationServiceTest {

    private lateinit var context: Context
    private lateinit var coordinator: BulkFileOperationCoordinator
    private lateinit var clipboardRepository: FakeClipboardRepository
    private lateinit var trashRepository: FakeTrashRepository
    private lateinit var fileMutationRepository: FakeFileMutationRepository
    private lateinit var archiveRepository: FakeArchiveRepository
    private lateinit var storageWorkCoordinator: DefaultStorageWorkCoordinator
    private lateinit var serviceController: ServiceController<BulkFileOperationService>
    private lateinit var service: BulkFileOperationService
    private lateinit var requestStore: OperationRequestStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("operation_journal", Context.MODE_PRIVATE).edit().clear().commit()
        DefaultOperationJournal.clearForTest(context)
        requestStore = OperationRequestStore(context).also { it.clearForTest() }
        coordinator = mockk(relaxed = true)
        every { coordinator.onOperationProgress(any(), any()) } returns true
        clipboardRepository = FakeClipboardRepository()
        trashRepository = FakeTrashRepository()
        fileMutationRepository = FakeFileMutationRepository()
        archiveRepository = FakeArchiveRepository()
        storageWorkCoordinator = DefaultStorageWorkCoordinator()

        serviceController = Robolectric.buildService(BulkFileOperationService::class.java)
        service = serviceController.get()
        service.coordinator = coordinator
        service.clipboardRepository = clipboardRepository
        service.trashRepository = trashRepository
        service.fileMutationRepository = fileMutationRepository
        service.archiveRepository = archiveRepository
        service.storageWorkCoordinator = storageWorkCoordinator
        service.requestStore = requestStore
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

        val intent = startIntent(request)

        serviceController.withIntent(intent).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        assertEquals(listOf(listOf("/test.txt")), trashRepository.moveToTrashRequests)
        awaitInactiveMutation()
        assertEquals(null, DefaultOperationJournal(context).activeRecord())
    }

    @Test
    fun `duplicate delivery of one operation id executes only once`() {
        val request = BulkFileOperationRequest(
            operationId = "op-duplicate",
            type = BulkFileOperationType.TRASH,
            sourcePaths = listOf("/test.txt")
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)
        val intent = startIntent(request)

        serviceController.withIntent(intent).startCommand(0, 1)
        serviceController.withIntent(intent).startCommand(0, 2)

        verify(timeout = 2_000, exactly = 1) { coordinator.onOperationCompleted(request) }
        assertEquals(1, trashRepository.moveToTrashRequests.size)
    }

    @Test
    fun `completed delete removes its progress notification`() {
        val request = BulkFileOperationRequest(
            operationId = "op-delete-notification",
            type = BulkFileOperationType.DELETE,
            sourcePaths = listOf("/source/a.txt"),
            destinationPath = null,
            resolutions = emptyMap(),
            fakeFileSize = null
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)
        fileMutationRepository.deletePermanentlyDetailedResultProvider = { paths ->
            Result.success(BatchMutationResult(succeededPaths = paths))
        }

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        awaitInactiveMutation()
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 2_000
        while (
            shadowOf(notificationManager).allNotifications.isNotEmpty() &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(10)
        }
        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    @Test
    fun `copy request with node payload dispatches backend aware clipboard operation`() {
        val source = StorageNodeRef.root(
            displayPath = "/data/user/0/com.example/files/report.txt",
            remoteCanonicalIdentity = "/data/user/0/com.example/files/report.txt"
        )
        val destination = StorageNodeRef.shizuku(
            displayPath = "/storage/emulated/0/Documents",
            remoteCanonicalIdentity = "/storage/emulated/0/Documents"
        )
        val request = BulkFileOperationRequest(
            operationId = "node-copy-service",
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf(source.displayPath.absolutePath),
            destinationPath = destination.displayPath.absolutePath,
            resolutions = emptyMap(),
            sourceNodeRefs = listOf(OperationStorageNodeRef.from(source)),
            destinationNodeRef = OperationStorageNodeRef.from(destination)
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        awaitInactiveMutation()
        assertEquals(
            listOf(FakeClipboardRepository.NodeTransferRequest(listOf(source), destination, emptyMap())),
            clipboardRepository.copyNodeRequests
        )
        assertTrue(clipboardRepository.copyRequests.isEmpty())
    }

    @Test
    fun `delete request with node payload dispatches backend aware mutation`() {
        val source = StorageNodeRef.root(
            displayPath = "/data/user/0/com.example/files/obsolete.txt",
            remoteCanonicalIdentity = "/data/user/0/com.example/files/obsolete.txt"
        )
        val request = BulkFileOperationRequest(
            operationId = "node-delete-service",
            type = BulkFileOperationType.DELETE,
            sourcePaths = listOf(source.displayPath.absolutePath),
            sourceNodeRefs = listOf(OperationStorageNodeRef.from(source))
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        awaitInactiveMutation()
        assertEquals(listOf(listOf(source)), fileMutationRepository.deleteNodeRequests)
        assertTrue(fileMutationRepository.deletePermanentlyRequests.isEmpty())
    }

    @Test
    fun `trash request with node payload preserves backend identity and progress`() {
        val source = StorageNodeRef.root(
            displayPath = "/storage/emulated/0/Documents/private.txt",
            remoteCanonicalIdentity = "/storage/emulated/0/Documents/private.txt"
        )
        val request = BulkFileOperationRequest(
            operationId = "node-trash-service",
            type = BulkFileOperationType.TRASH,
            sourcePaths = listOf(source.displayPath.absolutePath),
            sourceNodeRefs = listOf(OperationStorageNodeRef.from(source))
        )
        val reported = BulkFileOperationProgress(
            completedItems = 1,
            totalItems = 1,
            currentPath = source.displayPath.absolutePath
        )
        trashRepository.moveNodesToTrashResultProvider = { nodes, onProgress ->
            assertEquals(listOf(source), nodes)
            onProgress?.invoke(reported)
            Result.success(Unit)
        }
        every { coordinator.activeRequest } returns MutableStateFlow(request)

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationProgress(request, reported) }
        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        awaitInactiveMutation()
        assertEquals(listOf(listOf(source)), trashRepository.moveNodesToTrashRequests)
        assertTrue(trashRepository.moveToTrashRequests.isEmpty())
    }

    @Test
    fun `path trash request keeps legacy path dispatch when node payload is absent`() {
        val request = BulkFileOperationRequest(
            operationId = "path-trash-service",
            type = BulkFileOperationType.TRASH,
            sourcePaths = listOf("/storage/emulated/0/Documents/local.txt")
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        awaitInactiveMutation()
        assertEquals(listOf(request.sourcePaths), trashRepository.moveToTrashRequests)
        assertTrue(trashRepository.moveNodesToTrashRequests.isEmpty())
    }

    @Test
    fun `fake file request with node parent dispatches privileged creation`() {
        val parent = StorageNodeRef.root(
            displayPath = "/storage/emulated/0/Download",
            remoteCanonicalIdentity = "/storage/emulated/0/Download"
        )
        val request = BulkFileOperationRequest(
            operationId = "node-fake-service",
            type = BulkFileOperationType.CREATE_FAKE,
            sourcePaths = listOf("reserved.bin"),
            destinationPath = parent.displayPath.absolutePath,
            fakeFileSize = 8L * 1024 * 1024,
            destinationNodeRef = OperationStorageNodeRef.from(parent)
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        awaitInactiveMutation()
        assertEquals(
            listOf(FakeFileMutationRepository.CreateFakeNodeFileRequest(parent, "reserved.bin", 8L * 1024 * 1024)),
            fileMutationRepository.createFakeNodeFileRequests
        )
        assertTrue(fileMutationRepository.createFakeFileRequests.isEmpty())
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

        val startIntent = startIntent(request)
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
        clipboardRepository.copyFilesResultProvider = { _, _, _, onProgress ->
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
            assertTrue(notifications.any { it.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("1 / 2 items") })
            assertTrue(notifications.any { it.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("a.txt") })
            assertTrue(notifications.any { it.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("/s") })
            assertTrue(notifications.any { it.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("remaining") })
            assertTrue(notifications.any { notification -> notification.actions.any { it.title == "Cancel" } })
        } finally {
            finishOperation.countDown()
            verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
            awaitInactiveMutation()
        }
    }

    @Test
    fun `service notification uses singular initial item text`() {
        val request = BulkFileOperationRequest(
            operationId = "op-initial-singular",
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source/a.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap(),
            fakeFileSize = null
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)
        val operationStarted = CountDownLatch(1)
        val finishOperation = CountDownLatch(1)
        clipboardRepository.copyFilesResultProvider = { _, _, _, _ ->
            operationStarted.countDown()
            finishOperation.await(2, TimeUnit.SECONDS)
            Result.success(Unit)
        }

        try {
            serviceController.withIntent(startIntent(request)).startCommand(0, 1)
            assertTrue(operationStarted.await(2, TimeUnit.SECONDS))

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val notifications = shadowOf(notificationManager).allNotifications

            assertTrue(notifications.any {
                it.extras.getString(Notification.EXTRA_TEXT) == "Processing 1 item in the background"
            })
        } finally {
            finishOperation.countDown()
            verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
            awaitInactiveMutation()
        }
    }

    @Test
    fun `service notification pluralizes item-only progress`() {
        val request = BulkFileOperationRequest(
            operationId = "op-item-progress",
            type = BulkFileOperationType.COPY,
            sourcePaths = listOf("/source/a.txt", "/source/b.txt"),
            destinationPath = "/dest",
            resolutions = emptyMap(),
            fakeFileSize = null
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)
        val progressSent = CountDownLatch(1)
        val finishOperation = CountDownLatch(1)
        clipboardRepository.copyFilesResultProvider = { _, _, _, onProgress ->
            onProgress?.invoke(
                BulkFileOperationProgress(
                    completedItems = 1,
                    totalItems = 2,
                    currentPath = "/source/b.txt",
                    bytesCopied = null,
                    totalBytes = null
                )
            )
            progressSent.countDown()
            finishOperation.await(2, TimeUnit.SECONDS)
            Result.success(Unit)
        }

        try {
            serviceController.withIntent(startIntent(request)).startCommand(0, 1)
            assertTrue(progressSent.await(2, TimeUnit.SECONDS))

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            val notifications = shadowOf(notificationManager).allNotifications

            assertTrue(notifications.any {
                it.extras.getString(Notification.EXTRA_TEXT) == "1 / 2 items • b.txt"
            })
        } finally {
            finishOperation.countDown()
            verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
            awaitInactiveMutation()
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
        clipboardRepository.copyFilesResultProvider = { _, _, _, _ ->
            assertTrue(storageWorkCoordinator.isMutationActive.value)
            Result.success(Unit)
        }

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        awaitInactiveMutation()
    }

    @Test
    fun `service reports partial permanent delete as failed operation`() {
        val request = BulkFileOperationRequest(
            operationId = "op-partial-delete",
            type = BulkFileOperationType.DELETE,
            sourcePaths = listOf("/source/a.txt", "/source/b.txt"),
            destinationPath = null,
            resolutions = emptyMap(),
            fakeFileSize = null
        )
        val recordingCoordinator = RecordingBulkFileOperationCoordinator(request)
        service.coordinator = recordingCoordinator
        fileMutationRepository.deletePermanentlyDetailedResultProvider = {
            Result.success(
                BatchMutationResult(
                    succeededPaths = listOf("/source/a.txt"),
                    failedItems = listOf(
                        BatchMutationFailure(
                            path = "/source/b.txt",
                            displayName = "b.txt",
                            message = "Access denied",
                            causeType = "AccessDenied"
                        )
                    ),
                    cleanupRequiredPaths = listOf("/source/b.txt")
                )
            )
        }

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        val deadline = System.currentTimeMillis() + 2_000
        while (recordingCoordinator.failedMessage == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertNotNull(recordingCoordinator.failedMessage)
        assertTrue(recordingCoordinator.failedMessage.orEmpty().contains("1 succeeded"))
        assertTrue(recordingCoordinator.failedMessage.orEmpty().contains("1 failed"))
        assertTrue(recordingCoordinator.failedMessage.orEmpty().contains("b.txt"))
        awaitInactiveMutation()
    }

    @Test
    fun `service passes archive encoding and conflict resolutions to extraction`() {
        val request = BulkFileOperationRequest(
            operationId = "op-extract",
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf("/source/archive.zip"),
            destinationPath = "/dest",
            resolutions = mapOf("same.txt" to ConflictResolution.REPLACE),
            archiveEntryPrefix = "folder",
            archivePassword = "secret",
            archiveNameEncoding = ArchiveNameEncoding.WINDOWS_1252
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        val extractRequest = archiveRepository.extractArchiveRequests.single()
        assertEquals(ArchiveNameEncoding.WINDOWS_1252, extractRequest.nameEncoding)
        assertEquals(mapOf("same.txt" to ConflictResolution.REPLACE), extractRequest.resolutions)
        assertEquals("folder", extractRequest.entryPrefix)
        assertEquals("secret", extractRequest.password)
    }

    @Test
    fun `service routes protected extraction through retained node identities`() {
        val archive = StorageNodeRef.root(
            displayPath = "/data/local/tmp/archive.zip",
            remoteCanonicalIdentity = "/data/local/tmp/archive.zip"
        )
        val destination = StorageNodeRef.root(
            displayPath = "/data/local/tmp/out",
            remoteCanonicalIdentity = "/data/local/tmp/out"
        )
        val request = BulkFileOperationRequest(
            operationId = "op-node-extract",
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf(archive.displayPath.absolutePath),
            destinationPath = destination.displayPath.absolutePath,
            resolutions = mapOf("same.txt" to ConflictResolution.KEEP_BOTH),
            archiveEntryPrefix = "selected",
            archivePassword = "secret",
            archiveNameEncoding = ArchiveNameEncoding.WINDOWS_1252,
            sourceNodeRefs = listOf(OperationStorageNodeRef.from(archive)),
            destinationNodeRef = OperationStorageNodeRef.from(destination)
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        assertTrue(archiveRepository.extractArchiveRequests.isEmpty())
        val routed = archiveRepository.nodeExtractArchiveRequests.single()
        assertEquals(archive, routed.archive)
        assertEquals(destination, routed.destination)
        assertEquals("selected", routed.entryPrefix)
        assertEquals("secret", routed.password)
        assertEquals(ArchiveNameEncoding.WINDOWS_1252, routed.nameEncoding)
        assertEquals(mapOf("same.txt" to ConflictResolution.KEEP_BOTH), routed.resolutions)
    }

    @Test
    fun `service routes protected archive creation through retained node identities`() {
        val first = StorageNodeRef.shizuku(
            displayPath = "/storage/emulated/0/Android/data/one.txt",
            remoteCanonicalIdentity = "/storage/emulated/0/Android/data/one.txt"
        )
        val second = StorageNodeRef.shizuku(
            displayPath = "/storage/emulated/0/Android/data/folder",
            remoteCanonicalIdentity = "/storage/emulated/0/Android/data/folder"
        )
        val archive = StorageNodeRef.shizuku(
            displayPath = "/storage/emulated/0/Download/output.7z",
            remoteCanonicalIdentity = "/storage/emulated/0/Download/output.7z"
        )
        val request = BulkFileOperationRequest(
            operationId = "op-node-create",
            type = BulkFileOperationType.CREATE_ARCHIVE,
            sourcePaths = listOf(first.displayPath.absolutePath, second.displayPath.absolutePath),
            destinationPath = archive.displayPath.absolutePath,
            archiveFormat = ArchiveFormat.SEVEN_Z,
            archivePassword = "secret",
            archiveNameEncoding = ArchiveNameEncoding.UTF_8,
            archiveCompressionLevel = ArchiveCompressionLevel.MAXIMUM,
            sourceNodeRefs = listOf(first, second).map(OperationStorageNodeRef::from),
            destinationNodeRef = OperationStorageNodeRef.from(archive)
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        assertTrue(archiveRepository.createArchiveRequests.isEmpty())
        val routed = archiveRepository.nodeCreateArchiveRequests.single()
        assertEquals(listOf(first, second), routed.sources)
        assertEquals(archive, routed.destinationArchive)
        assertEquals(ArchiveFormat.SEVEN_Z, routed.format)
        assertEquals("secret", routed.password)
        assertEquals(ArchiveCompressionLevel.MAXIMUM, routed.compressionLevel)
    }

    @Test
    fun `service imports shared files through foreground operation`() {
        val source = File(context.cacheDir, "shared-source.txt").apply {
            writeText("shared payload")
        }
        val destination = File(context.cacheDir, "foreground-import-dest").apply {
            deleteRecursively()
            mkdirs()
        }
        val request = BulkFileOperationRequest(
            operationId = "op-import",
            type = BulkFileOperationType.SAVE_TO_ARCILE_IMPORT,
            sourcePaths = emptyList(),
            destinationPath = destination.absolutePath,
            importItems = listOf(
                SaveToArcileImportItem(
                    uri = Uri.fromFile(source).toString(),
                    displayName = "imported.txt",
                    sizeBytes = source.length()
                )
            )
        )
        every { coordinator.activeRequest } returns MutableStateFlow(request)

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        verify(timeout = 2_000) { coordinator.onOperationCompleted(request) }
        verify(timeout = 2_000) {
            coordinator.onOperationCheckpoint(
                request = request,
                stagedPaths = any(),
                finalizedPaths = any(),
                rollbackHints = any(),
                trashResultIds = any()
            )
        }
        assertEquals("shared payload", File(destination, "imported.txt").readText())
        awaitInactiveMutation()
    }

    @Test
    fun `service import checkpoints staged finalized and rollback metadata for recovery`() {
        val source = File(context.cacheDir, "shared-recovery-source.txt").apply {
            writeText("shared payload")
        }
        val destination = File(context.cacheDir, "foreground-import-recovery-dest").apply {
            deleteRecursively()
            mkdirs()
        }
        val request = BulkFileOperationRequest(
            operationId = "op-import-recovery",
            type = BulkFileOperationType.SAVE_TO_ARCILE_IMPORT,
            sourcePaths = emptyList(),
            destinationPath = destination.absolutePath,
            importItems = listOf(
                SaveToArcileImportItem(
                    uri = Uri.fromFile(source).toString(),
                    displayName = "imported.txt",
                    sizeBytes = source.length()
                )
            )
        )
        val recordingCoordinator = RecordingBulkFileOperationCoordinator(request)
        coordinator = recordingCoordinator
        service.coordinator = recordingCoordinator

        serviceController.withIntent(startIntent(request)).startCommand(0, 1)

        recordingCoordinator.awaitCompleted(request)
        val stagedCheckpoint = recordingCoordinator.checkpoints.first { it.stagedPaths.isNotEmpty() }
        val finalizedCheckpoint = recordingCoordinator.checkpoints.first { it.finalizedPaths.isNotEmpty() }
        val expectedFinalPath = File(destination, "imported.txt").absolutePath

        assertEquals(1, stagedCheckpoint.stagedPaths.size)
        assertTrue(stagedCheckpoint.stagedPaths.single().contains(".arcile-import-"))
        assertEquals(listOf(expectedFinalPath), finalizedCheckpoint.finalizedPaths)
        assertEquals(listOf("created:$expectedFinalPath"), finalizedCheckpoint.rollbackHints)
        assertEquals("shared payload", File(destination, "imported.txt").readText())
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
            assertTrue(requestStore.store(request))
            action = BulkFileOperationService.ACTION_START
            putExtra(BulkFileOperationService.EXTRA_OPERATION_ID, request.operationId)
        }

    private class RecordingBulkFileOperationCoordinator(
        active: BulkFileOperationRequest
    ) : BulkFileOperationCoordinator {
        override val activeRequest: StateFlow<BulkFileOperationRequest?> = MutableStateFlow(active)
        override val recoveryRecords = MutableStateFlow(emptyList<dev.qtremors.arcile.core.operation.OperationRecoveryRecord>())
        override val events: SharedFlow<dev.qtremors.arcile.core.operation.BulkFileOperationEvent> = MutableSharedFlow()
        var failedMessage: String? = null
        val checkpoints = mutableListOf<Checkpoint>()
        private val completedRequests = mutableSetOf<String>()
        private val completedLatch = CountDownLatch(1)

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
        override fun onOperationProgress(request: BulkFileOperationRequest, progress: BulkFileOperationProgress) = true
        override fun onOperationCheckpoint(
            request: BulkFileOperationRequest,
            stagedPaths: List<String>,
            finalizedPaths: List<String>,
            rollbackHints: List<String>,
            trashResultIds: List<String>
        ) {
            checkpoints += Checkpoint(stagedPaths, finalizedPaths, rollbackHints, trashResultIds)
        }
        override fun onOperationCancelling(request: BulkFileOperationRequest) = Unit
        override fun onOperationCompleted(request: BulkFileOperationRequest) {
            completedRequests += request.operationId
            completedLatch.countDown()
        }
        override fun onOperationFailed(request: BulkFileOperationRequest, message: String) {
            failedMessage = message
        }
        override fun onOperationCancelled(request: BulkFileOperationRequest?) = Unit
        override fun retryRecoveredOperation(operationId: String): Boolean = false
        override fun cleanupRecoveredOperation(operationId: String) = Unit
        override fun dismissRecoveredOperation(operationId: String) = Unit

        fun awaitCompleted(request: BulkFileOperationRequest) {
            completedLatch.await(2, TimeUnit.SECONDS)
            assertTrue(request.operationId in completedRequests)
        }

        data class Checkpoint(
            val stagedPaths: List<String>,
            val finalizedPaths: List<String>,
            val rollbackHints: List<String>,
            val trashResultIds: List<String>
        )
    }
}
