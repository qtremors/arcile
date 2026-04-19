package dev.qtremors.arcile.presentation.operations

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import dev.qtremors.arcile.R
import dev.qtremors.arcile.domain.FileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import javax.inject.Inject

@AndroidEntryPoint
class BulkFileOperationService : Service() {

    @Inject
    lateinit var repository: FileRepository

    @Inject
    lateinit var coordinator: BulkFileOperationCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }
    private var currentRequest: BulkFileOperationRequest? = null
    private var currentOperationJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                currentRequest?.let { coordinator.onOperationCancelling(it) }
                currentOperationJob?.cancel(CancellationException("Bulk file operation cancelled by user"))
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val requestJson = intent.getStringExtra(EXTRA_REQUEST_JSON) ?: return START_NOT_STICKY
                val request = json.decodeFromString<BulkFileOperationRequest>(requestJson)
                currentRequest = request
                startForeground(NOTIFICATION_ID, buildNotification(request))
                currentOperationJob = serviceScope.launch {
                    try {
                        val result = when (request.type) {
                            BulkFileOperationType.COPY -> repository.copyFiles(
                                request.sourcePaths,
                                request.destinationPath,
                                request.resolutions
                            ) { progress ->
                                coordinator.onOperationProgress(request, progress)
                            }
                            BulkFileOperationType.MOVE -> repository.moveFiles(
                                request.sourcePaths,
                                request.destinationPath,
                                request.resolutions
                            ) { progress ->
                                coordinator.onOperationProgress(request, progress)
                            }
                        }

                        result.onSuccess {
                            coordinator.onOperationCompleted(request)
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            coordinator.onOperationFailed(request, error.message ?: "File operation failed")
                        }
                    } catch (_: CancellationException) {
                        coordinator.onOperationCancelled(request)
                    } finally {
                        currentRequest = null
                        currentOperationJob = null
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf(startId)
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(request: BulkFileOperationRequest): Notification {
        ensureChannel()
        val title = if (request.type == BulkFileOperationType.MOVE) {
            "Moving files"
        } else {
            "Copying files"
        }
        val content = "Processing ${request.sourcePaths.size} item(s) in the background"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "File operations",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Background copy and move operations"
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "dev.qtremors.arcile.action.START_BULK_FILE_OPERATION"
        const val ACTION_CANCEL = "dev.qtremors.arcile.action.CANCEL_BULK_FILE_OPERATION"
        const val EXTRA_REQUEST_JSON = "bulk_request_json"

        private const val CHANNEL_ID = "bulk_file_operations"
        private const val NOTIFICATION_ID = 1001
    }
}
