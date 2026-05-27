package dev.qtremors.arcile.feature.storageusage

import dev.qtremors.arcile.core.storage.data.StorageUsageScanner
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.testutil.FakeFileRepository
import dev.qtremors.arcile.testutil.testVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class StorageUsageViewModelTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val dispatcher = StandardTestDispatcher()
    private lateinit var dispatchers: ArcileDispatchers

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dispatchers = ArcileDispatchers(
            io = dispatcher,
            default = dispatcher,
            main = dispatcher,
            storage = dispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `load scans selected indexed volume and selects root`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("storage")
        File(root, "movie.mp4").writeBytes(ByteArray(25))
        val repository = FakeFileRepository(volumes = listOf(indexedVolume("primary", root)))
        val viewModel = StorageUsageViewModel(repository, StorageUsageScanner(dispatchers))

        viewModel.load("primary")
        advanceUntilIdle()

        val state = viewModel.state.value
        assertTrue(state.scanState is StorageUsageScanState.Loaded)
        assertEquals(root.absolutePath, state.currentRoot?.path)
        assertEquals(state.currentRoot, state.selectedNode)
    }

    @Test
    fun `selecting and drilling into node updates detail state without navigating`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("storage")
        val folder = File(root, "Downloads").apply { mkdirs() }
        File(folder, "archive.zip").writeBytes(ByteArray(12))
        val repository = FakeFileRepository(volumes = listOf(indexedVolume("primary", root)))
        val viewModel = StorageUsageViewModel(repository, StorageUsageScanner(dispatchers))

        viewModel.load("primary")
        advanceUntilIdle()
        val child = requireNotNull(viewModel.state.value.currentRoot).children.first()

        viewModel.selectNode(child)
        assertEquals(child.path, viewModel.state.value.selectedNode?.path)

        viewModel.drillInto(child)
        assertEquals(child.path, viewModel.state.value.currentRoot?.path)
        assertEquals(2, viewModel.state.value.breadcrumbs.size)
    }

    @Test
    fun `temporary volume is unavailable and does not scan`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("usb")
        val repository = FakeFileRepository(
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
        val viewModel = StorageUsageViewModel(repository, StorageUsageScanner(dispatchers))

        viewModel.load("usb")
        advanceUntilIdle()

        assertNotNull(viewModel.state.value.unavailableVolume)
        assertNull(viewModel.state.value.currentRoot)
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
