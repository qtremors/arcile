package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultImportEvent
import dev.qtremors.arcile.core.vault.domain.VaultImportReservation
import dev.qtremors.arcile.core.vault.domain.VaultPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnlyFilesImportController(
    private val coordinator: VaultImportCoordinator,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    scope: CoroutineScope,
    private val showError: (Throwable) -> Unit,
    private val reload: () -> Unit
) {
    private var reservation: VaultImportReservation? = null

    init {
        scope.launch {
            coordinator.activeImports.collect { imports ->
                state.update { it.copy(activeImports = imports) }
            }
        }
        scope.launch {
            coordinator.events.collect { event ->
                when (event) {
                    is VaultImportEvent.Completed -> finish(event.vaultId, "Import complete")
                    is VaultImportEvent.Cancelled -> state.update { it.copy(message = "Import cancelled") }
                    is VaultImportEvent.Failed -> state.update { it.copy(message = event.message) }
                    is VaultImportEvent.Partial -> finish(
                        event.vaultId,
                        "Imported ${event.result.completed.size}; ${event.result.failed.size} failed"
                    )
                    is VaultImportEvent.Started,
                    is VaultImportEvent.Progress -> Unit
                }
            }
        }
    }

    fun begin(): Boolean {
        cancelSelection()
        val snapshot = state.value
        val id = snapshot.selectedVaultId ?: return false
        val prepared = coordinator.prepareImport(
            id,
            snapshot.currentDirectory?.path ?: VaultPath.Root
        ).getOrElse {
            showError(it)
            return false
        }
        reservation = prepared
        return true
    }

    fun finishSelection(sourceUris: List<String>) {
        val prepared = reservation
        reservation = null
        if (sourceUris.isEmpty() || prepared == null) {
            prepared?.close()
            return
        }
        if (!coordinator.startImport(prepared, sourceUris)) {
            prepared.close()
            state.update { it.copy(message = "Another import is already running") }
        }
    }

    fun cancelSelection() {
        reservation?.close()
        reservation = null
    }

    fun cancel(vaultId: VaultId) = coordinator.cancelImport(vaultId)

    private fun finish(vaultId: VaultId, message: String) {
        state.update { it.copy(message = message) }
        if (state.value.selectedVaultId == vaultId) reload()
    }
}
