package dev.qtremors.arcile.core.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import java.io.ByteArrayInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.util.Date
import java.util.concurrent.CancellationException
import kotlin.random.Random
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SevenZipArchiveWriterTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun `all compression options round trip files unicode folders and timestamps`() {
        val payload = Random(42).nextBytes(90_000)
        for (level in ArchiveCompressionLevel.entries) {
            val channel = SeekableInMemoryByteChannel()
            SevenZipArchiveWriter.open(channel, level = level, closeChannel = false).use { writer ->
                writer.add(entry("Empty/", directory = true)) { error("Folder readers are never opened") }
                writer.add(entry("秘密/Document.txt", payload.size.toLong())) { payload.inputStream() }
                writer.add(entry("Empty.txt")) { ByteArrayInputStream(byteArrayOf()) }
            }
            assertTrue(channel.isOpen)
            read(channel).use { archive ->
                assertEquals(listOf("Empty", "秘密/Document.txt", "Empty.txt"), archive.entries.map { it.segments!!.joinToString("/") })
                assertTrue(archive.entries[0].isDirectory)
                assertArrayEquals(payload, archive.entries[1].openStream().use { it.readBytes() })
                assertEquals(1_660_000_000_000L, archive.entries[1].modifiedAt)
                assertArrayEquals(byteArrayOf(), archive.entries[2].openStream().use { it.readBytes() })
            }
        }
    }

    @Test fun `password protected archives require the same password to recover file data`() {
        val password = "secret".toCharArray()
        val channel = SeekableInMemoryByteChannel()
        SevenZipArchiveWriter.open(channel, password, closeChannel = false).use { writer ->
            writer.add(entry("Private.txt", 7)) { "private".byteInputStream() }
        }
        assertArrayEquals("secret".toCharArray(), password)
        assertTrue(runCatching { read(channel).use { it.entries.single().openStream().use { input -> input.readBytes() } } }.isFailure)
        assertTrue(runCatching { read(channel, "wrong".toCharArray()).use { it.entries.single().openStream().use { input -> input.readBytes() } } }.isFailure)
        read(channel, password).use { archive ->
            assertEquals("private", archive.entries.single().openStream().use { it.readBytes().decodeToString() })
        }
        password.fill('\u0000')
    }

    @Test fun `native file adaptation preserves modified time and measures size before opening`() {
        val file = temporary.newFile("Source.txt").apply { writeText("source"); setLastModified(1_660_000_000_000L) }
        val channel = SeekableInMemoryByteChannel()
        SevenZipArchiveWriter.open(channel, closeChannel = false).use { writer ->
            val entry = writer.entryFrom(file, "Folder/Source.txt")
            assertEquals(6L, entry.size)
            assertEquals(file.lastModified(), entry.lastModifiedDate.time)
            writer.add(entry) { file.inputStream() }
        }
        read(channel).use { archive ->
            assertEquals("source", archive.entries.single().openStream().use { it.readBytes().decodeToString() })
        }
    }

    @Test fun `short and growing sources fail with their input closed`() {
        for (content in listOf("short", "longer than expected")) {
            var closed = false
            val input = object : ByteArrayInputStream(content.encodeToByteArray()) {
                override fun close() { closed = true; super.close() }
            }
            val result = runCatching {
                SevenZipArchiveWriter.open(SeekableInMemoryByteChannel()).use { writer ->
                    writer.add(entry("Changed.txt", 10)) { input }
                }
            }
            assertTrue(result.exceptionOrNull() is IOException)
            assertTrue(closed)
        }
    }

    @Test fun `unsafe paths and anti items fail before reading private inputs`() {
        for (entry in listOf(entry("../Outside.txt"), entry("/Outside.txt"), entry("Removed.txt").apply { isAntiItem = true })) {
            var opened = false
            assertTrue(runCatching {
                SevenZipArchiveWriter.open(SeekableInMemoryByteChannel()).use { writer ->
                    writer.add(entry) { opened = true; "".byteInputStream() }
                }
            }.isFailure)
            assertFalse(opened)
        }
    }

    @Test fun `byte entry and depth limits are applied before opening inputs`() {
        val cases = listOf(
            ArchiveSafetyPolicy(maxUncompressedBytes = 2) to listOf(entry("Large.txt", 3)),
            ArchiveSafetyPolicy(maxEntries = 1) to listOf(entry("One/", directory = true), entry("Two/", directory = true)),
            ArchiveSafetyPolicy(maxNestedDepth = 1) to listOf(entry("Folder/TooDeep.txt"))
        )
        for ((policy, entries) in cases) {
            var opened = false
            assertTrue(runCatching {
                SevenZipArchiveWriter.open(SeekableInMemoryByteChannel(), policy = policy).use { writer ->
                    for (entry in entries) writer.add(entry) { opened = true; "".byteInputStream() }
                }
            }.isFailure)
            assertFalse(opened)
        }
    }

    @Test fun `cancellation closes owned channels and inputs after partial progress`() {
        val channel = SeekableInMemoryByteChannel()
        var inputClosed = false
        var bytes = 0
        val input = object : ByteArrayInputStream(Random(1).nextBytes(150_000)) {
            override fun close() { inputClosed = true; super.close() }
        }
        val result = runCatching {
            SevenZipArchiveWriter.open(channel).use { writer ->
                writer.add(entry("Cancel.bin", 150_000),
                    checkActive = { if (bytes > 0) throw CancellationException() }, onBytes = { bytes += it }) { input }
            }
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertTrue(inputClosed)
        assertFalse(channel.isOpen)
        assertTrue(bytes in 1 until 150_000)
    }

    @Test fun `borrowed channels survive finish but owned channels close`() {
        for (owned in listOf(true, false)) {
            val channel = SeekableInMemoryByteChannel()
            val writer = SevenZipArchiveWriter.open(channel, closeChannel = owned)
            writer.add(entry("File.txt", 1)) { "x".byteInputStream() }
            writer.close()
            writer.close()
            assertEquals(!owned, channel.isOpen)
            assertTrue(runCatching { writer.add(entry("Late.txt")) { "".byteInputStream() } }.isFailure)
        }
    }

    @Test fun `channel failures release ownership and preserve the original exception`() {
        val expected = IOException("Injected storage failure")
        val memory = SeekableInMemoryByteChannel()
        val failing = object : SeekableByteChannel by memory {
            override fun write(src: ByteBuffer): Int = throw expected
        }
        val result = runCatching {
            SevenZipArchiveWriter.open(failing, level = ArchiveCompressionLevel.STORE).use { writer ->
                writer.add(entry("Fail.txt", 1)) { "x".byteInputStream() }
            }
        }
        assertSame(expected, result.exceptionOrNull())
        assertFalse(memory.isOpen)
    }

    @Test fun `nonempty outputs are rejected without destroying borrowed content`() {
        for (owned in listOf(true, false)) {
            val channel = SeekableInMemoryByteChannel(byteArrayOf(4, 5))
            assertTrue(runCatching { SevenZipArchiveWriter.open(channel, closeChannel = owned) }.isFailure)
            assertEquals(!owned, channel.isOpen)
            assertArrayEquals(byteArrayOf(4, 5), channel.array())
        }
    }

    private fun entry(name: String, size: Long = 0, directory: Boolean = false) = SevenZArchiveEntry().apply {
        this.name = name
        this.size = size
        isDirectory = directory
        lastModifiedDate = Date(1_660_000_000_000L)
    }

    private fun read(channel: SeekableInMemoryByteChannel, password: CharArray? = null): ArchiveReadSession {
        val bytes = channel.array().copyOf(channel.size().toInt())
        return ArchiveReader().open(ArchiveReadSource("Result.7z", bytes.size.toLong(), null) { SeekableInMemoryByteChannel(bytes) },
            ArchiveFormat.SEVEN_Z, password)
    }
}
