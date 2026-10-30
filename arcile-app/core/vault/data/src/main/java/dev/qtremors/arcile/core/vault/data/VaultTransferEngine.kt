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
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
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
    // The caller owns the new directory and publishes it only after full verification.
    suspend fun copyToUnpublishedVault(
        source: VaultSessionRecord,
        destination: VaultSessionRecord,
        cancellation: VaultCancellationSignal
    ) {
        val sourceRoot = source.root()
        val destinationRoot = destination.root()
        try {
            val snapshot = source.readDirectory(sourceRoot)
            try {
                CloneContext(
                    destination, VaultConflictResolver { VaultConflictDecision.SKIP },
                    cancellation, unpublishedDestination = true
                ).use { context ->
                    context.copyDirectoryEntries(snapshot.entries, source) { entries ->
                        val manifest = directoryCodec.prepare(
                            destination.id, destinationRoot.id, destinationRoot.key, 1L, entries
                        )
                        directoryCodec.publish(destination.directory, manifest)
                    }
                }
            } finally {
                snapshot.clearProtectedKeys()
            }
        } finally {
            sourceRoot.key.fill(0)
            destinationRoot.key.fill(0)
        }
    }

    fun topLevelConflicts(
        sessions: Map<VaultId, VaultSessionRecord>,
        sources: List<VaultNodeRef>,
        destinationVault: VaultId,
        destinationId: DirectoryId,
        move: Boolean,
        cancellation: VaultCancellationSignal
    ): List<VaultConflict> {
        val destination = sessions.getValue(destinationVault)
        val directory = destination.resolveDirectory(destinationId)
        try {
            val snapshot = destination.readDirectory(directory)
            try {
                val existing = snapshot.entries.associateBy { VaultName.of(it.name).comparisonKey }
                if (existing.isEmpty()) return emptyList()
                val conflicts = mutableMapOf<VaultNodeRef, VaultConflict>()
                val groups = sources.groupBy { it.vaultId to it.parentId }
                for ((location, siblings) in groups) {
                    if (cancellation.isCancelled()) return emptyList()
                    if (move && location.first == destinationVault && location.second == destinationId) continue
                    val source = sessions.getValue(location.first)
                    val parent = try {
                        source.resolveDirectory(location.second)
                    } catch (_: VaultFailure.InvalidPath) {
                        continue
                    }
                    try {
                        val entries = source.readDirectory(parent)
                        try {
                            val byId = entries.entries.associateBy(VaultManifestEntry::nodeId)
                            for (ref in siblings) {
                                if (cancellation.isCancelled()) return emptyList()
                                val entry = byId[ref.nodeId] ?: continue
                                val match = existing[VaultName.of(entry.name).comparisonKey] ?: continue
                                conflicts[ref] = match.conflictWith(entry)
                            }
                        } finally {
                            entries.clearProtectedKeys()
                        }
                    } finally {
                        parent.key.fill(0)
                    }
                }
                return sources.mapNotNull(conflicts::get)
            } finally {
                snapshot.clearProtectedKeys()
            }
        } finally {
            directory.key.fill(0)
        }
    }

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
        cancellation: VaultCancellationSignal,
        moveSource: Boolean = false,
        onPrepared: (VaultPublicationReceipt) -> Unit = {}
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
                    existing.kind == VaultNodeKind.DIRECTORY && resolved.entry.kind == VaultNodeKind.DIRECTORY
                ) {
                    context.cloneMergedDirectory(
                        existing, destination, resolved.entry, source, replacementName, ref.parentId, destinationId
                    )
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
                val pending = context.retry(ref, destinationId, moveSource, resolved.entry.name)
                onPrepared(VaultPublicationReceipt(
                    listOf(SavedVaultNode.from(cloned.toMetadata(destination.id, destinationId))), destinationSnapshot.generation,
                    pending?.let { VaultRetryCheckpoint.from(result(ref, resolved.entry, VaultItemOutcome.PARTIAL).copy(mergeRetry = it)) }
                ))
                context.commit(obsolete)
                val removedSourceFolders = mutableSetOf<DirectoryId>()
                if (moveSource) {
                    // Finish this item's move after the destination has been committed.
                    val sourcePrepared = mutableListOf<VaultPreparedDirectory>()
                    try {
                        val sourceObsolete = prepareMovedSource(
                            source, resolved, context.skippedSourceNodes,
                            context.retainedSourceDirectories, sourcePrepared,
                            onDirectoryRemoved = { removedSourceFolders.add(it) }
                        )
                        if (sourcePrepared.isNotEmpty()) {
                            transactions.commit(
                                source.directory, source.id, source.masterSecret,
                                sourcePrepared, emptySet(), sourceObsolete
                            )
                        }
                    } finally {
                        sourcePrepared.forEach { it.directoryKey.fill(0) }
                    }
                }
                return result(ref, resolved.entry, context.outcome()).copy(
                    removedFolders = removedSourceFolders,
                    mergeRetry = context.retry(ref, destinationId, moveSource, resolved.entry.name)
                )
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
        cancellation: VaultCancellationSignal,
        onPrepared: (VaultPublicationReceipt) -> Unit = {}
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
                    existing.kind == VaultNodeKind.DIRECTORY && resolved.entry.kind == VaultNodeKind.DIRECTORY
                val moved = when {
                    useClone -> context.cloneMergedDirectory(
                        requireNotNull(existing), session, resolved.entry, session, resolved.entry.name,
                        ref.parentId, destinationId
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
                try {
                    context.prepared += VaultPreparedDirectory(
                        directoryCodec.prepare(
                            session.id, destination.id, destination.key,
                            destinationSnapshot.generation + 1L, destinationEntries
                        ),
                        destination.key.copyOf()
                    )
                } finally {
                    destinationEntries.forEach { it.protectedKey.fill(0) }
                }
                val removedFolders = mutableSetOf<DirectoryId>()
                val recordRemovedFolder: (DirectoryId) -> Unit = { removedFolders.add(it) }
                val obsolete = buildSet {
                    if (existing != null && decision != VaultConflictDecision.KEEP_BOTH) {
                        addAll(collectObsolete(session, existing, recordRemovedFolder))
                    }
                    addAll(prepareMovedSource(
                        session, resolved, context.skippedSourceNodes,
                        context.retainedSourceDirectories, context.prepared,
                        deleteObjects = useClone,
                        onDirectoryRemoved = recordRemovedFolder
                    ))
                }
                val pending = context.retry(ref, destinationId, true, resolved.entry.name)
                onPrepared(VaultPublicationReceipt(
                    listOf(SavedVaultNode.from(moved.toMetadata(session.id, destinationId))), destinationSnapshot.generation,
                    pending?.let { VaultRetryCheckpoint.from(result(ref, resolved.entry, VaultItemOutcome.PARTIAL).copy(mergeRetry = it)) }
                ))
                context.commit(obsolete)
                return result(ref, resolved.entry, context.outcome()).copy(
                    removedFolders = removedFolders,
                    mergeRetry = context.retry(ref, destinationId, true, resolved.entry.name)
                )
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

    suspend fun resumeMerge(
        retry: PendingVaultMerge,
        source: VaultSessionRecord,
        destination: VaultSessionRecord,
        conflicts: VaultConflictResolver,
        cancellation: VaultCancellationSignal,
        onPublication: (DeferredVaultChild, VaultPublicationReceipt) -> Unit = { _, _ -> },
        onPublished: () -> Unit = {},
        onProgress: suspend (Int, String?) -> Unit
    ): VaultItemResult {
        val remaining = retry.children.toMutableList()
        val cleanup = retry.cleanupPaths.toMutableSet()
        val removed = linkedSetOf<DirectoryId>()
        var failure: VaultFailure? = null
        try {
            val knownConflicts = retry.children.mapNotNull { child ->
                cancellation.throwIfCancelled()
                retryConflict(retry, child, source, destination)
            }
            val resolver = conflicts.reviewed(knownConflicts, cancellation)
            for ((index, child) in retry.children.withIndex()) {
                cancellation.throwIfCancelled()
                onProgress(index, child.sourceName)
                val result = if (retry.move && source.id == destination.id) {
                    moveOneWithinVault(source, child.source, child.destination, resolver, cancellation) { onPublication(child, it) }
                } else {
                    copyOne(source, child.source, destination, child.destination, resolver, cancellation, retry.move) { onPublication(child, it) }
                }
                removed += result.removedFolders
                if (result.outcome == VaultItemOutcome.COMPLETED) {
                    remaining.remove(child)
                } else if (result.outcome == VaultItemOutcome.PARTIAL) {
                    val nested = requireNotNull(result.mergeRetry as? PendingVaultMerge)
                    remaining.remove(child)
                    remaining += nested.children.map(child::nest)
                    cleanup += nested.cleanupPaths.map { child.sourceFolders + it }
                }
                onPublished()
                onProgress(index + 1, null)
            }
            if (remaining.isEmpty() && retry.move) {
                pruneEmptyMergeFolders(retry.copy(cleanupPaths = cleanup), source, removed, cancellation)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            failure = error as? VaultFailure ?: VaultFailure.Unavailable("The vault merge could not be resumed", error)
        }
        val pending = if (remaining.isEmpty() && failure == null) null
        else retry.copy(children = remaining, cleanupPaths = cleanup)
        return VaultItemResult(
            retry.source.backendIdentity, retry.displayName,
            if (pending == null) VaultItemOutcome.COMPLETED else VaultItemOutcome.PARTIAL,
            failure, removed, pending
        )
    }

    private fun retryConflict(
        retry: PendingVaultMerge,
        child: DeferredVaultChild,
        source: VaultSessionRecord,
        destination: VaultSessionRecord
    ): VaultConflict? {
        require(child.sourceFolders.first().backendIdentity == retry.source.backendIdentity)
        require(child.source.parentId == child.sourceFolders.last().directoryId)
        validateFolderChain(source, retry.source.parentId, child.sourceFolders)
        validateFolderChain(destination, retry.destinationDirectory, child.destinationFolders)
        return resolve(source, child.source).use { resolved ->
            if (resolved.entry.revision != child.sourceRevision || resolved.entry.name != child.sourceName) {
                throw VaultFailure.SourceChanged()
            }
            val parent = destination.resolveDirectory(child.destination)
            try {
                val snapshot = destination.readDirectory(parent)
                try {
                    findByName(snapshot.entries, resolved.entry.name)?.conflictWith(resolved.entry)
                } finally {
                    snapshot.clearProtectedKeys()
                }
            } finally {
                parent.key.fill(0)
            }
        }
    }

    private fun pruneEmptyMergeFolders(
        retry: PendingVaultMerge,
        source: VaultSessionRecord,
        removed: MutableSet<DirectoryId>,
        cancellation: VaultCancellationSignal
    ) {
        val paths = retry.cleanupPaths.flatMap { path -> path.indices.map { path.take(it + 1) } }
            .distinct().sortedByDescending(List<VaultNodeRef>::size)
        for (path in paths) {
            cancellation.throwIfCancelled()
            val ref = path.last()
            val id = requireNotNull(ref.directoryId)
            try {
                validateFolderChain(source, retry.source.parentId, path.dropLast(1))
                val empty = resolve(source, ref).use { folder ->
                    if (folder.entry.childDirectoryId != id) throw VaultFailure.ConcurrentMutation()
                    val snapshot = source.readDirectory(id, folder.entry.protectedKey)
                    try {
                        snapshot.entries.isEmpty()
                    } finally {
                        snapshot.clearProtectedKeys()
                    }
                }
                if (empty) {
                    deleteOne(source, ref)
                    removed += id
                }
            } catch (_: VaultFailure.NodeNotFound) {
                // The original folder was removed separately. Never prune a replacement.
            }
        }
    }

    private fun validateFolderChain(session: VaultSessionRecord, root: DirectoryId, path: List<VaultNodeRef>) {
        var parent = root
        for (ref in path) {
            if (ref.parentId != parent) throw VaultFailure.ConcurrentMutation()
            parent = resolve(session, ref).use { folder ->
                val id = folder.entry.childDirectoryId
                if (id == null || id != ref.directoryId) throw VaultFailure.ConcurrentMutation()
                id
            }
        }
    }

    private inner class CloneContext(
        private val destination: VaultSessionRecord,
        private val conflicts: VaultConflictResolver,
        private val cancellation: VaultCancellationSignal,
        private val unpublishedDestination: Boolean = false
    ) : AutoCloseable {
        val prepared = mutableListOf<VaultPreparedDirectory>()
        val newObjects = linkedSetOf<String>()
        val skippedSourceNodes = mutableSetOf<NodeId>()
        val retainedSourceDirectories = mutableSetOf<NodeId>()
        private val deferred = mutableListOf<DeferredVaultChild>()
        private val sourceFolders = mutableListOf<VaultNodeRef>()
        private val destinationFolders = mutableListOf<VaultNodeRef>()
        private var committed = false

        fun outcome() = if (skippedSourceNodes.isEmpty()) VaultItemOutcome.COMPLETED else VaultItemOutcome.PARTIAL

        fun retry(source: VaultNodeRef, parent: DirectoryId, move: Boolean, name: String): PendingVaultMerge? {
            if (deferred.isEmpty()) return null
            return PendingVaultMerge(
                source, destination.id, parent, move, name, deferred.toList(),
                if (move) deferred.mapTo(linkedSetOf()) { it.sourceFolders } else emptySet()
            )
        }

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
                if (!unpublishedDestination) newObjects += path
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
                copyDirectoryEntries(snapshot.entries, source) { children ->
                    val manifest = directoryCodec.prepare(destination.id, childId, childKey, 0L, children)
                    if (unpublishedDestination) directoryCodec.publish(destination.directory, manifest)
                    else prepared += VaultPreparedDirectory(manifest, childKey.copyOf())
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

        suspend fun copyDirectoryEntries(
            entries: List<VaultManifestEntry>,
            source: VaultSessionRecord,
            publish: (List<VaultManifestEntry>) -> Unit
        ) {
            val children = ArrayList<VaultManifestEntry>(entries.size)
            try {
                entries.forEach { children.add(cloneEntry(it, source)) }
                cancellation.throwIfCancelled()
                publish(children)
            } finally {
                children.forEach { it.protectedKey.fill(0) }
            }
        }

        suspend fun cloneMergedDirectory(
            existing: VaultManifestEntry,
            existingSession: VaultSessionRecord,
            source: VaultManifestEntry,
            sourceSession: VaultSessionRecord,
            name: String,
            sourceParent: DirectoryId,
            destinationParent: DirectoryId
        ): VaultManifestEntry {
            cancellation.throwIfCancelled()
            val skippedBefore = skippedSourceNodes.size
            val existingSnapshot = existingSession.readDirectory(
                requireNotNull(existing.childDirectoryId), existing.protectedKey
            )
            val sourceSnapshot = sourceSession.readDirectory(
                requireNotNull(source.childDirectoryId), source.protectedKey
            )
            val childId = DirectoryId.random()
            val childNodeId = NodeId.random()
            val childKey = VaultCryptography.randomBytes(32)
            val sourceFolder = source.toMetadata(sourceSession.id, sourceParent).ref
            sourceFolders += sourceFolder
            destinationFolders += VaultNodeRef(
                destination.id, childNodeId, destinationParent, VaultNodeCapabilities(), childId
            )
            try {
                val output = mutableListOf<VaultManifestEntry>()
                val consumedSource = mutableSetOf<NodeId>()
                val reservedNames = hashSetOf<String>()
                for (entry in existingSnapshot.entries) reservedNames += VaultName.of(entry.name).comparisonKey
                for (entry in sourceSnapshot.entries) reservedNames += VaultName.of(entry.name).comparisonKey
                for (destinationEntry in existingSnapshot.entries) {
                    val sourceEntry = findByName(sourceSnapshot.entries, destinationEntry.name)
                    if (sourceEntry == null) {
                        output += cloneEntry(destinationEntry, existingSession)
                        continue
                    }
                    consumedSource += sourceEntry.nodeId
                    when (val decision = conflicts.decide(destinationEntry.conflictWith(sourceEntry))) {
                        VaultConflictDecision.SKIP -> {
                            skippedSourceNodes += sourceEntry.nodeId
                            deferred += DeferredVaultChild(
                                sourceFolders.toList(), destinationFolders.toList(),
                                sourceEntry.toMetadata(sourceSession.id, requireNotNull(sourceFolder.directoryId)).ref,
                                sourceEntry.revision, sourceEntry.name
                            )
                            output += cloneEntry(destinationEntry, existingSession)
                        }
                        VaultConflictDecision.REPLACE -> output += cloneEntry(sourceEntry, sourceSession)
                        VaultConflictDecision.KEEP_BOTH -> {
                            output += cloneEntry(destinationEntry, existingSession)
                            val newName = uniqueName(sourceEntry.name, reservedNames)
                            reservedNames += VaultName.of(newName).comparisonKey
                            output += cloneEntry(sourceEntry, sourceSession, newName)
                        }
                        VaultConflictDecision.MERGE_DIRECTORIES -> if (
                            destinationEntry.kind == VaultNodeKind.DIRECTORY &&
                            sourceEntry.kind == VaultNodeKind.DIRECTORY
                        ) {
                            output += cloneMergedDirectory(
                                destinationEntry, existingSession, sourceEntry, sourceSession, destinationEntry.name,
                                requireNotNull(sourceFolder.directoryId), childId
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
                if (skippedSourceNodes.size > skippedBefore) retainedSourceDirectories += source.nodeId
                return VaultManifestEntry(
                    childNodeId, name, VaultNodeKind.DIRECTORY, 1L,
                    maxOf(existing.modifiedAtMillis, source.modifiedAtMillis),
                    0L, null, null, childId, childKey.copyOf()
                )
            } finally {
                sourceFolders.removeAt(sourceFolders.lastIndex)
                destinationFolders.removeAt(destinationFolders.lastIndex)
                existingSnapshot.clearProtectedKeys()
                sourceSnapshot.clearProtectedKeys()
                childKey.fill(0)
            }
        }

        fun commit(obsolete: Set<String>) {
            cancellation.throwIfCancelled()
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

    // Publish source manifests containing only skipped nodes and their ancestors.
    // Within a vault these share the destination transaction. Across vaults they
    // are committed only after the destination is completely published.
    private fun prepareMovedSource(
        session: VaultSessionRecord,
        resolved: ResolvedTransferEntry,
        skippedNodes: Set<NodeId>,
        retainedDirectories: Set<NodeId>,
        prepared: MutableList<VaultPreparedDirectory>,
        deleteObjects: Boolean = true,
        onDirectoryRemoved: (DirectoryId) -> Unit = {}
    ): Set<String> {
        val obsolete = mutableSetOf<String>()
        fun retain(entry: VaultManifestEntry): Boolean {
            if (entry.nodeId in skippedNodes) return true
            if (entry.nodeId !in retainedDirectories) {
                if (deleteObjects) obsolete += collectObsolete(session, entry, onDirectoryRemoved)
                return false
            }
            val id = requireNotNull(entry.childDirectoryId)
            val snapshot = session.readDirectory(id, entry.protectedKey)
            try {
                val remaining = snapshot.entries.filter { retain(it) }
                if (remaining.size != snapshot.entries.size) {
                    prepared += VaultPreparedDirectory(
                        directoryCodec.prepare(
                            session.id, id, entry.protectedKey, snapshot.generation + 1L, remaining
                        ),
                        entry.protectedKey.copyOf()
                    )
                }
                return true
            } finally {
                snapshot.clearProtectedKeys()
            }
        }
        if (!retain(resolved.entry)) {
            prepared += VaultPreparedDirectory(
                directoryCodec.prepare(
                    session.id, resolved.parent.id, resolved.parent.key,
                    resolved.snapshot.generation + 1L,
                    resolved.snapshot.entries.filterNot { it.nodeId == resolved.entry.nodeId }
                ),
                resolved.parent.key.copyOf()
            )
        }
        return obsolete
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

    private fun collectObsolete(
        session: VaultSessionRecord,
        root: VaultManifestEntry,
        onDirectory: (DirectoryId) -> Unit = {}
    ): Set<String> {
        if (root.kind == VaultNodeKind.FILE) return setOf(requireNotNull(root.objectId).shardedPath())
        val obsolete = mutableSetOf<String>()
        val queue = ArrayDeque<Pair<DirectoryId, ByteArray>>()
        queue += requireNotNull(root.childDirectoryId) to root.protectedKey.copyOf()
        while (queue.isNotEmpty()) {
            val (id, key) = queue.removeFirst()
            try {
                val snapshot = session.readDirectory(id, key)
                try {
                    onDirectory(id)
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
