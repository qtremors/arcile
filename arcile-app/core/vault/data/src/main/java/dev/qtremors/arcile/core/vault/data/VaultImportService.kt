package dev.qtremors.arcile.core.vault.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.core.ui.R
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportProgress
import dev.qtremors.arcile.core.vault.domain.VaultPath
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class VaultImportService : Service() {
    @Inject internal lateinit var coordinator: DefaultVaultImportCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val operationLock = Any()
    private val jobs = mutableMapOf<String, Job>()
    private val activeNotifications = mutableMapOf<String, Notification>()
    private var foregroundVaultId: String? = null
    private var latestStartId = 0
    internal var executeImport:
        suspend (VaultId, VaultPath, List<String>, String, (VaultImportProgress) -> Unit) -> Unit =
        { vaultId, destination, sourceUris, token, onProgress ->
            coordinator.execute(vaultId, destination, sourceUris, token, onProgress)
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        clearPersistedNotifications()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(operationLock) {
            latestStartId = startId
        }
        when (intent?.action) {
            ACTION_CANCEL -> {
                intent.getStringExtra(EXTRA_VAULT_ID)?.let { vaultIdValue ->
                    val vaultId = VaultId.of(vaultIdValue)
                    val (cancelledJob, stopStartId) = synchronized(operationLock) {
                        val job = jobs.remove(vaultIdValue)
                        removeImportNotificationLocked(vaultId)
                        job to latestStartId.takeIf { jobs.isEmpty() }
                    }
                    cancelledJob?.cancel(CancellationException("Import cancelled"))
                    stopStartId?.let(::stopSelfResult)
                }
                return START_NOT_STICKY
            }
            ACTION_START -> startImport(intent, startId)
        }
        return START_NOT_STICKY
    }

    private fun startImport(intent: Intent, startId: Int) {
        val vaultIdValue = intent.getStringExtra(EXTRA_VAULT_ID) ?: return finishRejectedStart(startId)
        val vaultId = VaultId.of(vaultIdValue)
        val destination = VaultPath.of(intent.getStringExtra(EXTRA_DESTINATION).orEmpty())
        val token = intent.getStringExtra(EXTRA_RESERVATION_TOKEN) ?: return finishRejectedStart(startId, vaultId)
        val sourceUris = intent.getStringArrayListExtra(EXTRA_SOURCE_URIS)?.toList().orEmpty()
        val notificationId = notificationId(vaultId)
        val initialNotification = buildNotification(vaultId, null)
        lateinit var importJob: Job
        importJob = serviceScope.launch(start = CoroutineStart.LAZY) {
            try {
                executeImport(vaultId, destination, sourceUris, token) { progress ->
                    updateImportNotification(vaultId, importJob, progress)
                }
            } finally {
                val stopStartId = synchronized(operationLock) {
                    if (jobs[vaultId.value] !== importJob) return@synchronized null
                    jobs.remove(vaultId.value)
                    removeImportNotificationLocked(vaultId)
                    latestStartId.takeIf { jobs.isEmpty() }
                }
                stopStartId?.let(::stopSelfResult)
            }
        }
        val previousJob = synchronized(operationLock) {
            val previous = jobs.put(vaultId.value, importJob)
            activeNotifications[vaultId.value] = initialNotification
            persistNotificationOwner(vaultId)
            foregroundVaultId = vaultId.value
            startForeground(notificationId, initialNotification)
            previous
        }
        previousJob?.cancel(CancellationException("Import replaced by a newer request"))
        importJob.start()
    }

    private fun updateImportNotification(
        vaultId: VaultId,
        ownerJob: Job,
        progress: VaultImportProgress
    ) {
        synchronized(operationLock) {
            if (jobs[vaultId.value] !== ownerJob || vaultId.value !in activeNotifications) return
            val notification = buildNotification(vaultId, progress)
            activeNotifications[vaultId.value] = notification
            getSystemService(NotificationManager::class.java).notify(
                notificationId(vaultId),
                notification
            )
        }
    }

    private fun removeImportNotificationLocked(vaultId: VaultId) {
        val wasTracked = activeNotifications.remove(vaultId.value) != null
        if (wasTracked && foregroundVaultId == vaultId.value) {
            val replacement = activeNotifications.entries.lastOrNull()
            if (replacement != null) {
                foregroundVaultId = replacement.key
                val replacementVaultId = VaultId.of(replacement.key)
                startForeground(notificationId(replacementVaultId), replacement.value)
            } else {
                foregroundVaultId = null
                runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
            }
        }
        removePersistedNotificationOwner(vaultId)
        getSystemService(NotificationManager::class.java).cancel(notificationId(vaultId))
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    override fun onTimeout(startId: Int, fgsType: Int) {
        val timedOut = synchronized(operationLock) {
            val snapshot = jobs.toMap()
            jobs.clear()
            snapshot.keys.forEach { removeImportNotificationLocked(VaultId.of(it)) }
            foregroundVaultId = null
            snapshot
        }
        if (timedOut.isNotEmpty()) {
            timedOut.values.forEach { job ->
                job.cancel(CancellationException("Vault import exceeded the foreground-service time limit"))
            }
            coordinator.onServiceTimeout(
                timedOut.keys.map(VaultId::of),
                getString(R.string.file_operation_background_time_limit)
            )
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelfResult(startId)
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
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(dev.qtremors.arcile.core.vault.data.R.drawable.ic_onlyfiles_notification)
            .setContentTitle(getString(R.string.onlyfiles_import_notification_title))
            .setContentText(
                progress?.let {
                    resources.getQuantityString(
                        R.plurals.onlyfiles_import_notification_progress,
                        it.totalItems,
                        it.completedItems,
                        it.totalItems
                    )
                } ?: getString(R.string.onlyfiles_import_notification_preparing)
            )
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setColor(BRAND_ACCENT_COLOR)
            .setProgress(
                progress?.totalItems ?: 0,
                progress?.completedItems ?: 0,
                progress == null || progress.totalItems <= 0
            )
            .addAction(
                dev.qtremors.arcile.core.vault.data.R.drawable.ic_cancel,
                getString(R.string.notification_action_cancel),
                cancelPendingIntent
            )

        contentPendingIntent()?.let { builder.setContentIntent(it) }

        return builder.build()
    }

    private fun contentPendingIntent(): PendingIntent? {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName) ?: return null
        launchIntent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        return PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
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
        val notificationIds = synchronized(operationLock) {
            jobs.clear()
            foregroundVaultId = null
            activeNotifications.keys
                .map { notificationId(VaultId.of(it)) }
                .also { activeNotifications.clear() }
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationIds.forEach(notificationManager::cancel)
        notificationPreferences().edit().remove(PREF_ACTIVE_VAULT_IDS).apply()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun finishRejectedStart(startId: Int, vaultId: VaultId? = null) {
        synchronized(operationLock) {
            vaultId?.let(::removeImportNotificationLocked)
            if (jobs.isNotEmpty()) return
        }
        runCatching { stopForeground(STOP_FOREGROUND_REMOVE) }
        stopSelfResult(startId)
    }

    private fun persistNotificationOwner(vaultId: VaultId) {
        val preferences = notificationPreferences()
        preferences.edit()
            .putStringSet(
                PREF_ACTIVE_VAULT_IDS,
                preferences.getStringSet(PREF_ACTIVE_VAULT_IDS, emptySet()).orEmpty() + vaultId.value
            )
            .apply()
    }

    private fun removePersistedNotificationOwner(vaultId: VaultId) {
        val preferences = notificationPreferences()
        val remaining = preferences.getStringSet(PREF_ACTIVE_VAULT_IDS, emptySet()).orEmpty() - vaultId.value
        preferences.edit().putStringSet(PREF_ACTIVE_VAULT_IDS, remaining).apply()
    }

    private fun clearPersistedNotifications() {
        val preferences = notificationPreferences()
        val notificationManager = getSystemService(NotificationManager::class.java)
        preferences.getStringSet(PREF_ACTIVE_VAULT_IDS, emptySet()).orEmpty().forEach { value ->
            notificationManager.cancel(notificationId(VaultId.of(value)))
        }
        preferences.edit().remove(PREF_ACTIVE_VAULT_IDS).apply()
    }

    private fun notificationPreferences() =
        getSharedPreferences(PREFS_NOTIFICATIONS, MODE_PRIVATE)

    companion object {
        const val ACTION_START = "dev.qtremors.arcile.onlyfiles.IMPORT"
        const val ACTION_CANCEL = "dev.qtremors.arcile.onlyfiles.CANCEL_IMPORT"
        const val EXTRA_VAULT_ID = "vault_id"
        const val EXTRA_DESTINATION = "vault_destination"
        const val EXTRA_RESERVATION_TOKEN = "vault_import_token"
        const val EXTRA_SOURCE_URIS = "vault_source_uris"
        private const val CHANNEL_ID = "onlyfiles_imports"
        private const val PREFS_NOTIFICATIONS = "onlyfiles_import_notifications"
        private const val PREF_ACTIVE_VAULT_IDS = "active_vault_ids"
        private const val BRAND_ACCENT_COLOR = 0xFF0878F8.toInt()

        private fun notificationId(vaultId: VaultId): Int = 0x0F10 + vaultId.value.hashCode().and(0x0FFF)
    }
}
