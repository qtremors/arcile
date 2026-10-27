package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.*
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesBrowserImportControllerTest {
    @Test
    fun `copy uses the selected authenticated folder and consumes a successful handoff once`() = runTest {
        val fixture = Fixture(this)
        fixture.accept()
        fixture.controller.choose()
        fixture.controller.choose()

        verify(exactly = 1) { fixture.coordinator.prepareImport(fixture.vault, fixture.folder.path) }
        verify(exactly = 1) { fixture.coordinator.startImport(fixture.reservation, fixture.paths) }
        verify(exactly = 0) { fixture.coordinator.startMoveImport(any(), any()) }
        assertEquals(0, fixture.state.value.browserImportCount)
        assertFalse(fixture.controller.hasPending)
        assertEquals(1, fixture.closed)
    }

    @Test
    fun `move cannot reserve or remove anything before confirmation`() = runTest {
        val fixture = Fixture(this)
        fixture.accept()
        fixture.controller.choose(move = true)

        assertTrue(fixture.state.value.browserImportMovePending)
        verify(exactly = 0) { fixture.coordinator.prepareImport(any(), any()) }
        verify(exactly = 0) { fixture.coordinator.startMoveImport(any(), any()) }
        assertTrue(fixture.controller.hasPending)

        fixture.controller.confirmMove(true)
        fixture.controller.confirmMove(true)
        verify(exactly = 1) { fixture.coordinator.startMoveImport(fixture.reservation, fixture.paths) }
        verify(exactly = 0) { fixture.coordinator.startImport(any(), any()) }
        assertFalse(fixture.state.value.browserImportMovePending)
        assertFalse(fixture.controller.hasPending)
    }

    @Test
    fun `cancelled move leaves the selection available for copying`() = runTest {
        val fixture = Fixture(this)
        fixture.accept()
        fixture.controller.choose(move = true)
        fixture.controller.confirmMove(false)

        assertFalse(fixture.state.value.browserImportMovePending)
        assertEquals(2, fixture.state.value.browserImportCount)
        assertEquals(0, fixture.closed)
        verify(exactly = 0) { fixture.coordinator.startMoveImport(any(), any()) }

        fixture.controller.choose()
        verify(exactly = 1) { fixture.coordinator.startImport(fixture.reservation, fixture.paths) }
    }

    @Test
    fun `changed destination invalidates the previous move confirmation`() = runTest {
        val fixture = Fixture(this)
        fixture.accept()
        fixture.controller.choose(move = true)
        fixture.state.value = fixture.state.value.copy(locationPicker = fixture.picker.copy(
            stack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "", VaultPath.Root))
        ))
        fixture.controller.confirmMove(true)

        verify(exactly = 0) { fixture.coordinator.prepareImport(any(), any()) }
        assertTrue(fixture.controller.hasPending)
        assertFalse(fixture.state.value.browserImportMovePending)
    }

    @Test
    fun `lock and remount invalidate a pending move even when destination identifiers match`() = runTest {
        val fixture = Fixture(this)
        fixture.accept()
        fixture.controller.choose(move = true)
        fixture.state.value = fixture.state.value.copy(mountGeneration = 5)
        fixture.controller.confirmMove(true)

        verify(exactly = 0) { fixture.coordinator.prepareImport(any(), any()) }
        assertFalse(fixture.state.value.browserImportMovePending)
    }

    @Test
    fun `loading failed busy and wrong-mode destinations reject both actions`() = runTest {
        val fixture = Fixture(this)
        fixture.accept()
        val original = fixture.state.value
        val invalidStates = listOf(
            original.copy(locationPicker = fixture.picker.copy(loading = true)),
            original.copy(locationPicker = fixture.picker.copy(failed = true)),
            original.copy(locationPicker = fixture.picker.copy(mode = OnlyFilesLocationMode.SEARCH_SCOPE)),
            original.copy(locationPicker = fixture.picker.copy(stack = emptyList())),
            original.copy(locationPicker = null),
            original.copy(selectedVaultId = null),
            original.copy(busy = true),
            original.copy(activeImports = mapOf(fixture.vault to VaultImportState(fixture.vault)))
        )
        for (state in invalidStates) {
            fixture.state.value = state
            fixture.controller.choose()
            fixture.controller.choose(move = true)
            fixture.controller.confirmMove(true)
            assertFalse(fixture.state.value.browserImportMovePending)
        }
        verify(exactly = 0) { fixture.coordinator.prepareImport(any(), any()) }
    }

    @Test
    fun `coordinator refusal keeps Browser selection and destination available for retry`() = runTest {
        val fixture = Fixture(this)
        every { fixture.coordinator.startImport(any(), any()) } returns false
        fixture.accept()
        fixture.controller.choose()

        assertTrue(fixture.controller.hasPending)
        assertEquals(2, fixture.state.value.browserImportCount)
        assertEquals(0, fixture.closed)
        verify(exactly = 1) { fixture.reservation.close() }
        assertNotNull(fixture.state.value.operationFeedback)

        every { fixture.coordinator.startImport(any(), any()) } returns true
        fixture.controller.choose()
        assertFalse(fixture.controller.hasPending)
        assertEquals(1, fixture.closed)
    }

    @Test
    fun `expired handoff cannot offer copy move or recover paths`() = runTest {
        val fixture = Fixture(this, requestPaths = null)
        fixture.accept()
        fixture.controller.choose(move = true)
        fixture.controller.confirmMove(true)
        fixture.controller.choose()

        assertFalse(fixture.controller.hasPending)
        assertEquals(0, fixture.state.value.browserImportCount)
        assertNotNull(fixture.state.value.operationFeedback)
        verify(exactly = 0) { fixture.coordinator.prepareImport(any(), any()) }
    }

    @Test
    fun `recomposition cannot claim the same request a second time`() = runTest {
        val fixture = Fixture(this)
        fixture.accept()
        fixture.controller.accept("request")
        assertEquals(1, fixture.claimed)
        assertEquals(2, fixture.state.value.browserImportCount)
    }

    @Test
    fun `cancelling handoff destroys pending paths and move authorization`() = runTest {
        val fixture = Fixture(this)
        fixture.accept()
        fixture.controller.choose(move = true)
        fixture.controller.clear()
        fixture.controller.confirmMove(true)
        fixture.controller.choose()
        fixture.controller.accept("request")

        assertEquals(0, fixture.state.value.browserImportCount)
        assertFalse(fixture.state.value.browserImportMovePending)
        assertFalse(fixture.controller.hasPending)
        assertEquals(1, fixture.claimed)
        verify(exactly = 0) { fixture.coordinator.prepareImport(any(), any()) }
    }

    private class Fixture(scope: TestScope, requestPaths: List<String>? = listOf("/local/a", "/local/b")) {
        val vault = VaultId.of("vault")
        val paths = listOf("/local/a", "/local/b")
        val folder = VaultDirectoryCrumb(DirectoryId.of("chosen"), "Chosen", VaultPath.of("Chosen"))
        val picker = OnlyFilesLocationPickerState(
            OnlyFilesLocationMode.IMPORT_DESTINATION, listOf(folder), loading = false
        )
        val state = MutableStateFlow(OnlyFilesUiState(
            selectedVaultId = vault, mountGeneration = 4,
            directoryStack = listOf(VaultDirectoryCrumb(DirectoryId.Root, "", VaultPath.Root)),
            locationPicker = picker
        ))
        val reservation = mockk<VaultImportReservation>(relaxed = true) {
            every { vaultId } returns vault
            every { destination } returns folder.path
        }
        val coordinator = mockk<VaultImportCoordinator> {
            every { activeImports } returns MutableStateFlow(emptyMap())
            every { events } returns MutableSharedFlow()
            every { prepareImport(any(), any()) } returns Result.success(reservation)
            every { startImport(any(), any()) } returns true
            every { startMoveImport(any(), any()) } returns true
        }
        var closed = 0
        var claimed = 0
        val imports = OnlyFilesImportController(coordinator, state, scope.backgroundScope, { throw it }, {})
        val controller = OnlyFilesBrowserImportController(state, imports, { closed++ }) {
            claimed++
            requestPaths
        }
        fun accept() = controller.accept("request")
    }
}
