package dev.qtremors.arcile.core.storage.domain

interface ArchiveRepository {
    suspend fun listArchiveEntries(
        archive: StorageNodeRef,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<List<ArchiveEntryModel>> = listArchiveEntries(
        archive.displayPath.absolutePath,
        password,
        nameEncoding
    )

    suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>> =
        unsupportedCapability(StorageCapability.ARCHIVE_LIST)

    suspend fun listArchiveEntries(
        archivePath: String,
        password: String?
    ): Result<List<ArchiveEntryModel>> = listArchiveEntries(archivePath)

    suspend fun listArchiveEntries(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<List<ArchiveEntryModel>> = listArchiveEntries(archivePath, password)

    suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary> =
        unsupportedCapability(StorageCapability.ARCHIVE_METADATA)

    suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?
    ): Result<ArchiveSummary> = getArchiveMetadata(archivePath)

    suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<ArchiveSummary> = getArchiveMetadata(archivePath, password)

    suspend fun getArchiveMetadata(
        archive: StorageNodeRef,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<ArchiveSummary> = getArchiveMetadata(
        archive.displayPath.absolutePath,
        password,
        nameEncoding
    )

    suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String? = null,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit> = unsupportedCapability(StorageCapability.ARCHIVE_EXTRACT)

    suspend fun extractArchive(
        archive: StorageNodeRef,
        destination: StorageNodeRef,
        entryPrefix: String? = null,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
        resolutions: Map<String, ConflictResolution> = emptyMap(),
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit> = extractArchive(
        archivePath = archive.displayPath.absolutePath,
        destinationPath = destination.displayPath.absolutePath,
        entryPrefix = entryPrefix,
        password = password,
        nameEncoding = nameEncoding,
        resolutions = resolutions,
        onProgress = onProgress
    )

    suspend fun detectArchiveConflicts(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String? = null,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<List<FileConflict>> =
        unsupportedCapability(StorageCapability.ARCHIVE_CONFLICT_DETECTION)

    suspend fun detectArchiveConflicts(
        archive: StorageNodeRef,
        destination: StorageNodeRef,
        entryPrefix: String? = null,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8
    ): Result<List<FileConflict>> = detectArchiveConflicts(
        archivePath = archive.displayPath.absolutePath,
        destinationPath = destination.displayPath.absolutePath,
        entryPrefix = entryPrefix,
        password = password,
        nameEncoding = nameEncoding
    )

    suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat = ArchiveFormat.ZIP,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.STORE,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit> = unsupportedCapability(StorageCapability.ARCHIVE_CREATE)

    suspend fun createArchive(
        sources: Collection<StorageNodeRef>,
        destinationArchive: StorageNodeRef,
        format: ArchiveFormat = ArchiveFormat.ZIP,
        password: String? = null,
        nameEncoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
        compressionLevel: ArchiveCompressionLevel = ArchiveCompressionLevel.STORE,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Unit> = createArchive(
        sourcePaths = sources.map { it.displayPath.absolutePath },
        destinationArchivePath = destinationArchive.displayPath.absolutePath,
        format = format,
        password = password,
        nameEncoding = nameEncoding,
        compressionLevel = compressionLevel,
        onProgress = onProgress
    )
}
