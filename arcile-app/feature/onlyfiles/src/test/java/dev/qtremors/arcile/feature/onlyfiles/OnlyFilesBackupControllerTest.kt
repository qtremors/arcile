package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultBackupService
import dev.qtremors.arcile.core.vault.domain.VaultId
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesBackupControllerTest {
    @Test
    fun `restore can start from the vault list and never retains the password`() = runTest {
        val state = MutableStateFlow(OnlyFilesUiState())
        val service = mockk<VaultBackupService>()
        val issuedSecrets = mutableListOf<CharArray>()
        coEvery { service.restoreBackup("/storage/backup", "Restored", any()) } coAnswers {
            issuedSecrets += thirdArg<CharArray>()
            assertEquals("backup-password", issuedSecrets.single().concatToString())
            Result.success(VaultId.of("restored"))
        }
        val controller = OnlyFilesBackupController(service, state, this) { throw it }

        controller.chooseFolder("/storage/backup", VaultBackupMode.RESTORE)
        controller.submit("Restored", "backup-password", false)
        assertEquals(VaultBackupMode.RESTORE, state.value.backupInProgress)
        assertNull(state.value.backupRequest)
        advanceUntilIdle()

        assertTrue(issuedSecrets.single().all { it == '\u0000' })
        assertEquals(dev.qtremors.arcile.core.presentation.UiText.StringResource(R.string.onlyfiles_backup_restored), state.value.operationFeedback?.message)
        assertFalse(state.value.busy)
        assertNull(state.value.backupInProgress)
    }

    @Test
    fun `switching mounts cancels an active backup and clears the dialog`() = runTest {
        val source = VaultId.of("source")
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = source))
        val service = mockk<VaultBackupService>()
        coEvery { service.createBackup(source, any(), any(), any(), false) } coAnswers { awaitCancellation() }
        val controller = OnlyFilesBackupController(service, state, this) { throw it }

        controller.chooseFolder("/storage", VaultBackupMode.CREATE)
        controller.submit("Snapshot", "backup-password", false)
        runCurrent()
        state.value = state.value.copy(selectedVaultId = null, mountGeneration = 1)
        controller.cancel()
        advanceUntilIdle()

        coVerify(exactly = 1) { service.createBackup(source, any(), any(), any(), false) }
        assertNull(state.value.backupInProgress)
        assertNull(state.value.backupRequest)
        assertNull(state.value.message)
        assertFalse(state.value.busy)
    }
}
