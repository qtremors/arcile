package dev.qtremors.arcile.core.vault.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportProgress
import dev.qtremors.arcile.core.vault.domain.VaultPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

@AndroidEntryPoint
class VaultImportService : Service() {
    @Inject internal lateinit var coordinator: DefaultVaultImportCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                intent.getStringExtra(EXTRA_VAULT_ID)?.let { jobs[it]?.cancel(CancellationException("Import cancelled")) }
                return START_NOT_STICKY
            }
            ACTION_START -> startImport(intent, startId)
        }
        return START_NOT_STICKY
    }

    private fun startImport(intent: Intent, startId: Int) {
        val vaultId = VaultId.of(intent.getStringExtra(EXTRA_VAULT_ID) ?: return)
        val destination = VaultPath.of(intent.getStringExtra(EXTRA_DESTINATION).orEmpty())
        val token = intent.getStringExtra(EXTRA_RESERVATION_TOKEN) ?: return
        val sourceUris = intent.getStringArrayListExtra(EXTRA_SOURCE_URIS)?.toList().orEmpty()
        val notificationId = notificationId(vaultId)
        startForeground(notificationId, buildNotification(vaultId, null))
        jobs[vaultId.value] = serviceScope.launch {
            try {
                coordinator.execute(vaultId, destination, sourceUris, token) { progress ->
                    getSystemService(NotificationManager::class.java).notify(
                        notificationId,
                        buildNotification(vaultId, progress)
                    )
                }
            } finally {
                jobs.remove(vaultId.value)
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
    }

    private fun buildNotification(vaultId: VaultId, progress: VaultImportProgress?): Notification {
        ensureChannel()
        val cancelIntent = Intent(this, VaultImportService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_VAULT_ID, vaultId.value)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            vaultId.value.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(dev.qtremors.arcile.core.vault.data.R.drawable.ic_onlyfiles_notification)
            .setContentTitle(getString(R.string.onlyfiles_import_notification_title))
            .setContentText(
                progress?.let {
                    getString(R.string.onlyfiles_import_notification_progress, it.completedItems, it.totalItems)
                } ?: getString(R.string.onlyfiles_import_notification_preparing)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(
                progress?.totalItems ?: 0,
                progress?.completedItems ?: 0,
                progress == null || progress.totalItems <= 0
            )
            .addAction(
                dev.qtremors.arcile.core.vault.data.R.drawable.ic_onlyfiles_notification,
                getString(R.string.notification_action_cancel),
                cancelPendingIntent
            )
            .build()
    }

    private fun ensureChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.onlyfiles_import_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "dev.qtremors.arcile.onlyfiles.IMPORT"
        const val ACTION_CANCEL = "dev.qtremors.arcile.onlyfiles.CANCEL_IMPORT"
        const val EXTRA_VAULT_ID = "vault_id"
        const val EXTRA_DESTINATION = "vault_destination"
        const val EXTRA_RESERVATION_TOKEN = "vault_import_token"
        const val EXTRA_SOURCE_URIS = "vault_source_uris"
        private const val CHANNEL_ID = "onlyfiles_imports"

        private fun notificationId(vaultId: VaultId): Int = 0x0F10 + vaultId.value.hashCode().and(0x0FFF)
    }
}
