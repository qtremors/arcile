package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.crypto.VaultDirectorySnapshot
import dev.qtremors.arcile.core.vault.crypto.VaultManifestEntry
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VAULT_BATCH_RENAME_MAX_ITEMS
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultName
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultRenameItem
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Builds final sibling names before publishing any changes under the vault mutation lock. */
internal class VaultBatchRenamer(private val session: VaultSessionRecord) {
    suspend fun siblingNames(items: List<VaultRenameItem>): Map<DirectoryId, Set<String>> =
        groupItems(items).mapValues { (parentId, siblings) ->
            currentCoroutineContext().ensureActive()
            withFolder(parentId) { _, snapshot ->
                validateOriginals(snapshot, siblings)
                val selected = siblings.mapTo(mutableSetOf()) { it.ref.nodeId }
                snapshot.entries.filter { it.nodeId !in selected }.mapTo(mutableSetOf(), VaultManifestEntry::name)
            }
        }

    suspend fun rename(
        items: List<VaultRenameItem>,
        directoryCodec: VaultDirectoryManifestCodec,
        transactionManager: VaultTransactionManager
    ): List<VaultNodeMetadata> {
        val groups = groupItems(items)
        val prepared = mutableListOf<VaultPreparedDirectory>()
        val results = mutableMapOf<NodeId, VaultNodeMetadata>()
        val modifiedAt = System.currentTimeMillis()
        try {
            for ((parentId, siblings) in groups) {
                currentCoroutineContext().ensureActive()
                withFolder(parentId) { parent, snapshot ->
                    validateOriginals(snapshot, siblings)
                    val proposed = siblings.associate { it.ref.nodeId to normalizeMutationName(it.newName) }
                    val finalEntries = snapshot.entries.map { existing ->
                        val name = proposed[existing.nodeId] ?: return@map existing
                        val updated = if (name == existing.name) existing
                        else existing.copy(name = name, modifiedAtMillis = modifiedAt)
                        results[existing.nodeId] = updated.toMetadata(session.id, parentId)
                        updated
                    }
                    val seen = HashSet<String>(finalEntries.size)
                    for (entry in finalEntries) {
                        if (!seen.add(VaultName.of(entry.name).comparisonKey)) {
                            throw VaultFailure.NameConflict(entry.name)
                        }
                    }
                    if (siblings.any { proposed.getValue(it.ref.nodeId) != it.expectedName }) {
                        val manifest = directoryCodec.prepare(
                            session.id, parentId, parent.key, snapshot.generation + 1L, finalEntries
                        )
                        prepared += VaultPreparedDirectory(manifest, parent.key.copyOf())
                    }
                }
            }
            currentCoroutineContext().ensureActive()
            if (prepared.isNotEmpty()) {
                transactionManager.commit(
                    session.directory, session.id, session.masterSecret, prepared, emptySet(), emptySet()
                )
            }
            return items.map { results.getValue(it.ref.nodeId) }
        } finally {
            prepared.forEach { it.directoryKey.fill(0) }
        }
    }

    private fun groupItems(items: List<VaultRenameItem>): Map<DirectoryId, List<VaultRenameItem>> {
        if (items.size > VAULT_BATCH_RENAME_MAX_ITEMS) {
            throw VaultFailure.Unavailable("Select up to $VAULT_BATCH_RENAME_MAX_ITEMS items to rename")
        }
        if (items.any { it.ref.vaultId != session.id }) throw VaultFailure.StaleRegistration()
        if (items.any { !it.ref.capabilities.canRename } || items.distinctBy { it.ref.nodeId }.size != items.size) {
            throw VaultFailure.Unavailable("The selection contains items that cannot be batch renamed")
        }
        val groups = items.groupBy { it.ref.parentId }
        if (groups.size > VaultTransactionManager.MAX_PUBLICATIONS) {
            throw VaultFailure.Unavailable("Select items from up to ${VaultTransactionManager.MAX_PUBLICATIONS} folders")
        }
        return groups
    }

    private fun validateOriginals(snapshot: VaultDirectorySnapshot, siblings: List<VaultRenameItem>) {
        val originals = snapshot.entries.associateBy(VaultManifestEntry::nodeId)
        for (item in siblings) {
            val entry = originals[item.ref.nodeId] ?: throw VaultFailure.NodeNotFound(item.ref.nodeId)
            if (entry.name != item.expectedName) {
                throw VaultFailure.Unavailable("A selected item was renamed. Reopen batch rename to refresh the preview.")
            }
        }
    }

    private inline fun <T> withFolder(
        parentId: DirectoryId,
        block: (ResolvedVaultDirectory, VaultDirectorySnapshot) -> T
    ): T {
        val parent = session.resolveDirectory(parentId)
        try {
            val snapshot = session.readDirectory(parent)
            try {
                return block(parent, snapshot)
            } finally {
                snapshot.clearProtectedKeys()
            }
        } finally {
            parent.key.fill(0)
        }
    }
}
