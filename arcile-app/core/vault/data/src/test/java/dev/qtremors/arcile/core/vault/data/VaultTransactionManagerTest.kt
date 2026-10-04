package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultKeyDomain
import dev.qtremors.arcile.core.vault.domain.VaultId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.file.Files
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import dev.qtremors.arcile.core.vault.crypto.toBase64
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.put
import kotlinx.serialization.json.add
import org.junit.Assert.assertTrue
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryAccess
import io.mockk.mockk
import io.mockk.verify

class VaultTransactionManagerTest {
    @Test
    fun `oversized object sets and roots never write a committed marker`() {
        val backing = mockk<VaultDirectoryAccess>(relaxed = true)
        val directory = object : VaultDirectoryAccess by backing {
            override fun delete(relativePath: String): Boolean = true
        }
        val vault = VaultId.of("preflight-vault")
        val master = ByteArray(32) { it.toByte() }
        val key = VaultCryptography.deriveDomainKey(master, vault, VaultKeyDomain.ROOT_DIRECTORY)
        val manifest = VaultDirectoryManifestCodec().prepare(vault, DirectoryId.of("root"), key, 1L, emptyList())
        val tooMany = (0..100_000).map { "objects/$it" }.toSet()
        for ((newPaths, obsoletePaths) in listOf(tooMany to emptySet<String>(), emptySet<String>() to tooMany)) {
            assertThrows(IllegalArgumentException::class.java) {
                VaultTransactionManager().commit(directory, vault, master,
                    listOf(VaultPreparedDirectory(manifest, key.copyOf())), newPaths, obsoletePaths)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            VaultTransactionManager().commit(directory, vault, master,
                listOf(VaultPreparedDirectory(manifest.copy(rootBytes = ByteArray(8 * 1024 * 1024 + 1)), key.copyOf())), emptySet(), emptySet())
        }
        verify(exactly = 0) { backing.writeAtomic(any(), any()) }
    }
    @Test
    fun `31 32 and 33 directory trees fail safely beyond the publication limit`() {
        for (directoryCount in listOf(31, 32, 33)) {
            val root = Files.createTempDirectory("vault-limit").toFile()
            try {
                val directory = FileVaultDirectory(root)
                val vault = VaultId.of("limit-vault")
                val master = ByteArray(32) { it.toByte() }
                val key = VaultCryptography.deriveDomainKey(master, vault, VaultKeyDomain.ROOT_DIRECTORY)
                val codec = VaultDirectoryManifestCodec()
                val prepared = (0..directoryCount).map { index ->
                    val id = DirectoryId.of(java.util.UUID.randomUUID().toString())
                    VaultPreparedDirectory(codec.prepare(vault, id, key, 1L, emptyList()), key.copyOf())
                }
                val result = runCatching { VaultTransactionManager(codec).commit(directory, vault, master, prepared, emptySet(), emptySet()) }
                assertEquals(directoryCount == 31, result.isSuccess)
                assertFalse(VaultTransactionManager().hasPendingCommit(directory))
                assertFalse(VaultTransactionManager().recover(directory, vault, master))
                if (result.isSuccess) prepared.forEach {
                    assertEquals(1L, codec.read(directory, vault, it.manifest.directoryId, key).generation)
                }
                assertTrue(prepared.all { it.directoryKey.all { byte -> byte == 0.toByte() } })
            } finally { root.deleteRecursively() }
        }
    }

    @Test
    fun `authenticated legacy oversized marker publishes completely and recovers repeatedly`() {
        val root = Files.createTempDirectory("vault-legacy-marker").toFile()
        try {
            val directory = FileVaultDirectory(root)
            val vault = VaultId.of("legacy-limit-vault")
            val master = ByteArray(32) { it.toByte() }
            val key = VaultCryptography.deriveDomainKey(master, vault, VaultKeyDomain.ROOT_DIRECTORY)
            val codec = VaultDirectoryManifestCodec()
            val manifests = (0..32).map { codec.prepare(vault, DirectoryId.of(java.util.UUID.randomUUID().toString()), key, 1L, emptyList()) }
            val plaintext = buildJsonObject {
                put("transactionId", "legacy-oversized")
                put("vaultId", vault.value)
                put("publications", buildJsonArray { manifests.forEach { manifest -> add(buildJsonObject {
                    put("relativePath", manifest.rootRelativePath)
                    put("bytes", manifest.rootBytes.toBase64())
                }) } })
                put("newObjects", buildJsonArray { })
                put("obsoletePaths", buildJsonArray { })
            }.toString().toByteArray()
            val transactionKey = VaultCryptography.deriveDomainKey(master, vault, VaultKeyDomain.TRANSACTION)
            val sealed = VaultCryptography.seal(transactionKey, plaintext, "Arcile/OnlyFiles/v1/transaction/${vault.value}".toByteArray())
            transactionKey.fill(0)
            val encoded = ByteArrayOutputStream().use { buffer ->
                DataOutputStream(buffer).use { output ->
                    output.write("AOTXN001".toByteArray())
                    output.writeInt(1)
                    output.write(sealed.nonce)
                    output.writeInt(sealed.ciphertext.size)
                    output.write(sealed.ciphertext)
                }
                buffer.toByteArray()
            }
            val corrupted = encoded.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
            directory.writeAtomic(VaultTransactionManager.COMMIT_MARKER, corrupted)
            assertThrows(dev.qtremors.arcile.core.vault.domain.VaultFailure.TransactionRecoveryFailed::class.java) {
                VaultTransactionManager(codec).recover(directory, vault, master)
            }
            assertTrue(VaultTransactionManager(codec).hasPendingCommit(directory))
            directory.writeAtomic(VaultTransactionManager.COMMIT_MARKER, encoded)
            // Interruption during publication leaves the authenticated marker intact.
            val interrupted = VaultTransactionManager(codec, stageObserver = {
                if (it == VaultTransactionStage.MANIFESTS_PUBLISHED) throw InjectedFailure()
            })
            assertThrows(dev.qtremors.arcile.core.vault.domain.VaultFailure.TransactionRecoveryFailed::class.java) {
                interrupted.recover(directory, vault, master)
            }
            assertTrue(interrupted.hasPendingCommit(directory))
            assertTrue(VaultTransactionManager(codec).recover(directory, vault, master))
            assertFalse(VaultTransactionManager(codec).recover(directory, vault, master))
            manifests.forEach { assertEquals(1L, codec.read(directory, vault, it.directoryId, key).generation) }
        } finally { root.deleteRecursively() }
    }
    @Test
    fun `failure at every transaction stage reopens only the old or committed generation`() {
        VaultTransactionStage.entries.forEach { failingStage ->
            val root = Files.createTempDirectory("vault-txn-${failingStage.name}").toFile()
            val directory = FileVaultDirectory(root)
            val vaultId = VaultId.of("transaction-vault")
            val master = ByteArray(32) { it.toByte() }
            val rootKey = VaultCryptography.deriveDomainKey(master, vaultId, VaultKeyDomain.ROOT_DIRECTORY)
            val manifests = VaultDirectoryManifestCodec()
            manifests.createRoot(directory, vaultId, VaultSessionRecord.ROOT_DIRECTORY_ID, rootKey)
            val prepared = manifests.prepare(
                vaultId,
                VaultSessionRecord.ROOT_DIRECTORY_ID,
                rootKey,
                1L,
                emptyList()
            )
            val manager = VaultTransactionManager(manifests, stageObserver = { stage ->
                if (stage == failingStage) throw InjectedFailure()
            })

            assertThrows(InjectedFailure::class.java) {
                manager.commit(
                    directory,
                    vaultId,
                    master,
                    listOf(VaultPreparedDirectory(prepared, rootKey.copyOf())),
                    emptySet(),
                    emptySet()
                )
            }

            val recovery = VaultTransactionManager(manifests)
            if (recovery.hasPendingCommit(directory)) recovery.recover(directory, vaultId, master)
            val reopened = manifests.read(directory, vaultId, VaultSessionRecord.ROOT_DIRECTORY_ID, rootKey)
            val expected = if (failingStage.ordinal < VaultTransactionStage.COMMIT_MARKER_SYNCED.ordinal) 0L else 1L
            assertEquals("stage=$failingStage", expected, reopened.generation)
            assertFalse(recovery.hasPendingCommit(directory))
            rootKey.fill(0)
            master.fill(0)
            root.deleteRecursively()
        }
    }
}

private class InjectedFailure : RuntimeException()
