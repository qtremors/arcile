package dev.qtremors.arcile.core.vault.data

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.vault.domain.VaultId
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
    private val tokens = ConcurrentHashMap<String, String>()
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

    override fun startImport(reservation: VaultImportReservation, sourceUris: List<String>): Boolean {
        val prepared = reservation as? ImportReservation ?: run {
            reservation.close()
            return false
        }
        if (sourceUris.isEmpty() || _activeImports.value.isNotEmpty()) {
            prepared.close()
            return false
        }
        val token = prepared.consume() ?: return false
        val vaultId = prepared.vaultId
        val destination = prepared.destination
        tokens[vaultId.value] = token
        _activeImports.update { it + (vaultId to VaultImportState(vaultId)) }
        _events.tryEmit(VaultImportEvent.Started(vaultId))

        val intent = Intent(context, VaultImportService::class.java).apply {
            action = VaultImportService.ACTION_START
            putExtra(VaultImportService.EXTRA_VAULT_ID, vaultId.value)
            putExtra(VaultImportService.EXTRA_DESTINATION, destination.value)
            putExtra(VaultImportService.EXTRA_RESERVATION_TOKEN, token)
            putStringArrayListExtra(VaultImportService.EXTRA_SOURCE_URIS, ArrayList(sourceUris))
        }
        return try {
            ContextCompat.startForegroundService(context, intent)
            true
        } catch (error: Throwable) {
            tokens.remove(vaultId.value)
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
            current + (vaultId to requireNotNull(current[vaultId]).copy(isCancelling = true))
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
        onNotificationProgress: (VaultImportProgress) -> Unit
    ) {
        val result = repository.importUris(token, destination, sourceUris) {
                completedItems, totalItems, copiedBytes, totalBytes, currentName ->
            val progress = VaultImportProgress(
                completedItems = completedItems,
                totalItems = totalItems,
                bytesCopied = copiedBytes,
                totalBytes = totalBytes,
                currentName = currentName
            )
            _activeImports.update { current ->
                current + (vaultId to VaultImportState(vaultId, progress))
            }
            _events.tryEmit(VaultImportEvent.Progress(vaultId, progress))
            onNotificationProgress(progress)
        }
        tokens.remove(vaultId.value)
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
}
