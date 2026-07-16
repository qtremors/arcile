package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultKeyDomain
import dev.qtremors.arcile.core.vault.crypto.VaultManifestCodec
import dev.qtremors.arcile.core.vault.domain.VaultId
import java.io.File
import java.util.UUID

internal data class OpenAppPrivateVault(
    val id: VaultId,
    val access: FileVaultDirectory,
    val masterSecret: ByteArray
)

internal class VaultAppPrivateManager(private val root: File) {
    private val headerCodec = VaultManifestCodec()
    private val directoryCodec = VaultDirectoryManifestCodec()

    fun create(name: String, password: CharArray): OpenAppPrivateVault = try {
        createVault(name, password)
    } finally {
        password.fill('\u0000')
    }

    private fun createVault(name: String, password: CharArray): OpenAppPrivateVault {
        val label = validateVaultName(name)
        root.mkdirs()
        val id = VaultId.random()
        val staging = File(root, "$STAGING_PREFIX${UUID.randomUUID()}")
        val target = File(root, id.value)
        val masterSecret = VaultCryptography.randomBytes(VaultCryptography.KEY_SIZE_BYTES)
        var openedSecret: ByteArray? = null
        try {
            check(staging.mkdir()) { "Unable to create vault staging directory" }
            val access = FileVaultDirectory(staging)
            createVaultDirectories(access)
            headerCodec.create(access, id, label, System.currentTimeMillis(), password, masterSecret)
            val rootKey = VaultCryptography.deriveDomainKey(masterSecret, id, VaultKeyDomain.ROOT_DIRECTORY)
            try {
                directoryCodec.createRoot(access, id, VaultSessionRecord.ROOT_DIRECTORY_ID, rootKey)
            } finally {
                rootKey.fill(0)
            }
            val opened = headerCodec.open(access, password).getOrThrow()
            openedSecret = opened.masterKey
            verifyRoot(access, id, openedSecret)
            moveDirectory(staging, target)
            return OpenAppPrivateVault(id, FileVaultDirectory(target), openedSecret).also { openedSecret = null }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            target.deleteRecursively()
            throw error
        } finally {
            masterSecret.fill(0)
            openedSecret?.fill(0)
        }
    }

    private fun verifyRoot(access: FileVaultDirectory, id: VaultId, secret: ByteArray) {
        val rootKey = VaultCryptography.deriveDomainKey(secret, id, VaultKeyDomain.ROOT_DIRECTORY)
        try {
            directoryCodec.read(access, id, VaultSessionRecord.ROOT_DIRECTORY_ID, rootKey)
        } finally {
            rootKey.fill(0)
        }
    }
}
