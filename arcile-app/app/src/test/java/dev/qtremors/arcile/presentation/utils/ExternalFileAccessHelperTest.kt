package dev.qtremors.arcile.presentation.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import java.io.File
import java.io.RandomAccessFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ExternalFileAccessHelperTest {

    private fun configureExternalStorageRoot(): File {
        val root = File("build/tmp/arcile-open-with-external").absoluteFile.apply { mkdirs() }
        ShadowEnvironment.setExternalStorageDirectory(root.toPath())
        return root
    }

    private fun installDirectOpenUriFactory() {
        ExternalFileAccessHelper.directOpenUriFactory = { context, file ->
            Uri.parse("content://${context.packageName}.fileprovider/external/${file.name}")
        }
    }

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
        configureExternalStorageRoot()
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

    @Test
    fun `createOpenIntent uses direct user file uri without staging cache copy`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureExternalStorageRoot()
        installDirectOpenUriFactory()
        ExternalFileAccessHelper.clearStagingArea(context)
        val source = File(Environment.getExternalStorageDirectory(), "open-direct.txt").apply {
            parentFile?.mkdirs()
            writeText("direct")
        }

        val intent = try {
            ExternalFileAccessHelper.createOpenIntent(context, source.absolutePath)
        } finally {
            ExternalFileAccessHelper.resetDirectOpenUriFactoryForTest()
        }

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.data.toString().contains("open-direct.txt"))
        assertEquals(0, File(context.cacheDir, "external_access/open").walkTopDown().filter { it.isFile }.count())
    }

    @Test
    fun `createOpenIntent allows oversized direct handoff without staging cache copy`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        configureExternalStorageRoot()
        installDirectOpenUriFactory()
        ExternalFileAccessHelper.clearStagingArea(context)
        val source = File(Environment.getExternalStorageDirectory(), "large-open.bin").apply {
            parentFile?.mkdirs()
        }
        RandomAccessFile(source, "rw").use { it.setLength(300L * 1024L * 1024L) }

        val intent = try {
            ExternalFileAccessHelper.createOpenIntent(context, source.absolutePath)
        } finally {
            ExternalFileAccessHelper.resetDirectOpenUriFactoryForTest()
        }

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(0, File(context.cacheDir, "external_access/open").walkTopDown().filter { it.isFile }.count())
    }

    @Test
    fun `createOpenIntent rejects sensitive cache paths`() = runTest {
        configureExternalStorageRoot()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val source = File(context.cacheDir, "sensitive-open.txt").apply { writeText("nope") }

        try {
            ExternalFileAccessHelper.createOpenIntent(context, source.absolutePath)
            fail("Expected sensitive path to be rejected")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("Unsupported file path"))
        }
    }
}
