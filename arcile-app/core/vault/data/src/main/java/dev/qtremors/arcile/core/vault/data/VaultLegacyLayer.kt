package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.withContext

internal abstract class VaultLegacyLayer(
    context: Context,
    dispatchers: ArcileDispatchers,
    applicationScope: CoroutineScope,
    portableLocationResolver: VaultPortableLocationResolver
) : VaultTransferLayer(context, dispatchers, applicationScope, portableLocationResolver) {
    override suspend fun list(vaultId: VaultId, directory: VaultPath): Result<List<VaultNode>> =
        withSession(vaultId) { session ->
            val resolved = session.resolveDirectory(directory)
            try {
                val snapshot = session.readDirectory(resolved)
                try {
                    snapshot.entries.map { it.toLegacyNode(directory) }
                        .sortedWith(
                            compareByDescending<VaultNode> { it.isDirectory }
                                .thenBy { it.name.lowercase() }
                        )
                } finally {
                    snapshot.clearProtectedKeys()
                }
            } finally {
                resolved.key.fill(0)
            }
        }

    override suspend fun createDirectory(
        vaultId: VaultId,
        parent: VaultPath,
        name: String
    ): Result<VaultNode> = mutate(vaultId) { session ->
        val normalizedName = normalizeMutationName(name)
        val resolved = session.resolveDirectory(parent)
        val snapshot = session.readDirectory(resolved)
        val childKey = VaultCryptography.randomBytes(VaultCryptography.KEY_SIZE_BYTES)
        val childId = DirectoryId.random()
        try {
            ensureNameAvailable(snapshot, normalizedName)
            val entry = VaultManifestEntry(
                nodeId = NodeId.random(),
                name = normalizedName,
                kind = VaultNodeKind.DIRECTORY,
                revision = 1L,
                modifiedAtMillis = System.currentTimeMillis(),
                sizeBytes = 0L,
                mimeType = null,
                objectId = null,
                childDirectoryId = childId,
                protectedKey = childKey.copyOf()
            )
            val parentPrepared = directoryCodec.prepare(
                session.id,
                resolved.id,
                resolved.key,
                snapshot.generation + 1L,
                snapshot.entries + entry
            )
            val childPrepared = directoryCodec.prepare(session.id, childId, childKey, 0L, emptyList())
            session.cacheDirectoryKey(childId, childKey)
            transactionManager.commit(
                session.directory,
                session.id,
                session.masterSecret,
                listOf(
                    VaultPreparedDirectory(parentPrepared, resolved.key.copyOf()),
                    VaultPreparedDirectory(childPrepared, childKey.copyOf())
                ),
                emptySet(),
                emptySet()
            )
            entry.toLegacyNode(parent)
        } finally {
            childKey.fill(0)
            snapshot.clearProtectedKeys()
            resolved.key.fill(0)
        }
    }

    override suspend fun rename(
        vaultId: VaultId,
        path: VaultPath,
        newName: String
    ): Result<VaultNode> = mutate(vaultId) { session ->
        val resolved = session.resolveEntry(path)
        val snapshot = session.readDirectory(resolved.parent)
        try {
            val normalizedName = normalizeMutationName(newName)
            ensureNameAvailable(snapshot, normalizedName, except = resolved.entry.nodeId)
            val now = System.currentTimeMillis()
            val entries = snapshot.entries.map { entry ->
                if (entry.nodeId == resolved.entry.nodeId) {
                    entry.copy(
                        name = normalizedName,
                        modifiedAtMillis = now,
                        protectedKey = entry.protectedKey.copyOf()
                    )
                } else entry
            }
            val prepared = directoryCodec.prepare(
                session.id,
                resolved.parent.id,
                resolved.parent.key,
                snapshot.generation + 1L,
                entries
            )
            transactionManager.commit(
                session.directory,
                session.id,
                session.masterSecret,
                listOf(VaultPreparedDirectory(prepared, resolved.parent.key.copyOf())),
                emptySet(),
                emptySet()
            )
            entries.first { it.nodeId == resolved.entry.nodeId }
                .toLegacyNode(requireNotNull(path.parent))
        } finally {
            resolved.entry.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            resolved.parent.key.fill(0)
        }
    }

    override suspend fun delete(vaultId: VaultId, path: VaultPath): Result<Unit> =
        mutate(vaultId) { session ->
            val resolved = session.resolveEntry(path)
            val parentSnapshot = session.readDirectory(resolved.parent)
            try {
                val obsolete = collectObsoleteSubtree(session, resolved.entry)
                val remaining = parentSnapshot.entries.filterNot { it.nodeId == resolved.entry.nodeId }
                val prepared = directoryCodec.prepare(
                    session.id,
                    resolved.parent.id,
                    resolved.parent.key,
                    parentSnapshot.generation + 1L,
                    remaining
                )
                transactionManager.commit(
                    session.directory,
                    session.id,
                    session.masterSecret,
                    listOf(VaultPreparedDirectory(prepared, resolved.parent.key.copyOf())),
                    emptySet(),
                    obsolete
                )
            } finally {
                resolved.entry.protectedKey.fill(0)
                parentSnapshot.clearProtectedKeys()
                resolved.parent.key.fill(0)
            }
        }

    override suspend fun readBytes(
        vaultId: VaultId,
        path: VaultPath,
        maximumBytes: Long
    ): Result<ByteArray> = withContext(dispatchers.io) {
        val reader = openReader(vaultId, path).getOrElse { return@withContext Result.failure(it) }
        reader.use {
            if (it.sizeBytes > maximumBytes || it.sizeBytes > Int.MAX_VALUE) {
                return@withContext Result.failure(VaultFailure.FileTooLarge(it.sizeBytes, maximumBytes))
            }
            val output = ByteArray(it.sizeBytes.toInt())
            var offset = 0
            while (offset < output.size) {
                val count = it.readAt(offset.toLong(), output, offset, output.size - offset)
                if (count <= 0) {
                    return@withContext Result.failure(
                        VaultFailure.IntegrityFailed("Encrypted file ended before its authenticated size")
                    )
                }
                offset += count
            }
            Result.success(output)
        }
    }

    override fun openReader(vaultId: VaultId, path: VaultPath): Result<VaultSeekableReader> {
        val lease = holdSession(vaultId).getOrElse { return Result.failure(it) }
        val session = sessions[vaultId.value]
        if (session == null) {
            lease.close()
            return Result.failure(VaultFailure.Locked(vaultId))
        }
        return try {
            val resolved = session.resolveEntry(path)
            try {
                if (resolved.entry.kind != VaultNodeKind.FILE) {
                    throw VaultFailure.InvalidPath("File is unavailable")
                }
                val objectId = requireNotNull(resolved.entry.objectId)
                val reader = fileCodec.openObject(
                    session.directory,
                    objectId.shardedPath(),
                    session.id,
                    objectId,
                    resolved.entry.revision,
                    resolved.entry.protectedKey
                )
                val leased = VaultLeasedReader(reader, lease, session::unregisterReader)
                if (!session.registerReader(leased)) {
                    leased.close()
                    throw VaultFailure.Locked(vaultId)
                }
                Result.success(leased)
            } finally {
                resolved.entry.protectedKey.fill(0)
                resolved.parent.key.fill(0)
            }
        } catch (error: Throwable) {
            lease.close()
            Result.failure(error)
        }
    }
}
