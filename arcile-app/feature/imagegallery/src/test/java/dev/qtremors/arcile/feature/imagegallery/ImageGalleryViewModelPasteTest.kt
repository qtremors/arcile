package dev.qtremors.arcile.feature.imagegallery

import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationCompletionStatus
import dev.qtremors.arcile.core.storage.domain.ClipboardOperation
import dev.qtremors.arcile.core.storage.domain.ClipboardState
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeFilePreferencesStore
import dev.qtremors.arcile.testutil.FakeClipboardRepository
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class ImageGalleryViewModelPasteTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `paste copy into album starts foreground copy operation`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        val source = galleryFile("source.jpg", "/storage/emulated/0/DCIM/source.jpg")
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, listOf(source)))
        advanceUntilIdle()

        fixture.viewModel.pasteFromClipboard("/storage/emulated/0/Pictures/Album")
        advanceUntilIdle()

        val request = fixture.operationCoordinator.startedRequests.single()
        assertEquals(BulkFileOperationType.COPY, request.type)
        assertEquals(listOf(source.absolutePath), request.sourcePaths)
        assertEquals("/storage/emulated/0/Pictures/Album", request.destinationPath)
    }

    @Test
    fun `paste cut into album starts foreground move operation`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        val source = galleryFile("source.jpg", "/storage/emulated/0/DCIM/source.jpg")
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.CUT, listOf(source)))
        advanceUntilIdle()

        fixture.viewModel.pasteFromClipboard("/storage/emulated/0/Pictures/Album")
        advanceUntilIdle()

        val request = fixture.operationCoordinator.startedRequests.single()
        assertEquals(BulkFileOperationType.MOVE, request.type)
        assertEquals(listOf(source.absolutePath), request.sourcePaths)
        assertEquals("/storage/emulated/0/Pictures/Album", request.destinationPath)
    }

    @Test
    fun `paste conflicts show dialog and resolving starts operation with resolutions`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        val source = galleryFile("source.jpg", "/storage/emulated/0/DCIM/source.jpg")
        val existing = galleryFile("source.jpg", "/storage/emulated/0/Pictures/Album/source.jpg")
        val conflict = FileConflict(source.absolutePath, source, existing)
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, listOf(source)))
        fixture.clipboardRepository.detectCopyConflictsResultProvider = { _, _ -> Result.success(listOf(conflict)) }
        advanceUntilIdle()

        fixture.viewModel.pasteFromClipboard("/storage/emulated/0/Pictures/Album")
        advanceUntilIdle()

        assertTrue(fixture.viewModel.state.value.showConflictDialog)
        assertEquals(listOf(conflict), fixture.viewModel.state.value.pasteConflicts)

        val resolutions = mapOf(source.absolutePath to ConflictResolution.KEEP_BOTH)
        fixture.viewModel.resolvePasteConflicts(resolutions)
        advanceUntilIdle()

        val request = fixture.operationCoordinator.startedRequests.single()
        assertEquals(BulkFileOperationType.COPY, request.type)
        assertEquals(resolutions, request.resolutions)
        assertFalse(fixture.viewModel.state.value.showConflictDialog)
    }

    @Test
    fun `already running operation reports error and does not keep refreshing`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        val source = galleryFile("source.jpg", "/storage/emulated/0/DCIM/source.jpg")
        fixture.operationCoordinator.startResult = false
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, listOf(source)))
        advanceUntilIdle()

        fixture.viewModel.pasteFromClipboard("/storage/emulated/0/Pictures/Album")
        advanceUntilIdle()

        assertTrue(fixture.operationCoordinator.startedRequests.isEmpty())
        assertEquals(UiText.StringResource(dev.qtremors.arcile.core.runtime.R.string.error_operation_already_running), fixture.viewModel.state.value.error)
        assertFalse(fixture.viewModel.state.value.isRefreshing)
    }

    @Test
    fun `copy completion invalidates source and destination then reloads gallery`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        val source = galleryFile("source.jpg", "/storage/emulated/0/DCIM/source.jpg")
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, listOf(source)))
        advanceUntilIdle()

        fixture.viewModel.pasteFromClipboard("/storage/emulated/0/Pictures/Album")
        advanceUntilIdle()
        val request = fixture.operationCoordinator.startedRequests.single()
        val loadCallsBeforeCompletion = fixture.repository.loadCalls

        fixture.operationCoordinator.onOperationCompleted(request)
        advanceUntilIdle()

        assertTrue(fixture.repository.loadCalls > loadCallsBeforeCompletion)
        assertEquals(
            listOf(listOf(source.absolutePath, "/storage/emulated/0/Pictures/Album")),
            fixture.repository.invalidateRequests
        )
    }

    @Test
    fun `removeFromClipboard removes item and clears clipboard when empty`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        val first = galleryFile("first.jpg", "/storage/emulated/0/DCIM/first.jpg")
        val second = galleryFile("second.jpg", "/storage/emulated/0/DCIM/second.jpg")
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, listOf(first, second)))
        advanceUntilIdle()

        fixture.viewModel.removeFromClipboard(first.absolutePath)
        advanceUntilIdle()

        assertEquals(listOf(second), fixture.viewModel.state.value.clipboardState?.files)

        fixture.viewModel.removeFromClipboard(second.absolutePath)
        advanceUntilIdle()

        assertNull(fixture.viewModel.state.value.clipboardState)
    }

    @Test
    fun `operation progress events populate active gallery operation state`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        val source = galleryFile("source.jpg", "/storage/emulated/0/DCIM/source.jpg")
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, listOf(source)))
        advanceUntilIdle()

        fixture.viewModel.pasteFromClipboard("/storage/emulated/0/Pictures/Album")
        advanceUntilIdle()
        val request = fixture.operationCoordinator.startedRequests.single()

        fixture.operationCoordinator.onOperationProgress(
            request,
            BulkFileOperationProgress(
                completedItems = 1,
                totalItems = 2,
                currentPath = source.absolutePath,
                bytesCopied = 50L,
                totalBytes = 100L
            )
        )
        advanceUntilIdle()

        val operation = fixture.viewModel.state.value.activeFileOperation
        assertEquals(BulkFileOperationType.COPY, operation?.type)
        assertEquals(1, operation?.completedItems)
        assertEquals(2, operation?.totalItems)
        assertEquals(50L, operation?.bytesCopied)
        assertEquals(100L, operation?.totalBytes)
        assertEquals(source.absolutePath, operation?.currentPath)
    }

    @Test
    fun `operation terminal events update terminal state and clear clipboard`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        val source = galleryFile("source.jpg", "/storage/emulated/0/DCIM/source.jpg")
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, listOf(source)))
        advanceUntilIdle()
        fixture.viewModel.pasteFromClipboard("/storage/emulated/0/Pictures/Album")
        advanceUntilIdle()
        val request = fixture.operationCoordinator.startedRequests.single()

        fixture.operationCoordinator.onOperationCompleted(request)
        advanceUntilIdle()

        assertEquals(OperationCompletionStatus.SUCCESS, fixture.viewModel.state.value.activeFileOperation?.terminalStatus)
        assertNull(fixture.viewModel.state.value.clipboardState)

        fixture.viewModel.clearActiveFileOperation()
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, listOf(source)))
        advanceUntilIdle()
        fixture.viewModel.pasteFromClipboard("/storage/emulated/0/Pictures/Album")
        advanceUntilIdle()
        val failedRequest = fixture.operationCoordinator.startedRequests.last()

        fixture.operationCoordinator.onOperationFailed(failedRequest, "failed")
        advanceUntilIdle()

        assertEquals(OperationCompletionStatus.FAILED, fixture.viewModel.state.value.activeFileOperation?.terminalStatus)
        assertNull(fixture.viewModel.state.value.clipboardState)

        fixture.viewModel.clearActiveFileOperation()
        fixture.clipboardRepository.setClipboardState(ClipboardState(ClipboardOperation.COPY, listOf(source)))
        advanceUntilIdle()
        fixture.viewModel.pasteFromClipboard("/storage/emulated/0/Pictures/Album")
        advanceUntilIdle()
        val cancelledRequest = fixture.operationCoordinator.startedRequests.last()

        fixture.operationCoordinator.onOperationCancelled(cancelledRequest)
        advanceUntilIdle()

        assertEquals(OperationCompletionStatus.CANCELLED, fixture.viewModel.state.value.activeFileOperation?.terminalStatus)
        assertNull(fixture.viewModel.state.value.clipboardState)
    }

    @Test
    fun `archive operation terminal event does not clear queued clipboard`() = runTest(mainDispatcherRule.dispatcher) {
        val fixture = createFixture()
        val source = galleryFile("source.jpg", "/storage/emulated/0/DCIM/source.jpg")
        val clipboard = ClipboardState(ClipboardOperation.COPY, listOf(source))
        fixture.clipboardRepository.setClipboardState(clipboard)
        advanceUntilIdle()

        fixture.operationCoordinator.startOperation(
            type = BulkFileOperationType.CREATE_ARCHIVE,
            sourcePaths = listOf(source.absolutePath),
            destinationPath = "/storage/emulated/0/DCIM/source.zip",
            resolutions = emptyMap()
        )
        advanceUntilIdle()
        val request = fixture.operationCoordinator.startedRequests.single()

        fixture.operationCoordinator.onOperationCompleted(request)
        advanceUntilIdle()

        assertEquals(OperationCompletionStatus.SUCCESS, fixture.viewModel.state.value.activeFileOperation?.terminalStatus)
        assertEquals(clipboard, fixture.viewModel.state.value.clipboardState)
    }

    private fun createFixture(savedStateHandle: SavedStateHandle = SavedStateHandle()): Fixture {
        val repository = RecordingGalleryRepository()
        val clipboardRepository = FakeClipboardRepository()
        val operationCoordinator = FakeBulkFileOperationCoordinator()
        val viewModel = ImageGalleryViewModel(
            repository = repository,
            fileBrowserRepository = NoOpFileBrowserRepository,
            fileMutationRepository = NoOpFileMutationRepository,
            clipboardRepository = clipboardRepository,
            volumeRepository = NoOpVolumeRepository,
            browserPreferencesStore = FakeFilePreferencesStore(),
            bulkFileOperationCoordinator = operationCoordinator,
            archivePathResolver = TestArchivePathResolver,
            savedStateHandle = savedStateHandle
        )
        return Fixture(viewModel, repository, clipboardRepository, operationCoordinator)
    }

    private data class Fixture(
        val viewModel: ImageGalleryViewModel,
        val repository: RecordingGalleryRepository,
        val clipboardRepository: FakeClipboardRepository,
        val operationCoordinator: FakeBulkFileOperationCoordinator
    )
}

