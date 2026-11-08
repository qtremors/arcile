package dev.qtremors.arcile.core.vault.crypto

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.util.concurrent.CancellationException
import kotlin.random.Random
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultEncryptedScratchChannelTest {
    @get:Rule val temporary = TemporaryFolder()
    private val pageBytes = 64 * 1024

    @Test fun `header rewrites and random reads preserve a multi-page payload`() {
        val directory = temporary.newFolder()
        val expected = Random(42).nextBytes(pageBytes * 3 + 171)
        VaultEncryptedScratchChannel.create(directory).use { channel ->
            channel.write(ByteBuffer.wrap(expected))
            val header = "Patched archive header".encodeToByteArray()
            channel.position(0).write(ByteBuffer.wrap(header))
            header.copyInto(expected)
            for (offset in listOf(pageBytes - 7, expected.size - 9, 0, pageBytes * 2 + 10)) {
                val count = minOf(113, expected.size - offset)
                val result = ByteBuffer.allocate(count)
                assertEquals(count, channel.position(offset.toLong()).read(result))
                assertArrayEquals(expected.copyOfRange(offset, offset + count), result.array())
            }
            assertEquals(expected.size.toLong(), channel.size())
            assertArrayEquals(expected, readAll(channel))
        }
        assertTrue(directory.listFiles()!!.isEmpty())
    }

    @Test fun `disk holds encrypted bytes even after repeated rewrites`() {
        val directory = temporary.newFolder()
        val marker = "Confidential scratch data that must never reach disk".repeat(1000).encodeToByteArray()
        VaultEncryptedScratchChannel.create(directory).use { channel ->
            channel.write(ByteBuffer.wrap(marker))
            channel.position(pageBytes.toLong()).write(ByteBuffer.wrap(byteArrayOf(1)))
            channel.position(0).read(ByteBuffer.allocate(1))
            val physical = directory.listFiles()!!.single().readBytes()
            assertFalse(physical.decodeToString().contains("Confidential scratch data"))
            assertFalse(physical.take(marker.size).toByteArray().contentEquals(marker))
            assertEquals(marker.size.toLong().coerceAtLeast(pageBytes + 1L), channel.size())
        }
    }

    @Test fun `seeking past the end exposes zero-filled gaps and does not grow until write`() {
        VaultEncryptedScratchChannel.create(temporary.newFolder()).use { channel ->
            channel.write(ByteBuffer.wrap(byteArrayOf(7)))
            channel.position(pageBytes * 2L + 9)
            assertEquals(1L, channel.size())
            assertEquals(-1, channel.read(ByteBuffer.allocate(1)))
            channel.write(ByteBuffer.wrap(byteArrayOf(8)))
            val actual = readAll(channel)
            assertEquals(7, actual.first().toInt())
            assertEquals(8, actual.last().toInt())
            assertTrue(actual.drop(1).dropLast(1).all { it == 0.toByte() })
        }
    }

    @Test fun `truncate clears discarded bytes when output is extended again`() {
        for (cut in listOf(0, 11, pageBytes, pageBytes + 11)) {
            VaultEncryptedScratchChannel.create(temporary.newFolder()).use { channel ->
                val original = ByteArray(pageBytes * 3) { 23 }
                channel.write(ByteBuffer.wrap(original))
                channel.truncate(cut.toLong())
                assertEquals(cut.toLong(), channel.position())
                channel.position(pageBytes * 3L - 1).write(ByteBuffer.wrap(byteArrayOf(41)))
                val actual = readAll(channel)
                assertTrue(actual.take(cut).all { it == 23.toByte() })
                assertTrue(actual.drop(cut).dropLast(1).all { it == 0.toByte() })
                assertEquals(41, actual.last().toInt())
            }
        }
    }

    @Test fun `aligned truncate preserves an unflushed retained page`() {
        VaultEncryptedScratchChannel.create(temporary.newFolder()).use { channel ->
            channel.position(pageBytes * 3L).write(ByteBuffer.wrap(byteArrayOf(9)))
            channel.position(0).write(ByteBuffer.wrap("keep".encodeToByteArray()))
            channel.truncate(pageBytes.toLong())
            assertEquals("keep", readAll(channel).copyOf(4).decodeToString())
        }
    }

    @Test fun `larger truncate is a no-op and empty buffers follow channel semantics`() {
        VaultEncryptedScratchChannel.create(temporary.newFolder()).use { channel ->
            assertEquals(0, channel.read(ByteBuffer.allocate(0)))
            assertEquals(-1, channel.read(ByteBuffer.allocate(1)))
            channel.write(ByteBuffer.wrap(byteArrayOf(3, 4)))
            channel.truncate(100)
            assertEquals(2L, channel.size())
            assertEquals(2L, channel.position())
            assertEquals(0, channel.write(ByteBuffer.allocate(0)))
            assertEquals(0, channel.read(ByteBuffer.allocate(0)))
        }
    }

    @Test fun `bounded writes reject overflow without consuming or partly copying input`() {
        VaultEncryptedScratchChannel.create(temporary.newFolder(), 100).use { channel ->
            channel.write(ByteBuffer.wrap(ByteArray(98) { 1 }))
            val source = ByteBuffer.wrap(byteArrayOf(2, 3, 4))
            assertTrue(runCatching { channel.write(source) }.exceptionOrNull() is IOException)
            assertEquals(0, source.position())
            assertEquals(98L, channel.size())
            assertEquals(98L, channel.position())
            assertTrue(runCatching { channel.position(Long.MAX_VALUE) }.exceptionOrNull() is IOException)
            assertTrue(runCatching { channel.position(-1) }.exceptionOrNull() is IllegalArgumentException)
            assertTrue(runCatching { channel.truncate(-1) }.exceptionOrNull() is IllegalArgumentException)
        }
    }

    @Test fun `byte tampering and physically truncated pages fail authentication`() {
        for (truncate in listOf(false, true)) {
            val directory = temporary.newFolder()
            VaultEncryptedScratchChannel.create(directory).use { channel ->
                channel.write(ByteBuffer.wrap(ByteArray(pageBytes + 1) { 91 }))
                val file = directory.listFiles()!!.single()
                RandomAccessFile(file, "rw").use { disk ->
                    if (truncate) disk.setLength(1)
                    else { disk.seek(17); val value = disk.read(); disk.seek(17); disk.write(value xor 1) }
                }
                assertTrue(runCatching { channel.position(0).read(ByteBuffer.allocate(1)) }.isFailure)
            }
            assertTrue(directory.listFiles()!!.isEmpty())
        }
    }

    @Test fun `swapping encrypted pages cannot change their authenticated positions`() {
        val directory = temporary.newFolder()
        VaultEncryptedScratchChannel.create(directory).use { channel ->
            channel.write(ByteBuffer.wrap(ByteArray(pageBytes * 2 + 1) { 91 }))
            val file = directory.listFiles()!!.single()
            val bytes = file.readBytes()
            val slot = pageBytes + VaultCryptography.NONCE_SIZE_BYTES + VaultCryptography.GCM_TAG_SIZE_BYTES
            bytes.copyInto(bytes, slot, 0, slot)
            file.writeBytes(bytes)
            assertTrue(runCatching { channel.position(pageBytes.toLong()).read(ByteBuffer.allocate(1)) }.isFailure)
        }
    }

    @Test fun `an earlier authenticated page cannot replace a later rewrite`() {
        val directory = temporary.newFolder()
        VaultEncryptedScratchChannel.create(directory).use { channel ->
            channel.write(ByteBuffer.wrap(ByteArray(pageBytes + 1) { 11 }))
            val file = directory.listFiles()!!.single()
            val before = file.readBytes()
            channel.position(0).write(ByteBuffer.wrap(byteArrayOf(19)))
            channel.position(pageBytes.toLong()).read(ByteBuffer.allocate(1))
            RandomAccessFile(file, "rw").use { it.write(before, 0, before.size) }
            assertTrue(runCatching { channel.position(0).read(ByteBuffer.allocate(1)) }.isFailure)
        }
    }

    @Test fun `discarded pages cannot be replayed after truncate and regrow`() {
        val directory = temporary.newFolder()
        VaultEncryptedScratchChannel.create(directory).use { channel ->
            channel.write(ByteBuffer.wrap(ByteArray(pageBytes + 1) { 11 }))
            val file = directory.listFiles()!!.single()
            val before = file.readBytes()
            channel.truncate(0)
            channel.write(ByteBuffer.wrap(ByteArray(pageBytes + 1) { 19 }))
            RandomAccessFile(file, "rw").use { it.write(before) }
            assertTrue(runCatching { channel.position(0).read(ByteBuffer.allocate(1)) }.isFailure)
        }
    }

    @Test fun `another operation cannot read a transplanted encrypted page`() {
        val firstDirectory = temporary.newFolder()
        val secondDirectory = temporary.newFolder()
        VaultEncryptedScratchChannel.create(firstDirectory).use { first ->
            VaultEncryptedScratchChannel.create(secondDirectory).use { second ->
                for (channel in listOf(first, second)) channel.write(ByteBuffer.wrap(ByteArray(pageBytes + 1) { 7 }))
                val transplanted = firstDirectory.listFiles()!!.single().readBytes()
                RandomAccessFile(secondDirectory.listFiles()!!.single(), "rw").use { it.write(transplanted) }
                assertTrue(runCatching { second.position(0).read(ByteBuffer.allocate(1)) }.isFailure)
            }
        }
    }

    @Test fun `cancellation stops access but still closes and removes scratch`() {
        val directory = temporary.newFolder()
        var cancelled = false
        val channel = VaultEncryptedScratchChannel.create(directory) { if (cancelled) throw CancellationException() }
        channel.write(ByteBuffer.wrap(ByteArray(pageBytes * 2)))
        cancelled = true
        assertTrue(runCatching { channel.read(ByteBuffer.allocate(1)) }.exceptionOrNull() is CancellationException)
        assertTrue(runCatching { channel.write(ByteBuffer.allocate(1)) }.exceptionOrNull() is CancellationException)
        channel.close()
        channel.close()
        assertFalse(channel.isOpen)
        assertTrue(directory.listFiles()!!.isEmpty())
        assertTrue(runCatching { channel.size() }.exceptionOrNull() is ClosedChannelException)
    }

    @Test fun `abandoned cleanup removes only owned files and leaves active cleanup to its owner`() {
        val directory = temporary.newFolder()
        File(directory, "protected-old.scratch").writeBytes(ByteArray(100))
        File(directory, "keep.txt").writeText("keep")
        File(directory, "protected-folder.scratch").mkdir()
        VaultEncryptedScratchChannel.discardAbandoned(directory)
        assertEquals(setOf("keep.txt", "protected-folder.scratch"), directory.list()!!.toSet())
    }

    @Test fun `seek write read and truncate match an in-memory model under mixed operations`() {
        val random = Random(7242)
        VaultEncryptedScratchChannel.create(temporary.newFolder(), pageBytes * 5L).use { channel ->
            var model = ByteArray(0)
            repeat(300) {
                when (random.nextInt(3)) {
                    0 -> {
                        val offset = random.nextInt(pageBytes * 4)
                        val data = random.nextBytes(random.nextInt(1, 1500))
                        if (offset + data.size > model.size) model = model.copyOf(offset + data.size)
                        data.copyInto(model, offset)
                        channel.position(offset.toLong()).write(ByteBuffer.wrap(data))
                    }
                    1 -> {
                        val size = random.nextInt(pageBytes * 5)
                        if (size < model.size) model = model.copyOf(size)
                        channel.truncate(size.toLong())
                    }
                    else -> {
                        val offset = random.nextInt(pageBytes * 5)
                        val destination = ByteBuffer.allocate(53)
                        val count = channel.position(offset.toLong()).read(destination)
                        val expected = if (offset >= model.size) -1 else minOf(53, model.size - offset)
                        assertEquals(expected, count)
                        if (count > 0) assertArrayEquals(model.copyOfRange(offset, offset + count), destination.array().copyOf(count))
                    }
                }
                assertEquals(model.size.toLong(), channel.size())
            }
            assertArrayEquals(model, readAll(channel))
        }
    }

    private fun readAll(channel: VaultEncryptedScratchChannel): ByteArray {
        val output = ByteBuffer.allocate(channel.size().toInt())
        channel.position(0)
        while (output.hasRemaining()) check(channel.read(output) > 0)
        return output.array()
    }
}
