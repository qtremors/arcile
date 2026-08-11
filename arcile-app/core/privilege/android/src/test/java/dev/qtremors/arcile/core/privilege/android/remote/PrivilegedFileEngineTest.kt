package dev.qtremors.arcile.core.privilege.android.remote

import android.content.Context
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PrivilegedFileEngineTest {
    private lateinit var testRoot: File
    private val engine = PrivilegedFileEngine()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        testRoot = File(context.filesDir, "privileged-engine-${UUID.randomUUID()}")
        assertTrue(testRoot.mkdir())
    }

    @After
    fun tearDown() {
        testRoot.walkBottomUp().forEach { file -> runCatching { file.delete() } }
    }

    @Test
    fun `directory listing never exceeds binder page limit`() {
        repeat(267) { index -> File(testRoot, "item-$index").writeText(index.toString()) }

        val first = engine.listDirectory(testRoot.path, pageToken = "", pageSize = 10_000)
        val second = engine.listDirectory(
            testRoot.path,
            pageToken = requireNotNull(first.nextPageToken),
            pageSize = 250
        )

        assertEquals(250, first.entries.size)
        assertEquals(17, second.entries.size)
        assertNotNull(first.nextPageToken)
        assertEquals(null, second.nextPageToken)
    }

    @Test
    fun `recursive delete removes a symbolic link without following it`() {
        val outside = File(testRoot.parentFile, "outside-${UUID.randomUUID()}").apply {
            mkdir()
            resolve("keep.txt").writeText("keep")
        }
        val selected = File(testRoot, "selected").apply { mkdir() }
        Os.symlink(outside.path, File(selected, "outside-link").path)

        engine.deleteRecursively(selected.path, "delete-test") { }

        assertFalse(selected.exists())
        assertTrue(File(outside, "keep.txt").exists())
        File(outside, "keep.txt").delete()
        outside.delete()
    }

    @Test
    fun `path traversal is rejected before filesystem access`() {
        val failure = runCatching {
            engine.canonicalizeAndLstat("${testRoot.path}/../escape")
        }.exceptionOrNull()

        assertTrue(failure is RemoteFileException)
        assertEquals(RemoteFailureCode.INVALID_PATH, (failure as RemoteFileException).failureCode)
    }
}
