package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultIndex
import dev.qtremors.arcile.core.vault.crypto.VaultIndexCodec
import dev.qtremors.arcile.core.vault.crypto.VaultManifestCodec
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import java.io.File
import java.util.UUID

internal data class OpenAppPrivateVault(
    val id: VaultId,
    val access: FileVaultDirectory,
    val masterKey: ByteArray,
    val index: VaultIndex
)

internal class VaultAppPrivateManager(private val root: File) {
    private val manifestCodec = VaultManifestCodec()
    private val indexCodec = VaultIndexCodec()

    fun create(name: String, password: CharArray): OpenAppPrivateVault = try {
        createVault(name, password)
    } finally {
        password.fill('\u0000')
    }

    private fun createVault(name: String, password: CharArray): OpenAppPrivateVault {
        val cleanName = validateVaultName(name)
        root.mkdirs()
        val target = File(root, cleanName)
        if (target.exists() || root.listFiles().orEmpty().any { it.name.equals(cleanName, ignoreCase = true) }) {
            throw VaultFailure.NameConflict(cleanName)
        }
        val staging = File(root, "$STAGING_PREFIX${UUID.randomUUID()}")
        val id = VaultId.of(UUID.randomUUID().toString())
        val masterKey = VaultCryptography.randomBytes(VaultCryptography.KEY_SIZE_BYTES)
        var openedKey: ByteArray? = null
        try {
            check(staging.mkdir()) { "Unable to create vault staging directory" }
            check(File(staging, OBJECTS_DIRECTORY).mkdir()) { "Unable to create vault object directory" }
            manifestCodec.create(staging, id, cleanName, System.currentTimeMillis(), password, masterKey)
            indexCodec.create(staging, id, cleanName, masterKey)
            val opened = manifestCodec.open(staging, password).getOrThrow()
            openedKey = opened.masterKey
            val index = indexCodec.read(staging, id, openedKey)
            require(index.vaultName == cleanName)
            moveDirectory(staging, target)
            return OpenAppPrivateVault(id, FileVaultDirectory(target), openedKey, index).also { openedKey = null }
        } catch (error: Throwable) {
            staging.deleteRecursively()
            throw error
        } finally {
            masterKey.fill(0)
            openedKey?.fill(0)
        }
    }
}
