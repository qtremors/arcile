package dev.qtremors.arcile.feature.storageusage

import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.storage.domain.StorageKind
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
    val invalidatedPaths = mutableListOf<List<String>>()
    override fun scanStorageUsage(
        rootPath: String,
        limits: StorageUsageScanLimits
    ): Flow<StorageUsageScanState> {
        scanRequests += rootPath
        return resultFlow
    }

    override fun invalidateStorageUsage(paths: Collection<String>) {
        invalidatedPaths += paths.toList()
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
        fakeScanner.invalidatedPaths.clear()
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
}
