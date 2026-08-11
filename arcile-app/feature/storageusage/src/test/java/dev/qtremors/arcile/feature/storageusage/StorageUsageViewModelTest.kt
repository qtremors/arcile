package dev.qtremors.arcile.feature.storageusage

import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageMutationEvent
import dev.qtremors.arcile.core.storage.domain.StorageMutationNotifier
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanner
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class FakeStorageUsageScanner : StorageUsageScanner {
    val resultFlow = MutableStateFlow<StorageUsageScanState>(StorageUsageScanState.Idle)
    val scanRequests = mutableListOf<String>()
    val nodeScanRequests = mutableListOf<StorageNodeRef>()
    val invalidatedPaths = mutableListOf<List<String>>()
    val invalidatedNodes = mutableListOf<List<StorageNodeRef>>()
    override fun scanStorageUsage(
        rootPath: String,
        limits: StorageUsageScanLimits
    ): Flow<StorageUsageScanState> {
        scanRequests += rootPath
        return resultFlow
    }

    override fun scanStorageUsage(
        root: StorageNodeRef,
        limits: StorageUsageScanLimits
    ): Flow<StorageUsageScanState> {
        nodeScanRequests += root
        return resultFlow
    }

    override fun invalidateStorageUsage(paths: Collection<String>) {
        invalidatedPaths += paths.toList()
    }

    override fun invalidateStorageUsageNodes(nodes: Collection<StorageNodeRef>) {
        invalidatedNodes += nodes.toList()
    }
}

private class FakeUsageMutationNotifier : StorageMutationNotifier {
    private val mutableEvents = MutableSharedFlow<StorageMutationEvent>(extraBufferCapacity = 8)
    override val events = mutableEvents

