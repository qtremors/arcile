package dev.qtremors.arcile.feature.storagecleaner

import dev.qtremors.arcile.core.storage.domain.CleanerCandidate
import dev.qtremors.arcile.core.storage.domain.CleanerGroup
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.StorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanner
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.testutil.FakeStorageRepositoryBundle
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
import org.junit.Test
import java.io.File

class FakeStorageCleanerScanner : StorageCleanerScanner {
    var result = StorageCleanerResult(groups = emptyList(), scannedFiles = 0, isPartial = false)
    override suspend fun scan(
        rootPaths: List<String>,
        now: Long,
        limits: StorageCleanerScanLimits
    ): StorageCleanerResult = result
}

@OptIn(ExperimentalCoroutinesApi::class)
class StorageCleanerViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val fakeScanner = FakeStorageCleanerScanner()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `scan uses indexed volumes and populates groups`() = runTest(dispatcher) {
        val repository = FakeStorageRepositoryBundle(
            volumes = listOf(
                volume("internal", File("internal"), StorageKind.INTERNAL),
                volume("usb", File("usb"), StorageKind.OTG)
            )
        )
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(
                CleanerGroup(
                    type = CleanerGroupType.Apks,
                    candidates = listOf(
                        CleanerCandidate(
                            name = "keep.apk",
                            absolutePath = File("internal/keep.apk").absolutePath,
                            size = 1L,
                            lastModified = 0L,
                            groupTypes = setOf(CleanerGroupType.Apks)
                        )
                    )
                )
            ),
            scannedFiles = 1,
            isPartial = false
        )

        val viewModel = StorageCleanerViewModel(repository.volumeRepository, repository.trashRepository, fakeScanner)
        advanceUntilIdle()

        val apks = viewModel.state.value.group(CleanerGroupType.Apks).candidates
        assertEquals(listOf("keep.apk"), apks.map { it.name })
        assertFalse(viewModel.state.value.isScanning)
    }

    @Test
    fun `clean moves selected files to trash and exposes success`() = runTest(dispatcher) {
        val root = File("internal")
        val apkPath = File(root, "remove.apk").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL)))

        val viewModel = StorageCleanerViewModel(repository.volumeRepository, repository.trashRepository, fakeScanner)
        advanceUntilIdle()

        viewModel.clean(listOf(apkPath))
        advanceUntilIdle()

        assertEquals(listOf(listOf(apkPath)), repository.moveToTrashRequests)
        assertNotNull(viewModel.state.value.successMessage)
    }

    @Test
    fun `clean failure preserves candidates and exposes error`() = runTest(dispatcher) {
        val root = File("internal")
        val apkPath = File(root, "remove.apk").absolutePath
        val repository = FakeStorageRepositoryBundle(volumes = listOf(volume("internal", root, StorageKind.INTERNAL))).apply {
            moveToTrashResultProvider = { _, _ -> Result.failure(IllegalStateException("blocked")) }
        }
        fakeScanner.result = StorageCleanerResult(
            groups = listOf(
                CleanerGroup(
                    type = CleanerGroupType.Apks,
                    candidates = listOf(
                        CleanerCandidate(
                            name = "remove.apk",
                            absolutePath = apkPath,
                            size = 1L,
                            lastModified = 0L,
                            groupTypes = setOf(CleanerGroupType.Apks)
                        )
                    )
                )
            ),
            scannedFiles = 1,
            isPartial = false
        )

        val viewModel = StorageCleanerViewModel(repository.volumeRepository, repository.trashRepository, fakeScanner)
        advanceUntilIdle()

        viewModel.clean(listOf(apkPath))
        advanceUntilIdle()

        assertEquals("blocked", viewModel.state.value.errorMessage)
        assertTrue(viewModel.state.value.group(CleanerGroupType.Apks).candidates.any { it.absolutePath == apkPath })
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
