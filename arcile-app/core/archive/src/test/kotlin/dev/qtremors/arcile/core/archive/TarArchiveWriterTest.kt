package dev.qtremors.arcile.core.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.junit.Assert.*
import org.junit.Test

class TarArchiveWriterTest {
    @Test fun `every TAR alias and compression level round trips files empty folders and unicode names`() {
        val payload = "Private contents\n".repeat(100).encodeToByteArray()
        for (format in TarArchiveWriter.formats) for (level in ArchiveCompressionLevel.entries) {
            val output = ByteArrayOutputStream()
            TarArchiveWriter.open(output, format, level).use { writer ->
                writer.add(TarArchiveEntry("Empty/")) { error("Directories do not need a reader") }
                writer.add(TarArchiveEntry("秘密/Document.txt").apply { size = payload.size.toLong(); setModTime(1_660_000_000_000L) }) {
                    payload.inputStream()
                }
            }
            val bytes = output.toByteArray()
            val source = ArchiveReadSource("Result.${format.extension}", bytes.size.toLong(), null) { SeekableInMemoryByteChannel(bytes) }
            ArchiveReader().open(source, format).use { archive ->
                assertEquals(listOf("Empty", "秘密/Document.txt"), archive.entries.map { it.segments!!.joinToString("/") })
                assertTrue(archive.entries[0].isDirectory)
                assertArrayEquals(payload, archive.entries[1].openStream().use { it.readBytes() })
                assertEquals(1_660_000_000_000L, archive.entries[1].modifiedAt)
            }
        }
    }

    @Test fun `short and growing sources fail instead of publishing a misleading archive`() {
        for (content in listOf("short", "this source is longer")) {
            var closed = false
            val input = object : ByteArrayInputStream(content.encodeToByteArray()) {
                override fun close() { closed = true; super.close() }
            }
            val result = runCatching {
                TarArchiveWriter.open(ByteArrayOutputStream(), ArchiveFormat.TAR).use { tar ->
                    tar.add(TarArchiveEntry("Document.txt").apply { size = 10 }) { input }
                }
            }
            assertTrue(result.exceptionOrNull() is IOException)
            assertTrue(closed)
        }
    }

    @Test fun `unsafe paths and symbolic links are rejected before opening any source`() {
        val unsafe = listOf(TarArchiveEntry("../Escape.txt"), TarArchiveEntry("Link", TarConstants.LF_SYMLINK).apply { linkName = "Target" })
        for (entry in unsafe) {
            var opened = false
            assertTrue(runCatching {
                TarArchiveWriter.open(ByteArrayOutputStream(), ArchiveFormat.TAR).use { tar ->
                    tar.add(entry) { opened = true; ByteArrayInputStream(byteArrayOf()) }
                }
            }.isFailure)
            assertFalse(opened)
        }
    }

    @Test fun `operation byte and entry limits apply equally to local and protected streams`() {
        var opened = false
        assertTrue(runCatching {
            TarArchiveWriter.open(ByteArrayOutputStream(), ArchiveFormat.TAR,
                policy = ArchiveSafetyPolicy(maxUncompressedBytes = 2)).use { tar ->
                tar.add(TarArchiveEntry("Too large.txt").apply { size = 3 }) { opened = true; "big".byteInputStream() }
            }
        }.isFailure)
        assertFalse(opened)
        assertTrue(runCatching {
            TarArchiveWriter.open(ByteArrayOutputStream(), ArchiveFormat.TAR, policy = ArchiveSafetyPolicy(maxEntries = 1)).use { tar ->
                tar.add(TarArchiveEntry("One/")) { error("Directory") }
                tar.add(TarArchiveEntry("Two/")) { error("Directory") }
            }
        }.isFailure)
    }

    @Test fun `cancellation during streaming closes both the reader and output`() {
        var inputClosed = false
        var outputClosed = false
        var copied = 0
        val output = object : ByteArrayOutputStream() { override fun close() { outputClosed = true; super.close() } }
        val input = object : ByteArrayInputStream(ByteArray(100_000)) { override fun close() { inputClosed = true; super.close() } }
        val result = runCatching {
            TarArchiveWriter.open(output, ArchiveFormat.TAR_GZIP).use { tar ->
                tar.add(TarArchiveEntry("Cancel.bin").apply { size = 100_000 },
                    checkActive = { if (copied > 0) throw CancellationException("Cancelled") },
                    onBytes = { copied += it }) { input }
            }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(inputClosed)
        assertTrue(outputClosed)
        assertTrue(copied in 1 until 100_000)
    }
}
