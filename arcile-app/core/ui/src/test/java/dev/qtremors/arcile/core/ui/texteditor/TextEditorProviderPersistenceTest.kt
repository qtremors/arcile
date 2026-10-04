package dev.qtremors.arcile.core.ui.texteditor

import android.content.Context
import android.content.ContentResolver
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.OutputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextEditorProviderPersistenceTest {
    @Test
    fun `oversized existing recovery drafts are retained when reopening is rejected`() {
        val root = java.nio.file.Files.createTempDirectory("oversized-recovery").toFile()
        try {
            val context = mockk<Context>()
            every { context.noBackupFilesDir } returns root
            every { context.cacheDir } returns File(root, "legacy")
            val reference = "content://documents/large-draft"
            val file = File(root, "text_editor_drafts/${textContentHash(reference)}.draft")
            file.parentFile.mkdirs()
            file.writeText("${textContentHash("original")}\n${"a".repeat(MAX_TEXT_BYTES + 1)}")
            val size = file.length()
            assertTrue(runCatching { readRecoveryDraft(context, reference) }.exceptionOrNull() is TextTooLargeException)
            assertTrue(file.isFile)
            assertEquals(size, file.length())
        } finally { root.deleteRecursively() }
    }
    @Test
    fun `local and unknown length provider reads enforce the actual byte budget`() {
        val root = java.nio.file.Files.createTempDirectory("bounded-editor-reads").toFile()
        val oldRoots = dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper.readOnlySystemRootsOverrideForTest
        try {
            val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<Context>()
            val file = File(root, "large.txt").apply { writeBytes(ByteArray(MAX_TEXT_BYTES + 1) { 'a'.code.toByte() }) }
            dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper.readOnlySystemRootsOverrideForTest = listOf(root.canonicalPath)
            val localRead = runCatching { readTextFileContent(context, file.toURI().toString()) }
            assertTrue("Expected size limit, got ${localRead.exceptionOrNull()}", localRead.exceptionOrNull() is TextTooLargeException)
            val resolver = mockk<ContentResolver>()
            val providerContext = mockk<Context>()
            every { providerContext.contentResolver } returns resolver
            var consumed = 0
            var closed = false
            every { resolver.openInputStream(Uri.parse("content://documents/unknown")) } answers {
                object : java.io.InputStream() {
                    override fun available() = 0
                    override fun read(): Int { consumed++; return 'a'.code }
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                        buffer.fill('a'.code.toByte(), offset, offset + length)
                        consumed += length
                        return length
                    }
                    override fun close() { closed = true }
                }
            }
            assertTrue(runCatching { readTextFileContent(providerContext, "content://documents/unknown") }.exceptionOrNull() is TextTooLargeException)
            assertEquals(MAX_TEXT_BYTES + 1, consumed)
            assertTrue(closed)
        } finally {
            dev.qtremors.arcile.core.ui.externalfile.ExternalFileAccessHelper.readOnlySystemRootsOverrideForTest = oldRoots
            root.deleteRecursively()
        }
    }
    @Test
    fun `cancelled stale draft writes retain a newer durable save snapshot`() {
        val root = java.nio.file.Files.createTempDirectory("cancelled-editor-draft").toFile()
        try {
            val context = mockk<Context>()
            every { context.noBackupFilesDir } returns root
            val reference = "content://documents/cancelled.txt"
            writeDraft(context, reference, "original", "new save snapshot")
            val oldWriter = kotlinx.coroutines.Job().also { it.cancel() }
            val result = runCatching {
                writeDraft(context, reference, "original", "old background edits") {
                    if (!oldWriter.isActive) throw kotlinx.coroutines.CancellationException()
                }
            }
            assertTrue(result.exceptionOrNull() is kotlinx.coroutines.CancellationException)
            val draft = File(root, "text_editor_drafts/${textContentHash(reference)}.draft")
            assertEquals("new save snapshot", readTextDraft(draft)?.text)
        } finally { root.deleteRecursively() }
    }
    @Test
    fun `provider truncation and short writes retain the complete durable recovery draft`() {
        val root = java.nio.file.Files.createTempDirectory("provider-editor").toFile()
        try {
            val resolver = mockk<ContentResolver>()
            val context = mockk<Context>()
            every { context.contentResolver } returns resolver
            every { context.noBackupFilesDir } returns root
            val reference = "content://documents/test.txt"
            val uri = Uri.parse(reference)
            val draftFile = File(root, "text_editor_drafts/${textContentHash(reference)}.draft")
            for (throws in listOf(false, true)) {
                var persisted = "original"
                val edited = "the complete edited document"
                every { resolver.openInputStream(uri) } answers { persisted.byteInputStream() }
                every { resolver.openOutputStream(uri, "wt") } answers {
                    assertEquals(edited, readTextDraft(draftFile)?.text)
                    persisted = ""
                    object : OutputStream() {
                        override fun write(value: Int) {
                            if (persisted.length < 3) persisted += value.toChar()
                            if (throws && persisted.length == 3) throw IOException("provider stopped")
                        }
                    }
                }
                assertTrue(writeAndVerifyTextFile(context, reference, edited).isFailure)
                val recovery = requireNotNull(readTextDraft(draftFile))
                assertEquals(edited, recovery.text)
                assertTrue(!recovery.matches(persisted))
                assertEquals(recovery, readTextDraft(draftFile))
            }
        } finally { root.deleteRecursively() }
    }
}
