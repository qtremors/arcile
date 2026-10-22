package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultOperationRecovery
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryKind
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryQueue
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryResult
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class OnlyFilesRecoveryState(
    val queues: List<VaultRecoveryQueue> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null
)

internal class OnlyFilesRecoveryController(
    private val recovery: VaultOperationRecovery,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val reload: () -> Unit
) {
    val conflictReview = OnlyFilesConflictReview(state)
    private var query: Job? = null
    private var cancellation: AtomicBoolean? = null

    fun open() {
        val current = state.value
        val vault = current.selectedVaultId ?: return
        if (current.busy) return
        query?.cancel()
        state.update { it.copy(recovery = OnlyFilesRecoveryState(loading = true)) }
        query = scope.launch {
            val result = recovery.list(vault)
            state.update {
                if (it.mountGeneration != current.mountGeneration || it.recovery == null) it
                else it.copy(recovery = result.fold(
                    { queues -> OnlyFilesRecoveryState(queues) },
                    { error -> OnlyFilesRecoveryState(error = error.message ?: "Unable to load interrupted transfers") }
                ))
            }
        }
    }

    fun notifyAfterUnlock() {
        val current = state.value
        val vault = current.selectedVaultId ?: return
        query?.cancel()
        query = scope.launch {
            if (recovery.list(vault).getOrNull().isNullOrEmpty()) return@launch
            state.update {
                if (it.mountGeneration != current.mountGeneration || it.message != null || it.recovery != null) it
                else it.copy(message = "Interrupted transfers are available in the vault menu.")
            }
        }
    }

    fun close() {
        query?.cancel()
        query = null
        state.update { it.copy(recovery = null) }
    }

    fun discard(queue: VaultRecoveryQueue) {
        val current = state.value
        val panel = current.recovery ?: return
        if (current.selectedVaultId != queue.vaultId || current.busy || panel.loading || queue !in panel.queues) return
        state.update { it.copy(recovery = panel.copy(loading = true, error = null)) }
        query = scope.launch {
            val result = recovery.discard(queue)
            state.update {
                if (it.mountGeneration != current.mountGeneration || it.recovery == null) it
                else it.copy(recovery = result.fold(
                    { panel.copy(queues = panel.queues - queue, loading = false) },
                    { error -> panel.copy(loading = false, error = error.message ?: "Unable to discard the queue") }
                ))
            }
        }
    }

    fun resume(queue: VaultRecoveryQueue) {
        val current = state.value
        val panel = current.recovery ?: return
        val kind = queue.kind ?: return
        if (current.selectedVaultId != queue.vaultId || current.busy || panel.loading || queue !in panel.queues ||
            queue.remainingItems == 0 || queue.lockedSourceVaults.isNotEmpty()
        ) return
        val signal = AtomicBoolean(false)
        cancellation = signal
        val mount = current.mountGeneration
        state.update { it.copy(recovery = null, busy = true, message = null, transferMountGeneration = mount) }
        scope.launch {
            try {
                val conflicts = conflictReview.resolver(mount, exporting = kind == VaultRecoveryKind.EXPORT, signal::get)
                val outcome = recovery.resume(queue, conflicts, VaultCancellationSignal(signal::get))
                if (state.value.mountGeneration != mount) return@launch
                outcome.fold(
                    onSuccess = { result ->
                        if (result is VaultRecoveryResult.Transferred) {
                            val batch = result.batch
                            val merges = batch.partial.mapNotNull { it.mergeRetry }
                            val exports = batch.partial.mapNotNull { it.exportRetry }
                            state.update {
                                it.copy(
                                    message = "${batch.completed.size} completed, ${batch.partial.size} partial, " +
                                        "${batch.skipped.size} skipped, ${batch.failed.size + batch.rolledBack.size} failed",
                                    clipboard = it.clipboard ?: merges.takeIf { retries -> retries.isNotEmpty() }?.let { retries ->
                                        VaultClipboard(
                                            VaultClipboardAction.COPY, retries.map { retry -> retry.source },
                                            mergeRetries = retries.associateBy { retry -> retry.source.backendIdentity }
                                        )
                                    },
                                    exportRetries = it.exportRetries + exports
                                )
                            }
                            reload()
                        }
                    },
                    onFailure = { error ->
                        state.update { it.copy(message = error.message ?: "Unable to resume the transfer") }
                    }
                )
            } finally {
                cancellation = null
                conflictReview.dismiss()
                state.update {
                    if (it.transferMountGeneration == mount) it.copy(
                        busy = false, transferMountGeneration = null, transferProgress = null, pendingConflict = null
                    ) else it
                }
            }
        }
    }

    fun cancel() {
        cancellation?.set(true)
        conflictReview.dismiss()
    }
}
