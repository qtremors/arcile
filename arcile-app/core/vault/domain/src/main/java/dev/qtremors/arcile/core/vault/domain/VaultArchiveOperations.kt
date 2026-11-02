package dev.qtremors.arcile.core.vault.domain

import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/** An archive entry that may be read only while its caller holds the private source open. */
sealed interface VaultArchiveSource {
    val path: List<String>

    data class Folder(override val path: List<String>) : VaultArchiveSource

    data class File(
        override val path: List<String>,
        val sizeBytes: Long,
        /** A per-entry checksum, when the archive format provides one for this stream. */
        val crc32: Long?,
        val open: () -> InputStream
    ) : VaultArchiveSource
}

data class VaultArchiveProgress(val completed: Int, val total: Int, val currentName: String?)

data class VaultArchiveDestinationResult(val directory: DirectoryId, val result: VaultBatchResult)

const val VAULT_ARCHIVE_MAX_FOLDERS = 31
const val VAULT_ARCHIVE_MAX_ENTRIES = 1_000
const val VAULT_ARCHIVE_MAX_BYTES = 2L * 1024L * 1024L * 1024L

interface VaultArchiveOperations {
    suspend fun extractArchiveToNamedFolder(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        sources: List<VaultArchiveSource>,
        conflicts: VaultConflictResolver,
        onProgress: (VaultArchiveProgress) -> Unit
    ): Result<VaultArchiveDestinationResult> = Result.failure(VaultFailure.Unavailable("Archive extraction is unavailable"))

    /** Extract directly into an existing folder using normal vault conflicts and one publication. */
    suspend fun extractArchiveInto(
        vaultId: VaultId,
        parentId: DirectoryId,
        sources: List<VaultArchiveSource>,
        conflicts: VaultConflictResolver,
        onProgress: (VaultArchiveProgress) -> Unit
    ): Result<VaultBatchResult> = Result.failure(VaultFailure.Unavailable("Archive extraction is unavailable"))

    suspend fun extractArchive(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        sources: List<VaultArchiveSource>,
        keepBoth: Boolean = false,
        onProgress: (VaultArchiveProgress) -> Unit
    ): Result<VaultNodeMetadata> = Result.failure(VaultFailure.Unavailable("Archive extraction is unavailable"))

    suspend fun createZip(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        writeZip: (OutputStream) -> Unit
    ): Result<VaultNodeMetadata> = Result.failure(VaultFailure.Unavailable("Archive creation is unavailable"))

    suspend fun createArchiveFile(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        mimeType: String,
        writeArchive: (OutputStream) -> Unit
    ): Result<VaultNodeMetadata> = Result.failure(VaultFailure.Unavailable("Archive creation is unavailable"))

    /** Borrowed operation-scoped channel. Only encrypted scratch and the committed object reach disk. */
    suspend fun createSeekableArchiveFile(
        vaultId: VaultId,
        parentId: DirectoryId,
        name: String,
        mimeType: String,
        writeArchive: (SeekableByteChannel) -> Unit
    ): Result<VaultNodeMetadata> = Result.failure(VaultFailure.Unavailable("Archive creation is unavailable"))
}
