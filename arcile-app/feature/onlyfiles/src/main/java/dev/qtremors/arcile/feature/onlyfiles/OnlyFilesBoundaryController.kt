package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultBoundaryTransferReservation
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
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
    private val reload: () -> Unit,
    private val onFoldersDeleted: suspend (VaultId, Set<DirectoryId>) -> Unit = { _, _ -> }
) {
    private var cancellation: AtomicBoolean? = null
    private var reservation: VaultBoundaryTransferReservation? = null
    private var preparedMount: Long? = null
    val conflictReview = OnlyFilesConflictReview(state)

    fun prepare(nodes: List<VaultNodeMetadata>): Boolean {
        cancelSelection()
        val sources = nodes.map(VaultNodeMetadata::ref).distinctBy { it.backendIdentity }
        if (sources.isEmpty() || state.value.busy || cancellation != null) return false
        val prepared = coordinator.prepareExport(sources).getOrElse {
            state.update { current -> current.copy(message = null, operationFeedback = vaultOperationFailure(it, R.string.onlyfiles_export_prepare_failed, current.mountGeneration)) }
            return false
        }
        reservation = prepared
        preparedMount = state.value.mountGeneration
        return true
    }

    fun start(destinationPath: String, move: Boolean) = start(destinationPath, move, trash = false)

    fun startTrash() = start(null, move = true, trash = true)

    private fun start(destinationPath: String?, move: Boolean, trash: Boolean) {
        val prepared = reservation ?: return
        val mount = preparedMount ?: state.value.mountGeneration
        reservation = null
        preparedMount = null
        if (state.value.busy || cancellation != null) {
            prepared.close()
            return
        }
        runTransfer(move, trash, prepared.sources.firstOrNull()?.vaultId, destinationPath, mountGeneration = mount, close = prepared::close) { conflicts, cancellation ->
            if (trash) coordinator.moveToMainTrash(prepared, cancellation)
            else coordinator.exportToDestination(prepared, requireNotNull(destinationPath), move, conflicts, cancellation)
        }
    }

    fun resume() {
        val current = state.value
        val retries = current.exportRetries.filter { it.source.vaultId == current.selectedVaultId }
        if (current.busy || cancellation != null || retries.isEmpty()) return
        runTransfer(move = retries.any { it.move }, trash = false, sourceVault = current.selectedVaultId) { conflicts, cancellation ->
            VaultBatchResult(retries.map { retry ->
                if (cancellation.isCancelled()) {
                    VaultItemResult(
                        retry.source.backendIdentity, retry.displayName, VaultItemOutcome.PARTIAL,
                        VaultFailure.Cancelled(), exportRetry = retry
                    )
                } else try {
                    coordinator.resumeExport(retry, conflicts, cancellation)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    VaultItemResult(
                        sourceIdentity = retry.source.backendIdentity,
                        displayName = retry.displayName,
                        outcome = VaultItemOutcome.PARTIAL,
                        failure = error as? VaultFailure ?: VaultFailure.Unavailable("Unable to resume export", error),
                        exportRetry = retry
                    )
                }
            })
        }
    }

    private fun runTransfer(
        move: Boolean,
        trash: Boolean,
        sourceVault: VaultId?,
        destinationPath: String? = null,
        mountGeneration: Long = state.value.mountGeneration,
        close: () -> Unit = {},
        execute: suspend (VaultConflictResolver, VaultCancellationSignal) -> VaultBatchResult
    ) {
        val cancelled = AtomicBoolean(false)
        cancellation = cancelled
        scope.launch {
            state.update {
                it.copy(busy = true, selectedNodeIds = emptySet(), message = null, operationFeedback = null, transferMountGeneration = mountGeneration)
            }
            val conflicts = conflictReview.resolver(mountGeneration, exporting = true, cancelled::get)
            try {
                val result = execute(conflicts, VaultCancellationSignal(cancelled::get))
                complete(result, sourceVault, mountGeneration, move, trash, destinationPath)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                state.update {
                    if (it.mountGeneration == mountGeneration) it.copy(
                        message = null, operationFeedback = vaultOperationFailure(
                            error, if (trash) R.string.onlyfiles_trash_failed else R.string.onlyfiles_export_failed,
                            mountGeneration
                        )
                    )
                    else it
                }
            } finally {
                cancellation = null
                conflictReview.dismiss()
                close()
                state.update {
                    it.copy(busy = false, pendingConflict = null, transferProgress = null, transferMountGeneration = null)
                }
            }
        }
    }

    private suspend fun complete(
        result: VaultBatchResult,
        sourceVault: VaultId?,
        mountGeneration: Long,
        move: Boolean,
        trash: Boolean,
        destinationPath: String?
    ) {
        val completed = result.completed.size
        val partial = result.partial
        val processedSources = result.items.mapTo(mutableSetOf()) { it.sourceIdentity }
        val pendingRetries = partial.mapNotNull { it.exportRetry }
        state.update {
            if (it.mountGeneration != mountGeneration) return@update it
            it.copy(
                message = null,
                operationFeedback = vaultBatchFeedback(
                    result,
                    when {
                        trash -> OnlyFilesOperationKind.TRASH
                        move -> OnlyFilesOperationKind.MOVE_OUT
                        else -> OnlyFilesOperationKind.EXPORT
                    },
                    mountGeneration,
                    when {
                        trash -> OnlyFilesOperationDestination.MainTrash
                        destinationPath?.startsWith('/') == true -> OnlyFilesOperationDestination.LocalDirectory(destinationPath)
                        else -> null
                    }
                ),
                exportRetries = if (trash || it.selectedVaultId != sourceVault || sourceVault == null) it.exportRetries else {
                    it.exportRetries.filterNot { retry -> retry.source.backendIdentity in processedSources } + pendingRetries
                }
            )
        }
        val removedFolders = result.items.flatMap { it.removedFolders }.toSet()
        if (sourceVault != null && removedFolders.isNotEmpty()) onFoldersDeleted(sourceVault, removedFolders)
        if (state.value.mountGeneration == mountGeneration && move && completed > 0) reload()
    }

    fun cancelSelection() {
        reservation?.close()
        reservation = null
        preparedMount = null
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
