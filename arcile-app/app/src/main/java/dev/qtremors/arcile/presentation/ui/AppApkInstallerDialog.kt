package dev.qtremors.arcile.presentation.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.qtremors.arcile.core.operation.android.apk.ApkInstallState
import dev.qtremors.arcile.core.operation.android.apk.ApkPackageDetails
import dev.qtremors.arcile.core.operation.android.apk.ApkPackageParser
import dev.qtremors.arcile.core.operation.android.apk.PackageInstallerEngine
import dev.qtremors.arcile.presentation.ui.apk.ApkInstallerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/* ============================================================================
 * APP APK INSTALLER DIALOG COMPOSABLE HOST
 * ============================================================================
 * Connects the App File Resolution to the ApkInstallerDialog and PackageInstallerEngine.
 */

@Composable
internal fun AppApkInstallerDialog(
    target: AppFileOpenResolution.InstallApk?,
    onDismiss: () -> Unit,
    cleanupWhenDisposed: Boolean = false
) {
    if (target == null) return

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val installState by PackageInstallerEngine.installState.collectAsState()
    var details by remember(target) { mutableStateOf<ApkPackageDetails?>(null) }
    val currentDetails by rememberUpdatedState(details)
    val currentInstallState by rememberUpdatedState(installState)
    val targetKey = "${target.path}|${target.contentUri}|${target.splitPaths}"

    LaunchedEffect(target) {
        PackageInstallerEngine.prepareTarget(targetKey)
        var pending: ApkPackageDetails? = null
        try {
            details = PackageInstallerEngine.preparedDetails(targetKey) ?: withContext(Dispatchers.IO) {
                val contentUri = target.contentUri
                val parsed = if (contentUri != null) {
                    ApkPackageParser.parseContentUri(
                        context = context,
                        contentUri = contentUri,
                        displayName = target.displayName ?: target.path.substringAfterLast('/')
                    )
                } else ApkPackageParser.parse(context, target.path, target.splitPaths)
                parsed.also { pending = it }
            }
            PackageInstallerEngine.rememberPreparedDetails(targetKey, details)
            pending = null
        } finally {
            ApkPackageParser.cleanupStaging(context, pending)
        }
    }

    if (cleanupWhenDisposed) DisposableEffect(targetKey) {
        onDispose {
            val released = PackageInstallerEngine.releaseTarget(targetKey)
            ApkPackageParser.cleanupStaging(context, released ?: currentDetails)
        }
    }

    // Auto-detect permission grant when returning from Android Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                PackageInstallerEngine.reconcileSession(context)
                if (PackageInstallerEngine.canRequestPackageInstalls(context)) {
                    if (currentInstallState is ApkInstallState.UnknownAppSourcesPermissionRequired) {
                        currentDetails?.let { pkg ->
                            PackageInstallerEngine.installPackage(context, pkg)
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val handleDismiss = {
        if (!cleanupWhenDisposed) {
            PackageInstallerEngine.resetState()
            ApkPackageParser.cleanupStaging(context, details)
        }
        onDismiss()
    }

    ApkInstallerDialog(
        details = details,
        installState = installState,
        isSelfUpdate = details?.packageName == context.packageName,
        onInstall = {
            details?.let { pkg ->
                PackageInstallerEngine.installPackage(context, pkg)
                if (!PackageInstallerEngine.canRequestPackageInstalls(context)) {
                    context.openUnknownAppSourcesSettings()
                }
            }
        },
        onGrantPermission = context::openUnknownAppSourcesSettings,
        onOpenApp = { packageName ->
            val targetPackage = packageName.ifBlank { details?.packageName.orEmpty() }
            if (targetPackage.isNotBlank()) {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
                    ?: context.packageManager.getLeanbackLaunchIntentForPackage(targetPackage)
                    ?: Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_LAUNCHER)
                        `package` = targetPackage
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                try {
                    context.startActivity(launchIntent)
                } catch (_: Exception) {
                    val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", targetPackage, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(settingsIntent)
                }
            }
            handleDismiss()
        },
        onDismiss = handleDismiss
    )
}

private fun android.content.Context.openUnknownAppSourcesSettings() {
    val appSources = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
        data = "package:$packageName".toUri()
    }
    runCatching { startActivity(appSources) }.recoverCatching {
        startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
    }
}
