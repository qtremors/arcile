package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultId
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.file.Files

class VaultIndexCodecTest {
    @Test
    fun `newest authenticated slot wins and damaged newest slot falls back`() {
        val directory = Files.createTempDirectory("onlyfiles-index").toFile()
        val codec = VaultIndexCodec()
        val id = VaultId.of("vault-id")
        val key = ByteArray(32) { (it + 1).toByte() }
        codec.create(directory, id, "Private", key)
        val next = VaultIndex(
            generation = 1,
            vaultName = "Private",
            entries = listOf(
                VaultIndexEntry("node", "file.txt", "object", 5, 9, false, "text/plain")
            )
        )
        codec.write(directory, id, key, next)

        assertEquals(next, codec.read(directory, id, key))
        File(directory, VaultIndexCodec.SLOT_B).writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(0L, codec.read(directory, id, key).generation)
        directory.deleteRecursively()
    }
}
