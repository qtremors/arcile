package dev.qtremors.arcile.core.vault.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.*
import java.io.ByteArrayInputStream
import java.util.ArrayDeque
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class DefaultVaultRepository @Inject constructor(
    @param:ApplicationContext context: Context,
    dispatchers: ArcileDispatchers,
    @param:ApplicationScope applicationScope: CoroutineScope,
    portableLocationResolver: VaultPortableLocationResolver
) : VaultHealthLayer(context, dispatchers, applicationScope, portableLocationResolver),
    VaultFileSystem {
    init {
        applicationScope.launch(dispatchers.io) {
            cleanupInterruptedArtifacts(vaultRoot)
            refreshVaults()
        }
    }

    override suspend fun listDirectory(
        vaultId: VaultId,
        directoryId: DirectoryId,
        options: VaultListOptions
    ): Result<VaultPage<VaultNodeMetadata>> = withSession(vaultId) { session ->
        listDirectory(session, directoryId, options)
    }

    internal fun listDirectory(
        session: VaultSessionRecord,
        directoryId: DirectoryId,
        options: VaultListOptions
    ): VaultPage<VaultNodeMetadata> {
        val directory = session.resolveDirectory(directoryId)
        return try {
            val snapshot = session.readDirectory(directory)
            try {
                snapshot.entries.filter { entry ->
                    if (entry.kind == VaultNodeKind.DIRECTORY) {
                        session.cacheDirectoryKey(
                            requireNotNull(entry.childDirectoryId),
                            entry.protectedKey
                        )
                    }
                    true
                }.map { it.toMetadata(session.id, directoryId) }
                    .sortedWith(options.comparator())
                    .let { sorted ->
                        val offset = options.pageToken.toPageOffset(sorted.size)
                        val items = sorted.drop(offset).take(options.pageSize)
                        VaultPage(
                            items,
                            (offset + items.size).takeIf { it < sorted.size }?.toString(),
                            snapshot.generation
                        )
                    }
            } finally {
                snapshot.clearProtectedKeys()
            }
        } finally {
            directory.key.fill(0)
        }
    }

    override suspend fun metadata(ref: VaultNodeRef): Result<VaultNodeMetadata> =
        withSession(ref.vaultId) { session ->
            metadata(session, ref)
        }

    internal fun metadata(session: VaultSessionRecord, ref: VaultNodeRef): VaultNodeMetadata {
        val (parent, snapshot, entry) = resolveStableEntry(session, ref)
        return try {
            entry.toMetadata(session.id, parent.id)
        } finally {
            entry.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    override suspend fun search(
        vaultId: VaultId,
        directoryId: DirectoryId,
        query: VaultSearchQuery
    ): Result<VaultPage<VaultSearchHit>> = withSession(vaultId) { session ->
        val start = session.resolveDirectory(directoryId)
        val frames = ArrayDeque<SearchFrame>()
        frames += SearchFrame(start.id, start.key, emptyList())
        val requestedOffset = query.pageToken.toPageOffset(Int.MAX_VALUE)
        val matches = mutableListOf<VaultSearchHit>()
        var skipped = 0
        var generation = 0L
        try {
            searchLoop@ while (frames.isNotEmpty()) {
                val frame = frames.removeLast()
                try {
                    val snapshot = session.readDirectory(frame.directoryId, frame.key)
                    generation = maxOf(generation, snapshot.generation)
                    try {
                        for (entry in snapshot.entries) {
                            if (query.recursive && entry.kind == VaultNodeKind.DIRECTORY) {
                                val childId = requireNotNull(entry.childDirectoryId)
                                session.cacheDirectoryKey(childId, entry.protectedKey)
                                frames += SearchFrame(
                                    childId,
                                    entry.protectedKey.copyOf(),
                                    frame.parentNames + entry.name
                                )
                            }
                            if (entry.name.contains(query.text, ignoreCase = true)) {
                                if (skipped < requestedOffset) {
                                    skipped++
                                } else {
                                    matches += VaultSearchHit(
                                        entry.toMetadata(session.id, frame.directoryId),
                                        frame.parentNames
                                    )
                                    if (matches.size == query.pageSize) break@searchLoop
                                }
                            }
                        }
                    } finally {
                        snapshot.clearProtectedKeys()
                    }
                } finally {
                    frame.key.fill(0)
                }
                if (!query.recursive) break
            }
        } finally {
            while (frames.isNotEmpty()) frames.removeLast().key.fill(0)
        }
        VaultPage(
            matches,
            if (matches.size == query.pageSize) {
                (requestedOffset + matches.size).toString()
            } else null,
            generation
        )
    }

    override suspend fun createDirectory(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String
    ): Result<VaultNodeMetadata> = mutate(vaultId) { session ->
        createStableDirectory(session, parentId, normalizeMutationName(name))
    }

    override suspend fun createEmptyFile(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        mimeType: String?
    ): Result<VaultNodeMetadata> = mutate(vaultId) { session ->
        val parent = session.resolveDirectory(parentId)
        val snapshot = session.readDirectory(parent)
        val contentKey = VaultCryptography.randomBytes(32)
        val objectId = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32))
        val objectPath = objectId.shardedPath()
        try {
            val normalized = normalizeMutationName(name)
            ensureNameAvailable(snapshot, normalized)
            fileCodec.writeObject(
                session.directory,
                objectPath,
                session.id,
                objectId,
                1L,
                contentKey,
                ByteArrayInputStream(ByteArray(0))
            )
            val entry = VaultManifestEntry(
                NodeId.random(),
                normalized,
                VaultNodeKind.FILE,
                1L,
                System.currentTimeMillis(),
                0L,
                mimeType,
                objectId,
                null,
                contentKey.copyOf()
            )
            val prepared = directoryCodec.prepare(
                session.id,
                parent.id,
                parent.key,
                snapshot.generation + 1L,
                snapshot.entries + entry
            )
            transactionManager.commit(
                session.directory,
                session.id,
                session.masterSecret,
                listOf(VaultPreparedDirectory(prepared, parent.key.copyOf())),
                setOf(objectPath),
                emptySet()
            )
            entry.toMetadata(session.id, parent.id)
        } catch (error: Throwable) {
            if (!transactionManager.hasPendingCommit(session.directory)) {
                session.directory.delete(objectPath)
            }
            throw error
        } finally {
            contentKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    override suspend fun rename(ref: VaultNodeRef, newName: String): Result<VaultNodeMetadata> =
        mutate(ref.vaultId) { session ->
            val (parent, snapshot, existing) = resolveStableEntry(session, ref)
            try {
                val normalized = normalizeMutationName(newName)
                ensureNameAvailable(snapshot, normalized, existing.nodeId)
                val replacement = existing.copy(
                    name = normalized,
                    modifiedAtMillis = System.currentTimeMillis(),
                    protectedKey = existing.protectedKey.copyOf()
                )
                val entries = snapshot.entries.map {
                    if (it.nodeId == existing.nodeId) replacement else it
                }
                val prepared = directoryCodec.prepare(
                    session.id,
                    parent.id,
                    parent.key,
                    snapshot.generation + 1L,
                    entries
                )
                transactionManager.commit(
                    session.directory,
                    session.id,
                    session.masterSecret,
                    listOf(VaultPreparedDirectory(prepared, parent.key.copyOf())),
                    emptySet(),
                    emptySet()
                )
                replacement.toMetadata(session.id, parent.id)
            } finally {
                existing.protectedKey.fill(0)
                snapshot.clearProtectedKeys()
                parent.key.fill(0)
            }
        }

    override suspend fun deletePermanently(ref: VaultNodeRef): Result<Unit> =
        withSession(ref.vaultId) { session ->
            deletePermanently(session, ref)
        }

    internal suspend fun deletePermanently(session: VaultSessionRecord, ref: VaultNodeRef) =
        session.mutationMutex.withLock {
        val (parent, snapshot, existing) = resolveStableEntry(session, ref)
        try {
            val obsolete = collectObsoleteSubtree(session, existing)
            val prepared = directoryCodec.prepare(
                session.id,
                parent.id,
                parent.key,
                snapshot.generation + 1L,
                snapshot.entries.filterNot { it.nodeId == existing.nodeId }
            )
            transactionManager.commit(
                session.directory,
                session.id,
                session.masterSecret,
                listOf(VaultPreparedDirectory(prepared, parent.key.copyOf())),
                emptySet(),
                obsolete
            )
        } finally {
            existing.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    override fun openReader(ref: VaultNodeRef): Result<VaultSeekableReader> {
        val lease = holdSession(ref.vaultId).getOrElse { return Result.failure(it) }
        val session = sessions[ref.vaultId.value] ?: run {
            lease.close()
            return Result.failure(VaultFailure.Locked(ref.vaultId))
        }
        return try {
            val (parent, snapshot, entry) = resolveStableEntry(session, ref)
            try {
                if (entry.kind != VaultNodeKind.FILE) {
                    throw VaultFailure.InvalidPath("File is unavailable")
                }
                val objectId = requireNotNull(entry.objectId)
                val delegate = fileCodec.openObject(
                    session.directory,
                    objectId.shardedPath(),
                    session.id,
                    objectId,
                    entry.revision,
                    entry.protectedKey
                )
                val leased = VaultLeasedReader(delegate, lease, session::unregisterReader)
                if (!session.registerReader(leased)) {
                    leased.close()
                    throw VaultFailure.Locked(ref.vaultId)
                }
                Result.success(leased)
            } finally {
                entry.protectedKey.fill(0)
                snapshot.clearProtectedKeys()
                parent.key.fill(0)
            }
        } catch (error: Throwable) {
            lease.close()
            Result.failure(error)
        }
    }

    internal fun openReader(session: VaultSessionRecord, ref: VaultNodeRef): VaultSeekableReader {
        val (parent, snapshot, entry) = resolveStableEntry(session, ref)
        return try {
            if (entry.kind != VaultNodeKind.FILE) throw VaultFailure.InvalidPath("File is unavailable")
            val objectId = requireNotNull(entry.objectId)
            fileCodec.openObject(
                session.directory,
                objectId.shardedPath(),
                session.id,
                objectId,
                entry.revision,
                entry.protectedKey
            )
        } finally {
            entry.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    internal fun createBoundarySession(sources: List<VaultNodeRef>): VaultSessionRecord {
        val vaultId = sources.firstOrNull()?.vaultId
            ?: throw VaultFailure.InvalidPath("Select at least one vault item")
        if (sources.any { it.vaultId != vaultId }) {
            throw VaultFailure.InvalidPath("Export selections must belong to one vault")
        }
        val interactive = sessions[vaultId.value] ?: throw VaultFailure.Locked(vaultId)
        return interactive.copyForOperation()
    }

    internal fun reserveImport(vaultId: VaultId): String? {
        val interactive = sessions[vaultId.value] ?: return null
        val operation = runCatching { interactive.copyForOperation() }.getOrNull() ?: return null
        val token = UUID.randomUUID().toString()
        importReservations[token] = operation
        return token
    }

    internal fun createExternalAccessSession(
        ref: VaultNodeRef
    ): Pair<VaultSessionRecord, VaultNodeMetadata> {
        val interactive = sessions[ref.vaultId.value] ?: throw VaultFailure.Locked(ref.vaultId)
        val operation = interactive.copyForOperation()
        return try {
            val (parent, snapshot, entry) = resolveStableEntry(operation, ref)
            try {
                operation to entry.toMetadata(operation.id, parent.id)
            } finally {
                entry.protectedKey.fill(0)
                snapshot.clearProtectedKeys()
                parent.key.fill(0)
            }
        } catch (error: Throwable) {
            operation.destroy()
            throw error
        }
    }

    internal fun releaseImportReservation(token: String) {
        importReservations.remove(token)?.destroy()
    }

    internal suspend fun importUris(
        token: String,
        destination: VaultPath,
        sourceUris: List<String>,
        onProgress: (Int, Int, Long, Long?, String?) -> Unit
    ): Result<VaultBatchResult> = withContext(dispatchers.io) {
        val operationSession = importReservations[token]
            ?: return@withContext Result.failure(
                VaultFailure.ImportUnavailable("Import session expired")
            )
        try {
            Result.success(importEngine.import(operationSession, destination, sourceUris, onProgress))
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Result.failure(error)
        } finally {
            releaseImportReservation(token)
        }
    }
}
