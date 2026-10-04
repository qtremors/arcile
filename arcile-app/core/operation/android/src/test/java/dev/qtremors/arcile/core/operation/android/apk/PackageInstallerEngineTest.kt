package dev.qtremors.arcile.core.operation.android.apk

import android.content.pm.PackageInstaller
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageInstallerEngineTest {
    @After
    fun cleanup() = PackageInstallerEngine.resetState()

    @Test
    fun `session activity leaves confirmation and waits for terminal result`() {
        PackageInstallerEngine.onUserConfirmationRequested(42)
        assertTrue((PackageInstallerEngine.installState.value as ApkInstallState.Installing).awaitingUserConfirmation)

        PackageInstallerEngine.onSessionActivity(7)
        assertTrue((PackageInstallerEngine.installState.value as ApkInstallState.Installing).awaitingUserConfirmation)
        PackageInstallerEngine.onSessionActivity(42)
        assertFalse((PackageInstallerEngine.installState.value as ApkInstallState.Installing).awaitingUserConfirmation)
        assertEquals("Installing...", (PackageInstallerEngine.installState.value as ApkInstallState.Installing).currentFile)

        PackageInstallerEngine.onInstallationResult(42, PackageInstaller.STATUS_SUCCESS, null, "dev.test")
        PackageInstallerEngine.onSessionActivity(42)
        assertEquals(ApkInstallState.Success("dev.test"), PackageInstallerEngine.installState.value)
    }

    @Test
    fun `recreated same target retains pending install but new target resets it`() {
        PackageInstallerEngine.prepareTarget("one.apk")
        PackageInstallerEngine.onUserConfirmationRequested(42)
        val waiting = PackageInstallerEngine.installState.value

        PackageInstallerEngine.prepareTarget("one.apk")
        assertEquals(waiting, PackageInstallerEngine.installState.value)
        PackageInstallerEngine.prepareTarget("two.apk")
        assertEquals(ApkInstallState.Idle, PackageInstallerEngine.installState.value)
        PackageInstallerEngine.onSessionActivity(42)
        assertEquals(ApkInstallState.Idle, PackageInstallerEngine.installState.value)
    }

    @Test
    fun `cancelled confirmation reports cancellation`() {
        PackageInstallerEngine.onUserConfirmationRequested(42)
        PackageInstallerEngine.onInstallationResult(42, PackageInstaller.STATUS_FAILURE_ABORTED, null, null)
        assertEquals(ApkInstallState.Failed("Installation was cancelled."), PackageInstallerEngine.installState.value)
    }
}
