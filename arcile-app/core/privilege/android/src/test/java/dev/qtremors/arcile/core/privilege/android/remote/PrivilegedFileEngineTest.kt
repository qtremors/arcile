package dev.qtremors.arcile.core.privilege.android.remote

import android.content.Context
import android.os.ParcelFileDescriptor
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.RandomAccessFile
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeFalse
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

    @Test
    fun `directory listing preserves Unicode filenames`() {
        val names = listOf("résumé-雪.txt", "данные.txt")
        names.forEach { name -> File(testRoot, name).writeText(name) }

        val listedNames = engine.listDirectory(testRoot.path, "", 250)
            .entries
            .map(RemoteFileEntry::displayName)

        assertTrue(listedNames.containsAll(names))
    }

    @Test
    fun `directory listing preserves newline filenames where the host supports them`() {
        assumeFalse(isWindowsHost())
        val name = "line\nbreak.txt"
        File(testRoot, name).writeText(name)

        val listedNames = engine.listDirectory(testRoot.path, "", 250)
            .entries
            .map(RemoteFileEntry::displayName)

        assertTrue(name in listedNames)
    }

    @Test
    fun `read descriptor supports random access`() {
        val source = File(testRoot, "random.bin").apply { writeText("0123456789") }

        val descriptor = engine.openForReading(source.path)
        val bytes = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            input.channel.position(4)
            ByteArray(3).also { assertEquals(3, input.read(it)) }
        }

        assertEquals("456", bytes.decodeToString())
    }

    @Test
    fun `sparse files larger than four gigabytes preserve size and random access`() {
        val markerOffset = 4L * 1024 * 1024 * 1024 + 17
        val source = File(testRoot, "larger-than-4gb.bin")
        RandomAccessFile(source, "rw").use { file ->
            file.seek(markerOffset)
            file.write(byteArrayOf(0x41, 0x42, 0x43))
        }

        val entry = engine.canonicalizeAndLstat(source.path)
        val descriptor = engine.openForReading(source.path)
        val marker = ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
            input.channel.position(markerOffset)
            ByteArray(3).also { assertEquals(3, input.read(it)) }
        }

        assertEquals(markerOffset + 3, entry.size)
        assertEquals("ABC", marker.decodeToString())
    }

    @Test
    fun `move supports destinations in another directory`() {
        assumeFalse(isWindowsHost())
        val sourceDirectory = File(testRoot, "source").apply { mkdir() }
        val destinationDirectory = File(testRoot, "destination").apply { mkdir() }
        val source = File(sourceDirectory, "move.txt").apply { writeText("payload") }
        val destination = File(destinationDirectory, "moved.txt")

        engine.move(source.path, destination.path, "cross-directory-move") { }

        assertFalse(source.exists())
        assertEquals("payload", destination.readText())
    }

    @Test
    fun `copy cancellation reports interruption and leaves source intact`() {
        val source = File(testRoot, "large.bin").apply {
            outputStream().use { output ->
                repeat(4) { output.write(ByteArray(128 * 1024) { it.toByte() }) }
            }
        }
        val destination = File(testRoot, "partial.bin")
        val operationId = "cancel-copy"

        val failure = runCatching {
            engine.copy(source.path, destination.path, operationId) {
                engine.cancel(operationId)
            }
        }.exceptionOrNull()

        assertTrue(failure is RemoteFileException)
        assertEquals(
            RemoteFailureCode.OPERATION_INTERRUPTED,
            (failure as RemoteFileException).failureCode
        )
        assertTrue(source.exists())
        assertTrue(destination.length() < source.length())
    }

    @Test
    fun `lstat preserves a broken symbolic link`() {
        assumeFalse(isWindowsHost())
        val link = File(testRoot, "broken-link")
        Os.symlink(File(testRoot, "missing-target").path, link.path)

        val entry = engine.canonicalizeAndLstat(link.path)

        assertEquals("SYMBOLIC_LINK", entry.type)
        assertEquals(link.absolutePath, entry.canonicalIdentity)
    }

    private fun isWindowsHost(): Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
}
