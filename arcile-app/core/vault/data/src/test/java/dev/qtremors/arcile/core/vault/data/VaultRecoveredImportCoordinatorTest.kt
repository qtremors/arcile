package dev.qtremors.arcile.core.vault.data

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.plus
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultRecoveredImportCoordinatorTest {
    @Test
    fun `recovery emits its opaque destination before foreground service completion`() = runTest {
        val f = Fixture()
        val events = mutableListOf<VaultImportEvent>()
        f.coordinator.events.onEach(events::add).launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        coEvery { f.repository.importUris("token", VaultPath.Root, emptyList(), false, "queue", any()) } returns
            Result.success(VaultBatchResult(emptyList()))

        assertTrue(f.coordinator.startRecoveredImport(f.vault, "queue", f.destination))
        assertEquals(listOf(VaultImportEvent.Started(f.vault, f.destination)), events)
        assertEquals(emptyList<String>(), f.intent.captured.getStringArrayListExtra(VaultImportService.EXTRA_SOURCE_URIS))
        assertFalse(f.intent.captured.getBooleanExtra(VaultImportService.EXTRA_MOVE, true))

        f.coordinator.execute(f.vault, VaultPath.Root, emptyList(), "token", false) {}
        assertEquals(VaultImportEvent.Completed(f.vault), events.last())
        assertTrue(f.coordinator.activeImports.value.isEmpty())
        verify(exactly = 1) { f.repository.releaseImportReservation("token") }
    }

    @Test
    fun `service launch failure releases the reservation and reports failure after the start identity`() = runTest {
        val f = Fixture()
        every { f.context.startForegroundService(any()) } throws IllegalStateException("service unavailable")
        val events = mutableListOf<VaultImportEvent>()
        f.coordinator.events.onEach(events::add).launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))

        assertFalse(f.coordinator.startRecoveredImport(f.vault, "queue", f.destination))
        assertEquals(VaultImportEvent.Started(f.vault, f.destination), events.first())
        assertTrue(events.last() is VaultImportEvent.Failed)
        assertTrue(f.coordinator.activeImports.value.isEmpty())
        verify(exactly = 1) { f.repository.releaseImportReservation("token") }
    }

    @Test
    fun `second recovery cannot replace a running import destination or consume another reservation`() = runTest {
        val f = Fixture()
        val events = mutableListOf<VaultImportEvent>()
        f.coordinator.events.onEach(events::add).launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        assertTrue(f.coordinator.startRecoveredImport(f.vault, "first", f.destination))

        assertTrue(runCatching {
            f.coordinator.startRecoveredImport(f.vault, "second", DirectoryId.of("other"))
        }.isFailure)

        assertEquals(listOf(VaultImportEvent.Started(f.vault, f.destination)), events)
        verify(exactly = 1) { f.repository.reserveImport(f.vault) }
        verify(exactly = 1) { f.context.startForegroundService(any()) }
    }

    @Test
    fun `ordinary import starts without inventing a recovered directory`() = runTest {
        val f = Fixture()
        val events = mutableListOf<VaultImportEvent>()
        f.coordinator.events.onEach(events::add).launchIn(backgroundScope + UnconfinedTestDispatcher(testScheduler))
        val reservation = f.coordinator.prepareImport(f.vault, VaultPath.of("Folder")).getOrThrow()

        assertTrue(f.coordinator.startImport(reservation, listOf("/local/file.txt")))

        assertEquals(listOf(VaultImportEvent.Started(f.vault)), events)
        assertEquals("Folder", f.intent.captured.getStringExtra(VaultImportService.EXTRA_DESTINATION))
        assertEquals(listOf("/local/file.txt"), f.intent.captured.getStringArrayListExtra(VaultImportService.EXTRA_SOURCE_URIS))
    }

    private class Fixture {
        val vault = VaultId.of("vault")
        val destination = DirectoryId.of("opaque-destination")
        val intent = slot<Intent>()
        val context = mockk<Context> {
            every { packageName } returns "dev.qtremors.arcile"
            every { startForegroundService(capture(intent)) } returns ComponentName("dev.qtremors.arcile", "VaultImportService")
        }
        val repository = mockk<DefaultVaultRepository> {
            every { reserveImport(vault) } returns "token"
            every { releaseImportReservation(any()) } just Runs
        }
        val coordinator = DefaultVaultImportCoordinator(context, repository)
    }
}
