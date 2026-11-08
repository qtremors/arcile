package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import dev.qtremors.arcile.core.ui.R as UiR

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesImportFeedbackTest {
    @Test
    fun `import completion points to the submitted folder after user navigates elsewhere`() = runTest {
        val fixture = Fixture(this)
        fixture.start()
        fixture.state.value = fixture.state.value.copy(directoryStack = listOf(
            VaultDirectoryCrumb(DirectoryId.of("elsewhere"), "Elsewhere", VaultPath.of("Elsewhere"))
        ))
        fixture.events.emit(VaultImportEvent.Completed(fixture.vault))

        assertEquals(fixture.target, fixture.state.value.operationFeedback?.destination)
        assertEquals(UiText.PluralResource(UiR.plurals.file_operation_copied_items, 2, listOf(2)), fixture.message)
        assertEquals(1, fixture.reloads)
    }

    @Test
    fun `move completion uses moved wording and the same verified import coordinator`() = runTest {
        val fixture = Fixture(this)
        fixture.start(move = true)
        fixture.events.emit(VaultImportEvent.Completed(fixture.vault))

        verify(exactly = 1) { fixture.coordinator.startMoveImport(fixture.reservation, listOf("/local/a", "/local/b")) }
        assertEquals(UiText.PluralResource(UiR.plurals.file_operation_moved_items, 2, listOf(2)), fixture.message)
        assertEquals(fixture.target, fixture.state.value.operationFeedback?.destination)
    }

    @Test
    fun `partial move explains that originals were retained and counts rollback as failure`() = runTest {
        val fixture = Fixture(this)
        fixture.start(move = true)
        fixture.events.emit(VaultImportEvent.Partial(fixture.vault, VaultBatchResult(listOf(
            item(VaultItemOutcome.COMPLETED), item(VaultItemOutcome.PARTIAL),
            item(VaultItemOutcome.SKIPPED), item(VaultItemOutcome.ROLLED_BACK)
        ))))

        assertEquals(UiText.StringResource(R.string.onlyfiles_move_in_result, listOf(1, 1, 1, 1)), fixture.message)
        assertEquals(fixture.target, fixture.state.value.operationFeedback?.destination)
    }

    @Test
    fun `completion after lock cannot publish a private action or refresh the closed mount`() = runTest {
        val fixture = Fixture(this)
        fixture.start()
        fixture.state.value = OnlyFilesUiState(mountGeneration = 13)
        fixture.events.emit(VaultImportEvent.Completed(fixture.vault))

        assertNull(fixture.state.value.operationFeedback)
        assertNull(fixture.state.value.message)
        assertEquals(0, fixture.reloads)
    }

    @Test
    fun `reopening the same vault does not revive completion from an earlier mount`() = runTest {
        val fixture = Fixture(this)
        fixture.start()
        fixture.state.value = fixture.state.value.copy(mountGeneration = 14)
        fixture.events.emit(VaultImportEvent.Completed(fixture.vault))

        assertNull(fixture.state.value.operationFeedback)
        assertEquals(0, fixture.reloads)
    }

    @Test
    fun `events from a different vault never replace visible feedback`() = runTest {
        val fixture = Fixture(this)
        val prior = clipboardFeedback(VaultClipboardAction.COPY, 1, 12)
        fixture.state.value = fixture.state.value.copy(operationFeedback = prior)
        val other = VaultId.of("other")
        fixture.events.emit(VaultImportEvent.Failed(other, "private error"))
        fixture.events.emit(VaultImportEvent.Cancelled(other))
        fixture.events.emit(VaultImportEvent.Completed(other))

        assertSame(prior, fixture.state.value.operationFeedback)
        assertEquals(0, fixture.reloads)
    }

    @Test
    fun `synchronous completion during coordinator handoff still retains destination provenance`() = runTest {
        val fixture = Fixture(this)
        every { fixture.coordinator.startImport(any(), any()) } answers {
            fixture.events.tryEmit(VaultImportEvent.Completed(fixture.vault))
            true
        }
        fixture.start()

        assertEquals(fixture.target, fixture.state.value.operationFeedback?.destination)
        assertEquals(UiText.PluralResource(UiR.plurals.file_operation_copied_items, 2, listOf(2)), fixture.message)
    }

    @Test
    fun `failed import uses a localizable error without exposing service exception details`() = runTest {
        val fixture = Fixture(this)
        fixture.start()
        fixture.events.emit(VaultImportEvent.Failed(fixture.vault, "/private/secret-name.txt failed"))

        assertEquals(UiText.StringResource(R.string.onlyfiles_import_failed), fixture.message)
        assertNull(fixture.state.value.operationFeedback?.destination)
        assertEquals(0, fixture.reloads)
    }

    @Test
    fun `cancelled import never announces successful copies or exposes a destination`() = runTest {
        val fixture = Fixture(this)
        fixture.start()
        fixture.events.emit(VaultImportEvent.Cancelled(fixture.vault))

        assertEquals(UiText.StringResource(R.string.onlyfiles_import_cancelled), fixture.message)
        assertNull(fixture.state.value.operationFeedback?.destination)
        assertEquals(0, fixture.reloads)
    }

    @Test
    fun `unknown background import can report completion without inventing a destination`() = runTest {
        val fixture = Fixture(this)
        fixture.events.emit(VaultImportEvent.Completed(fixture.vault))

        assertEquals(UiText.StringResource(R.string.onlyfiles_import_complete), fixture.message)
        assertNull(fixture.state.value.operationFeedback?.destination)
    }

    @Test
    fun `recovered import reveals its authenticated destination after navigation`() = runTest {
        val fixture = Fixture(this)
        fixture.events.emit(VaultImportEvent.Started(fixture.vault, fixture.folder.id))
        fixture.state.value = fixture.state.value.copy(directoryStack = emptyList())
        fixture.events.emit(VaultImportEvent.Completed(fixture.vault))

        assertEquals(fixture.target, fixture.state.value.operationFeedback?.destination)
        assertEquals(UiText.StringResource(R.string.onlyfiles_import_complete), fixture.message)
        assertEquals(1, fixture.reloads)
    }

    @Test
    fun `recovered move completion never claims remaining originals were moved`() = runTest {
        val fixture = Fixture(this)
        fixture.events.emit(VaultImportEvent.Started(fixture.vault, fixture.folder.id))
        fixture.events.emit(VaultImportEvent.Partial(fixture.vault, VaultBatchResult(listOf(
            item(VaultItemOutcome.COMPLETED), item(VaultItemOutcome.SKIPPED)
        ))))

        assertEquals(UiText.StringResource(R.string.onlyfiles_import_result, listOf(1, 0, 1, 0)), fixture.message)
        assertEquals(fixture.target, fixture.state.value.operationFeedback?.destination)
    }

    @Test
    fun `lock and reopen invalidate a recovered import destination`() = runTest {
        val fixture = Fixture(this)
        fixture.events.emit(VaultImportEvent.Started(fixture.vault, fixture.folder.id))
        fixture.state.value = fixture.state.value.copy(mountGeneration = 13)
        fixture.events.emit(VaultImportEvent.Completed(fixture.vault))

        assertNull(fixture.state.value.operationFeedback)
        assertEquals(0, fixture.reloads)
    }

    @Test
    fun `recovery in another vault cannot replace a submitted import target`() = runTest {
        val fixture = Fixture(this)
        fixture.start()
        val other = VaultId.of("other")
        fixture.events.emit(VaultImportEvent.Started(other, DirectoryId.of("unrelated")))
        fixture.events.emit(VaultImportEvent.Completed(other))
        fixture.events.emit(VaultImportEvent.Completed(fixture.vault))

        assertEquals(fixture.target, fixture.state.value.operationFeedback?.destination)
        assertEquals(UiText.PluralResource(UiR.plurals.file_operation_copied_items, 2, listOf(2)), fixture.message)
    }

    @Test
    fun `ordinary started event preserves the target registered before service handoff`() = runTest {
        val fixture = Fixture(this)
        fixture.start(move = true)
        fixture.events.emit(VaultImportEvent.Started(fixture.vault))
        fixture.events.emit(VaultImportEvent.Completed(fixture.vault))

        assertEquals(fixture.target, fixture.state.value.operationFeedback?.destination)
        assertEquals(UiText.PluralResource(UiR.plurals.file_operation_moved_items, 2, listOf(2)), fixture.message)
    }

    @Test
    fun `failed recovery drops destination and does not leak private failure details`() = runTest {
        val fixture = Fixture(this)
        fixture.events.emit(VaultImportEvent.Started(fixture.vault, fixture.folder.id))
        fixture.events.emit(VaultImportEvent.Failed(fixture.vault, "private source path"))

        assertNull(fixture.state.value.operationFeedback?.destination)
        assertEquals(UiText.StringResource(R.string.onlyfiles_import_failed), fixture.message)
    }

    private fun item(outcome: VaultItemOutcome) = VaultItemResult("source-${outcome.name}", "private", outcome)

    private class Fixture(scope: TestScope) {
        val vault = VaultId.of("vault")
        val folder = VaultDirectoryCrumb(DirectoryId.of("destination"), "Private folder", VaultPath.of("Private folder"))
        val target = OnlyFilesOperationDestination.VaultDirectory(vault, folder.id)
        val state = MutableStateFlow(OnlyFilesUiState(
            selectedVaultId = vault, mountGeneration = 12, directoryStack = listOf(folder)
        ))
        val events = MutableSharedFlow<VaultImportEvent>(extraBufferCapacity = 16)
        val reservation = mockk<VaultImportReservation>(relaxed = true) {
            every { vaultId } returns vault
            every { destination } returns folder.path
        }
        val coordinator = mockk<VaultImportCoordinator> {
            every { activeImports } returns MutableStateFlow(emptyMap())
            every { events } returns this@Fixture.events
            every { prepareImport(vault, folder.path) } returns Result.success(reservation)
            every { startImport(any(), any()) } returns true
            every { startMoveImport(any(), any()) } returns true
        }
        var reloads = 0
        val controller = OnlyFilesImportController(
            coordinator, state, kotlinx.coroutines.CoroutineScope(
                scope.backgroundScope.coroutineContext + UnconfinedTestDispatcher(scope.testScheduler)
            ), { throw it }, { reloads++ }
        )
        val message get() = state.value.operationFeedback?.message
        fun start(move: Boolean = false) {
            assertTrue(controller.begin())
            assertTrue(controller.finishSelection(listOf("/local/a", "/local/b"), move))
        }
    }
}
