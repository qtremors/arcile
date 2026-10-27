package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesBoundaryControllerTest {
    @Test
    fun `destination picker uses reservation created before interactive lock`() = runTest {
        val vaultId = VaultId.of("export-vault")
        val node = VaultNodeMetadata(
            ref = VaultNodeRef(
                vaultId,
                NodeId.of("photo"),
                DirectoryId.Root,
                VaultNodeCapabilities()
            ),
            name = "photo.jpg",
            kind = VaultNodeKind.FILE,
            revision = 1,
            modifiedAtMillis = 1,
            sizeBytes = 10,
            mimeType = "image/jpeg"
        )
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = vaultId))
        val coordinator = RecordingBoundaryCoordinator()
        val controller = OnlyFilesBoundaryController(coordinator, state, this, {})

        assertTrue(controller.prepare(listOf(node)))
        val prepared = coordinator.prepared.single()
        state.value = OnlyFilesUiState() // ProcessLifecycleOwner locks the visible mount.
        controller.start("content://documents/tree/root", move = false)
        advanceUntilIdle()

        assertSame(prepared, coordinator.started)
        assertEquals("content://documents/tree/root", coordinator.destination)
    }

    @Test
    fun `partial exports preserve other pending roots and consume only completed checkpoints`() = runTest {
        val vault = VaultId.of("vault")
        val first = exportRetry(vault, "First")
        val second = exportRetry(vault, "Second")
        val removed = DirectoryId.of("removed")
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = vault, exportRetries = listOf(first)))
        val coordinator = mockk<VaultBoundaryTransferCoordinator>()
        val reservation = TestReservation(listOf(second.source))
        val node = VaultNodeMetadata(second.source, second.displayName, VaultNodeKind.DIRECTORY, 0, 0, 0)
        every { coordinator.prepareExport(listOf(second.source)) } returns Result.success(reservation)
        coEvery { coordinator.exportToDestination(reservation, "/export", true, any(), any()) } returns
            VaultBatchResult(listOf(
                VaultItemResult(second.source.backendIdentity, second.displayName, VaultItemOutcome.PARTIAL, exportRetry = second)
            ))
        coEvery { coordinator.resumeExport(first, any(), any()) } returns VaultItemResult(
            first.source.backendIdentity, first.displayName, VaultItemOutcome.COMPLETED, removedFolders = setOf(removed)
        )
        coEvery { coordinator.resumeExport(second, any(), any()) } returns VaultItemResult(
            second.source.backendIdentity, second.displayName, VaultItemOutcome.PARTIAL, exportRetry = second
        )
        var reloads = 0
        val deleted = mutableListOf<Pair<VaultId, Set<DirectoryId>>>()
        val controller = OnlyFilesBoundaryController(coordinator, state, this, { reloads++ }) { id, folders ->
            deleted += id to folders
        }

        assertTrue(controller.prepare(listOf(node)))
        controller.start("/export", move = true)
        advanceUntilIdle()
        assertEquals(listOf(first, second), state.value.exportRetries)
        assertTrue(reservation.isClosed)

        controller.resume()
        controller.resume()
        advanceUntilIdle()

        assertEquals(listOf(second), state.value.exportRetries)
        assertEquals(listOf(vault to setOf(removed)), deleted)
        assertEquals(1, reloads)
        coVerify(exactly = 1) { coordinator.resumeExport(first, any(), any()) }
        coVerify(exactly = 1) { coordinator.resumeExport(second, any(), any()) }
    }

    @Test
    fun `resume ignores a checkpoint from a different vault`() = runTest {
        val retry = exportRetry(VaultId.of("old"), "First")
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = VaultId.of("new"), exportRetries = listOf(retry)))
        val coordinator = mockk<VaultBoundaryTransferCoordinator>()

        OnlyFilesBoundaryController(coordinator, state, this, {}).resume()
        advanceUntilIdle()

        coVerify(exactly = 0) { coordinator.resumeExport(any(), any(), any()) }
        assertEquals(listOf(retry), state.value.exportRetries)
    }

    @Test
    fun `export completion points to the selected local destination`() = runTest {
        val vault = VaultId.of("vault")
        val ref = VaultNodeRef(vault, NodeId.of("file"), DirectoryId.Root, VaultNodeCapabilities())
        val node = VaultNodeMetadata(ref, "private.txt", VaultNodeKind.FILE, 1, 0, 0)
        val reservation = TestReservation(listOf(ref))
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = vault, mountGeneration = 4))
        val coordinator = mockk<VaultBoundaryTransferCoordinator>()
        every { coordinator.prepareExport(listOf(ref)) } returns Result.success(reservation)
        coEvery { coordinator.exportToDestination(reservation, "/storage/Exports", false, any(), any()) } returns
            VaultBatchResult(listOf(VaultItemResult(ref.backendIdentity, node.name, VaultItemOutcome.COMPLETED)))
        val controller = OnlyFilesBoundaryController(coordinator, state, this, {})

        assertTrue(controller.prepare(listOf(node)))
        controller.start("/storage/Exports", move = false)
        advanceUntilIdle()

        assertEquals(OnlyFilesOperationDestination.LocalDirectory("/storage/Exports"), state.value.operationFeedback?.destination)
        assertTrue(reservation.isClosed)
    }

    @Test
    fun `export reservation retained across lock cannot publish an action on a new mount`() = runTest {
        val vault = VaultId.of("vault")
        val ref = VaultNodeRef(vault, NodeId.of("file"), DirectoryId.Root, VaultNodeCapabilities())
        val node = VaultNodeMetadata(ref, "private.txt", VaultNodeKind.FILE, 1, 0, 0)
        val reservation = TestReservation(listOf(ref))
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = vault, mountGeneration = 4))
        val coordinator = mockk<VaultBoundaryTransferCoordinator>()
        every { coordinator.prepareExport(listOf(ref)) } returns Result.success(reservation)
        coEvery { coordinator.exportToDestination(reservation, any(), false, any(), any()) } returns
            VaultBatchResult(listOf(VaultItemResult(ref.backendIdentity, node.name, VaultItemOutcome.COMPLETED)))
        val controller = OnlyFilesBoundaryController(coordinator, state, this, {})

        assertTrue(controller.prepare(listOf(node)))
        state.value = OnlyFilesUiState(selectedVaultId = vault, mountGeneration = 6)
        controller.start("/storage/Exports", move = false)
        advanceUntilIdle()

        assertEquals(null, state.value.operationFeedback)
        assertTrue(reservation.isClosed)
    }

    @Test
    fun `partial move to Trash offers inspection without claiming the originals were removed`() = runTest {
        val vault = VaultId.of("vault")
        val ref = VaultNodeRef(vault, NodeId.of("file"), DirectoryId.Root, VaultNodeCapabilities())
        val node = VaultNodeMetadata(ref, "private.txt", VaultNodeKind.FILE, 1, 0, 0)
        val reservation = TestReservation(listOf(ref))
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = vault, mountGeneration = 4))
        val coordinator = mockk<VaultBoundaryTransferCoordinator>()
        every { coordinator.prepareExport(listOf(ref)) } returns Result.success(reservation)
        coEvery { coordinator.moveToMainTrash(reservation, any()) } returns
            VaultBatchResult(listOf(VaultItemResult(ref.backendIdentity, node.name, VaultItemOutcome.PARTIAL)))
        val controller = OnlyFilesBoundaryController(coordinator, state, this, {})

        assertTrue(controller.prepare(listOf(node)))
        controller.startTrash()
        advanceUntilIdle()

        assertEquals(OnlyFilesOperationDestination.MainTrash, state.value.operationFeedback?.destination)
        assertEquals(
            dev.qtremors.arcile.core.presentation.UiText.StringResource(R.string.onlyfiles_trash_result, listOf(0, 1, 0, 0)),
            state.value.operationFeedback?.message
        )
    }

    private fun exportRetry(vault: VaultId, name: String): VaultExportRetry = object : VaultExportRetry {
        override val source = VaultNodeRef(vault, NodeId.of(name), DirectoryId.Root, VaultNodeCapabilities(), DirectoryId.of(name))
        override val displayName = name
        override val move = true
        override val remainingCount = 1
    }

    private class RecordingBoundaryCoordinator : VaultBoundaryTransferCoordinator {
        override val progress = MutableSharedFlow<VaultTransferProgress>()
        val prepared = mutableListOf<TestReservation>()
        var started: VaultBoundaryTransferReservation? = null
        var destination: String? = null

        override fun prepareExport(sources: List<VaultNodeRef>): Result<VaultBoundaryTransferReservation> =
            Result.success(TestReservation(sources).also(prepared::add))

        override suspend fun exportToDestination(
            reservation: VaultBoundaryTransferReservation,
            destinationPath: String,
            move: Boolean,
            conflicts: VaultConflictResolver,
            cancellation: VaultCancellationSignal
        ): VaultBatchResult {
            started = reservation
            destination = destinationPath
            return VaultBatchResult(emptyList())
        }
    }

    private class TestReservation(
        override val sources: List<VaultNodeRef>
    ) : VaultBoundaryTransferReservation {
        private val closed = AtomicBoolean(false)
        override val isClosed: Boolean get() = closed.get()
        override fun close() { closed.set(true) }
    }
}
