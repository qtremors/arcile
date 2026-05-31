package dev.qtremors.arcile.presentation.operations

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
import dev.qtremors.arcile.core.operation.BulkFileOperationCoordinator
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.operation.BulkFileOperationType
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ClipboardRepository
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.StorageWorkCoordinator
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.toArcileError
import dev.qtremors.arcile.core.storage.domain.userMessage
import dev.qtremors.arcile.core.ui.asString
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
    lateinit var clipboardRepository: ClipboardRepository

    @Inject
    lateinit var trashRepository: TrashRepository

    @Inject
    lateinit var fileMutationRepository: FileMutationRepository

    @Inject
    lateinit var archiveRepository: ArchiveRepository

    @Inject
    lateinit var coordinator: BulkFileOperationCoordinator

    @Inject
    lateinit var storageWorkCoordinator: StorageWorkCoordinator

    @Inject
    lateinit var operationJournal: OperationJournal

    @Inject
    lateinit var dispatchers: ArcileDispatchers

    private val serviceScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + serviceDispatchers.io)
    }
    private val serviceDispatchers: ArcileDispatchers
        get() = if (::dispatchers.isInitialized) {
            dispatchers
        } else {
            ArcileDispatchers(
                io = Dispatchers.IO,
                default = Dispatchers.Default,
                main = Dispatchers.Main,
                storage = Dispatchers.IO
            )
        }
    private val json = Json { ignoreUnknownKeys = true }
    private var currentRequest: BulkFileOperationRequest? = null
    private var currentOperationJob: Job? = null
    private var lastNotificationUpdateAt = 0L
    private val serviceOperationJournal: OperationJournal
        get() = if (::operationJournal.isInitialized) operationJournal else NoOpOperationJournal()


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                val cancelOperationId = intent.getStringExtra(EXTRA_OPERATION_ID)
                val request = currentRequest
                if (request != null && cancelOperationId == request.operationId) {
                    serviceOperationJournal.update(request.operationId) { it.copy(phase = OperationPhase.CANCELLING) }
                    coordinator.onOperationCancelling(request)
                    currentOperationJob?.cancel(CancellationException("Bulk file operation cancelled by user"))
                    stopForegroundSafely()
                    stopSelf(startId)
                }
                return START_NOT_STICKY
            }
            ACTION_START -> {
                val requestJson = intent.getStringExtra(EXTRA_REQUEST_JSON) ?: return START_NOT_STICKY
                val request = json.decodeFromString<BulkFileOperationRequest>(requestJson)
                currentRequest = request
                serviceOperationJournal.upsert(request.toJournalRecord(OperationPhase.RUNNING))
                startForeground(NOTIFICATION_ID, buildNotification(request))
                storageWorkCoordinator.beginMutation()
                val capturedStartId = startId
                currentOperationJob = serviceScope.launch {
                    try {
                        val result = when (request.type) {
                            BulkFileOperationType.COPY -> clipboardRepository.copyFiles(
                                request.sourcePaths,
                                requireNotNull(request.destinationPath) { "Destination path is required for copy" },
                                request.resolutions
                            ) { progress ->
                                coordinator.onOperationProgress(request, progress)
                                updateNotification(request, progress)
                            }
                            BulkFileOperationType.MOVE -> clipboardRepository.moveFiles(
                                request.sourcePaths,
                                requireNotNull(request.destinationPath) { "Destination path is required for move" },
                                request.resolutions
                            ) { progress ->
                                coordinator.onOperationProgress(request, progress)
                                updateNotification(request, progress)
                            }
                            BulkFileOperationType.TRASH -> trashRepository.moveToTrash(request.sourcePaths) { progress ->
                                coordinator.onOperationProgress(request, progress)
                                updateNotification(request, progress)
                            }
                            BulkFileOperationType.DELETE -> fileMutationRepository.deletePermanently(request.sourcePaths)
                            BulkFileOperationType.SHRED -> fileMutationRepository.shred(request.sourcePaths)
                            BulkFileOperationType.CREATE_FAKE -> fileMutationRepository.createFakeFile(
                                requireNotNull(request.destinationPath),
                                request.sourcePaths.first(),
                                requireNotNull(request.fakeFileSize)
                            ) { progress ->
                                coordinator.onOperationProgress(request, progress)
                                updateNotification(request, progress)
                            }
                            BulkFileOperationType.EXTRACT_ARCHIVE -> archiveRepository.extractArchive(
                                archivePath = request.sourcePaths.first(),
                                destinationPath = requireNotNull(request.destinationPath) { "Destination path is required for extraction" },
                                entryPrefix = request.archiveEntryPrefix,
                                password = request.archivePassword
                            ) { progress ->
                                coordinator.onOperationProgress(request, progress)
                                updateNotification(request, progress)
                            }
                            BulkFileOperationType.CREATE_ARCHIVE -> archiveRepository.createArchive(
                                sourcePaths = request.sourcePaths,
                                destinationArchivePath = requireNotNull(request.destinationPath) { "Archive path is required" },
                                format = requireNotNull(request.archiveFormat) { "Archive format is required" },
                                password = request.archivePassword
                            ) { progress ->
                                coordinator.onOperationProgress(request, progress)
                                updateNotification(request, progress)
                            }
                        }

                        result.onSuccess {
                            serviceOperationJournal.update(request.operationId) { it.copy(phase = OperationPhase.COMPLETED) }
                            coordinator.onOperationCompleted(request)
                            serviceOperationJournal.clearActive(request.operationId)
                        }.onFailure { error ->
                            if (error is CancellationException) throw error
                            serviceOperationJournal.update(request.operationId) {
                                it.copy(phase = OperationPhase.FAILED, error = error.message)
                            }
                            coordinator.onOperationFailed(
                                request,
                                error.toArcileError().userMessage.asString(this@BulkFileOperationService)
                            )
                            serviceOperationJournal.clearActive(request.operationId)
                        }
                    } catch (_: CancellationException) {
                        serviceOperationJournal.update(request.operationId) { it.copy(phase = OperationPhase.CANCELLED) }
                        coordinator.onOperationCancelled(request)
                        serviceOperationJournal.clearActive(request.operationId)
                    } finally {
                        storageWorkCoordinator.endMutation()
                        currentRequest = null
                        currentOperationJob = null
                        stopForegroundSafely()
                        stopSelf(capturedStartId)
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

    private fun updateNotification(request: BulkFileOperationRequest, progress: BulkFileOperationProgress) {
        serviceOperationJournal.update(request.operationId) {
            it.copy(phase = OperationPhase.RUNNING, progress = progress)
        }
        val now = System.currentTimeMillis()
        val finished = progress.completedItems >= progress.totalItems
        if (!finished && now - lastNotificationUpdateAt < NOTIFICATION_UPDATE_THROTTLE_MS) return
        lastNotificationUpdateAt = now
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(request, progress)
        )
    }

    private fun buildNotification(request: BulkFileOperationRequest, progress: BulkFileOperationProgress? = null): Notification {
        ensureChannel()
        val title = operationTitle(request.type)
        val content = progress?.let(::progressContent)
            ?: getString(R.string.file_operation_processing_background, request.sourcePaths.size)
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(dev.qtremors.arcile.R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(content)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                dev.qtremors.arcile.R.drawable.ic_notification,
                getString(R.string.notification_action_cancel),
                cancelPendingIntent(request)
            )

        applyProgress(builder, progress)
        return builder.build()
    }

    private fun operationTitle(type: BulkFileOperationType): String =
        when (type) {
            BulkFileOperationType.COPY -> getString(R.string.file_operation_copying_files)
            BulkFileOperationType.MOVE -> getString(R.string.file_operation_moving_files)
            BulkFileOperationType.TRASH -> getString(R.string.file_operation_moving_files_to_trash)
            BulkFileOperationType.DELETE -> getString(R.string.file_operation_deleting_files)
            BulkFileOperationType.SHRED -> getString(R.string.file_operation_shredding_files)
            BulkFileOperationType.CREATE_FAKE -> getString(R.string.file_operation_creating_fake_file)
            BulkFileOperationType.EXTRACT_ARCHIVE -> getString(R.string.file_operation_extracting_archive)
            BulkFileOperationType.CREATE_ARCHIVE -> getString(R.string.file_operation_creating_archive)
        }

    private fun progressContent(progress: BulkFileOperationProgress): String {
        val currentName = progress.currentPath
            ?.substringAfterLast('/')
            ?.takeIf { it.isNotBlank() }
        val primary = if ((progress.totalBytes ?: 0L) > 0L) {
            "${progressPercent(progress)}% • ${progress.completedItems}/${progress.totalItems} items"
        } else {
            "${progress.completedItems}/${progress.totalItems} items"
        }
        return listOfNotNull(primary, currentName).joinToString(" • ")
    }

    private fun applyProgress(
        builder: NotificationCompat.Builder,
        progress: BulkFileOperationProgress?
    ) {
        val totalBytes = progress?.totalBytes
        when {
            totalBytes != null && totalBytes > 0L -> {
                builder.setProgress(100, progressPercent(progress), false)
            }
            progress != null && progress.totalItems > 0 -> {
                builder.setProgress(progress.totalItems, progress.completedItems.coerceAtMost(progress.totalItems), false)
            }
            else -> builder.setProgress(0, 0, true)
        }
    }

    private fun progressPercent(progress: BulkFileOperationProgress): Int {
        val totalBytes = progress.totalBytes ?: return 0
        if (totalBytes <= 0L) return 0
        val copied = progress.bytesCopied ?: 0L
        return ((copied.toDouble() / totalBytes.toDouble()) * 100.0).toInt().coerceIn(0, 100)
    }

    private fun cancelPendingIntent(request: BulkFileOperationRequest): PendingIntent {
        val intent = Intent(this, BulkFileOperationService::class.java).apply {
            action = ACTION_CANCEL
            putExtra(EXTRA_OPERATION_ID, request.operationId)
        }
        return PendingIntent.getService(
            this,
            request.operationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun ensureChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_file_operations),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_file_operations_description)
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopForegroundSafely() {
        runCatching {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    companion object {
        const val ACTION_START = "dev.qtremors.arcile.action.START_BULK_FILE_OPERATION"
        const val ACTION_CANCEL = "dev.qtremors.arcile.action.CANCEL_BULK_FILE_OPERATION"
        const val EXTRA_REQUEST_JSON = "bulk_request_json"
        const val EXTRA_OPERATION_ID = "bulk_operation_id"

        private const val CHANNEL_ID = "bulk_file_operations"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_UPDATE_THROTTLE_MS = 500L
    }
}
