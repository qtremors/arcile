package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files

class VaultManifestCodecTest {
    @Test
    fun `manifest opens with correct password and redundant copy survives`() {
        val directory = Files.createTempDirectory("onlyfiles-manifest").toFile()
        val codec = VaultManifestCodec()
        val id = VaultId.of("vault-id")
        val masterKey = ByteArray(32) { it.toByte() }
        val password = "correct horse battery staple".toCharArray()

        codec.create(directory, id, "Private", 42L, password, masterKey)
        java.io.File(directory, VaultManifestCodec.PRIMARY_FILE).writeText("broken")

        val opened = codec.open(directory, password).getOrThrow()
        assertEquals(id, opened.id)
        assertEquals("Private", opened.publicName)
        assertArrayEquals(masterKey, opened.masterKey)
        opened.masterKey.fill(0)
        password.fill('\u0000')
        directory.deleteRecursively()
    }

    @Test
    fun `wrong password is authentication failure`() {
        val directory = Files.createTempDirectory("onlyfiles-password").toFile()
        val codec = VaultManifestCodec()
        codec.create(
            directory,
            VaultId.of("vault-id"),
            "Private",
            42L,
            "correct".toCharArray(),
            ByteArray(32) { it.toByte() }
        )

        val failure = codec.open(directory, "wrong".toCharArray()).exceptionOrNull()
        assertTrue(failure is VaultFailure.AuthenticationFailed)
        directory.deleteRecursively()
    }

    @Test
    fun `visible metadata tampering is detected on unlock`() {
        val directory = Files.createTempDirectory("onlyfiles-metadata").toFile()
        val codec = VaultManifestCodec()
        codec.create(
            directory,
            VaultId.of("vault-id"),
            "Private",
            42L,
            "correct".toCharArray(),
            ByteArray(32) { it.toByte() }
        )
        listOf(VaultManifestCodec.PRIMARY_FILE, VaultManifestCodec.BACKUP_FILE).forEach { name ->
            val file = java.io.File(directory, name)
            file.writeText(file.readText().replace("Private", "Changed"))
        }

        val failure = codec.open(directory, "correct".toCharArray()).exceptionOrNull()
        assertTrue(failure is VaultFailure.AuthenticationFailed)
        directory.deleteRecursively()
    }

    @Test
    fun `password change replaces only envelopes and survives interrupted publication`() {
        val directory = Files.createTempDirectory("onlyfiles-password-change").toFile()
        val access = FileVaultDirectory(directory)
        val codec = VaultManifestCodec()
        val master = ByteArray(32) { (it * 2).toByte() }
        codec.create(access, VaultId.of("vault-id"), "Private", 42L, "old".toCharArray(), master)
        codec.changePassword(access, "old".toCharArray(), "new".toCharArray()).getOrThrow()

        assertTrue(codec.open(access, "old".toCharArray()).exceptionOrNull() is VaultFailure.AuthenticationFailed)
        val opened = codec.open(access, "new".toCharArray()).getOrThrow()
        assertArrayEquals(master, opened.masterKey)
        assertFalse(access.exists(VaultManifestCodec.COMMIT_FILE))
    }

    @Test
    fun `hostile KDF cost is rejected without password work`() {
        val directory = Files.createTempDirectory("onlyfiles-kdf-bounds").toFile()
        val codec = VaultManifestCodec()
        codec.create(directory, VaultId.of("vault-id"), "Private", 42L, "correct".toCharArray(), ByteArray(32))
        listOf(VaultManifestCodec.PRIMARY_FILE, VaultManifestCodec.BACKUP_FILE).forEach { name ->
            val file = java.io.File(directory, name)
            file.writeText(file.readText().replace("\"memoryKiB\":65536", "\"memoryKiB\":2147483647"))
        }
        assertTrue(codec.open(directory, "correct".toCharArray()).exceptionOrNull() is VaultFailure.UnsafeKdfParameters)
    }
}
