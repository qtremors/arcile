package dev.qtremors.arcile.core.vault.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class VaultSecurityPreferencesTest {
    @Test
    fun `screenshot protection defaults on and persists an explicit change`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        File(context.filesDir, "datastore/onlyfiles_security.preferences_pb").delete()
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val scope = CoroutineScope(SupervisorJob() + dispatcher)
        val preferences = DefaultVaultSecurityPreferences(context, scope)

        runCurrent()
        assertTrue(preferences.settings.value.screenshotProtectionEnabled)
        preferences.setScreenshotProtectionEnabled(false)
        runCurrent()
        assertFalse(preferences.settings.value.screenshotProtectionEnabled)

        scope.cancel()
    }
}
