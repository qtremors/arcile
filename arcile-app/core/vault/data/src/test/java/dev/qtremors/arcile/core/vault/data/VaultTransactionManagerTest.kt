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

class VaultTransactionManagerTest {
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
