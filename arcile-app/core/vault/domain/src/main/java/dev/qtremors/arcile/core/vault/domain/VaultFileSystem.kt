package dev.qtremors.arcile.core.vault.domain

interface VaultFileSystem : VaultArchiveOperations, VaultUndoOperations {
    suspend fun listDirectory(
        vaultId: VaultId,
        directoryId: DirectoryId,
        options: VaultListOptions = VaultListOptions()
    ): Result<VaultPage<VaultNodeMetadata>>

    suspend fun metadata(ref: VaultNodeRef): Result<VaultNodeMetadata>
    suspend fun directoryTrail(vaultId: VaultId, directoryId: DirectoryId): Result<List<VaultDirectoryLocation>> =
        Result.failure(VaultFailure.Unavailable("Folder navigation is unavailable"))
    suspend fun folderTotals(ref: VaultNodeRef): Result<VaultFolderTotals> =
        Result.failure(VaultFailure.Unavailable("Folder totals are unavailable"))
    suspend fun selectionTotals(refs: List<VaultNodeRef>): Result<VaultFolderTotals> =
        Result.failure(VaultFailure.Unavailable("Selection totals are unavailable"))
    suspend fun storageUsage(vaultId: VaultId): Result<VaultStorageUsage> =
        Result.failure(VaultFailure.Unavailable("Vault storage usage is unavailable"))

    suspend fun search(
        vaultId: VaultId,
        directoryId: DirectoryId,
        query: VaultSearchQuery
    ): Result<VaultPage<VaultSearchHit>>

    suspend fun selectDirectoryRows(
        vaultId: VaultId,
        directoryId: DirectoryId,
        options: VaultListOptions
    ): Result<VaultPage<VaultNodeMetadata>> =
        Result.failure(VaultFailure.Unavailable("Selection across pages is unavailable"))

    suspend fun selectSearchRows(
        vaultId: VaultId,
        directoryId: DirectoryId,
        query: VaultSearchQuery
    ): Result<VaultPage<VaultSearchHit>> =
        Result.failure(VaultFailure.Unavailable("Selection across pages is unavailable"))

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

    suspend fun createSyntheticFile(vaultId: VaultId, parentId: DirectoryId, name: String, size: Long): Result<VaultNodeMetadata> =
        Result.failure(VaultFailure.Unavailable("Synthetic file creation is unavailable"))

    suspend fun rename(ref: VaultNodeRef, newName: String): Result<VaultNodeMetadata>
    suspend fun renameWithPreviousName(ref: VaultNodeRef, newName: String): Result<VaultRenamedNode> =
        rename(ref, newName).map { VaultRenamedNode(it, previousName = null) }
    suspend fun batchRenameSiblingNames(items: List<VaultRenameItem>): Result<Map<DirectoryId, Set<String>>> =
        Result.failure(VaultFailure.Unavailable("Batch rename is unavailable"))
    suspend fun batchRename(items: List<VaultRenameItem>): Result<List<VaultNodeMetadata>> =
        Result.failure(VaultFailure.Unavailable("Batch rename is unavailable"))
    suspend fun replaceFileContents(
        ref: VaultNodeRef,
        expectedRevision: Long,
        content: ByteArray
    ): Result<VaultNodeMetadata> = Result.failure(VaultFailure.Unavailable("File editing is unavailable"))
    /** Streams a new encrypted revision; fails without publishing when the stream length differs from [sizeBytes]. */
    suspend fun replaceFileContents(
        ref: VaultNodeRef,
        expectedRevision: Long,
        sizeBytes: Long,
        open: () -> java.io.InputStream
    ): Result<VaultNodeMetadata> = Result.failure(VaultFailure.Unavailable("File editing is unavailable"))
    suspend fun deletePermanently(ref: VaultNodeRef): Result<Unit>
    suspend fun deleteAndReportFolders(ref: VaultNodeRef): Result<Set<DirectoryId>> =
        deletePermanently(ref).map { setOfNotNull(ref.directoryId) }
    fun openReader(ref: VaultNodeRef): Result<VaultSeekableReader>
}

data class VaultRenameItem(val ref: VaultNodeRef, val expectedName: String, val newName: String = expectedName)

const val VAULT_BATCH_RENAME_MAX_ITEMS = 1_000
const val VAULT_SELECT_ROWS_LIMIT = 5_000

data class VaultDirectoryLocation(val id: DirectoryId, val name: String)

data class VaultFolderTotals(val fileCount: Long, val totalBytes: Long, val folderCount: Int = 0)

data class VaultFolderUsage(val id: DirectoryId, val name: String, val totalBytes: Long)

data class VaultStorageUsage(
    val storedBytes: Long?,
    val storedBytesPartial: Boolean,
    val largestFolders: List<VaultFolderUsage>,
    val folderCount: Int,
    val foldersMeasured: Int
)

data class VaultSearchHit(
    val metadata: VaultNodeMetadata,
    /** Display context only; it must never be persisted in navigation or SavedState. */
    val parentNames: List<String>,
    val parentDirectoryIds: List<DirectoryId> = emptyList()
)
