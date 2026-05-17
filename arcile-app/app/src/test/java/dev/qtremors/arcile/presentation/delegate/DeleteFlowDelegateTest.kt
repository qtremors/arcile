package dev.qtremors.arcile.presentation.delegate

import android.content.IntentSender
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.presentation.operations.BulkFileOperationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@OptIn(ExperimentalCoroutinesApi::class)
class DeleteFlowDelegateTest {

    private lateinit var repository: FileRepository
    private lateinit var callbacks: DeleteStateCallbacks
    private lateinit var testScope: TestScope
    private lateinit var delegate: DeleteFlowDelegate
    private var startBulkFileOperationResult = true
    private var onSuccessCalled = false
    private var onFailureCalled = false

    @Before
    fun setup() {
        testScope = TestScope(UnconfinedTestDispatcher())
        repository = mockk(relaxed = true)
        callbacks = mockk(relaxed = true)
        startBulkFileOperationResult = true
        onSuccessCalled = false
        onFailureCalled = false

        delegate = DeleteFlowDelegate(
            coroutineScope = testScope,
            repository = repository,
            callbacks = callbacks,
            startBulkDeleteOperation = { _, _ -> startBulkFileOperationResult },
            emitNativeRequest = {},
            onSuccess = { onSuccessCalled = true },
            onFailure = { onFailureCalled = true }
        )
    }

    @Test
    fun `requestDeleteSelected with trashable files shows trash confirmation`() = testScope.runTest {
        val selected = listOf("/path/to/file.txt")
        every { callbacks.getSelectedFiles() } returns selected
        val volume = mockk<dev.qtremors.arcile.domain.StorageVolume> {
            every { kind } returns dev.qtremors.arcile.domain.StorageKind.INTERNAL
        }
        coEvery { repository.getVolumeForPath("/path/to/file.txt") } returns Result.success(volume)

        delegate.requestDeleteSelected()

        verify { callbacks.setLoading(true) }
        verify { callbacks.setLoading(false) }
        verify { callbacks.showTrashConfirmation() }
    }

    @Test
    fun `requestDeleteSelected with permanent delete files shows permanent confirmation`() = testScope.runTest {
        val selected = listOf("/path/to/file.txt")
        every { callbacks.getSelectedFiles() } returns selected
        val volume = mockk<dev.qtremors.arcile.domain.StorageVolume> {
            every { kind } returns dev.qtremors.arcile.domain.StorageKind.OTG
        }
        coEvery { repository.getVolumeForPath("/path/to/file.txt") } returns Result.success(volume)

        delegate.requestDeleteSelected()

        verify { callbacks.showPermanentDeleteConfirmation() }
    }

    @Test
    fun `requestDeleteSelected with mixed files shows mixed explanation`() = testScope.runTest {
        val selected = listOf("/path/to/file1.txt", "/path/to/file2.txt")
        every { callbacks.getSelectedFiles() } returns selected
        val volumeTrash = mockk<dev.qtremors.arcile.domain.StorageVolume> {
            every { kind } returns dev.qtremors.arcile.domain.StorageKind.INTERNAL
        }
        val volumePerm = mockk<dev.qtremors.arcile.domain.StorageVolume> {
            every { kind } returns dev.qtremors.arcile.domain.StorageKind.OTG
        }
        coEvery { repository.getVolumeForPath("/path/to/file1.txt") } returns Result.success(volumeTrash)
        coEvery { repository.getVolumeForPath("/path/to/file2.txt") } returns Result.success(volumePerm)

        delegate.requestDeleteSelected()
        
        verify { callbacks.showMixedDeleteExplanation() }
    }

    @Test
    fun `togglePermanentDelete toggles when enabled`() {
        every { callbacks.isPermanentDeleteToggleEnabled() } returns true
        delegate.togglePermanentDelete()
        verify { callbacks.togglePermanentDeleteChecked() }
    }

    @Test
    fun `confirmDeleteSelected routes to trash when permanent is not checked`() = testScope.runTest {
        val selected = listOf("/path/to/file.txt")
        every { callbacks.getSelectedFiles() } returns selected
        every { callbacks.isPermanentDeleteChecked() } returns false
        startBulkFileOperationResult = true

        delegate.confirmDeleteSelected()

        verify { callbacks.setLoading(true) }
        verify { callbacks.dismissDeleteConfirmation() }
        verify { callbacks.setLoading(false) }
        verify { callbacks.clearSelection() }
    }

    @Test
    fun `confirmDeleteSelected routes to permanent delete when checked`() = testScope.runTest {
        val selected = listOf("/path/to/file.txt")
        every { callbacks.getSelectedFiles() } returns selected
        every { callbacks.isPermanentDeleteChecked() } returns true
        startBulkFileOperationResult = true

        delegate.confirmDeleteSelected()

        verify { callbacks.setLoading(true) }
        verify { callbacks.dismissDeleteConfirmation() }
        verify { callbacks.setLoading(false) }
        verify { callbacks.clearSelection() }
    }
    
    @Test
    fun `moveSelectedToTrash handles operation start failure`() = testScope.runTest {
        val selected = listOf("/path/to/file.txt")
        every { callbacks.getSelectedFiles() } returns selected
        startBulkFileOperationResult = false

        delegate.moveSelectedToTrash()

        verify { callbacks.setLoading(true) }
        verify { callbacks.dismissDeleteConfirmation() }
        verify { callbacks.setLoading(false) }
        verify { callbacks.setError("Another file operation is already running") }
        assert(onFailureCalled)
    }

    @Test
    fun `deleteSelectedPermanently handles operation start failure`() = testScope.runTest {
        val selected = listOf("/path/to/file.txt")
        every { callbacks.getSelectedFiles() } returns selected
        startBulkFileOperationResult = false

        delegate.deleteSelectedPermanently()

        verify { callbacks.setLoading(true) }
        verify { callbacks.dismissDeleteConfirmation() }
        verify { callbacks.setLoading(false) }
        verify { callbacks.setError("Another file operation is already running") }
        assert(onFailureCalled)
    }
}
