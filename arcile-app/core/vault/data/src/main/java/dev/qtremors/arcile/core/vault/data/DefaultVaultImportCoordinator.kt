package dev.qtremors.arcile.core.vault.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultImportCoordinator
import dev.qtremors.arcile.core.vault.domain.VaultImportEvent
import dev.qtremors.arcile.core.vault.domain.VaultImportProgress
import dev.qtremors.arcile.core.vault.domain.VaultImportReservation
import dev.qtremors.arcile.core.vault.domain.VaultImportState
import dev.qtremors.arcile.core.vault.domain.VaultPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
internal class DefaultVaultImportCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val repository: DefaultVaultRepository
) : VaultImportCoordinator {
    private data class ImportRequest(val token: String, val recoveryId: String?)

    private val requests = ConcurrentHashMap<String, ImportRequest>()
    private val _activeImports = MutableStateFlow<Map<VaultId, VaultImportState>>(emptyMap())
    override val activeImports: StateFlow<Map<VaultId, VaultImportState>> = _activeImports.asStateFlow()
    private val _events = MutableSharedFlow<VaultImportEvent>(extraBufferCapacity = 32)
    override val events: Flow<VaultImportEvent> = _events.asSharedFlow()

    override fun prepareImport(
        vaultId: VaultId,
        destination: VaultPath
    ): Result<VaultImportReservation> {
        if (_activeImports.value.isNotEmpty()) {
            return Result.failure(IllegalStateException("Another import is already running"))
        }
        val token = repository.reserveImport(vaultId)
            ?: return Result.failure(IllegalStateException("Unlock the vault before importing"))
        return Result.success(ImportReservation(vaultId, destination, token, repository))
    }

    override fun startImport(reservation: VaultImportReservation, sourceUris: List<String>): Boolean =
        start(reservation, sourceUris, move = false)

    override fun startMoveImport(reservation: VaultImportReservation, localPaths: List<String>): Boolean =
        start(reservation, localPaths, move = true)

    internal fun startRecoveredImport(vault: VaultId, queueId: String, destination: DirectoryId): Boolean {
        val prepared = prepareImport(vault, VaultPath.Root).getOrThrow()
        return start(prepared, emptyList(), move = false, recoveryId = queueId, recoveredDestination = destination)
    }

    @Synchronized
    private fun start(
        reservation: VaultImportReservation,
        sourceUris: List<String>,
        move: Boolean,
        recoveryId: String? = null,
        recoveredDestination: DirectoryId? = null
    ): Boolean {
        val prepared = reservation as? ImportReservation ?: run {
            reservation.close()
            return false
        }
        if ((sourceUris.isEmpty() && recoveryId == null) || _activeImports.value.isNotEmpty()) {
            prepared.close()
            return false
        }
        val token = prepared.consume() ?: return false
        val vaultId = prepared.vaultId
        val destination = prepared.destination
        requests[vaultId.value] = ImportRequest(token, recoveryId)
        _activeImports.update { it + (vaultId to VaultImportState(vaultId)) }
        _events.tryEmit(VaultImportEvent.Started(vaultId, recoveredDestination))

        val intent = Intent(context, VaultImportService::class.java).apply {
            action = VaultImportService.ACTION_START
            putExtra(VaultImportService.EXTRA_VAULT_ID, vaultId.value)
            putExtra(VaultImportService.EXTRA_DESTINATION, destination.value)
            putExtra(VaultImportService.EXTRA_RESERVATION_TOKEN, token)
            putStringArrayListExtra(VaultImportService.EXTRA_SOURCE_URIS, ArrayList(sourceUris))
            putExtra(VaultImportService.EXTRA_MOVE, move)
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (error: Throwable) {
            requests.remove(vaultId.value)
            repository.releaseImportReservation(token)
            _activeImports.update { it - vaultId }
            _events.tryEmit(VaultImportEvent.Failed(vaultId, error.message ?: "Unable to start import"))
            false
        }
    }

    private class ImportReservation(
        override val vaultId: VaultId,
        override val destination: VaultPath,
        private val token: String,
        private val repository: DefaultVaultRepository
    ) : VaultImportReservation {
        private val closed = AtomicBoolean(false)
        override val isClosed: Boolean get() = closed.get()

        fun consume(): String? = token.takeIf { closed.compareAndSet(false, true) }

        override fun close() {
            if (closed.compareAndSet(false, true)) repository.releaseImportReservation(token)
        }
    }

    override fun cancelImport(vaultId: VaultId) {
        if (!_activeImports.value.containsKey(vaultId)) return
        _activeImports.update { current ->
            current[vaultId]?.let { current + (vaultId to it.copy(isCancelling = true)) } ?: current
        }
        val intent = Intent(context, VaultImportService::class.java).apply {
            action = VaultImportService.ACTION_CANCEL
            putExtra(VaultImportService.EXTRA_VAULT_ID, vaultId.value)
        }
        context.startService(intent)
    }

    internal suspend fun execute(
        vaultId: VaultId,
        destination: VaultPath,
        sourceUris: List<String>,
        token: String,
        move: Boolean,
        onNotificationProgress: (VaultImportProgress) -> Unit
    ) {
        val request = requests[vaultId.value]?.takeIf { it.token == token } ?: return
        val result = try {
            repository.importUris(token, destination, sourceUris, move, request.recoveryId) {
                completedItems, totalItems, copiedBytes, totalBytes, currentName ->
                if (requests[vaultId.value] !== request) return@importUris
                val progress = VaultImportProgress(
                    completedItems = completedItems,
                    totalItems = totalItems,
                    bytesProcessed = copiedBytes,
                    totalBytes = totalBytes,
                    currentName = currentName
                )
                _activeImports.update { current ->
                    val active = current[vaultId]
                    if (active == null || requests[vaultId.value] !== request) current
                    else current + (vaultId to active.copy(progress = progress))
                }
                _events.tryEmit(VaultImportEvent.Progress(vaultId, progress))
                onNotificationProgress(progress)
            }
        } catch (error: CancellationException) {
            Result.failure(error)
        } finally {
            repository.releaseImportReservation(token)
        }
        if (!requests.remove(vaultId.value, request)) return
        _activeImports.update { it - vaultId }
        result.fold(
            onSuccess = { batch ->
                if (batch.items.all { it.outcome == dev.qtremors.arcile.core.vault.domain.VaultItemOutcome.COMPLETED }) {
                    _events.tryEmit(VaultImportEvent.Completed(vaultId))
                } else {
                    _events.tryEmit(VaultImportEvent.Partial(vaultId, batch))
                }
            },
            onFailure = { error ->
                if (error is CancellationException) {
                    _events.tryEmit(VaultImportEvent.Cancelled(vaultId))
                } else {
                    _events.tryEmit(VaultImportEvent.Failed(vaultId, error.message ?: "Import failed"))
                }
            }
        )
    }

    internal fun onServiceTimeout(vaultIds: List<VaultId>, message: String) {
        vaultIds.forEach { vaultId ->
            requests.remove(vaultId.value)?.token?.let(repository::releaseImportReservation)
            _activeImports.update { it - vaultId }
            _events.tryEmit(VaultImportEvent.Failed(vaultId, message))
        }
    }
}
