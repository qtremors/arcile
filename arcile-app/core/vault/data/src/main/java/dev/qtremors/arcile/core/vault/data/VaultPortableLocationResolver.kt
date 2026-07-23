package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.vault.crypto.FileVaultDirectory
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultLocation
import java.io.File
import javax.inject.Inject

internal data class ResolvedPortableVault(
    val location: VaultLocation.Portable,
    val access: FileVaultDirectory
)

class VaultPortableLocationResolver @Inject constructor(
    private val volumes: VolumeRepository
) {
    internal suspend fun identify(absolutePath: String): ResolvedPortableVault {
        val target = File(absolutePath).canonicalFile
        val volume = volumes.getVolumeForPath(target.path).getOrElse {
            throw VaultFailure.Unavailable("The selected folder is outside Arcile's managed storage", it)
        }
        return resolve(volume, relativePath(volume, target))
    }

    internal suspend fun resolve(pointer: ExternalVaultPointer): ResolvedPortableVault {
        if (pointer.volumeId.isBlank() || pointer.relativePath.isBlank()) {
            val legacy = pointer.path?.takeIf(String::isNotBlank)
                ?: throw VaultFailure.StaleRegistration()
            return identify(legacy)
        }
        val volume = volumes.getStorageVolumes().getOrElse {
            throw VaultFailure.Unavailable("Storage volumes could not be read", it)
        }.firstOrNull { it.id == pointer.volumeId }
            ?: throw VaultFailure.RemovableStorageMissing(pointer.volumeId)
        return resolve(volume, pointer.relativePath)
    }

    private fun resolve(volume: StorageVolume, relativePath: String): ResolvedPortableVault {
        val normalized = normalizeRelativePath(relativePath)
        val root = File(volume.path).canonicalFile
        val target = File(root, normalized).canonicalFile
        requireDescendant(root, target)
        if (!target.isDirectory) throw VaultFailure.Unavailable("The registered vault folder is missing")
        if (!target.canRead() || !target.canWrite()) {
            throw VaultFailure.Unavailable("The registered vault folder is unavailable or read-only")
        }
        return ResolvedPortableVault(
            VaultLocation.Portable(volume.id, normalized.replace(File.separatorChar, '/')),
            FileVaultDirectory(target)
        )
    }

    private fun relativePath(volume: StorageVolume, target: File): String {
        val root = File(volume.path).canonicalFile
        requireDescendant(root, target)
        val relative = target.relativeTo(root).path
        if (relative.isBlank() || relative == ".") {
            throw VaultFailure.InvalidPath("A portable vault must use a folder below the storage-volume root")
        }
        return normalizeRelativePath(relative)
    }

    private fun requireDescendant(root: File, target: File) {
        val prefix = root.path.trimEnd(File.separatorChar) + File.separator
        if (!target.path.startsWith(prefix, ignoreCase = true)) {
            throw VaultFailure.InvalidPath("The vault path escapes its registered storage volume")
        }
    }

    private fun normalizeRelativePath(value: String): String {
        if (value.indexOf('\u0000') >= 0) throw VaultFailure.InvalidPath("The vault path contains a null character")
        val segments = value.replace('\\', '/').split('/').filter(String::isNotBlank)
        if (segments.isEmpty() || segments.any { it == "." || it == ".." }) {
            throw VaultFailure.InvalidPath("The vault path is not a safe relative path")
        }
        return segments.joinToString(File.separator)
    }
}
