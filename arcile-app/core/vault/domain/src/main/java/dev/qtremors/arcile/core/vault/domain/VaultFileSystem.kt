package dev.qtremors.arcile.core.vault.domain

interface VaultFileSystem {
    suspend fun listDirectory(
        vaultId: VaultId,
        directoryId: DirectoryId,
        options: VaultListOptions = VaultListOptions()
    ): Result<VaultPage<VaultNodeMetadata>>

    suspend fun metadata(ref: VaultNodeRef): Result<VaultNodeMetadata>

    suspend fun search(
        vaultId: VaultId,
        directoryId: DirectoryId,
        query: VaultSearchQuery
    ): Result<VaultPage<VaultSearchHit>>

    suspend fun createDirectory(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String
    ): Result<VaultNodeMetadata>

    suspend fun createEmptyFile(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        mimeType: String? = null
    ): Result<VaultNodeMetadata>

    suspend fun rename(ref: VaultNodeRef, newName: String): Result<VaultNodeMetadata>
    suspend fun deletePermanently(ref: VaultNodeRef): Result<Unit>
    fun openReader(ref: VaultNodeRef): Result<VaultSeekableReader>
}

data class VaultSearchHit(
    val metadata: VaultNodeMetadata,
    /** Display context only; it must never be persisted in navigation or SavedState. */
    val parentNames: List<String>
)
