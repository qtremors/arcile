package dev.qtremors.arcile.presentation.delegate

import android.content.IntentSender
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileRepository
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.presentation.UiText
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
        coEvery { repository.getSelectionProperties(any()) } answers {
            val paths = firstArg<List<String>>()
            Result.success(selectionProperties(paths.size))
        }

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
        val volume = mockk<dev.qtremors.arcile.core.storage.domain.StorageVolume> {
            every { kind } returns dev.qtremors.arcile.core.storage.domain.StorageKind.INTERNAL
        }
        coEvery { repository.getVolumeForPath("/path/to/file.txt") } returns Result.success(volume)

        delegate.requestDeleteSelected()

        verify { callbacks.setLoading(true) }
        verify { callbacks.setLoading(false) }
        verify { callbacks.setDeleteDecision(match { !it.irreversible && it.selectedCount == 1 }) }
        verify { callbacks.showTrashConfirmation() }
    }

    @Test
    fun `requestDeleteSelected with permanent delete files shows permanent confirmation`() = testScope.runTest {
        val selected = listOf("/path/to/file.txt")
        every { callbacks.getSelectedFiles() } returns selected
        val volume = mockk<dev.qtremors.arcile.core.storage.domain.StorageVolume> {
            every { kind } returns dev.qtremors.arcile.core.storage.domain.StorageKind.OTG
        }
        coEvery { repository.getVolumeForPath("/path/to/file.txt") } returns Result.success(volume)

        delegate.requestDeleteSelected()

        verify { callbacks.showPermanentDeleteConfirmation() }
        verify { callbacks.setDeleteDecision(match { it.irreversible && it.selectedCount == 1 }) }
    }

    @Test
    fun `requestDeleteSelected with mixed files shows mixed explanation`() = testScope.runTest {
        val selected = listOf("/path/to/file1.txt", "/path/to/file2.txt")
        every { callbacks.getSelectedFiles() } returns selected
        val volumeTrash = mockk<dev.qtremors.arcile.core.storage.domain.StorageVolume> {
            every { kind } returns dev.qtremors.arcile.core.storage.domain.StorageKind.INTERNAL
        }
        val volumePerm = mockk<dev.qtremors.arcile.core.storage.domain.StorageVolume> {
            every { kind } returns dev.qtremors.arcile.core.storage.domain.StorageKind.OTG
        }
        coEvery { repository.getVolumeForPath("/path/to/file1.txt") } returns Result.success(volumeTrash)
        coEvery { repository.getVolumeForPath("/path/to/file2.txt") } returns Result.success(volumePerm)

        delegate.requestDeleteSelected()
        
        verify { callbacks.showMixedDeleteExplanation() }
        verify { callbacks.setDeleteDecision(match { it.irreversible && it.selectedCount == 2 }) }
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
        verify { callbacks.setError(UiText.StringResource(dev.qtremors.arcile.R.string.error_operation_already_running)) }
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
        verify { callbacks.setError(UiText.StringResource(dev.qtremors.arcile.R.string.error_operation_already_running)) }
        assert(onFailureCalled)
    }

    private fun selectionProperties(count: Int): SelectionProperties =
        SelectionProperties(
            displayName = "$count items",
            pathSummary = "/path",
            itemCount = count,
            fileCount = count,
            folderCount = 0,
            totalBytes = count * 1024L,
            newestModifiedAt = null,
            oldestModifiedAt = null,
            mimeTypeSummary = null,
            extensionSummary = null,
            hiddenCount = 0,
            accessStatus = PropertiesAccessStatus.Full
        )
}
