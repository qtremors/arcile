package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultBackupService
import dev.qtremors.arcile.core.vault.domain.VaultId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal enum class VaultBackupMode { CREATE, RESTORE }

internal data class VaultBackupRequest(val path: String, val mode: VaultBackupMode, val vaultId: VaultId?)

internal class OnlyFilesBackupController(
    private val service: VaultBackupService,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val showError: (Throwable) -> Unit
) {
    private var operation: Job? = null

    fun chooseFolder(path: String, mode: VaultBackupMode) = state.update { current ->
        if (current.busy || (mode == VaultBackupMode.CREATE && current.selectedVaultId == null)) current
        else current.copy(backupRequest = VaultBackupRequest(path, mode, current.selectedVaultId))
    }

    fun dismiss() = state.update { it.copy(backupRequest = null) }

    fun submit(label: String, password: String, weakConfirmed: Boolean) {
        val snapshot = state.value
        val request = snapshot.backupRequest ?: return
        if (snapshot.busy || request.vaultId != snapshot.selectedVaultId) return
        state.update { it.copy(backupRequest = null, backupInProgress = request.mode, busy = true, message = null) }
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val secret = password.toCharArray()
            try {
                val result = when (request.mode) {
                    VaultBackupMode.CREATE -> service.createBackup(
                        requireNotNull(request.vaultId), request.path, label, secret, weakConfirmed
                    ).map { operationSuccess(R.string.onlyfiles_backup_created, snapshot.mountGeneration,
                        listOf(it.substringAfterLast(java.io.File.separatorChar))) }
                    VaultBackupMode.RESTORE -> service.restoreBackup(request.path, label, secret)
                        .map { operationSuccess(R.string.onlyfiles_backup_restored, snapshot.mountGeneration) }
                }
                if (state.value.mountGeneration == snapshot.mountGeneration) {
                    result.fold(onSuccess = { feedback -> state.update { it.copy(message = null, operationFeedback = feedback) } }, onFailure = showError)
                }
            } finally {
                secret.fill('\u0000')
                operation = null
                state.update { it.copy(backupInProgress = null, busy = false) }
            }
        }
        operation = job
        job.start()
    }

    fun cancel() {
        operation?.cancel()
        dismiss()
    }
}
