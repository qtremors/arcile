package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultFileCodec
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.InputStream
import java.util.ArrayDeque

internal class VaultTransferEngine(
    private val directoryCodec: VaultDirectoryManifestCodec,
    private val fileCodec: VaultFileCodec,
    private val transactions: VaultTransactionManager
) {
    fun deleteOne(session: VaultSessionRecord, ref: VaultNodeRef) {
        val resolved = resolve(session, ref)
        try {
            val prepared = directoryCodec.prepare(
                session.id,
                resolved.parent.id,
                resolved.parent.key,
                resolved.snapshot.generation + 1L,
                resolved.snapshot.entries.filterNot { it.nodeId == resolved.entry.nodeId }
            )
            transactions.commit(
                session.directory,
                session.id,
                session.masterSecret,
                listOf(VaultPreparedDirectory(prepared, resolved.parent.key.copyOf())),
                emptySet(),
                collectObsolete(session, resolved.entry)
            )
        } finally {
            resolved.close()
        }
    }

    suspend fun copyOne(
        source: VaultSessionRecord,
        ref: VaultNodeRef,
        destination: VaultSessionRecord,
        destinationId: DirectoryId,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultItemResult {
        val resolved = resolve(source, ref)
        try {
            cancellation.throwIfCancelled()
            val destinationDirectory = destination.resolveDirectory(destinationId)
            val destinationSnapshot = destination.readDirectory(destinationDirectory)
            val context = CloneContext(destination, conflicts, cancellation)
            try {
                val existing = findByName(destinationSnapshot.entries, resolved.entry.name)
                val decision = existing?.let {
                    conflicts.decide(it.conflictWith(resolved.entry))
                }
                if (decision == VaultConflictDecision.SKIP) return result(ref, resolved.entry, VaultItemOutcome.SKIPPED)

                val replacementName = when (decision) {
                    VaultConflictDecision.KEEP_BOTH -> uniqueName(resolved.entry.name, destinationSnapshot.entries)
                    else -> resolved.entry.name
                }
                val cloned = if (
                    decision == VaultConflictDecision.MERGE_DIRECTORIES &&
                    existing?.kind == VaultNodeKind.DIRECTORY && resolved.entry.kind == VaultNodeKind.DIRECTORY
                ) {
                    context.cloneMergedDirectory(existing, destination, resolved.entry, source, replacementName)
                } else {
                    context.cloneEntry(resolved.entry, source, replacementName)
                }
                val obsolete = if (existing != null && decision in setOf(
                        VaultConflictDecision.REPLACE,
                        VaultConflictDecision.MERGE_DIRECTORIES
                    )) collectObsolete(destination, existing) else emptySet()
                val next = destinationSnapshot.entries
                    .filterNot { existing != null && it.nodeId == existing.nodeId && decision != VaultConflictDecision.KEEP_BOTH }
                    .map(VaultManifestEntry::copyDefensively) + cloned
                try {
                    context.prepared += VaultPreparedDirectory(
                        directoryCodec.prepare(
                            destination.id,
                            destinationDirectory.id,
                            destinationDirectory.key,
                            destinationSnapshot.generation + 1L,
                            next
                        ),
                        destinationDirectory.key.copyOf()
                    )
                } finally {
                    next.forEach { it.protectedKey.fill(0) }
                }
                context.commit(obsolete)
                return result(ref, resolved.entry, VaultItemOutcome.COMPLETED)
            } catch (error: Throwable) {
                context.rollbackIfUncommitted()
                throw error
            } finally {
                destinationSnapshot.clearProtectedKeys()
                destinationDirectory.key.fill(0)
                context.close()
            }
        } finally {
            resolved.close()
        }
    }

    suspend fun moveOneWithinVault(
        session: VaultSessionRecord,
        ref: VaultNodeRef,
        destinationId: DirectoryId,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal
    ): VaultItemResult {
        val resolved = resolve(session, ref)
        try {
            cancellation.throwIfCancelled()
            if (resolved.parent.id == destinationId) return result(ref, resolved.entry, VaultItemOutcome.COMPLETED)
            if (resolved.entry.kind == VaultNodeKind.DIRECTORY &&
                subtreeDirectoryIds(session, resolved.entry).contains(destinationId)
            ) throw VaultFailure.InvalidPath("A folder cannot be moved inside itself")

            val destination = session.resolveDirectory(destinationId)
            val destinationSnapshot = session.readDirectory(destination)
            val context = CloneContext(session, conflicts, cancellation)
            try {
                val existing = findByName(destinationSnapshot.entries, resolved.entry.name)
                val decision = existing?.let { conflicts.decide(it.conflictWith(resolved.entry)) }
                if (decision == VaultConflictDecision.SKIP) return result(ref, resolved.entry, VaultItemOutcome.SKIPPED)
                val useClone = decision == VaultConflictDecision.MERGE_DIRECTORIES &&
                    existing?.kind == VaultNodeKind.DIRECTORY && resolved.entry.kind == VaultNodeKind.DIRECTORY
                val moved = when {
                    useClone -> context.cloneMergedDirectory(
                        requireNotNull(existing), session, resolved.entry, session, resolved.entry.name
                    )
                    decision == VaultConflictDecision.KEEP_BOTH -> resolved.entry.copy(
                        name = uniqueName(resolved.entry.name, destinationSnapshot.entries),
                        modifiedAtMillis = System.currentTimeMillis(),
                        protectedKey = resolved.entry.protectedKey.copyOf()
                    )
                    else -> resolved.entry.copy(
                        modifiedAtMillis = System.currentTimeMillis(),
                        protectedKey = resolved.entry.protectedKey.copyOf()
                    )
                }
                val destinationEntries = destinationSnapshot.entries
                    .filterNot { existing != null && it.nodeId == existing.nodeId && decision != VaultConflictDecision.KEEP_BOTH }
                    .map(VaultManifestEntry::copyDefensively) + moved
                val sourceEntries = resolved.snapshot.entries
                    .filterNot { it.nodeId == resolved.entry.nodeId }
                    .map(VaultManifestEntry::copyDefensively)
                try {
                    context.prepared += VaultPreparedDirectory(
                        directoryCodec.prepare(
                            session.id, destination.id, destination.key,
                            destinationSnapshot.generation + 1L, destinationEntries
                        ),
                        destination.key.copyOf()
                    )
                    context.prepared += VaultPreparedDirectory(
                        directoryCodec.prepare(
                            session.id, resolved.parent.id, resolved.parent.key,
                            resolved.snapshot.generation + 1L, sourceEntries
                        ),
                        resolved.parent.key.copyOf()
                    )
                } finally {
                    destinationEntries.forEach { it.protectedKey.fill(0) }
                    sourceEntries.forEach { it.protectedKey.fill(0) }
                }
                val obsolete = buildSet {
                    if (existing != null && decision != VaultConflictDecision.KEEP_BOTH) addAll(collectObsolete(session, existing))
                    if (useClone) addAll(collectObsolete(session, resolved.entry))
                }
                context.commit(obsolete)
                return result(ref, resolved.entry, VaultItemOutcome.COMPLETED)
            } catch (error: Throwable) {
                context.rollbackIfUncommitted()
                throw error
            } finally {
                context.close()
                destinationSnapshot.clearProtectedKeys()
                destination.key.fill(0)
            }
        } finally {
            resolved.close()
        }
    }

    private inner class CloneContext(
        private val destination: VaultSessionRecord,
        private val conflicts: VaultConflictResolver,
        private val cancellation: VaultCancellationSignal
    ) : AutoCloseable {
        val prepared = mutableListOf<VaultPreparedDirectory>()
        val newObjects = linkedSetOf<String>()
        private var committed = false

        suspend fun cloneEntry(
            entry: VaultManifestEntry,
            source: VaultSessionRecord,
            name: String = entry.name
        ): VaultManifestEntry {
            cancellation.throwIfCancelled()
            return when (entry.kind) {
                VaultNodeKind.FILE -> cloneFile(entry, source, name)
                VaultNodeKind.DIRECTORY -> cloneDirectory(entry, source, name)
            }
        }

        private fun cloneFile(
            entry: VaultManifestEntry,
            source: VaultSessionRecord,
            name: String
        ): VaultManifestEntry {
            val sourceObject = requireNotNull(entry.objectId)
            val objectId = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32))
            val key = VaultCryptography.randomBytes(32)
            val path = objectId.shardedPath()
            try {
                fileCodec.openObject(
                    source.directory,
                    sourceObject.shardedPath(),
                    source.id,
                    sourceObject,
                    entry.revision,
                    entry.protectedKey
                ).use { reader ->
                    fileCodec.writeObject(
                        destination.directory,
                        path,
                        destination.id,
                        objectId,
                        1L,
                        key,
                        ReaderInputStream(reader, cancellation)
                    )
                }
                newObjects += path
                return VaultManifestEntry(
                    NodeId.random(), name, VaultNodeKind.FILE, 1L,
                    entry.modifiedAtMillis, entry.sizeBytes, entry.mimeType,
                    objectId, null, key.copyOf()
                )
            } finally {
                key.fill(0)
            }
        }

        private suspend fun cloneDirectory(
            entry: VaultManifestEntry,
            source: VaultSessionRecord,
            name: String
        ): VaultManifestEntry {
            val sourceId = requireNotNull(entry.childDirectoryId)
            val snapshot = source.readDirectory(sourceId, entry.protectedKey)
            val childId = DirectoryId.random()
            val childKey = VaultCryptography.randomBytes(32)
            try {
                val children = snapshot.entries.map { cloneEntry(it, source) }
                try {
                    prepared += VaultPreparedDirectory(
                        directoryCodec.prepare(destination.id, childId, childKey, 0L, children),
                        childKey.copyOf()
                    )
                } finally {
                    children.forEach { it.protectedKey.fill(0) }
                }
                return VaultManifestEntry(
                    NodeId.random(), name, VaultNodeKind.DIRECTORY, 1L,
                    entry.modifiedAtMillis, 0L, null, null, childId, childKey.copyOf()
                )
            } finally {
                snapshot.clearProtectedKeys()
                childKey.fill(0)
            }
        }

        suspend fun cloneMergedDirectory(
            existing: VaultManifestEntry,
            existingSession: VaultSessionRecord,
            source: VaultManifestEntry,
            sourceSession: VaultSessionRecord,
            name: String
        ): VaultManifestEntry {
            cancellation.throwIfCancelled()
            val existingSnapshot = existingSession.readDirectory(
                requireNotNull(existing.childDirectoryId), existing.protectedKey
            )
            val sourceSnapshot = sourceSession.readDirectory(
                requireNotNull(source.childDirectoryId), source.protectedKey
            )
            val childId = DirectoryId.random()
            val childKey = VaultCryptography.randomBytes(32)
            try {
                val output = mutableListOf<VaultManifestEntry>()
                val consumedSource = mutableSetOf<NodeId>()
                for (destinationEntry in existingSnapshot.entries) {
                    val sourceEntry = findByName(sourceSnapshot.entries, destinationEntry.name)
                    if (sourceEntry == null) {
                        output += cloneEntry(destinationEntry, existingSession)
                        continue
                    }
                    consumedSource += sourceEntry.nodeId
                    when (val decision = conflicts.decide(destinationEntry.conflictWith(sourceEntry))) {
                        VaultConflictDecision.SKIP -> output += cloneEntry(destinationEntry, existingSession)
                        VaultConflictDecision.REPLACE -> output += cloneEntry(sourceEntry, sourceSession)
                        VaultConflictDecision.KEEP_BOTH -> {
                            output += cloneEntry(destinationEntry, existingSession)
                            output += cloneEntry(
                                sourceEntry,
                                sourceSession,
                                uniqueName(sourceEntry.name, output)
                            )
                        }
                        VaultConflictDecision.MERGE_DIRECTORIES -> if (
                            destinationEntry.kind == VaultNodeKind.DIRECTORY &&
                            sourceEntry.kind == VaultNodeKind.DIRECTORY
                        ) {
                            output += cloneMergedDirectory(
                                destinationEntry, existingSession, sourceEntry, sourceSession, destinationEntry.name
                            )
                        } else {
                            output += cloneEntry(sourceEntry, sourceSession)
                        }
                    }
                }
                sourceSnapshot.entries.filterNot { it.nodeId in consumedSource }.forEach {
                    output += cloneEntry(it, sourceSession)
                }
                try {
                    prepared += VaultPreparedDirectory(
                        directoryCodec.prepare(destination.id, childId, childKey, 0L, output),
                        childKey.copyOf()
                    )
                } finally {
                    output.forEach { it.protectedKey.fill(0) }
                }
                return VaultManifestEntry(
                    NodeId.random(), name, VaultNodeKind.DIRECTORY, 1L,
                    maxOf(existing.modifiedAtMillis, source.modifiedAtMillis),
                    0L, null, null, childId, childKey.copyOf()
                )
            } finally {
                existingSnapshot.clearProtectedKeys()
                sourceSnapshot.clearProtectedKeys()
                childKey.fill(0)
            }
        }

        fun commit(obsolete: Set<String>) {
            transactions.commit(
                destination.directory,
                destination.id,
                destination.masterSecret,
                prepared,
                newObjects,
                obsolete
            )
            committed = true
        }

        fun rollbackIfUncommitted() {
            if (!committed && !transactions.hasPendingCommit(destination.directory)) {
                newObjects.forEach(destination.directory::delete)
            }
        }

        override fun close() {
            prepared.forEach { it.directoryKey.fill(0) }
        }
    }

    private fun resolve(session: VaultSessionRecord, ref: VaultNodeRef): ResolvedTransferEntry {
        require(ref.vaultId == session.id)
        val parent = session.resolveDirectory(ref.parentId)
        val snapshot = session.readDirectory(parent)
        val entry = snapshot.entries.firstOrNull { it.nodeId == ref.nodeId }?.copyDefensively()
            ?: run {
                snapshot.clearProtectedKeys()
                parent.key.fill(0)
                throw VaultFailure.NodeNotFound(ref.nodeId)
            }
        return ResolvedTransferEntry(parent, snapshot, entry)
    }

    private fun collectObsolete(session: VaultSessionRecord, root: VaultManifestEntry): Set<String> {
        if (root.kind == VaultNodeKind.FILE) return setOf(requireNotNull(root.objectId).shardedPath())
        val obsolete = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<DirectoryId, ByteArray>>()
        queue += requireNotNull(root.childDirectoryId) to root.protectedKey.copyOf()
        while (queue.isNotEmpty()) {
            val (id, key) = queue.removeFirst()
            try {
                val snapshot = session.readDirectory(id, key)
                try {
                    obsolete += snapshot.pageObjectIds.map(VaultObjectId::shardedPath)
                    obsolete += VaultDirectoryManifestCodec.rootSlot(id, 0L)
                    obsolete += VaultDirectoryManifestCodec.rootSlot(id, 1L)
                    snapshot.entries.forEach {
                        if (it.kind == VaultNodeKind.FILE) obsolete += requireNotNull(it.objectId).shardedPath()
                        else queue += requireNotNull(it.childDirectoryId) to it.protectedKey.copyOf()
                    }
                } finally {
                    snapshot.clearProtectedKeys()
                }
            } finally {
                key.fill(0)
            }
        }
        return obsolete
    }

    private fun subtreeDirectoryIds(session: VaultSessionRecord, root: VaultManifestEntry): Set<DirectoryId> {
        val ids = mutableSetOf<DirectoryId>()
        val queue = ArrayDeque<Pair<DirectoryId, ByteArray>>()
        queue += requireNotNull(root.childDirectoryId) to root.protectedKey.copyOf()
        while (queue.isNotEmpty()) {
            val (id, key) = queue.removeFirst()
            if (!ids.add(id)) {
                key.fill(0)
                continue
            }
            try {
                val snapshot = session.readDirectory(id, key)
                try {
                    snapshot.entries.filter { it.kind == VaultNodeKind.DIRECTORY }.forEach {
                        queue += requireNotNull(it.childDirectoryId) to it.protectedKey.copyOf()
                    }
                } finally {
                    snapshot.clearProtectedKeys()
                }
            } finally {
                key.fill(0)
            }
        }
        return ids
    }
}
