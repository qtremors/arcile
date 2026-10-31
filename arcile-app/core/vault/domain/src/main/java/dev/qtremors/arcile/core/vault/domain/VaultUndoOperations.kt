package dev.qtremors.arcile.core.vault.domain

/** An in-memory proof for a reversible move. Never persist it or reuse it after locking. */
interface VaultMoveUndo {
    val vaultId: VaultId
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
}
