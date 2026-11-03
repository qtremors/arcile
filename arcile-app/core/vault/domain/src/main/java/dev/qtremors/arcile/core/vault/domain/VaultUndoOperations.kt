package dev.qtremors.arcile.core.vault.domain

/** An in-memory proof for a reversible move. Never persist it or reuse it after locking. */
interface VaultMoveUndo {
    val vaultId: VaultId
}

/** Where a reversed operation returns its item. In memory only, never persisted or logged. */
sealed interface VaultUndoOrigin {
    /** The source folder of a cross-vault move or a move out of a vault. */
    data class VaultFolder(val vaultId: VaultId, val parentId: DirectoryId, val name: String) : VaultUndoOrigin

    /** The local folder of a Move into vault. */
    data class LocalFolder(val parentPath: String, val name: String) : VaultUndoOrigin
}

/**
 * An in-memory proof that a vault item published by a copy, import or cross-vault move is unchanged.
 * It expires when its vault locks. Deleting the item reverses a copy; [origin] restores a move.
 */
interface VaultCreatedUndo {
    val expected: VaultNodeMetadata
    val origin: VaultUndoOrigin?
    val vaultId: VaultId get() = expected.ref.vaultId
}

/**
 * An in-memory proof for plaintext published outside a vault by export or Move to main Trash.
 * [origin] is null for a copy-only export, whose reversal only removes the unchanged plaintext.
 */
interface VaultPlaintextUndo {
    val displayName: String
    val origin: VaultUndoOrigin.VaultFolder?
    val trashed: Boolean
}

data class VaultRenameUndoEntry(val expected: VaultNodeMetadata, val originalName: String)

/** Null means this provider cannot return the previous name atomically with publication. */
data class VaultRenamedNode(val updated: VaultNodeMetadata, val previousName: String?)

data class VaultUndoResult(
    val updated: List<VaultNodeMetadata> = emptyList(),
    val removedNodeIds: Set<NodeId> = emptySet(),
    val removedDirectoryIds: Set<DirectoryId> = emptySet()
)

interface VaultUndoOperations {
    suspend fun undoCreated(expected: VaultNodeMetadata): Result<VaultUndoResult> =
        Result.failure(VaultFailure.Unavailable("Undo is unavailable"))

    suspend fun undoRenames(entries: List<VaultRenameUndoEntry>): Result<VaultUndoResult> =
        Result.failure(VaultFailure.Unavailable("Undo is unavailable"))

    suspend fun undoMoves(proofs: List<VaultMoveUndo>): Result<VaultUndoResult> =
        Result.failure(VaultFailure.Unavailable("Undo is unavailable"))

    /** Fails unless every proven item, including folder contents, is unchanged and still unlocked. */
    suspend fun verifyCreated(proofs: List<VaultCreatedUndo>): Result<List<VaultNodeMetadata>> =
        Result.failure(VaultFailure.Unavailable("Undo is unavailable"))

    /** Verifies every proof before deleting any item, so a changed copy cancels the whole Undo. */
    suspend fun undoCreatedCopies(proofs: List<VaultCreatedUndo>): Result<VaultUndoResult> =
        Result.failure(VaultFailure.Unavailable("Undo is unavailable"))
}
