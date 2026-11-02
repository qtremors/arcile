package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryAccess
import dev.qtremors.arcile.core.vault.crypto.VaultRandomAccess
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult

/** Encrypted manifests are readable by the existing transfer planner before they are published. */
internal class VaultUnpublishedArchive(
    private val storage: VaultDirectoryAccess,
    prepared: List<VaultPreparedDirectory>
) : VaultDirectoryAccess by storage {
    private val manifests = buildMap {
        for (directory in prepared) {
            put(directory.manifest.rootRelativePath, directory.manifest.rootBytes)
            for (page in directory.manifest.pages) put(page.relativePath, page.bytes)
        }
    }

    override fun exists(relativePath: String): Boolean = relativePath in manifests || storage.exists(relativePath)
    override fun readBytes(relativePath: String): ByteArray = manifests[relativePath]?.copyOf() ?: storage.readBytes(relativePath)
    override fun openRandom(relativePath: String, writable: Boolean): VaultRandomAccess {
        check(!writable) { "Unpublished archives are read-only" }
        return storage.openRandom(relativePath, false)
    }
    override fun writeAtomic(relativePath: String, bytes: ByteArray): Unit = error("Unpublished archives are read-only")
    override fun createDirectory(relativePath: String): Boolean = error("Unpublished archives are read-only")
    override fun delete(relativePath: String): Boolean = error("Unpublished archives are read-only")
    override fun rename(fromRelativePath: String, toName: String): Boolean = error("Unpublished archives are read-only")
}

/** The caller commits these roots once, or discards the unpublished archive and prepared keys. */
internal data class VaultArchiveMergePlan(
    val prepared: List<VaultPreparedDirectory>,
    val newObjects: Set<String>,
    val obsolete: Set<String>,
    val result: VaultBatchResult
) : AutoCloseable {
    override fun close() { prepared.forEach { it.directoryKey.fill(0) } }
}
