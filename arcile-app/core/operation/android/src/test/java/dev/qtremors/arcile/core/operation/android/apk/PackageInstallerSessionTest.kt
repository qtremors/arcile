package dev.qtremors.arcile.core.operation.android.apk

import android.content.Context
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PackageInstallerSessionTest {
    @After
    fun cleanup() = PackageInstallerEngine.resetState()

    @Test
    fun `session callbacks and resume reconciliation preserve truthful installation states`() {
        val callback = slot<PackageInstaller.SessionCallback>()
        var active = false
        val info = mockk<PackageInstaller.SessionInfo> {
            every { isActive } answers { active }
        }
        val installer = mockk<PackageInstaller>(relaxed = true) {
            every { registerSessionCallback(capture(callback), any()) } returns Unit
            every { getSessionInfo(42) } returns info
        }
        val manager = mockk<PackageManager> { every { packageInstaller } returns installer }
        val context = mockk<Context> {
            every { applicationContext } returns this
            every { packageManager } returns manager
        }
        PackageInstallerEngine.reconcileSession(context)
        PackageInstallerEngine.onUserConfirmationRequested(42)

        callback.captured.onProgressChanged(42, 0.8f)
        callback.captured.onActiveChanged(42, true)
        assertTrue((PackageInstallerEngine.installState.value as ApkInstallState.Installing).awaitingUserConfirmation)

        active = true
        PackageInstallerEngine.reconcileSession(context)
        assertFalse((PackageInstallerEngine.installState.value as ApkInstallState.Installing).awaitingUserConfirmation)
        callback.captured.onFinished(42, true)
        assertTrue(PackageInstallerEngine.installState.value is ApkInstallState.Installing)
        PackageInstallerEngine.onInstallationResult(42, PackageInstaller.STATUS_SUCCESS, null, "dev.test")
        callback.captured.onFinished(42, false)
        assertEquals(ApkInstallState.Success("dev.test"), PackageInstallerEngine.installState.value)
    }
}
