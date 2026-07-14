package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.RandomAccessFile
import java.nio.file.Files

class VaultFileCodecTest {
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
