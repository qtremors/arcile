package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultBiometricChallenge
import dev.qtremors.arcile.core.vault.domain.VaultCatalog
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultSessionManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnlyFilesAdministrationController(
    private val catalog: VaultCatalog,
    private val sessions: VaultSessionManager,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val runBusy: ((suspend () -> Unit) -> Unit),
    private val showError: (Throwable) -> Unit,
    private val selectVault: (VaultId) -> Unit
) {
    fun changePassword(current: String, replacement: String, weakConfirmed: Boolean) {
        val vaultId = state.value.selectedVaultId ?: return
        runBusy {
            sessions.changePassword(
                vaultId, current.toCharArray(), replacement.toCharArray(), weakConfirmed
            ).fold(
                onSuccess = { state.update { it.copy(message = "Password changed") } },
                onFailure = showError
            )
        }
    }

    fun prepareBiometricEnrollment(password: String, onReady: (VaultBiometricChallenge) -> Unit) {
        val vaultId = state.value.selectedVaultId ?: return
        scope.launch {
            sessions.prepareBiometricEnrollment(vaultId, password.toCharArray()).fold(onReady, showError)
        }
    }

    fun prepareBiometricUnlock(vaultId: VaultId, onReady: (VaultBiometricChallenge) -> Unit) {
        scope.launch { sessions.prepareBiometricUnlock(vaultId).fold(onReady, showError) }
    }

    fun biometricCompleted(vaultId: VaultId) {
        selectVault(vaultId)
        state.update { it.copy(message = "Biometric authentication complete") }
        scope.launch {
            if (sessions.hasBiometricEnrollment(vaultId)) {
                state.update { it.copy(biometricVaultIds = it.biometricVaultIds + vaultId) }
            }
        }
    }

    fun removeBiometric() {
        val vaultId = state.value.selectedVaultId ?: return
        runBusy {
            sessions.removeBiometric(vaultId).fold(
                onSuccess = {
                    state.update {
                        it.copy(
                            biometricVaultIds = it.biometricVaultIds - vaultId,
                            message = "Biometric unlock removed"
                        )
                    }
                },
                onFailure = showError
            )
        }
    }

    fun removeRegistration() {
        val vaultId = state.value.selectedVaultId ?: return
        runBusy {
            catalog.removeRegistration(vaultId).fold(
                onSuccess = { clearSelection("Vault removed from Arcile") }, onFailure = showError
            )
        }
    }

    fun deleteVault(confirmation: String) {
        val vaultId = state.value.selectedVaultId ?: return
        runBusy {
            catalog.deletePermanently(vaultId, confirmation).fold(
                onSuccess = { clearSelection("Vault data permanently deleted") }, onFailure = showError
            )
        }
    }

    private fun clearSelection(message: String) {
        state.update {
            it.copy(
                selectedVaultId = null, directoryStack = emptyList(), nodes = emptyList(), searchHits = emptyList(),
                selectedNodeIds = emptySet(), clipboard = null, viewer = null, message = message
            )
        }
    }
}
