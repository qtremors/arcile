package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlyFilesAdministrationControllerTest {
    @Test
    fun `biometric completion refreshes enrollment state outside composables`() = runTest {
        val vaultId = VaultId.of("vault")
        val state = MutableStateFlow(OnlyFilesUiState(selectedVaultId = vaultId))
        val sessions = TestSessions(enrolled = true)
        val controller = controller(state, sessions)

        controller.biometricCompleted(vaultId)
        advanceUntilIdle()

        assertTrue(vaultId in state.value.biometricVaultIds)
    }

    @Test
    fun `removing biometric enrollment updates state after storage succeeds`() = runTest {
        val vaultId = VaultId.of("vault")
        val state = MutableStateFlow(
            OnlyFilesUiState(selectedVaultId = vaultId, biometricVaultIds = setOf(vaultId))
        )
        val sessions = TestSessions(enrolled = true)
        val controller = controller(state, sessions)

        controller.removeBiometric()
        advanceUntilIdle()

        assertFalse(vaultId in state.value.biometricVaultIds)
    }

    private fun kotlinx.coroutines.CoroutineScope.controller(
        state: MutableStateFlow<OnlyFilesUiState>,
        sessions: TestSessions
    ) = OnlyFilesAdministrationController(
        catalog = TestCatalog,
        sessions = sessions,
        state = state,
        scope = this,
        runBusy = { block -> launch { block() } },
        showError = { throw AssertionError(it) },
        selectVault = {}
    )

    private class TestSessions(var enrolled: Boolean) : VaultSessionManager {
        override suspend fun unlock(vaultId: VaultId, options: VaultUnlockOptions) = Result.success(Unit)
        override suspend fun lockInteractive(vaultId: VaultId) = Unit
        override suspend fun lockAllInteractive() = Unit
        override fun acquireLease(vaultId: VaultId, purpose: VaultLeasePurpose) =
            Result.failure<VaultKeyLease>(UnsupportedOperationException())
        override suspend fun changePassword(
            vaultId: VaultId,
            currentPassword: CharArray,
            newPassword: CharArray,
            weakPasswordConfirmed: Boolean
        ) = Result.success(Unit)
        override suspend fun prepareBiometricEnrollment(vaultId: VaultId, password: CharArray) =
            Result.failure<VaultBiometricChallenge>(UnsupportedOperationException())
        override suspend fun prepareBiometricUnlock(vaultId: VaultId) =
            Result.failure<VaultBiometricChallenge>(UnsupportedOperationException())
        override suspend fun hasBiometricEnrollment(vaultId: VaultId) = enrolled
        override suspend fun removeBiometric(vaultId: VaultId): Result<Unit> {
            enrolled = false
            return Result.success(Unit)
        }
    }

    private object TestCatalog : VaultCatalog {
        override suspend fun list() = emptyList<VaultSummary>()
        override suspend fun refresh() = Unit
        override suspend fun create(request: VaultCreationRequest) =
            Result.failure<VaultId>(UnsupportedOperationException())
        override suspend fun attach(request: VaultAttachmentRequest) =
            Result.failure<VaultId>(UnsupportedOperationException())
        override suspend fun removeRegistration(vaultId: VaultId) = Result.success(Unit)
        override suspend fun deletePermanently(vaultId: VaultId, confirmation: String) = Result.success(Unit)
    }
}
