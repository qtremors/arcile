package dev.qtremors.arcile.core.storage.domain

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress

enum class ArchiveFormat(val extension: String, val displayName: String) {
    ZIP("zip", "ZIP"),
    SEVEN_Z("7z", "7z");

    companion object {
        fun fromPath(path: String): ArchiveFormat? {
            val ext = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
            return entries.firstOrNull { it.extension == ext }
        }

        fun isSupported(path: String): Boolean = fromPath(path) != null
    }
}

@Immutable
data class ArchiveEntryModel(
    val name: String,
    val path: String,
    val size: Long,
    val compressedSize: Long?,
    val lastModified: Long?,
    val isDirectory: Boolean,
    val canRead: Boolean = true
)

@Immutable
data class ArchiveSummary(
    val archivePath: String,
    val format: ArchiveFormat,
    val archiveSize: Long,
    val totalUncompressedSize: Long,
    val fileCount: Int,
    val folderCount: Int,
    val newestModifiedAt: Long?,
    val oldestModifiedAt: Long?,
    val hasUnreadableEntries: Boolean
) {
    val entryCount: Int get() = fileCount + folderCount
    val compressionRatio: Double?
        get() = totalUncompressedSize.takeIf { it > 0L }?.let { archiveSize.toDouble() / it.toDouble() }
}

enum class ArchiveExtractionDestination {
    HERE,
    NAMED_FOLDER
}

@Immutable
data class ArchiveCreateOptions(
    val password: String? = null
) {
    val hasPassword: Boolean get() = !password.isNullOrEmpty()
}

@Immutable
data class ArchiveExtractOptions(
    val password: String? = null
) {
    val hasPassword: Boolean get() = !password.isNullOrEmpty()
}

interface ArchiveManager {
    suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>>
    suspend fun listArchiveEntries(archivePath: String, password: String?): Result<List<ArchiveEntryModel>> =
        listArchiveEntries(archivePath)
    suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary>
    suspend fun getArchiveMetadata(archivePath: String, password: String?): Result<ArchiveSummary> =
        getArchiveMetadata(archivePath)
    suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String? = null,
        password: String? = null,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String? = null,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit>
}
