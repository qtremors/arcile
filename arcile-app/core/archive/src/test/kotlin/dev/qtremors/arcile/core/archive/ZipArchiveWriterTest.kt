package dev.qtremors.arcile.core.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.junit.Assert.*
import org.junit.Test

class ZipArchiveWriterTest {
    @Test fun `plain and password ZIPs round trip every level with files empty folders and dates`() {
        val payload = "Private ZIP contents".repeat(100).encodeToByteArray()
        for (password in listOf(null, "secret".toCharArray())) for (level in ArchiveCompressionLevel.entries) {
            val output = ByteArrayOutputStream()
            var copied = 0
            ZipArchiveWriter.open(output, password, level = level).use { zip ->
                zip.add("Empty", 0, true, 1_660_000_000_000L) { error("Folders do not open readers") }
                zip.add("秘密/Document.txt", payload.size.toLong(), false, 1_660_000_000_000L,
                    onBytes = { copied += it }) { payload.inputStream() }
                zip.add("Empty.txt", 0, false, 1_660_000_000_000L) { "".byteInputStream() }
            }
            assertEquals(payload.size, copied)
            read(output.toByteArray(), password).use { archive ->
                assertEquals(listOf("Empty", "秘密/Document.txt", "Empty.txt"), archive.entries.map { it.segments!!.joinToString("/") })
                assertTrue(archive.entries[0].isDirectory)
                assertEquals(1_660_000_000_000L, archive.entries[1].modifiedAt)
                assertArrayEquals(payload, archive.entries[1].openStream().use { it.readBytes() })
                assertArrayEquals(byteArrayOf(), archive.entries[2].openStream().use { it.readBytes() })
            }
            if (password != null) assertArrayEquals("secret".toCharArray(), password)
        }
    }

    @Test fun `legacy name encoding works with both native and encrypted ZIP streams`() {
        for (password in listOf(null, "secret".toCharArray())) {
            val output = ByteArrayOutputStream()
            ZipArchiveWriter.open(output, password, ArchiveNameEncoding.CP437).use { zip ->
                zip.add("café.txt", 4, false, 0) { "text".byteInputStream() }
            }
            read(output.toByteArray(), password, ArchiveNameEncoding.CP437).use { archive ->
                assertEquals(listOf("café.txt"), archive.entries.single().segments)
                assertEquals("text", archive.entries.single().openStream().use { it.readBytes().decodeToString() })
            }
        }
    }

    @Test fun `wrong passwords cannot read encrypted file contents`() {
        val output = ByteArrayOutputStream()
        ZipArchiveWriter.open(output, "secret".toCharArray()).use { zip ->
            zip.add("Private.txt", 7, false, 0) { "private".byteInputStream() }
        }
        for (password in listOf(null, "wrong".toCharArray())) {
            assertTrue(runCatching {
                read(output.toByteArray(), password).use { it.entries.single().openStream().use { input -> input.readBytes() } }
            }.isFailure)
        }
    }

    @Test fun `empty passwords create an ordinary readable archive`() {
        val output = ByteArrayOutputStream()
        ZipArchiveWriter.open(output, charArrayOf()).use { zip ->
            zip.add("File.txt", 1, false, 0) { "x".byteInputStream() }
        }
        read(output.toByteArray()).use { archive ->
            assertFalse(archive.entries.single().encrypted)
            assertEquals("x", archive.entries.single().openStream().use { it.readBytes().decodeToString() })
        }
    }

    @Test fun `source changes close readers and prevent more entries from being added`() {
        for (content in listOf("short", "a source that grew")) {
            var inputClosed = false
            val input = object : ByteArrayInputStream(content.encodeToByteArray()) {
                override fun close() { inputClosed = true; super.close() }
            }
            val writer = ZipArchiveWriter.open(ByteArrayOutputStream())
            assertTrue(runCatching { writer.add("Changed.txt", 10, false, 0) { input } }.exceptionOrNull() is IOException)
            assertTrue(inputClosed)
            assertTrue(runCatching { writer.add("Late.txt", 0, false, 0) { "".byteInputStream() } }.isFailure)
            runCatching { writer.close() }
        }
    }

    @Test fun `unsafe names and oversized inputs are rejected before opening a reader`() {
        for ((name, size) in listOf("../Outside.txt" to 0L, "Large.txt" to 5L)) {
            var opened = false
            assertTrue(runCatching {
                ZipArchiveWriter.open(ByteArrayOutputStream(), policy = ArchiveSafetyPolicy(maxUncompressedBytes = 4)).use { zip ->
                    zip.add(name, size, false, 0) { opened = true; "".byteInputStream() }
                }
            }.isFailure)
            assertFalse(opened)
        }
    }

    @Test fun `cancellation closes the source and output after bounded progress`() {
        var outputClosed = false
        var inputClosed = false
        var copied = 0
        val output = object : ByteArrayOutputStream() { override fun close() { outputClosed = true; super.close() } }
        val input = object : ByteArrayInputStream(ByteArray(100_000)) {
            override fun close() { inputClosed = true; super.close() }
        }
        val result = runCatching {
            ZipArchiveWriter.open(output).use { zip ->
                zip.add("Cancel.bin", 100_000, false, 0,
                    checkActive = { if (copied > 0) throw CancellationException() }, onBytes = { copied += it }) { input }
            }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(inputClosed)
        assertTrue(outputClosed)
        assertTrue(copied in 1 until 100_000)
    }

    private fun read(bytes: ByteArray, password: CharArray? = null, encoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8) =
        ArchiveReader().open(ArchiveReadSource("Result.zip", bytes.size.toLong(), null) { SeekableInMemoryByteChannel(bytes) },
            ArchiveFormat.ZIP, password, encoding)
}
