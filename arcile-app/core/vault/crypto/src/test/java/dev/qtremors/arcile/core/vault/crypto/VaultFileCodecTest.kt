package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.RandomAccessFile
import java.nio.file.Files

class VaultFileCodecTest {
    @Test
    fun `empty exact chunk and multi chunk files round trip`() {
        val sizes = listOf(0, 32 * 1024, 32 * 1024 + 1, 96 * 1024 + 17)
        sizes.forEach { size ->
            val directory = Files.createTempDirectory("onlyfiles-boundary").toFile()
            val encrypted = directory.resolve("object")
            val codec = VaultFileCodec(32 * 1024)
            val plaintext = ByteArray(size) { (it * 31).toByte() }
            codec.write(encrypted, VaultId.of("vault-id"), ByteArray(32) { it.toByte() }, ByteArrayInputStream(plaintext))
            codec.open(encrypted, VaultId.of("vault-id"), ByteArray(32) { it.toByte() }).use { reader ->
                val actual = ByteArray(size)
                if (size == 0) assertEquals(-1, reader.readAt(0, ByteArray(1), 0, 1))
                else assertEquals(size, reader.readAt(0, actual, 0, size))
                assertArrayEquals(plaintext, actual)
            }
            directory.deleteRecursively()
        }
    }

    @Test
    fun `physical layout supports simulated files larger than four GiB`() {
        val logicalSize = (5L shl 30) + 123L
        val physicalSize = VaultFileCodec.encryptedSizeForPlaintext(logicalSize)
        assertTrue(physicalSize > logicalSize)
        assertTrue(physicalSize > Int.MAX_VALUE.toLong())
    }

    @Test
    fun `large content supports authenticated random access across chunk boundaries`() {
        val directory = Files.createTempDirectory("onlyfiles-file").toFile()
        val encrypted = directory.resolve("object")
        val codec = VaultFileCodec(32 * 1024)
        val id = VaultId.of("vault-id")
        val key = ByteArray(32) { (it * 3).toByte() }
        val plaintext = ByteArray(100_000) { (it % 251).toByte() }

        val result = codec.write(encrypted, id, key, ByteArrayInputStream(plaintext))
        assertEquals(plaintext.size.toLong(), result.sizeBytes)
        codec.open(encrypted, id, key).use { reader ->
            val actual = ByteArray(70_000)
            assertEquals(actual.size, reader.readAt(20_000, actual, 0, actual.size))
            assertArrayEquals(plaintext.copyOfRange(20_000, 90_000), actual)
        }
        directory.deleteRecursively()
    }

    @Test
    fun `truncated and modified encrypted files fail closed`() {
        val directory = Files.createTempDirectory("onlyfiles-tamper").toFile()
        val encrypted = directory.resolve("object")
        val codec = VaultFileCodec(32 * 1024)
        val id = VaultId.of("vault-id")
        val key = ByteArray(32) { it.toByte() }
        codec.write(encrypted, id, key, ByteArrayInputStream(ByteArray(80_000) { 7 }))

        RandomAccessFile(encrypted, "rw").use { file ->
            file.seek(file.length() - 4)
            file.writeInt(123)
        }
        codec.open(encrypted, id, key).use { reader ->
            assertThrows(VaultFailure.IntegrityFailed::class.java) {
                reader.readAt(70_000, ByteArray(100), 0, 100)
            }
        }

        RandomAccessFile(encrypted, "rw").use { file -> file.setLength(file.length() - 1) }
        assertThrows(VaultFailure.IntegrityFailed::class.java) { codec.open(encrypted, id, key) }
        directory.deleteRecursively()
    }
}
