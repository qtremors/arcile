package dev.qtremors.arcile.core.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.CRC32
import java.util.zip.GZIPOutputStream
import java.util.Base64
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.archivers.tar.TarConstants
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveReaderTest {
    @Test
    fun `zip paths are checked before Windows separators or absolute paths can become targets`() {
        val payload = ByteArrayOutputStream().also { bytes ->
            ZipArchiveOutputStream(bytes).use { output ->
                for (name in listOf("../outside.txt", "/absolute.txt", "C:/drive.txt", "valid.txt")) {
                    output.putArchiveEntry(ZipArchiveEntry(name))
                    output.write("content".encodeToByteArray())
                    output.closeArchiveEntry()
                }
            }
        }.toByteArray()

        ArchiveReader().open(memorySource("paths.zip", payload), ArchiveFormat.ZIP).use { archive ->
            assertEquals(listOf("valid.txt"), archive.entries.mapNotNull { it.toModel()?.path })
            assertTrue(archive.entries.filter { it.segments == null }.all { failure { it.openStream() } is IOException })
            assertEquals("content", archive.entries.last().openStream().use { it.readBytes().decodeToString() })
        }
        assertNull(archiveEntrySegments("folder\\windows.txt", allowBackslashes = false))
        assertEquals(listOf("folder", "windows.txt"), archiveEntrySegments("folder\\windows.txt"))
        assertNull(archiveEntrySegments("./C:/drive.txt"))
    }

    @Test
    fun `zip source uses the chosen legacy filename encoding and closes its channel`() {
        val payload = ByteArrayOutputStream().also { bytes ->
            ZipArchiveOutputStream(bytes).use { output ->
                output.setEncoding("CP437")
                output.setUseLanguageEncodingFlag(false)
                output.putArchiveEntry(ZipArchiveEntry("café.txt"))
                output.write(7)
                output.closeArchiveEntry()
            }
        }.toByteArray()
        val channel = SeekableInMemoryByteChannel(payload)
        val source = ArchiveReadSource("encoded.zip", payload.size.toLong(), null) { channel }

        ArchiveReader().open(source, ArchiveFormat.ZIP, encoding = ArchiveNameEncoding.CP437).use { archive ->
            val entry = archive.entries.single()
            assertEquals("café.txt", entry.toModel()?.name)
            assertEquals(listOf<Byte>(7), entry.openStream().use { it.readBytes().toList() })
        }
        assertFalse(channel.isOpen)
    }

    @Test
    fun `tar selects members in order across skipped data and does not open symbolic links`() {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(GZIPOutputStream(bytes)).use { output ->
            output.putArchiveEntry(TarArchiveEntry("./"))
            output.closeArchiveEntry()
            output.writeEntry("./docs/one.txt", "one".encodeToByteArray())
            output.writeEntry("unselected.bin", ByteArray(256 * 1024) { 5 })
            output.putArchiveEntry(TarArchiveEntry("docs/link.txt", TarConstants.LF_SYMLINK).apply {
                linkName = "../outside.txt"
            })
            output.closeArchiveEntry()
            output.writeEntry("./docs/two.txt", "two".encodeToByteArray())
        }
        val opened = mutableListOf<SeekableInMemoryByteChannel>()
        val payload = bytes.toByteArray()
        val source = ArchiveReadSource("example.tar.gz", payload.size.toLong(), null) {
            SeekableInMemoryByteChannel(payload).also(opened::add)
        }

        ArchiveReader().open(source, ArchiveFormat.TAR_GZIP).use { archive ->
            assertEquals(
                listOf("docs/one.txt", "unselected.bin", "docs/link.txt", "docs/two.txt"),
                archive.entries.mapNotNull { it.toModel()?.path }
            )
            val link = archive.entries[2]
            assertFalse(link.canRead)
            assertTrue(failure { link.openStream() } is IOException)
            assertEquals("one", archive.entries[0].openStream().use { it.readBytes().decodeToString() })
            assertEquals("two", archive.entries[3].openStream().use { it.readBytes().decodeToString() })
        }
        assertEquals(2, opened.size)
        assertTrue(opened.none { it.isOpen })
    }

    @Test
    fun `standalone compression derives the output name and enforces expanded bytes`() {
        val bytes = ByteArrayOutputStream()
        val expected = ByteArray(1_024) { 3 }
        GZIPOutputStream(bytes).use { it.write(expected) }
        val source = memorySource("payload.bin.GZ", bytes.toByteArray())
        val atLimit = ArchiveReader(ArchiveSafetyPolicy(maxUncompressedBytes = expected.size.toLong()))
        val belowLimit = ArchiveReader(ArchiveSafetyPolicy(maxUncompressedBytes = expected.size - 1L))

        atLimit.open(source, ArchiveFormat.GZIP).use { archive ->
            val entry = archive.entries.single()
            assertEquals(listOf("payload.bin"), entry.segments)
            assertEquals(expected.toList(), entry.openStream().use { it.readBytes().toList() })
        }
        assertTrue(failure { belowLimit.open(source, ArchiveFormat.GZIP) } is UnsafeArchiveEntryException)
    }

    @Test
    fun `preview may stop after a sentinel entry while extraction enforces the full entry cap`() {
        val bytes = ByteArrayOutputStream()
        ZipArchiveOutputStream(bytes).use { output ->
            repeat(4) { index ->
                output.putArchiveEntry(ZipArchiveEntry("note-$index.txt"))
                output.closeArchiveEntry()
            }
        }
        val source = memorySource("many.zip", bytes.toByteArray())
        val bounded = ArchiveReader(ArchiveSafetyPolicy(maxEntries = 3))
        bounded.open(source, ArchiveFormat.ZIP, listingLimit = 3).use { archive ->
            assertEquals(listOf("note-0.txt", "note-1.txt", "note-2.txt"), archive.entries.mapNotNull { it.toModel()?.path })
        }
        assertTrue(failure { bounded.open(source, ArchiveFormat.ZIP) } is IllegalArgumentException)
    }

    @Test
    fun `encrypted 7z headers request a password before revealing entries`() {
        // Small 7z fixture containing note.txt with header encryption enabled.
        val payload = Base64.getDecoder().decode(
            "N3q8ryccAASGi6T+gAAAAAAAAAAuAAAAAAAAAJfj2sCcC5J/7dT1uu43ef8RumxT6Dt6MWuogEt/1EBu6Y+pSiqeFVtttJVq6I4wpGOGgu4dhRN+QV7TdBIyam6jff2Orn75BkykfKi0X4w7oU1yq8cNt1giwlTQTp0SfEahinhry4iPetwjLxcOn+dprvHPaB/DxqRKtzs36J61ApoHlhcGIAEJYAAHCwEAASQG8QcBElMP+R58kGzyTXFvvBc1xMHHVwxeCgFE93dgAAA="
        )
        val source = memorySource("protected.7z", payload)
        assertTrue(failure { ArchiveReader().open(source, ArchiveFormat.SEVEN_Z) } is ArchivePasswordRequired)
        val password = "test-password".toCharArray()
        try {
            ArchiveReader().open(source, ArchiveFormat.SEVEN_Z, password).use { archive ->
                val entry = archive.entries.single()
                assertEquals("note.txt", entry.toModel()?.name)
                assertEquals("private content", entry.openStream().use { it.readBytes().decodeToString() })
            }
        } finally {
            password.fill('\u0000')
        }
    }

    @Test
    fun `archive stream verifies bytes skipped through the public stream interface`() {
        val bytes = "abc".encodeToByteArray()
        val crc = CRC32().apply { update(bytes) }.value
        ExactArchiveEntryStream(ByteArrayInputStream(bytes), bytes.size.toLong(), crc).use { stream ->
            assertEquals(2L, stream.skip(2))
            assertEquals('c'.code, stream.read())
            assertEquals(-1, stream.read())
        }
        for ((size, checksum) in listOf(1L to crc, 4L to crc, 3L to 0L)) {
            assertTrue(failure {
                ExactArchiveEntryStream(ByteArrayInputStream(bytes), size, checksum).use { it.readBytes() }
            } is IOException)
        }
    }

    @Test
    fun `cancelling a listing releases an opened archive source`() {
        val bytes = ByteArrayOutputStream()
        ZipArchiveOutputStream(bytes).use { output ->
            output.putArchiveEntry(ZipArchiveEntry("file.txt"))
            output.closeArchiveEntry()
        }
        val payload = bytes.toByteArray()
        val channel = SeekableInMemoryByteChannel(payload)
        val source = ArchiveReadSource("cancel.zip", payload.size.toLong(), null) { channel }
        var checks = 0
        val stop = IllegalStateException("cancel")
        val error = failure {
            ArchiveReader().open(source, ArchiveFormat.ZIP) {
                if (++checks == 2) throw stop
            }
        }
        assertEquals(stop, error)
        assertFalse(channel.isOpen)
    }

    private fun TarArchiveOutputStream.writeEntry(name: String, bytes: ByteArray) {
        putArchiveEntry(TarArchiveEntry(name).apply { size = bytes.size.toLong() })
        write(bytes)
        closeArchiveEntry()
    }

    private fun memorySource(name: String, bytes: ByteArray) =
        ArchiveReadSource(name, bytes.size.toLong(), null) { SeekableInMemoryByteChannel(bytes) }

    private fun failure(block: () -> Unit): Throwable? = runCatching(block).exceptionOrNull()
}
