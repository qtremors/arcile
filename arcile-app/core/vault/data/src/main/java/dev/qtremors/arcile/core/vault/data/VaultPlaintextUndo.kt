package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_BYTES
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_ENTRIES
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_FOLDERS
import dev.qtremors.arcile.core.vault.domain.VaultArchiveSource
import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultPlaintextUndo
import dev.qtremors.arcile.core.vault.domain.VaultUndoOrigin
import dev.qtremors.arcile.core.vault.domain.VaultBatchResult
import dev.qtremors.arcile.core.vault.domain.VaultConflictDecision
import dev.qtremors.arcile.core.vault.domain.VaultConflictResolver
import dev.qtremors.arcile.core.vault.domain.VaultItemOutcome
import dev.qtremors.arcile.core.vault.domain.VaultItemResult
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.storage.domain.StorageMutationResult
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

/**
 * Plaintext published by an export or Trash move. [path] is the exported item; Trash items are
 * located again by [trashId] because Trash owns its payload paths. Never persisted.
 */
internal data class PendingVaultPlaintextUndo(
    override val displayName: String,
    override val origin: VaultUndoOrigin.VaultFolder?,
    override val trashed: Boolean,
    val path: String?,
    val trashId: String?,
    val fingerprint: LocalTreeFingerprint
) : VaultPlaintextUndo

/** Names, kinds, sizes and modification times of a local tree, without following links. */
internal data class LocalTreeFingerprint(val entries: Map<String, Entry>) {
    data class Entry(val directory: Boolean, val sizeBytes: Long, val modifiedAtMillis: Long)

    /** Whether this tree can be published again by one vault archive extraction. */
    val restorable: Boolean
        get() = entries.size <= VAULT_ARCHIVE_MAX_ENTRIES &&
            entries.values.filterNot(Entry::directory).sumOf(Entry::sizeBytes) <= VAULT_ARCHIVE_MAX_BYTES &&
            entries.keys.all { relative -> relative.isEmpty() || relative.count { it == '/' } < VAULT_ARCHIVE_MAX_FOLDERS }

    fun matches(root: File, cancellation: VaultCancellationSignal): Boolean = try {
        capture(root, entries.size, cancellation) == this
    } catch (_: VaultFailure) {
        false
    }

    /**
     * Encrypts the current tree under [rootName]. The caller verifies the fingerprint first; the vault
     * extraction then checks each stream against the recorded size, so a file changed meanwhile fails.
     */
    fun archiveSources(root: File, rootName: String): List<VaultArchiveSource> = entries.toSortedMap().map { (relative, entry) ->
        val segments = listOf(rootName) + relative.split('/').filter(String::isNotEmpty)
        val file = if (relative.isEmpty()) root else File(root, relative)
        if (entry.directory) VaultArchiveSource.Folder(segments)
        else VaultArchiveSource.File(segments, entry.sizeBytes, null) { FileInputStream(file) }
    }

    companion object {
        const val MAX_ENTRIES = 10_000

        fun capture(root: File, limit: Int = MAX_ENTRIES, cancellation: VaultCancellationSignal): LocalTreeFingerprint {
            val entries = linkedMapOf<String, Entry>()
            val pending = ArrayDeque<Pair<String, File>>()
            pending += "" to root
            while (pending.isNotEmpty()) {
                if (cancellation.isCancelled()) throw VaultFailure.Cancelled()
                val (relative, file) = pending.removeFirst()
                val attributes = try {
                    Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                } catch (_: Exception) {
                    throw VaultFailure.SourceChanged()
                }
                if (attributes.isSymbolicLink || !(attributes.isDirectory || attributes.isRegularFile)) {
                    throw VaultFailure.SymlinkRejected(file.name)
                }
                entries[relative] = Entry(attributes.isDirectory, if (attributes.isDirectory) 0L else attributes.size(),
                    attributes.lastModifiedTime().toMillis())
                if (entries.size > limit) throw VaultFailure.Unavailable("The exported tree exceeds the Undo verification limit")
                if (attributes.isDirectory) {
                    val children = file.listFiles() ?: throw VaultFailure.SourceChanged()
                    for (child in children.sortedBy(File::getName)) {
                        pending += (if (relative.isEmpty()) child.name else "$relative/${child.name}") to child
                    }
                }
            }
            return LocalTreeFingerprint(entries)
        }

        /** Null when the tree cannot be proven; the completed operation then simply offers no Undo. */
        fun captureOrNull(root: File, cancellation: VaultCancellationSignal): LocalTreeFingerprint? = try {
            capture(root, MAX_ENTRIES, cancellation)
        } catch (_: VaultFailure.Cancelled) {
            null
        } catch (_: VaultFailure) {
            null
        }
    }
}