    override fun notify(paths: Collection<String>) {
        mutableEvents.tryEmit(StorageMutationEvent(paths.toList()))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StorageUsageViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fakeScanner = FakeStorageUsageScanner()
    private lateinit var operationCoordinator: FakeBulkFileOperationCoordinator

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        operationCoordinator = FakeBulkFileOperationCoordinator()
        fakeScanner.scanRequests.clear()
        fakeScanner.nodeScanRequests.clear()
        fakeScanner.invalidatedPaths.clear()
        fakeScanner.invalidatedNodes.clear()
        fakeScanner.resultFlow.value = StorageUsageScanState.Idle
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load scans selected indexed volume and selects root`() = runTest(dispatcher) {
        val root = File("storage")
        val repository = FakeStorageRepositoryBundle(volumes = listOf(indexedVolume("primary", root)))
        val viewModel = StorageUsageViewModel(repository.volumeRepository, fakeScanner, operationCoordinator)

        viewModel.load("primary")
        advanceUntilIdle()

        val expectedNode = StorageUsageNode(
            name = "storage",
            path = root.absolutePath,
            sizeBytes = 25L,
            kind = StorageUsageNodeKind.Folder,
            childCount = 1,
            children = listOf(
                StorageUsageNode(
                    name = "movie.mp4",
                    path = File(root, "movie.mp4").absolutePath,
                    sizeBytes = 25L,
                    kind = StorageUsageNodeKind.File,
                    childCount = 0
                )
            )
        )
        fakeScanner.resultFlow.value = StorageUsageScanState.Loaded(expectedNode)
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.scanState is StorageUsageScanState.Loaded)
        assertEquals(root.absolutePath, state.currentRoot?.path)
        assertEquals(state.currentRoot, state.selectedNode)
    }

    @Test
    fun `selecting and drilling into node updates detail state without navigating`() = runTest(dispatcher) {
        val root = File("storage")
        val folder = File(root, "Downloads")
        val repository = FakeStorageRepositoryBundle(volumes = listOf(indexedVolume("primary", root)))
        val viewModel = StorageUsageViewModel(repository.volumeRepository, fakeScanner, operationCoordinator)

        viewModel.load("primary")
        advanceUntilIdle()

        val expectedNode = StorageUsageNode(
            name = "storage",
            path = root.absolutePath,
            sizeBytes = 12L,
            kind = StorageUsageNodeKind.Folder,
            childCount = 1,
            children = listOf(
                StorageUsageNode(
                    name = "Downloads",
                    path = folder.absolutePath,
                    sizeBytes = 12L,
                    kind = StorageUsageNodeKind.Folder,
                    childCount = 1,
                    children = listOf(
                        StorageUsageNode(
                            name = "archive.zip",
                            path = File(folder, "archive.zip").absolutePath,
                            sizeBytes = 12L,
                            kind = StorageUsageNodeKind.File,
                            childCount = 0
                        )
                    )
                )
            )
        )
        fakeScanner.resultFlow.value = StorageUsageScanState.Loaded(expectedNode)
        advanceUntilIdle()

        val child = requireNotNull(viewModel.state.value.currentRoot).children.first()

        viewModel.selectNode(child)
        assertEquals(child.path, viewModel.state.value.selectedNode?.path)

        viewModel.drillInto(child)
        assertEquals(child.path, viewModel.state.value.currentRoot?.path)
        assertEquals(2, viewModel.state.value.breadcrumbs.size)

        viewModel.navigateToBreadcrumb(0)
        assertEquals(expectedNode.path, viewModel.state.value.currentRoot?.path)
        assertEquals(1, viewModel.state.value.breadcrumbs.size)
        assertTrue(viewModel.state.value.isDrilledDown)

        viewModel.resetToOverview()
        assertEquals(expectedNode.path, viewModel.state.value.currentRoot?.path)
        assertFalse(viewModel.state.value.isDrilledDown)
    }

    @Test
    fun `temporary volume is unavailable and does not scan`() = runTest(dispatcher) {
        val root = File("usb")
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(
                testVolume(
                    id = "usb",
                    storageKey = "usb",
                    name = "USB",
                    path = root.absolutePath,
                    totalBytes = 100L,
                    freeBytes = 50L,
                    isPrimary = false,
                    isRemovable = true,
                    kind = StorageKind.OTG
                )
            )
        )
        val viewModel = StorageUsageViewModel(repository.volumeRepository, fakeScanner, operationCoordinator)

        viewModel.load("usb")
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.unavailableVolume)
        assertNull(viewModel.state.value.currentRoot)
    }

    @Test
    fun `completed mutating operation refreshes selected volume`() = runTest(dispatcher) {
        val root = File("storage")
        val repository = FakeStorageRepositoryBundle(volumes = listOf(indexedVolume("primary", root)))
        val viewModel = StorageUsageViewModel(repository.volumeRepository, fakeScanner, operationCoordinator)

        viewModel.load("primary")
        advanceUntilIdle()
        assertEquals(1, fakeScanner.scanRequests.size)

        operationCoordinator.startOperation(
            type = BulkFileOperationType.DELETE,
            sourcePaths = listOf(File(root, "large.bin").absolutePath),
            destinationPath = null,
            resolutions = emptyMap()
        )
        operationCoordinator.onOperationCompleted(operationCoordinator.startedRequests.single())
        advanceUntilIdle()

        assertEquals(2, fakeScanner.scanRequests.size)
    }

    @Test
    fun `explicit protected root scans through node identity without loading a volume`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            identity = "root-device:/data/local/tmp"
        )
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageUsageViewModel(repository.volumeRepository, fakeScanner, operationCoordinator)

        viewModel.loadExplicitRoot(root)
        advanceUntilIdle()

        assertEquals(listOf(root), fakeScanner.nodeScanRequests)
        assertTrue(fakeScanner.scanRequests.isEmpty())
        assertEquals(root, viewModel.state.value.explicitRoot)
        assertNull(viewModel.state.value.rootVolume)
        assertNull(viewModel.state.value.unavailableVolume)
    }

    @Test
    fun `manual protected refresh invalidates only the selected backend node`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.SHIZUKU_BACKEND_ID,
            identity = "shizuku-user-0:/storage/emulated/0/Android/data"
        )
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageUsageViewModel(repository.volumeRepository, fakeScanner, operationCoordinator)
        viewModel.loadExplicitRoot(root)
        advanceUntilIdle()

        viewModel.refresh(forceScan = true)
        advanceUntilIdle()

        assertEquals(listOf(listOf(root)), fakeScanner.invalidatedNodes)
        assertTrue(fakeScanner.invalidatedPaths.isEmpty())
        assertEquals(listOf(root, root), fakeScanner.nodeScanRequests)
    }

    @Test
    fun `same visible path on root and shizuku remains two usage scopes`() = runTest(dispatcher) {
        val rootScope = protectedRoot(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            identity = "root-device:/data/shared"
        )
        val shizukuScope = protectedRoot(
            backendId = StorageNodeRef.SHIZUKU_BACKEND_ID,
            identity = "shizuku-user-0:/data/shared"
        )
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageUsageViewModel(repository.volumeRepository, fakeScanner, operationCoordinator)

        viewModel.loadExplicitRoot(rootScope)
        advanceUntilIdle()
        viewModel.loadExplicitRoot(shizukuScope)
        advanceUntilIdle()

        assertEquals(listOf(rootScope, shizukuScope), fakeScanner.nodeScanRequests)
        assertEquals(shizukuScope.canonicalIdentity, viewModel.state.value.explicitRoot?.canonicalIdentity)
        assertTrue(fakeScanner.scanRequests.isEmpty())
    }

    @Test
    fun `completed protected mutation invalidates root and rescans the same identity`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            identity = "root-device:/data/local/tmp"
        )
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageUsageViewModel(repository.volumeRepository, fakeScanner, operationCoordinator)
        viewModel.loadExplicitRoot(root)
        advanceUntilIdle()

        operationCoordinator.startOperation(
            type = BulkFileOperationType.CREATE_ARCHIVE,
            sourcePaths = listOf("/data/local/tmp/source"),
            destinationPath = "/data/local/tmp/output.zip",
            resolutions = emptyMap()
        )
        operationCoordinator.onOperationCompleted(operationCoordinator.startedRequests.single())
        advanceUntilIdle()

        assertEquals(listOf(listOf(root)), fakeScanner.invalidatedNodes)
        assertEquals(listOf(root, root), fakeScanner.nodeScanRequests)
        assertTrue(fakeScanner.invalidatedPaths.isEmpty())
    }

    @Test
    fun `storage mutation notifier refreshes protected root without path fallback`() = runTest(dispatcher) {
        val root = protectedRoot(
            backendId = StorageNodeRef.SHIZUKU_BACKEND_ID,
            identity = "shizuku-user-0:/storage/emulated/0/Android/data"
        )
        val notifier = FakeUsageMutationNotifier()
        val repository = FakeStorageRepositoryBundle()
        val viewModel = StorageUsageViewModel(
            repository.volumeRepository,
            fakeScanner,
            operationCoordinator,
            notifier
        )
        viewModel.loadExplicitRoot(root)
        advanceUntilIdle()

        notifier.notify(listOf("/storage/emulated/0/Android/data/app/cache"))
        advanceUntilIdle()

        assertEquals(listOf(listOf(root)), fakeScanner.invalidatedNodes)
        assertEquals(listOf(root, root), fakeScanner.nodeScanRequests)
        assertTrue(fakeScanner.invalidatedPaths.isEmpty())
    }

    private fun indexedVolume(id: String, root: File) = testVolume(
        id = id,
        storageKey = id,
        name = id,
        path = root.absolutePath,
        totalBytes = 100L,
        freeBytes = 40L,
        isPrimary = true,
        isRemovable = false,
        kind = StorageKind.INTERNAL
    )

    private fun protectedRoot(
        backendId: String,
        identity: String
    ) = StorageNodeRef.privileged(
        backendId = backendId,
        displayPath = identity.substringAfter(':'),
        remoteCanonicalIdentity = identity,
        capabilities = StorageNodeCapabilities(
            canRead = true,
            canWrite = true,
            canDelete = true,
            canTrash = true
        )
    )
}
