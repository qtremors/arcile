package dev.qtremors.arcile.presentation.utils

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalFileAccessHelperTest {

    @Test
    fun `clearStagingArea removes staged open and share cache files`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val root = File(context.cacheDir, "external_access")
        File(root, "open").mkdirs()
        File(root, "share").mkdirs()
        File(root, "open/test.txt").writeText("open")
        File(root, "share/test.txt").writeText("share")

        val before = ExternalFileAccessHelper.getStagingCacheStats(context)
        assertEquals(2, before.fileCount)
        assertTrue(before.sizeBytes > 0L)

        val after = ExternalFileAccessHelper.clearStagingArea(context)

        assertEquals(0, after.fileCount)
        assertEquals(0L, after.sizeBytes)
        assertEquals(0, ExternalFileAccessHelper.getStagingCacheStats(context).fileCount)
    }

    @Test
    fun `createShareTargets rejects oversized staged handoff files`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val oversized = File(context.cacheDir, "oversized-share.bin")
        RandomAccessFile(oversized, "rw").use { it.setLength(257L * 1024L * 1024L) }

        try {
            ExternalFileAccessHelper.createShareTargets(context, listOf(oversized.absolutePath))
            fail("Expected oversized share handoff to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("too large"))
        }
    }
}
