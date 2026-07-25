package dev.qtremors.arcile.apk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import dev.qtremors.arcile.core.operation.android.apk.PackageInstallerEngine

/* ============================================================================
 * APK INSTALL STATUS RECEIVER
 * ============================================================================
 * Receives PackageInstaller session completion broadcasts and handles user confirmation
 * intents or forwards result status to the PackageInstallerEngine state flow.
 */

class ApkInstallStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        if (intent.action != PackageInstallerEngine.ACTION_APK_INSTALL_STATUS) return
        val sessionId = intent.getIntExtra(PackageInstallerEngine.EXTRA_SESSION_ID, -1)
        if (sessionId < 0) return

        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirmIntent?.let {
                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(it)
            }
            PackageInstallerEngine.onUserConfirmationRequested(sessionId)
            return
        }

        PackageInstallerEngine.onInstallationResult(sessionId, status, message, packageName)
    }
}
