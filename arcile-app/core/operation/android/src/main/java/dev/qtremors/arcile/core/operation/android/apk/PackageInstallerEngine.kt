package dev.qtremors.arcile.core.operation.android.apk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

/* ============================================================================
 * PACKAGE INSTALLER ENGINE
 * ============================================================================
 * Coordinates installation of single and split APK packages using Android's
 * PackageInstaller API.
 */

sealed interface ApkInstallState {
    data object Idle : ApkInstallState
    data object UnknownAppSourcesPermissionRequired : ApkInstallState
    data class Installing(
        val progress: Float = 0f,
        val currentFile: String = "",
        val awaitingUserConfirmation: Boolean = false
    ) : ApkInstallState
    data class Success(val packageName: String) : ApkInstallState
    data class Failed(val reason: String) : ApkInstallState
}

object PackageInstallerEngine {

    const val ACTION_APK_INSTALL_STATUS = "dev.qtremors.arcile.apk.ACTION_INSTALL_STATUS"
    const val EXTRA_SESSION_ID = "extra_session_id"

    private val _installState = MutableStateFlow<ApkInstallState>(ApkInstallState.Idle)
    val installState: StateFlow<ApkInstallState> = _installState.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSessionIds = ConcurrentHashMap.newKeySet<Int>()
    private val activeInstallJob = AtomicReference<Job?>(null)
    private val sessionStatuses = ConcurrentHashMap<Int, String>()
    private var observedInstaller: PackageInstaller? = null
    private var targetKey: String? = null
    private var preparedDetails: ApkPackageDetails? = null

    @Synchronized
    fun preparedDetails(key: String): ApkPackageDetails? = preparedDetails.takeIf { targetKey == key }

    @Synchronized
    fun rememberPreparedDetails(key: String, details: ApkPackageDetails?) {
        if (targetKey == key) preparedDetails = details
    }

    @Synchronized
    fun prepareTarget(key: String) {
        if (targetKey == key) return
        resetState()
        targetKey = key
    }

    @Synchronized
    private fun observeSessions(context: Context) {
        if (observedInstaller != null) return
        val installer = context.applicationContext.packageManager.packageInstaller
        installer.registerSessionCallback(object : PackageInstaller.SessionCallback() {
            override fun onCreated(sessionId: Int) = Unit
            override fun onBadgingChanged(sessionId: Int) = Unit
            override fun onActiveChanged(sessionId: Int, active: Boolean) {
                if (active && installer.getSessionInfo(sessionId)?.isActive == true) {
                    onSessionActivity(sessionId)
                }
            }
            override fun onProgressChanged(sessionId: Int, progress: Float) {
                // Re-read activity so queued staging progress cannot dismiss confirmation.
                if (installer.getSessionInfo(sessionId)?.isActive == true) {
                    onSessionActivity(sessionId)
                }
            }
            override fun onFinished(sessionId: Int, success: Boolean) {
                if (sessionId !in activeSessionIds) return
                _installState.value = ApkInstallState.Installing(
                    progress = 0.99f,
                    currentFile = "Finishing installation..."
                )
            }
        }, Handler(Looper.getMainLooper()))
        observedInstaller = installer
    }

    internal fun onSessionActivity(sessionId: Int) {
        if (sessionId !in activeSessionIds) return
        val current = _installState.value as? ApkInstallState.Installing ?: return
        if (!current.awaitingUserConfirmation) return
        _installState.value = current.copy(
            currentFile = sessionStatuses[sessionId] ?: "Installing...",
            awaitingUserConfirmation = false
        )
    }

    fun reconcileSession(context: Context) {
        observeSessions(context)
        activeSessionIds.forEach { sessionId ->
            if (observedInstaller?.getSessionInfo(sessionId)?.isActive == true) {
                onSessionActivity(sessionId)
            }
        }
    }

    fun resetState() {
        activeInstallJob.getAndSet(null)?.cancel()
        activeSessionIds.clear()
        sessionStatuses.clear()
        targetKey = null
        preparedDetails = null
        _installState.value = ApkInstallState.Idle
    }

