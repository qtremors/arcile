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

internal data class OpenExternalVault(
    val id: VaultId,
    val access: FileVaultDirectory,
    val name: String,
    val createdAtMillis: Long,
    val masterKey: ByteArray? = null,
    val index: VaultIndex? = null
)

internal class VaultExternalManager(private val registry: VaultLocationRegistry) {
    private val manifestCodec = VaultManifestCodec()
    private val indexCodec = VaultIndexCodec()

    fun create(path: String, name: String, password: CharArray): OpenExternalVault = try {
        createVault(path, name, password)
    } finally {
        password.fill('\u0000')
    }

    private fun createVault(path: String, name: String, password: CharArray): OpenExternalVault {
        val cleanName = validateVaultName(name)
        val access = portableDirectory(path)
        val reserved = reservedNames()
        if (reserved.any(access::exists)) {
            val existingName = manifestCodec.readPublic(access).getOrNull()?.publicName
            throw if (existingName != null) VaultFailure.NameConflict(existingName)
            else VaultFailure.Unavailable("The selected folder contains incomplete vault data")
        }

        val id = VaultId.of(UUID.randomUUID().toString())
        val createdAt = System.currentTimeMillis()
        val masterKey = VaultCryptography.randomBytes(VaultCryptography.KEY_SIZE_BYTES)
        var openedKey: ByteArray? = null
        try {
            check(access.createDirectory(OBJECTS_DIRECTORY)) { "Unable to create the vault object directory" }
            manifestCodec.create(access, id, cleanName, createdAt, password, masterKey)
            indexCodec.create(access, id, cleanName, masterKey)
            val opened = manifestCodec.open(access, password).getOrThrow()
            openedKey = opened.masterKey
            val index = indexCodec.read(access, id, openedKey)
            require(index.vaultName == cleanName)
            register(id, access.directory, cleanName, createdAt)
            return OpenExternalVault(id, access, cleanName, createdAt, openedKey, index).also { openedKey = null }
        } catch (error: Throwable) {
            reserved.forEach { runCatching { access.delete(it) } }
            throw error
        } finally {
            masterKey.fill(0)
            openedKey?.fill(0)
        }
    }

    fun attach(path: String): OpenExternalVault {
        val access = portableDirectory(path)
        val manifest = manifestCodec.readPublic(access).getOrElse {
            throw VaultFailure.Unavailable("The selected folder is not an OnlyFiles vault")
        }
        if (!access.exists(OBJECTS_DIRECTORY) ||
            (!access.exists(VaultIndexCodec.SLOT_A) && !access.exists(VaultIndexCodec.SLOT_B))
        ) {
            throw VaultFailure.IntegrityFailed("The selected vault is incomplete")
        }
        return OpenExternalVault(manifest.id, access, manifest.publicName, manifest.createdAtMillis)
    }

    fun register(vault: OpenExternalVault) {
        register(vault.id, vault.access.directory, vault.name, vault.createdAtMillis)
    }

    private fun register(id: VaultId, directory: File, name: String, createdAtMillis: Long) {
        registry.put(
            ExternalVaultPointer(
                vaultId = id.value,
                path = directory.canonicalPath,
                cachedName = name,
                cachedCreatedAtMillis = createdAtMillis
            )
        )
    }

    private fun portableDirectory(path: String): FileVaultDirectory {
        val directory = File(path).canonicalFile
        if (!directory.isDirectory || !directory.canRead() || !directory.canWrite()) {
            throw VaultFailure.Unavailable("The selected folder is unavailable or read-only")
        }
        return FileVaultDirectory(directory)
    }

    private fun reservedNames() = listOf(
        VaultManifestCodec.PRIMARY_FILE,
        VaultManifestCodec.BACKUP_FILE,
        VaultIndexCodec.SLOT_A,
        VaultIndexCodec.SLOT_B,
        OBJECTS_DIRECTORY
    )
}