private object TestArchivePathResolver :
    dev.qtremors.arcile.core.storage.domain.ArchivePathResolver {
    override suspend fun resolve(
        request: dev.qtremors.arcile.core.storage.domain.ArchivePathRequest
    ): Result<String> = Result.success(
        "${request.parentPath.orEmpty().trimEnd('/')}/Archive.${request.format.extension}"
    )

    override suspend fun resolveExtraction(
        request: dev.qtremors.arcile.core.storage.domain.ArchiveExtractionPathRequest
    ): Result<String> = Result.success(
        request.customDestination
            ?: request.currentPath
            ?: dev.qtremors.arcile.core.storage.domain.storageParentPath(request.archivePath)
            ?: request.archivePath
    )
}

private class RecordingGalleryRepository : ImageGalleryRepository {
    override val mutationEvents = MutableSharedFlow<dev.qtremors.arcile.core.storage.domain.StorageMutationEvent>()
    val invalidateRequests = mutableListOf<List<String>>()
    var loadCalls = 0

    override suspend fun loadImages(
        volumeId: String?,
        forceRefresh: Boolean,
        categoryName: String
    ): ImageGallerySnapshot {
        loadCalls += 1
        return ImageGallerySnapshot(
            files = emptyList(),
            albums = emptyList(),
            aspectRatios = emptyMap(),
            isStale = false
        )
    }