    fun canRequestPackageInstalls(context: Context): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun installPackage(context: Context, details: ApkPackageDetails) {
        if (!canRequestPackageInstalls(context)) {
            _installState.value = ApkInstallState.UnknownAppSourcesPermissionRequired
            return
        }

        val initialStatus = when {
            details.isUpdate -> "Preparing update..."
            details.isDowngrade -> "Preparing downgrade..."
            details.isSameVersion -> "Preparing reinstall..."
            else -> "Preparing..."
        }
        val activeStatus = when {
            details.isUpdate -> "Updating..."
            details.isDowngrade -> "Downgrading..."
            details.isSameVersion -> "Reinstalling..."
            else -> "Installing..."
        }

        _installState.value = ApkInstallState.Installing(
            progress = 0.1f,
            currentFile = initialStatus
        )

        val appContext = context.applicationContext
        observeSessions(appContext)
        lateinit var installJob: Job
        installJob = engineScope.launch(start = CoroutineStart.LAZY) {
            var createdSessionId = -1
            var openedSession: PackageInstaller.Session? = null
            try {
                val packageInstaller = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)

                if (details.packageName.isNotBlank()) {
                    params.setAppPackageName(details.packageName)
                }

                require(details.apkPaths.isNotEmpty()) { "No APK files were selected" }
                val sessionId = packageInstaller.createSession(params)
                createdSessionId = sessionId
                activeSessionIds.add(sessionId)
                sessionStatuses[sessionId] = activeStatus
                val session = packageInstaller.openSession(sessionId)
                openedSession = session

                val totalFiles = details.apkPaths.size
                var processedFiles = 0

                for (path in details.apkPaths) {
                    val file = File(path)
                    require(file.isFile) { "APK file is no longer available: ${file.name}" }

                    val fileLength = file.length().coerceAtLeast(1)
                    var bytesCopied = 0L

                    session.openWrite(file.name, 0, file.length()).use { output ->
                        file.inputStream().use { input ->
                            val buffer = ByteArray(64 * 1024)
                            var read: Int
                            while (input.read(buffer).also { read = it } >= 0) {
                                output.write(buffer, 0, read)
                                bytesCopied += read
                                val fileRatio = bytesCopied.toFloat() / fileLength
                                val overallProgress = 0.1f + (0.75f * ((processedFiles + fileRatio) / totalFiles.coerceAtLeast(1)))
                                _installState.value = ApkInstallState.Installing(progress = overallProgress.coerceIn(0.1f, 0.85f), currentFile = file.name)
                            }
                        }
                    }
                    processedFiles++
                }

                _installState.value = ApkInstallState.Installing(
                    progress = 0.85f,
                    currentFile = activeStatus
                )

                val intent = Intent(context, Class.forName("dev.qtremors.arcile.apk.ApkInstallStatusReceiver")).apply {
                    action = ACTION_APK_INSTALL_STATUS
                    putExtra(EXTRA_SESSION_ID, sessionId)
                }

                val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    pendingIntentFlags
                )

                session.commit(pendingIntent.intentSender)

            } catch (e: Exception) {
                if (createdSessionId >= 0) {
                    activeSessionIds.remove(createdSessionId)
                    sessionStatuses.remove(createdSessionId)
                    runCatching {
                        context.packageManager.packageInstaller.abandonSession(createdSessionId)
                    }
                }
                if (e is CancellationException) throw e
                _installState.value = ApkInstallState.Failed(e.localizedMessage ?: "Failed to initiate package installation")
            } finally {
                runCatching { openedSession?.close() }
                cleanupApkStaging(appContext, details)
                activeInstallJob.compareAndSet(installJob, null)
            }
        }
        activeInstallJob.getAndSet(installJob)?.cancel()
        installJob.start()
    }

    fun onUserConfirmationRequested(sessionId: Int) {
        activeSessionIds.add(sessionId)
        _installState.value = ApkInstallState.Installing(
            progress = 0.85f,
            currentFile = "Awaiting user confirmation...",
            awaitingUserConfirmation = true
        )
    }

    fun onInstallationResult(sessionId: Int, status: Int, message: String?, packageName: String?) {
        activeSessionIds.remove(sessionId)
        sessionStatuses.remove(sessionId)
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                _installState.value = ApkInstallState.Success(packageName.orEmpty())
            }
            else -> {
                val failureMsg = formatCleanErrorMessage(status, message)
                _installState.value = ApkInstallState.Failed(failureMsg)
            }
        }
    }

    private fun formatCleanErrorMessage(status: Int, rawMessage: String?): String {
        val msg = rawMessage.orEmpty()
        return when {
            msg.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true) -> {
                "Cannot install an older version over a newer installed app."
            }
            msg.contains("INCONSISTENT_CERTIFICATES", ignoreCase = true) || msg.contains("SHARED_USER_INCOMPATIBLE", ignoreCase = true) || status == PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                "Package signature or version conflict with the installed app."
            }
            msg.contains("INSUFFICIENT_STORAGE", ignoreCase = true) || status == PackageInstaller.STATUS_FAILURE_STORAGE -> {
                "Insufficient storage space on device."
            }
            msg.contains("PARSE_FAILED", ignoreCase = true) || status == PackageInstaller.STATUS_FAILURE_INVALID -> {
                "Invalid or corrupted package file."
            }
            msg.contains("UPDATE_INCOMPATIBLE", ignoreCase = true) -> {
                "Package is incompatible with the installed app."
            }
            status == PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation was cancelled."
            status == PackageInstaller.STATUS_FAILURE_BLOCKED -> "Installation blocked by security settings."
            status == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "App is incompatible with this device."
            else -> "Installation failed. Please verify the package and try again."
        }
    }
}
