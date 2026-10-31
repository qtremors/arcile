package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultDirectorySnapshot
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex

internal data class PendingVaultMoveUndo(
    val expected: VaultNodeMetadata,
    val originalParent: DirectoryId,
    val originalName: String,
    val mount: Mutex
) : VaultMoveUndo {
    override val vaultId: VaultId get() = expected.ref.vaultId
}

/** The repository holds the mutation lock across verification and the inverse transaction. */
internal class VaultUndoWriter(
    private val session: VaultSessionRecord,
    private val codec: VaultDirectoryManifestCodec,
    private val transactions: VaultTransactionManager,
    private val resolve: (VaultNodeRef) -> Triple<ResolvedVaultDirectory, VaultDirectorySnapshot, VaultManifestEntry>,
    private val delete: (VaultNodeRef) -> Set<DirectoryId>
) {
    fun created(expected: VaultNodeMetadata): VaultUndoResult {
        if (!expected.ref.capabilities.canDeletePermanently || expected.revision != 1L) throw VaultFailure.ConcurrentMutation()
        withExpected(expected) { _, _, entry ->
            if (entry.kind == VaultNodeKind.DIRECTORY) {
                val contents = session.readDirectory(requireNotNull(entry.childDirectoryId), entry.protectedKey)
                try {
                    // Adding and then removing a child still consumes this creation's Undo.
                    if (contents.generation != 0L || contents.entries.isNotEmpty()) throw VaultFailure.ConcurrentMutation()
                } finally { contents.clearProtectedKeys() }
            }
        }
        val folders = delete(expected.ref)
        return VaultUndoResult(removedNodeIds = setOf(expected.ref.nodeId), removedDirectoryIds = folders)
    }

    suspend fun renamed(entries: List<VaultRenameUndoEntry>): VaultUndoResult {
        require(entries.isNotEmpty() && entries.size <= VAULT_BATCH_RENAME_MAX_ITEMS)
        require(entries.map { it.expected.ref.nodeId }.distinct().size == entries.size)
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            VaultName.of(entry.originalName)
            if (!entry.expected.ref.capabilities.canRename) throw VaultFailure.ConcurrentMutation()
            withExpected(entry.expected) { _, _, _ -> }
        }
        val updated = VaultBatchRenamer(session).rename(entries.map {
            VaultRenameItem(it.expected.ref, it.expected.name, it.originalName)
        }, codec, transactions)
        return VaultUndoResult(updated)
    }

    suspend fun moved(proofs: List<VaultMoveUndo>): VaultUndoResult {
        require(proofs.isNotEmpty() && proofs.size <= VAULT_SELECT_ROWS_LIMIT)
        val pending = proofs.map { it as? PendingVaultMoveUndo ?: throw VaultFailure.ConcurrentMutation() }
        require(pending.map { it.expected.ref.nodeId }.distinct().size == pending.size)
        if (pending.any { it.vaultId != session.id || it.mount !== session.mutationMutex || !it.expected.ref.capabilities.canMove }) {
            throw VaultFailure.ConcurrentMutation()
        }
        val ids = pending.flatMap { listOf(it.expected.ref.parentId, it.originalParent) }.distinct()
        require(ids.size <= VaultTransactionManager.MAX_PUBLICATIONS)
        val folders = linkedMapOf<DirectoryId, Pair<ResolvedVaultDirectory, VaultDirectorySnapshot>>()
        val prepared = mutableListOf<VaultPreparedDirectory>()
        try {
            for (id in ids) {
                currentCoroutineContext().ensureActive()
                val directory = session.resolveDirectory(id)
                try { folders[id] = directory to session.readDirectory(directory) }
                catch (error: Throwable) { directory.key.fill(0); throw error }
            }
            val next = folders.mapValues { (_, folder) -> folder.second.entries.toMutableList() }
            val originals = pending.associateWith { proof ->
                val ref = proof.expected.ref
                val entry = next.getValue(ref.parentId).find { it.nodeId == ref.nodeId }
                    ?: throw VaultFailure.NodeNotFound(ref.nodeId)
                if (entry.toMetadata(session.id, ref.parentId) != proof.expected) throw VaultFailure.ConcurrentMutation()
                entry
            }
            val coroutine = currentCoroutineContext()
            val parents = pending.mapTo(hashSetOf()) { it.originalParent }
            var verifiedFolders = 0
            for ((proof, entry) in originals) {
                if (proof.expected.isDirectory) {
                    val descendants = vaultSubtreeDirectoryIds(session, entry, MAX_UNDO_FOLDERS - verifiedFolders) { coroutine.ensureActive() }
                    verifiedFolders += descendants.size
                    if (descendants.any { it in parents }) throw VaultFailure.InvalidPath("Undo would move a folder inside itself")
                }
            }
            for ((proof, entry) in originals) next.getValue(proof.expected.ref.parentId).remove(entry)
            val updated = originals.map { (proof, entry) ->
                val name = normalizeMutationName(proof.originalName)
                val destination = next.getValue(proof.originalParent)
                if (destination.any { VaultName.of(it.name).comparisonKey == VaultName.of(name).comparisonKey }) {
                    throw VaultFailure.NameConflict(name)
                }
                val restored = entry.copy(name = name, modifiedAtMillis = System.currentTimeMillis())
                destination += restored
                restored.toMetadata(session.id, proof.originalParent)
            }
            for ((id, folder) in folders) {
                val (directory, snapshot) = folder
                prepared += VaultPreparedDirectory(codec.prepare(session.id, id, directory.key, snapshot.generation + 1L,
                    next.getValue(id)), directory.key.copyOf())
            }
            currentCoroutineContext().ensureActive()
            transactions.commit(session.directory, session.id, session.masterSecret, prepared, emptySet(), emptySet())
            return VaultUndoResult(updated)
        } finally {
            prepared.forEach { it.directoryKey.fill(0) }
            folders.values.forEach { (directory, snapshot) -> snapshot.clearProtectedKeys(); directory.key.fill(0) }
        }
    }

    private inline fun <T> withExpected(
        expected: VaultNodeMetadata,
        block: (ResolvedVaultDirectory, VaultDirectorySnapshot, VaultManifestEntry) -> T
    ): T {
        if (expected.ref.vaultId != session.id) throw VaultFailure.StaleRegistration()
        val (parent, snapshot, entry) = resolve(expected.ref)
        try {
            if (entry.toMetadata(session.id, parent.id) != expected) throw VaultFailure.ConcurrentMutation()
            return block(parent, snapshot, entry)
        } finally {
            entry.protectedKey.fill(0)
            snapshot.clearProtectedKeys()
            parent.key.fill(0)
        }
    }

    private companion object { const val MAX_UNDO_FOLDERS = 10_000 }
}
