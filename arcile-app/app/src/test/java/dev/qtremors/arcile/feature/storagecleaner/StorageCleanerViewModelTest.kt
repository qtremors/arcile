package dev.qtremors.arcile.feature.storagecleaner

import dev.qtremors.arcile.core.storage.data.StorageCleanerScanner
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.StorageKind
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class StorageCleanerViewModelTest {

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
    fun `scan uses indexed volumes and populates groups`() = runTest(dispatcher) {
        val internalRoot = temporaryFolder.newFolder("internal")
        val usbRoot = temporaryFolder.newFolder("usb")
        File(internalRoot, "keep.apk").writeBytes(ByteArray(1))
        File(usbRoot, "ignore.apk").writeBytes(ByteArray(1))
        val repository = FakeFileRepository(
            volumes = listOf(
                volume("internal", internalRoot, StorageKind.INTERNAL),
                volume("usb", usbRoot, StorageKind.OTG)
            )
        )

        val viewModel = StorageCleanerViewModel(repository, StorageCleanerScanner(dispatchers))
        advanceUntilIdle()

        val apks = viewModel.state.value.group(CleanerGroupType.Apks).candidates
        assertEquals(listOf("keep.apk"), apks.map { it.name })
        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `clean moves selected files to trash and exposes success`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("internal")
        val apk = File(root, "remove.apk").apply { writeBytes(ByteArray(1)) }
        val repository = FakeFileRepository(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))

        val viewModel = StorageCleanerViewModel(repository, StorageCleanerScanner(dispatchers))
        advanceUntilIdle()

        viewModel.clean(listOf(apk.absolutePath))
        advanceUntilIdle()

        assertEquals(listOf(listOf(apk.absolutePath)), repository.moveToTrashRequests)
        assertNotNull(viewModel.state.value.successMessage)
    }

    @Test
    fun `clean failure preserves candidates and exposes error`() = runTest(dispatcher) {
        val root = temporaryFolder.newFolder("internal")
        val apk = File(root, "remove.apk").apply { writeBytes(ByteArray(1)) }
        val repository = FakeFileRepository(volumes = listOf(volume("internal", root, StorageKind.INTERNAL))).apply {
            moveToTrashResultProvider = { Result.failure(IllegalStateException("blocked")) }
        }

        val viewModel = StorageCleanerViewModel(repository, StorageCleanerScanner(dispatchers))
        advanceUntilIdle()

        viewModel.clean(listOf(apk.absolutePath))
        advanceUntilIdle()

        assertEquals("blocked", viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.group(CleanerGroupType.Apks).candidates.any { it.absolutePath == apk.absolutePath })
    }

    private fun volume(id: String, root: File, kind: StorageKind) = testVolume(
        id = id,
        storageKey = id,
        name = id,
        path = root.absolutePath,
        totalBytes = 100L,
        freeBytes = 50L,
        isPrimary = kind == StorageKind.INTERNAL,
        isRemovable = kind != StorageKind.INTERNAL,
        kind = kind
    )
}
