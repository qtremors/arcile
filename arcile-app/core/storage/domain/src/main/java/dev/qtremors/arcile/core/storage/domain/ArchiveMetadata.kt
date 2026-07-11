package dev.qtremors.arcile.core.storage.domain

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
        get() = totalUncompressedSize.takeIf { it > 0L }
            ?.let { archiveSize.toDouble() / it.toDouble() }
}
