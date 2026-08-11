package dev.qtremors.arcile.core.storage.domain

/** Backend-owned atomic persistence for protected text documents. */
interface PrivilegedTextFileEditor {
    suspend fun saveAtomically(node: StorageNodeRef, content: ByteArray): Result<FileModel>
}
