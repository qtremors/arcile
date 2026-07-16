package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryAccess
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultDirectorySnapshot
import dev.qtremors.arcile.core.vault.crypto.VaultKeyDomain
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultPath
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap
import java.io.Closeable

internal data class ResolvedVaultDirectory(
    val id: DirectoryId,
    val key: ByteArray,
    val path: VaultPath
)

internal data class ResolvedVaultEntry(
    val parent: ResolvedVaultDirectory,
    val entry: VaultManifestEntry,
    val path: VaultPath
)

internal class VaultSessionRecord(
    val id: VaultId,
    val directory: VaultDirectoryAccess,
    val masterSecret: ByteArray,
    private val manifestCodec: VaultDirectoryManifestCodec = VaultDirectoryManifestCodec()
) {
    val mutationMutex = Mutex()
    var holdCount: Int = 0
    var lockRequested: Boolean = false
    private val directoryKeys = ConcurrentHashMap<String, ByteArray>()
    private val activeReaders = ConcurrentHashMap.newKeySet<Closeable>()

    init {
        require(masterSecret.size == VaultCryptography.KEY_SIZE_BYTES)
        directoryKeys[ROOT_DIRECTORY_ID.value] = VaultCryptography.deriveDomainKey(
            masterSecret,
            id,
            VaultKeyDomain.ROOT_DIRECTORY
        )
    }

    fun root(): ResolvedVaultDirectory = ResolvedVaultDirectory(
        ROOT_DIRECTORY_ID,
        requireNotNull(directoryKeys[ROOT_DIRECTORY_ID.value]).copyOf(),
        VaultPath.Root
    )

    fun resolveDirectory(path: VaultPath): ResolvedVaultDirectory {
        if (path.isRoot) return root()
        var current = root()
        try {
            var currentPath = VaultPath.Root
            path.value.split('/').forEach { segment ->
                val snapshot = readDirectory(current.id, current.key)
                val entry = try {
                    snapshot.entries.firstOrNull {
                        it.kind == VaultNodeKind.DIRECTORY &&
                            VaultName.of(it.name).comparisonKey == VaultName.of(segment).comparisonKey
                    }?.copyDefensively() ?: throw VaultFailure.InvalidPath("Folder is unavailable")
                } finally {
                    snapshot.clearProtectedKeys()
                }
                val childId = requireNotNull(entry.childDirectoryId)
                val childKey = cachedDirectoryKey(childId, entry.protectedKey)
                current.key.fill(0)
                currentPath = currentPath.resolve(entry.name)
                current = ResolvedVaultDirectory(childId, childKey, currentPath)
            }
            return current
        } catch (error: Throwable) {
            current.key.fill(0)
            throw error
        }
    }

    fun resolveEntry(path: VaultPath): ResolvedVaultEntry {
        if (path.isRoot) throw VaultFailure.InvalidPath("The vault root is not an item")
        val parentPath = requireNotNull(path.parent)
        val parent = resolveDirectory(parentPath)
        try {
            val snapshot = readDirectory(parent.id, parent.key)
            val entry = try {
                val key = VaultName.of(path.name).comparisonKey
                snapshot.entries.firstOrNull { VaultName.of(it.name).comparisonKey == key }
                    ?.copyDefensively()
                    ?: throw VaultFailure.InvalidPath("Item is unavailable")
            } finally {
                snapshot.clearProtectedKeys()
            }
            return ResolvedVaultEntry(parent, entry, parentPath.resolve(entry.name))
        } catch (error: Throwable) {
            parent.key.fill(0)
            throw error
        }
    }

    fun resolveDirectory(directoryId: DirectoryId): ResolvedVaultDirectory {
        directoryKeys[directoryId.value]?.let { cached ->
            return ResolvedVaultDirectory(directoryId, cached.copyOf(), VaultPath.Root)
        }
        val queue = java.util.ArrayDeque<Pair<DirectoryId, ByteArray>>()
        val visited = mutableSetOf<DirectoryId>()
        queue += ROOT_DIRECTORY_ID to requireNotNull(directoryKeys[ROOT_DIRECTORY_ID.value]).copyOf()
        try {
            while (queue.isNotEmpty()) {
            val (currentId, currentKey) = queue.removeFirst()
            if (!visited.add(currentId)) {
                currentKey.fill(0)
                continue
            }
            if (currentId == directoryId) return ResolvedVaultDirectory(currentId, currentKey, VaultPath.Root)
            try {
                val snapshot = readDirectory(currentId, currentKey)
                try {
                    snapshot.entries.asSequence().filter { it.kind == VaultNodeKind.DIRECTORY }.forEach { entry ->
                        val childId = requireNotNull(entry.childDirectoryId)
                        cacheDirectoryKey(childId, entry.protectedKey)
                        queue += childId to entry.protectedKey.copyOf()
                    }
                } finally {
                    snapshot.clearProtectedKeys()
                }
            } finally {
                currentKey.fill(0)
            }
            }
        } finally {
            while (queue.isNotEmpty()) queue.removeFirst().second.fill(0)
        }
        throw VaultFailure.InvalidPath("Folder is unavailable")
    }

    fun readDirectory(resolved: ResolvedVaultDirectory): VaultDirectorySnapshot =
        readDirectory(resolved.id, resolved.key)

    fun readDirectory(directoryId: DirectoryId, key: ByteArray): VaultDirectorySnapshot =
        manifestCodec.read(directory, id, directoryId, key)

    fun cacheDirectoryKey(directoryId: DirectoryId, key: ByteArray) {
        require(key.size == VaultCryptography.KEY_SIZE_BYTES)
        directoryKeys.putIfAbsent(directoryId.value, key.copyOf())
    }

    private fun cachedDirectoryKey(directoryId: DirectoryId, protectedKey: ByteArray): ByteArray {
        val cached = directoryKeys[directoryId.value]
        if (cached != null) return cached.copyOf()
        cacheDirectoryKey(directoryId, protectedKey)
        return protectedKey.copyOf()
    }

    fun destroy() {
        activeReaders.toList().forEach { runCatching { it.close() } }
        activeReaders.clear()
        masterSecret.fill(0)
        directoryKeys.values.forEach { it.fill(0) }
        directoryKeys.clear()
    }

    fun registerReader(reader: Closeable): Boolean = synchronized(this) {
        if (lockRequested || masterSecret.all { it == 0.toByte() }) false else activeReaders.add(reader)
    }

    fun unregisterReader(reader: Closeable) {
        activeReaders.remove(reader)
    }

    fun copyForOperation(): VaultSessionRecord = synchronized(this) {
        check(!lockRequested) { "Interactive vault session is closing" }
        VaultSessionRecord(id, directory, masterSecret.copyOf(), manifestCodec)
    }

    companion object {
        val ROOT_DIRECTORY_ID: DirectoryId = DirectoryId.of("root")
    }
}

internal fun VaultDirectorySnapshot.clearProtectedKeys() {
    entries.forEach { it.protectedKey.fill(0) }
}
