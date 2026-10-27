package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.ArcileFeedbackSeverity
import dev.qtremors.arcile.core.vault.domain.DirectoryId
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
    private data class Destination(val vaultId: VaultId, val directoryId: DirectoryId?, val mount: Long)
    private data class Submitted(val destination: Destination, val count: Int, val move: Boolean)
    private var reservation: VaultImportReservation? = null
    private var reservedDestination: Destination? = null
    private val submitted = mutableMapOf<VaultId, Submitted>()

    init {
        scope.launch {
            coordinator.activeImports.collect { imports ->
                state.update { it.copy(activeImports = imports) }
            }
        }
        scope.launch {
            coordinator.events.collect { event ->
                when (event) {
                    is VaultImportEvent.Completed -> finish(event.vaultId) { request ->
                        val count = request?.count ?: 0
                        OnlyFilesOperationFeedback(
                            if (count > 0) UiText.PluralResource(
                                if (request?.move == true) dev.qtremors.arcile.core.ui.R.plurals.file_operation_moved_items
                                else dev.qtremors.arcile.core.ui.R.plurals.file_operation_copied_items,
                                count, listOf(count)
                            ) else UiText.StringResource(R.string.onlyfiles_import_complete),
                            ArcileFeedbackSeverity.Success,
                            request?.destination?.mount ?: state.value.mountGeneration,
                            request?.destination?.target()
                        )
                    }
                    is VaultImportEvent.Partial -> finish(event.vaultId) { request ->
                        vaultBatchFeedback(
                            event.result,
                            if (request?.move == true) OnlyFilesOperationKind.MOVE_IN else OnlyFilesOperationKind.IMPORT,
                            request?.destination?.mount ?: state.value.mountGeneration,
                            request?.destination?.target()
                        )
                    }
                    is VaultImportEvent.Cancelled -> finish(event.vaultId, refresh = false) { request ->
                        operationNotice(R.string.onlyfiles_import_cancelled, request?.destination?.mount ?: state.value.mountGeneration)
                    }
                    is VaultImportEvent.Failed -> finish(event.vaultId, refresh = false) { request ->
                        operationNotice(R.string.onlyfiles_import_failed, request?.destination?.mount ?: state.value.mountGeneration, error = true)
                    }
                    is VaultImportEvent.Started,
                    is VaultImportEvent.Progress -> Unit
                }
            }
        }
    }

    fun begin(destination: VaultPath? = null, directoryId: DirectoryId? = null): Boolean {
        cancelSelection()
        val snapshot = state.value
        val id = snapshot.selectedVaultId ?: return false
        val prepared = coordinator.prepareImport(
            id,
            destination ?: snapshot.currentDirectory?.path ?: VaultPath.Root
        ).getOrElse {
            showError(it)
            return false
        }
        reservation = prepared
        reservedDestination = Destination(
            id,
            directoryId ?: snapshot.currentDirectory?.takeIf { destination == null || it.path == destination }?.id,
            snapshot.mountGeneration
        )
        return true
    }

    fun finishSelection(sourceUris: List<String>, move: Boolean = false): Boolean {
        val prepared = reservation
        val destination = reservedDestination
        reservation = null
        reservedDestination = null
        if (sourceUris.isEmpty() || prepared == null || destination == null) {
            prepared?.close()
            return false
        }
        // Register before start: coordinator events may be emitted synchronously during handoff.
        val request = Submitted(destination, sourceUris.distinct().size, move)
        submitted[prepared.vaultId] = request
        val started = if (move) coordinator.startMoveImport(prepared, sourceUris)
        else coordinator.startImport(prepared, sourceUris)
        if (!started) {
            if (submitted[prepared.vaultId] === request) submitted.remove(prepared.vaultId)
            prepared.close()
            state.update {
                if (it.mountGeneration != destination.mount) it
                else it.copy(message = null, operationFeedback = operationNotice(R.string.onlyfiles_import_running, destination.mount))
            }
        }
        return started
    }

    fun cancelSelection() {
        reservation?.close()
        reservation = null
        reservedDestination = null
    }

    fun cancel(vaultId: VaultId) = coordinator.cancelImport(vaultId)

    private fun Destination.target(): OnlyFilesOperationDestination.VaultDirectory? =
        directoryId?.let { OnlyFilesOperationDestination.VaultDirectory(vaultId, it) }

    private fun finish(vaultId: VaultId, refresh: Boolean = true, feedback: (Submitted?) -> OnlyFilesOperationFeedback) {
        val request = submitted.remove(vaultId)
        val current = state.value
        if (current.selectedVaultId != vaultId) return
        if (request != null && request.destination.mount != current.mountGeneration) return
        val completed = feedback(request)
        state.update {
            if (it.selectedVaultId != vaultId || !completed.belongsTo(it)) it
            else it.copy(message = null, operationFeedback = completed)
        }
        if (refresh) reload()
    }
}