    override fun invalidate(paths: Collection<String>) {
        invalidateRequests += paths.toList()
    }
}

private object NoOpFileBrowserRepository : FileBrowserRepository {
    override suspend fun listFiles(path: String): Result<List<FileModel>> = Result.success(emptyList())
    override fun listFilePages(path: String, pageSize: Int): Flow<ListingPage> = emptyFlow()
    override suspend fun getCachedFolderStats(paths: Collection<String>): Map<String, FolderStats> = emptyMap()
    override fun queueFolderStats(paths: List<String>) = Unit
    override fun observeFolderStatUpdates(): Flow<FolderStatUpdate> = emptyFlow()
    override suspend fun getSelectionProperties(paths: List<String>) = Result.failure<dev.qtremors.arcile.core.storage.domain.SelectionProperties>(NotImplementedError())
}

private object NoOpFileMutationRepository : FileMutationRepository {
    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = Result.failure(NotImplementedError())
    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((dev.qtremors.arcile.core.operation.BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = Result.failure(NotImplementedError())

    override suspend fun deleteFile(path: String): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun shred(paths: List<String>): Result<Unit> = Result.failure(NotImplementedError())
    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = Result.failure(NotImplementedError())
}

private object NoOpVolumeRepository : VolumeRepository {
    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = emptyFlow()
    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = Result.success(emptyList())
    override suspend fun getVolumeForPath(path: String): Result<StorageVolume> = Result.failure(NotImplementedError())
    override fun getStandardFolders(): Map<String, String?> = emptyMap()
}

private fun galleryFile(name: String, path: String) = FileModel(
    name = name,
    absolutePath = path,
    size = 100L,
    lastModified = 100L,
    isDirectory = false,
    extension = name.substringAfterLast('.', ""),
    mimeType = "image/jpeg"
)
