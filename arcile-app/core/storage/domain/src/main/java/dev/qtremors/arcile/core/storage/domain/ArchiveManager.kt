package dev.qtremors.arcile.core.storage.domain

interface ArchiveManager {
    suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>>
    suspend fun listArchiveEntries(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<List<ArchiveEntryModel>> = listArchiveEntries(archivePath)

    suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary>
    suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<ArchiveSummary> = getArchiveMetadata(archivePath)

    suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String? = null,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit>

    suspend fun detectArchiveConflicts(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String? = null,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<List<FileConflict>> =
        unsupportedCapability(StorageCapability.ARCHIVE_CONFLICT_DETECTION)

    suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.STORE,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit>
}
