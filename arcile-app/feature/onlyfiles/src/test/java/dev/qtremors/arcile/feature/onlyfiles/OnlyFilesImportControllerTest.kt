package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultImportEvent
import dev.qtremors.arcile.core.vault.domain.VaultImportReservation
import dev.qtremors.arcile.core.vault.domain.VaultImportState
import dev.qtremors.arcile.core.vault.domain.VaultPath
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesImportControllerTest {
    @Test
    fun `picker handoff reserves operation before interactive vault locks`() = runTest {
        val vaultId = VaultId.of("picker-vault")
        val destination = VaultPath.of("Photos")
        val state = MutableStateFlow(
            OnlyFilesUiState(
                selectedVaultId = vaultId,
                directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.of("photos"), "Photos", destination))
            )
        )
        val coordinator = RecordingImportCoordinator()
        val controller = OnlyFilesImportController(
            coordinator,
            state,
            backgroundScope,
            { throw AssertionError(it) },
            {}
        )

        assertTrue(controller.begin())
        val reservation = coordinator.prepared.single()
        assertEquals(vaultId, reservation.vaultId)
        assertEquals(destination, reservation.destination)

        // Android's document picker backgrounds Arcile and locks the interactive mount.
        state.value = OnlyFilesUiState()
        controller.finishSelection(listOf("content://documents/photo.jpg"))

        assertSame(reservation, coordinator.startedReservation)
        assertEquals(listOf("content://documents/photo.jpg"), coordinator.startedUris)
    }

    @Test
    fun `cancelling picker destroys prepared operation reservation`() = runTest {
        val vaultId = VaultId.of("cancel-vault")
        val state = MutableStateFlow(
            OnlyFilesUiState(
                selectedVaultId = vaultId,
                directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "", VaultPath.Root))
            )
        )
        val coordinator = RecordingImportCoordinator()
        val controller = OnlyFilesImportController(
            coordinator,
            state,
            backgroundScope,
            { throw AssertionError(it) },
            {}
        )

        assertTrue(controller.begin())
        val reservation = coordinator.prepared.single()
        controller.finishSelection(emptyList())

        assertTrue(reservation.isClosed)
        assertEquals(null, coordinator.startedReservation)
    }

    private class RecordingImportCoordinator : VaultImportCoordinator {
        override val activeImports = MutableStateFlow<Map<VaultId, VaultImportState>>(emptyMap())
        override val events = MutableSharedFlow<VaultImportEvent>()
        val prepared = mutableListOf<TestReservation>()
        var startedReservation: VaultImportReservation? = null
        var startedUris: List<String> = emptyList()

        override fun prepareImport(vaultId: VaultId, destination: VaultPath): Result<VaultImportReservation> =
            Result.success(TestReservation(vaultId, destination).also(prepared::add))

        override fun startImport(reservation: VaultImportReservation, sourceUris: List<String>): Boolean {
            startedReservation = reservation
            startedUris = sourceUris
            return true
        }

        override fun cancelImport(vaultId: VaultId) = Unit
    }

    private class TestReservation(
        override val vaultId: VaultId,
        override val destination: VaultPath
    ) : VaultImportReservation {
        private val closed = AtomicBoolean(false)
        override val isClosed: Boolean get() = closed.get()
        override fun close() {
            closed.set(true)
        }
    }
}
