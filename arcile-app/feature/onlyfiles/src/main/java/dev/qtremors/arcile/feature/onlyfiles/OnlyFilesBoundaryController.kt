package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferReservation
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class OnlyFilesBoundaryController(
    private val coordinator: VaultBoundaryTransferCoordinator,
    private val state: MutableStateFlow<OnlyFilesUiState>,
    private val scope: CoroutineScope,
    private val reload: () -> Unit
) {
    private var cancellation: AtomicBoolean? = null
    private var reservation: VaultBoundaryTransferReservation? = null
    val conflictReview = OnlyFilesConflictReview(state)

    fun prepare(nodes: List<VaultNodeMetadata>): Boolean {
        cancelSelection()
        val sources = nodes.map(VaultNodeMetadata::ref).distinctBy { it.backendIdentity }
        if (sources.isEmpty() || state.value.busy) return false
        val prepared = coordinator.prepareExport(sources).getOrElse {
            state.update { current -> current.copy(message = it.message ?: "Unable to prepare export") }
            return false
        }
        reservation = prepared
        return true
    }

    fun start(destinationPath: String, move: Boolean) {
        val prepared = reservation ?: return
        reservation = null
        if (state.value.busy) {
            prepared.close()
            return
        }
        val cancelled = AtomicBoolean(false)
        cancellation = cancelled
        val mountGeneration = state.value.mountGeneration
        scope.launch {
            state.update {
                it.copy(busy = true, selectedNodeIds = emptySet(), message = null, transferMountGeneration = mountGeneration)
            }
            val conflicts = conflictReview.resolver(mountGeneration, exporting = true, cancelled::get)
            val result = try {
                coordinator.exportToDestination(prepared, destinationPath, move, conflicts, VaultCancellationSignal(cancelled::get))
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    if (it.mountGeneration == mountGeneration) it.copy(message = error.message ?: "Vault export failed")
                    else it
                }
                return@launch
            } finally {
                cancellation = null
                conflictReview.dismiss()
                prepared.close()
                state.update {
                    it.copy(busy = false, pendingConflict = null, transferProgress = null, transferMountGeneration = null)
                }
            }
            if (state.value.mountGeneration != mountGeneration) return@launch
            val completed = result.items.count { it.outcome == VaultItemOutcome.COMPLETED }
            val skipped = result.items.count { it.outcome == VaultItemOutcome.SKIPPED }
            val partial = result.partial.size
            val failed = result.items.size - completed - skipped - partial
            state.update {
                val partialSummary = if (partial > 0) ", $partial partially exported" else ""
                it.copy(message = "$completed exported$partialSummary, $skipped skipped, $failed failed")
            }
            if (move && completed > 0) reload()
        }
    }

    fun cancelSelection() {
        reservation?.close()
        reservation = null
    }

    fun cancel() {
        cancellation?.set(true)
        conflictReview.dismiss()
    }

    fun clear() {
        cancelSelection()
        cancellation?.set(true)
        conflictReview.dismiss()
    }
}
