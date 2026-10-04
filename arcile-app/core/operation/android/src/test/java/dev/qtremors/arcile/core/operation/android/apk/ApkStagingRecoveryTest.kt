package dev.qtremors.arcile.core.operation.android.apk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApkStagingRecoveryTest {
    @Test
    fun `startup preserves live staging and cleans abandoned owners and legacy staging`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val live = File(context.cacheDir, "${APK_STAGING_PREFIX}42-100_live").apply { mkdirs() }
        val dead = File(context.cacheDir, "${APK_STAGING_PREFIX}43-200_dead").apply { mkdirs() }
        val legacy = File(context.cacheDir, "${APK_STAGING_PREFIX}legacy").apply { mkdirs() }
        try {
            File(live, "base.apk").writeText("in use")
            cleanupAbandonedApkStaging(context) { it == "42-100" }
            assertTrue(File(live, "base.apk").exists())
            assertFalse(dead.exists())
            assertFalse(legacy.exists())
            cleanupAbandonedApkStaging(context) { false }
            assertFalse(live.exists())
        } finally {
            live.deleteRecursively()
            dead.deleteRecursively()
            legacy.deleteRecursively()
        }
    }
}