/**
 * Reverses exports and Trash moves. Each proof restores first and removes plaintext last: a failure before
 * the vault publication leaves the plaintext where it was, and plaintext changed during re-encryption is
 * kept and reported as partial. The caller serializes this with boundary operations.
 */
internal class VaultPlaintextUndoRunner(
    private val files: dev.qtremors.arcile.core.vault.domain.VaultFileSystem,
    private val trash: dev.qtremors.arcile.core.storage.domain.TrashRepository?
) {
    suspend fun undo(proofs: List<VaultPlaintextUndo>, cancellation: VaultCancellationSignal): VaultBatchResult =
        VaultBatchResult(proofs.map { proof ->
            val pending = proof as? PendingVaultPlaintextUndo
                ?: return@map VaultItemResult(proof.displayName, proof.displayName, VaultItemOutcome.FAILED, VaultFailure.ConcurrentMutation())
            try {
                undoOne(pending, cancellation)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                VaultItemResult(pending.displayName, pending.displayName,
                    if (error is VaultFailure.Cancelled) VaultItemOutcome.ROLLED_BACK else VaultItemOutcome.FAILED,
                    error as? VaultFailure ?: VaultFailure.Unavailable("Undo failed", error))
            }
        })

    private suspend fun undoOne(proof: PendingVaultPlaintextUndo, cancellation: VaultCancellationSignal): VaultItemResult {
        if (cancellation.isCancelled()) throw VaultFailure.Cancelled()
        val trashEntry = proof.trashId?.let { id ->
            requireNotNull(trash) { "Main Trash is unavailable" }.getTrashFiles().getOrThrow().find { it.id == id }
                ?: throw VaultFailure.NodeNotFound(NodeId.of(id))
        }
        val file = File(trashEntry?.fileModel?.reference ?: requireNotNull(proof.path))
        if (!proof.fingerprint.matches(file, cancellation)) throw VaultFailure.SourceChanged()
        val origin = proof.origin
        if (origin != null) {
            val restored = files.extractArchiveInto(
                origin.vaultId, origin.parentId, proof.fingerprint.archiveSources(file, origin.name),
                // Undo never overwrites or renames: an item now using the original name blocks the restore.
                VaultConflictResolver { VaultConflictDecision.SKIP }
            ) {}.getOrThrow()
            if (restored.items.isEmpty() || restored.items.any { it.outcome != VaultItemOutcome.COMPLETED }) {
                throw VaultFailure.NameConflict(origin.name)
            }
            if (!proof.fingerprint.matches(file, cancellation)) {
                return VaultItemResult(proof.displayName, proof.displayName, VaultItemOutcome.PARTIAL, VaultFailure.SourceChanged())
            }
        }
        if (cancellation.isCancelled()) throw VaultFailure.Cancelled()
        if (trashEntry != null) {
            val removal = requireNotNull(trash).deletePermanentlyFromTrash(listOf(trashEntry.id))
            if (removal !is StorageMutationResult.Completed) {
                return VaultItemResult(proof.displayName, proof.displayName, VaultItemOutcome.PARTIAL,
                    VaultFailure.Unavailable("The restored item is still in main Trash"))
            }
        } else {
            file.deleteLocalTree()
        }
        return VaultItemResult(proof.displayName, proof.displayName, VaultItemOutcome.COMPLETED)
    }
}
