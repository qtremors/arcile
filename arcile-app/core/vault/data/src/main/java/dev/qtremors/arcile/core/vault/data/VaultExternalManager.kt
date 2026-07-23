package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultKeyDomain
import dev.qtremors.arcile.core.vault.crypto.VaultManifestCodec
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultLocation

internal data class OpenExternalVault(
    val id: VaultId,
    val access: FileVaultDirectory,
    val name: String,
    val createdAtMillis: Long,
    val headerFingerprint: String,
    val location: VaultLocation.Portable,
    val masterSecret: ByteArray? = null
)

internal class VaultExternalManager(private val registry: VaultLocationRegistry) {
    private val headerCodec = VaultManifestCodec()
    private val directoryCodec = VaultDirectoryManifestCodec()

    fun create(target: ResolvedPortableVault, name: String, password: CharArray): OpenExternalVault = try {
        createVault(target, name, password)
    } finally {
        password.fill('\u0000')
    }

    private fun createVault(target: ResolvedPortableVault, name: String, password: CharArray): OpenExternalVault {
        val label = validateVaultName(name)
        val access = target.access
        if (access.directory.listFiles().orEmpty().isNotEmpty()) throw VaultFailure.FolderNotEmpty()

        val id = VaultId.random()
        val createdAt = System.currentTimeMillis()
        val masterSecret = VaultCryptography.randomBytes(VaultCryptography.KEY_SIZE_BYTES)
        var openedSecret: ByteArray? = null
        try {
            createVaultDirectories(access)
            headerCodec.create(access, id, label, createdAt, password, masterSecret)
            val rootKey = VaultCryptography.deriveDomainKey(masterSecret, id, VaultKeyDomain.ROOT_DIRECTORY)
            try {
                directoryCodec.createRoot(access, id, VaultSessionRecord.ROOT_DIRECTORY_ID, rootKey)
            } finally {
                rootKey.fill(0)
            }
            val opened = headerCodec.open(access, password).getOrThrow()
            openedSecret = opened.masterKey
            verifyRoot(access, id, openedSecret)
            val result = OpenExternalVault(
                id,
                access,
                label,
                createdAt,
                opened.headerFingerprint,
                target.location,
                openedSecret
            ).also { openedSecret = null }
            register(result)
            return result
        } catch (error: Throwable) {
            access.directory.listFiles().orEmpty().forEach { it.deleteRecursively() }
            throw error
        } finally {
            masterSecret.fill(0)
            openedSecret?.fill(0)
        }
    }

    fun attach(target: ResolvedPortableVault): OpenExternalVault {
        val access = target.access
        val manifest = headerCodec.readPublic(access).getOrElse {
            throw VaultFailure.Unavailable("The selected folder is not an OnlyFiles vault", it)
        }
        if (!access.exists(OBJECTS_DIRECTORY) || !access.exists(MANIFESTS_DIRECTORY) ||
            (!access.exists(VaultDirectoryManifestCodec.rootSlot(VaultSessionRecord.ROOT_DIRECTORY_ID, 0L)) &&
                !access.exists(VaultDirectoryManifestCodec.rootSlot(VaultSessionRecord.ROOT_DIRECTORY_ID, 1L)))
        ) {
            throw VaultFailure.IntegrityFailed("The selected vault is incomplete")
        }
        return OpenExternalVault(
            manifest.id,
            access,
            manifest.publicName,
            manifest.createdAtMillis,
            manifest.headerFingerprint,
            target.location
        )
    }

    fun register(vault: OpenExternalVault) {
        val existing = registry.find(vault.id)
        if (existing != null) throw VaultFailure.DuplicateVault(vault.id)
        registry.put(
            ExternalVaultPointer(
                vaultId = vault.id.value,
                volumeId = vault.location.volumeId,
                relativePath = vault.location.relativePath,
                cachedName = vault.name,
                cachedCreatedAtMillis = vault.createdAtMillis,
                headerFingerprint = vault.headerFingerprint
            )
        )
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
