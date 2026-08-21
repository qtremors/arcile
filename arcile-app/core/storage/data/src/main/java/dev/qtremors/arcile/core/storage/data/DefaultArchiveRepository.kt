package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveManager
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileOperationProgress

class DefaultArchiveRepository(
    private val archiveManager: ArchiveManager
) : ArchiveRepository {
    override suspend fun listArchiveEntries(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<ArchiveEntryModel>> =
        archiveManager.listArchiveEntries(archivePath, password, nameEncoding)

    override suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<ArchiveSummary> =
        archiveManager.getArchiveMetadata(archivePath, password, nameEncoding)

    override suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Unit> = archiveManager.extractArchive(
        archivePath,
        destinationPath,
        entryPrefix,
        password,
        nameEncoding,
        resolutions,
        onProgress
    )

    override suspend fun detectArchiveConflicts(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<FileConflict>> = archiveManager.detectArchiveConflicts(
        archivePath,
        destinationPath,
        entryPrefix,
        password,
        nameEncoding
    )

    override suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Unit> = archiveManager.createArchive(
        sourcePaths,
        destinationArchivePath,
        format,
        password,
        nameEncoding,
        compressionLevel,
        onProgress
    )
}
