package dev.qtremors.arcile.feature.browser

import app.cash.turbine.test
import androidx.lifecycle.SavedStateHandle
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.core.operation.OperationRecoveryRecord
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.ui.UiText
import dev.qtremors.arcile.testutil.FakeBrowserPreferencesStore
import dev.qtremors.arcile.testutil.FakeBulkFileOperationCoordinator
import dev.qtremors.arcile.testutil.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelOperationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `bulk operation progress updates browser operation ui state`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val coordinator = FakeBulkFileOperationCoordinator()
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByPath = mapOf("/storage/emulated/0/Download" to listOf(browserFile("source.txt", "/storage/emulated/0/Download/source.txt")))
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true)),
            bulkFileOperationCoordinator = coordinator
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Download/source.txt")
        viewModel.copySelectedToClipboard()
        viewModel.pasteFromClipboard()
        advanceUntilIdle()

        val request = coordinator.activeRequest.value!!
        coordinator.onOperationProgress(
            request,
            BulkFileOperationProgress(
                completedItems = 1,
                totalItems = 2,
                currentPath = "/storage/emulated/0/Download/source.txt"
            )
        )
        advanceUntilIdle()

        val operation = viewModel.state.value.activeFileOperation
        assertEquals(BulkFileOperationType.COPY, operation?.type)
        assertEquals(1, operation?.completedItems)
        assertEquals(2, operation?.totalItems)
        assertEquals("/storage/emulated/0/Download/source.txt", operation?.currentPath)
        assertFalse(operation?.isCancelling ?: true)
    }

    @Test
    fun `bulk operation terminal events clear browser operation ui and publish snackbar message`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val coordinator = FakeBulkFileOperationCoordinator()
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(
                volumes = listOf(internal),
                filesByPath = mapOf("/storage/emulated/0/Download" to listOf(browserFile("source.txt", "/storage/emulated/0/Download/source.txt")))
            ),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true)),
            bulkFileOperationCoordinator = coordinator
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Download/source.txt")
        viewModel.cutSelectedToClipboard()
        viewModel.pasteFromClipboard()
        advanceUntilIdle()

        val request = coordinator.activeRequest.value!!
        coordinator.onOperationCompleted(request)
        advanceUntilIdle()

        viewModel.clearActiveFileOperation()
        assertNull(viewModel.state.value.activeFileOperation)
        assertEquals(UiText.PluralResource(R.plurals.file_operation_moved_items, 1, listOf(1)), viewModel.state.value.fileOperationStatusMessage)

        viewModel.clearFileOperationStatusMessage()
        assertNull(viewModel.state.value.fileOperationStatusMessage)
    }

    @Test
    fun `completed simple move exposes undo that moves files back to original parent`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val coordinator = FakeBulkFileOperationCoordinator()
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf("/storage/emulated/0/Download" to listOf(browserFile("source.txt", "/storage/emulated/0/Download/source.txt")))
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true)),
            bulkFileOperationCoordinator = coordinator
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/Download/source.txt")
        viewModel.cutSelectedToClipboard()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Documents")
        advanceUntilIdle()
        viewModel.pasteFromClipboard()
        advanceUntilIdle()

        val request = coordinator.activeRequest.value!!
        coordinator.onOperationCompleted(request)
        advanceUntilIdle()
        viewModel.undoLastOperation()
        advanceUntilIdle()

        val undoRequest = repo.moveRequests.last()
        assertEquals(listOf("/storage/emulated/0/Documents/source.txt"), undoRequest.sourcePaths)
        assertEquals("/storage/emulated/0/Download", undoRequest.destinationPath)
    }

    @Test
    fun `completed fake file creation exposes undo that permanently deletes created file`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val coordinator = FakeBulkFileOperationCoordinator()
        val repo = BrowserFakeFileRepository(volumes = listOf(internal))
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true)),
            bulkFileOperationCoordinator = coordinator
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()
        viewModel.createFakeFile("payload.bin", 128L)
        advanceUntilIdle()

        val request = coordinator.activeRequest.value!!
        coordinator.onOperationCompleted(request)
        advanceUntilIdle()
        viewModel.undoLastOperation()
        advanceUntilIdle()

        assertEquals(listOf(listOf("/storage/emulated/0/Download/payload.bin")), repo.deletePermanentlyRequests)
    }

    @Test
    fun `completed bulk operation refreshes current folder contents`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val coordinator = FakeBulkFileOperationCoordinator()
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            filesByPath = mapOf(
                "/storage/emulated/0/Download" to listOf(browserFile("before.txt", "/storage/emulated/0/Download/before.txt"))
            )
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true)),
            bulkFileOperationCoordinator = coordinator
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0/Download")
        advanceUntilIdle()
        assertEquals(listOf("before.txt"), viewModel.state.value.files.map { it.name })

        viewModel.createFakeFile("after.txt", 128L)
        advanceUntilIdle()
        val request = coordinator.activeRequest.value!!
        repo.filesByPath = mapOf(
            "/storage/emulated/0/Download" to listOf(browserFile("after.txt", "/storage/emulated/0/Download/after.txt"))
        )

        coordinator.onOperationCompleted(request)
        advanceUntilIdle()

        assertEquals(listOf("after.txt"), viewModel.state.value.files.map { it.name })
        assertEquals(UiText.PluralResource(R.plurals.file_operation_created_items, 1, listOf(1)), viewModel.state.value.fileOperationStatusMessage)
    }

    @Test
    fun `requestDeleteSelected shows mixed explanation for cross-policy selection`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val otg = browserVolume("otg", "USB", "/storage/otg", isPrimary = false, isRemovable = true, kind = StorageKind.OTG)
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal, otg)),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true))
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/alpha.txt")
        viewModel.toggleSelection("/storage/otg/beta.txt")

        viewModel.requestDeleteSelected()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showMixedDeleteExplanation)
        assertFalse(viewModel.state.value.showTrashConfirmation)
        assertFalse(viewModel.state.value.showPermanentDeleteConfirmation)
    }

    @Test
    fun `moveSelectedToTrash starts foreground trash operation`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val coordinator = FakeBulkFileOperationCoordinator()
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal)),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true)),
            bulkFileOperationCoordinator = coordinator
        )

        advanceUntilIdle()
        viewModel.navigateToSpecificFolder("/storage/emulated/0")
        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/alpha.txt")

        viewModel.moveSelectedToTrash()
        advanceUntilIdle()

        assertEquals(BulkFileOperationType.TRASH, coordinator.startedRequests.single().type)
        assertEquals(listOf("/storage/emulated/0/alpha.txt"), coordinator.startedRequests.single().sourcePaths)
        assertTrue(viewModel.state.value.selectedFiles.isEmpty())
    }

    @Test
    fun `foreground trash operation does not emit native confirmation request`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val coordinator = FakeBulkFileOperationCoordinator()
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal)),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true)),
            bulkFileOperationCoordinator = coordinator
        )

        viewModel.nativeRequestFlow.test {
            advanceUntilIdle()
            viewModel.navigateToSpecificFolder("/storage/emulated/0")
            advanceUntilIdle()
            viewModel.toggleSelection("/storage/emulated/0/alpha.txt")
            viewModel.moveSelectedToTrash()
            advanceUntilIdle()

            expectNoEvents()
            assertNull(viewModel.state.value.pendingNativeAction)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `seeded recovery record is exposed and cleanup action clears it`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val coordinator = FakeBulkFileOperationCoordinator()
        val request = BulkFileOperationRequest(
            operationId = "op-recovery",
            type = BulkFileOperationType.EXTRACT_ARCHIVE,
            sourcePaths = listOf("/storage/emulated/0/Download/archive.zip"),
            destinationPath = "/storage/emulated/0/Download/archive"
        )
        coordinator.seedRecovery(
            OperationRecoveryRecord(
                request = request,
                phase = "CLEANUP_REQUIRED",
                startedAtMillis = 1L,
                updatedAtMillis = 2L,
                progress = BulkFileOperationProgress(
                    completedItems = 1,
                    totalItems = 3,
                    currentPath = "/storage/emulated/0/Download/archive.zip"
                ),
                error = "File operation was interrupted and needs cleanup."
            )
        )
        val viewModel = createViewModel(
            repository = BrowserFakeFileRepository(volumes = listOf(internal)),
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to true)),
            bulkFileOperationCoordinator = coordinator
        )

        advanceUntilIdle()

        val recovery = viewModel.state.value.activeRecoveryOperation
        assertEquals("op-recovery", recovery?.operationId)
        assertEquals(BulkFileOperationType.EXTRACT_ARCHIVE, recovery?.type)
        assertEquals(1, recovery?.completedItems)

        viewModel.cleanupRecoveredOperation("op-recovery")
        advanceUntilIdle()

        assertEquals(listOf("op-recovery"), coordinator.cleanupRequests)
        assertNull(viewModel.state.value.activeRecoveryOperation)
    }

    @Test
    fun `renameFile handles collisions or append copy behavior without throwing error`() = runTest(mainDispatcherRule.dispatcher) {
        val internal = browserVolume("primary", "Internal", "/storage/emulated/0", isPrimary = true)
        val repo = BrowserFakeFileRepository(
            volumes = listOf(internal),
            renameResult = Result.success(browserFile("test - Copy.txt", "/storage/emulated/0/test - Copy.txt"))
        )
        val viewModel = createViewModel(
            repository = repo,
            browserPreferencesRepository = FakeBrowserPreferencesStore(),
            savedStateHandle = SavedStateHandle(mapOf("isVolumeRootScreen" to false))
        )

        advanceUntilIdle()
        viewModel.toggleSelection("/storage/emulated/0/test.txt")
        viewModel.renameFile("/storage/emulated/0/test.txt", "test - Copy.txt")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.selectedFiles.isEmpty())
        assertNull(viewModel.state.value.error)
        assertEquals("/storage/emulated/0/test.txt", repo.lastRenamePath)
        assertEquals("test - Copy.txt", repo.lastRenameNewName)
    }
}
