package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class VaultCryptographyTest {
    @Test
    fun `HKDF SHA-256 matches RFC 5869 test case one`() {
        val ikm = ByteArray(22) { 0x0b }
        val salt = "000102030405060708090a0b0c".hex()
        val info = "f0f1f2f3f4f5f6f7f8f9".hex()
        val expected = "3cb25f25faacd57a90434f64d0362f2a" +
            "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
            "34007208d5b887185865"

        assertArrayEquals(expected.hex(), VaultCryptography.hkdfSha256(ikm, salt, info, 42))
    }

    @Test
    fun `domain separation is deterministic and distinct`() {
        val master = ByteArray(32) { it.toByte() }
        val vault = VaultId.of("vault-one")
        val root = VaultCryptography.deriveDomainKey(master, vault, VaultKeyDomain.ROOT_DIRECTORY)
        val rootAgain = VaultCryptography.deriveDomainKey(master, vault, VaultKeyDomain.ROOT_DIRECTORY)
        val thumbnail = VaultCryptography.deriveDomainKey(master, vault, VaultKeyDomain.THUMBNAILS)

        assertArrayEquals(root, rootAgain)
        assertFalse(root.contentEquals(thumbnail))
        assertFalse(root.contentEquals(VaultCryptography.deriveDomainKey(master, VaultId.of("vault-two"), VaultKeyDomain.ROOT_DIRECTORY)))
    }

    @Test
    fun `imported Argon2 parameters are bounded before derivation`() {
        val safe = VaultKdfParameters(ByteArray(16), 64 * 1024, 3, 1)
        assertEquals(safe, VaultKdfPolicy.validate(safe))

        listOf(
            safe.copy(memoryKiB = VaultKdfPolicy.MAX_MEMORY_KIB + 1),
            safe.copy(iterations = VaultKdfPolicy.MAX_ITERATIONS + 1),
            safe.copy(parallelism = VaultKdfPolicy.MAX_PARALLELISM + 1),
            safe.copy(salt = ByteArray(15)),
            safe.copy(algorithm = "argon2i"),
            safe.copy(version = 0x10)
        ).forEach { hostile ->
            assertThrows(VaultFailure.UnsafeKdfParameters::class.java) {
                VaultKdfPolicy.validate(hostile)
            }
        }
    }

    @Test
    fun `AES GCM authentication covers associated data`() {
        val key = ByteArray(32) { it.toByte() }
        val plaintext = "secret".toByteArray()
        val sealed = VaultCryptography.seal(key, plaintext, "identity-a".toByteArray(), ByteArray(12))
        assertArrayEquals(plaintext, VaultCryptography.open(key, sealed, "identity-a".toByteArray()))
        assertThrows(VaultAuthenticationException::class.java) {
            VaultCryptography.open(key, sealed, "identity-b".toByteArray())
        }
    }
}

private fun String.hex(): ByteArray = ByteArray(length / 2) { index ->
    substring(index * 2, index * 2 + 2).toInt(16).toByte()
}
