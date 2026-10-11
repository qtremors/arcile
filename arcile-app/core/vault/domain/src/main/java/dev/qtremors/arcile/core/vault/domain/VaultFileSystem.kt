package dev.qtremors.arcile.core.vault.domain

interface VaultFileSystem {
    suspend fun listDirectory(
        vaultId: VaultId,
        directoryId: DirectoryId,
        options: VaultListOptions = VaultListOptions()
    ): Result<VaultPage<VaultNodeMetadata>>

    suspend fun metadata(ref: VaultNodeRef): Result<VaultNodeMetadata>
    suspend fun folderTotals(ref: VaultNodeRef): Result<VaultFolderTotals> =
        Result.failure(VaultFailure.Unavailable("Folder totals are unavailable"))

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
    suspend fun replaceFileContents(
        ref: VaultNodeRef,
        expectedRevision: Long,
        content: ByteArray
    ): Result<VaultNodeMetadata> = Result.failure(VaultFailure.Unavailable("File editing is unavailable"))
    suspend fun deletePermanently(ref: VaultNodeRef): Result<Unit>
    fun openReader(ref: VaultNodeRef): Result<VaultSeekableReader>
}

data class VaultFolderTotals(val fileCount: Long, val totalBytes: Long, val folderCount: Int = 0)

data class VaultSearchHit(
    val metadata: VaultNodeMetadata,
    /** Display context only; it must never be persisted in navigation or SavedState. */
    val parentNames: List<String>,
    val parentDirectoryIds: List<DirectoryId> = emptyList()
)
