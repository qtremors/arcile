package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.*
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
