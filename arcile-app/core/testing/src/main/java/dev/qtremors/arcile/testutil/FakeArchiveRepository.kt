package dev.qtremors.arcile.testutil

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveRepository
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef

class FakeArchiveRepository : ArchiveRepository {
    var archiveEntriesResultProvider: (suspend (String, String?, ArchiveNameEncoding) -> Result<List<ArchiveEntryModel>>)? = null
    var archiveMetadataResultProvider: (suspend (String, String?, ArchiveNameEncoding) -> Result<ArchiveSummary>)? = null
    var detectArchiveConflictsResultProvider: (suspend (String, String, String?, String?, ArchiveNameEncoding) -> Result<List<FileConflict>>)? = null
    var extractArchiveResultProvider: (suspend (String, String, String?, String?, ArchiveNameEncoding, Map<String, ConflictResolution>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null
    var createArchiveResultProvider: (suspend (List<String>, String, ArchiveFormat, String?, ArchiveNameEncoding, ArchiveCompressionLevel, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null
    var nodeArchiveEntriesResultProvider: (suspend (StorageNodeRef, String?, ArchiveNameEncoding) -> Result<List<ArchiveEntryModel>>)? = null
    var nodeArchiveMetadataResultProvider: (suspend (StorageNodeRef, String?, ArchiveNameEncoding) -> Result<ArchiveSummary>)? = null
    var nodeDetectArchiveConflictsResultProvider: (suspend (StorageNodeRef, StorageNodeRef, String?, String?, ArchiveNameEncoding) -> Result<List<FileConflict>>)? = null
    var nodeExtractArchiveResultProvider: (suspend (StorageNodeRef, StorageNodeRef, String?, String?, ArchiveNameEncoding, Map<String, ConflictResolution>, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null
    var nodeCreateArchiveResultProvider: (suspend (List<StorageNodeRef>, StorageNodeRef, ArchiveFormat, String?, ArchiveNameEncoding, ArchiveCompressionLevel, ((BulkFileOperationProgress) -> Unit)?) -> Result<Unit>)? = null

    val extractArchiveRequests = mutableListOf<ArchiveExtractRequest>()
    val createArchiveRequests = mutableListOf<ArchiveCreateRequest>()
    val nodeExtractArchiveRequests = mutableListOf<ArchiveNodeExtractRequest>()
    val nodeCreateArchiveRequests = mutableListOf<ArchiveNodeCreateRequest>()

    data class ArchiveExtractRequest(
        val archivePath: String,
        val destinationPath: String,
        val entryPrefix: String?,
        val password: String?,
        val nameEncoding: ArchiveNameEncoding,
        val resolutions: Map<String, ConflictResolution>
    )

    data class ArchiveCreateRequest(
        val sourcePaths: List<String>,
        val destinationArchivePath: String,
        val format: ArchiveFormat,
        val password: String?,
        val nameEncoding: ArchiveNameEncoding,
        val compressionLevel: ArchiveCompressionLevel
    )

    data class ArchiveNodeExtractRequest(
        val archive: StorageNodeRef,
        val destination: StorageNodeRef,
        val entryPrefix: String?,
        val password: String?,
        val nameEncoding: ArchiveNameEncoding,
        val resolutions: Map<String, ConflictResolution>
    )

    data class ArchiveNodeCreateRequest(
        val sources: List<StorageNodeRef>,
        val destinationArchive: StorageNodeRef,
        val format: ArchiveFormat,
        val password: String?,
        val nameEncoding: ArchiveNameEncoding,
        val compressionLevel: ArchiveCompressionLevel
    )

    override suspend fun listArchiveEntries(
        archive: StorageNodeRef,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<ArchiveEntryModel>> =
        nodeArchiveEntriesResultProvider?.invoke(archive, password, nameEncoding)
            ?: listArchiveEntries(archive.displayPath.absolutePath, password, nameEncoding)

    override suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>> =
        listArchiveEntries(archivePath, null, ArchiveNameEncoding.UTF_8)

    override suspend fun listArchiveEntries(
        archivePath: String,
        password: String?
    ): Result<List<ArchiveEntryModel>> =
        listArchiveEntries(archivePath, password, ArchiveNameEncoding.UTF_8)

    override suspend fun listArchiveEntries(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<ArchiveEntryModel>> =
        archiveEntriesResultProvider?.invoke(archivePath, password, nameEncoding)
            ?: Result.success(emptyList())

    override suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary> =
        getArchiveMetadata(archivePath, null, ArchiveNameEncoding.UTF_8)

    override suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?
    ): Result<ArchiveSummary> =
        getArchiveMetadata(archivePath, password, ArchiveNameEncoding.UTF_8)

    override suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<ArchiveSummary> =
        archiveMetadataResultProvider?.invoke(archivePath, password, nameEncoding)
            ?: Result.failure(NotImplementedError())

    override suspend fun getArchiveMetadata(
        archive: StorageNodeRef,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<ArchiveSummary> =
        nodeArchiveMetadataResultProvider?.invoke(archive, password, nameEncoding)
            ?: getArchiveMetadata(archive.displayPath.absolutePath, password, nameEncoding)

    override suspend fun detectArchiveConflicts(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<FileConflict>> =
        detectArchiveConflictsResultProvider?.invoke(
            archivePath,
            destinationPath,
            entryPrefix,
            password,
            nameEncoding
        ) ?: Result.success(emptyList())

    override suspend fun detectArchiveConflicts(
        archive: StorageNodeRef,
        destination: StorageNodeRef,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<FileConflict>> =
        nodeDetectArchiveConflictsResultProvider?.invoke(
            archive,
            destination,
            entryPrefix,
            password,
            nameEncoding
        ) ?: detectArchiveConflicts(
            archive.displayPath.absolutePath,
            destination.displayPath.absolutePath,
            entryPrefix,
            password,
            nameEncoding
        )

    override suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        extractArchiveRequests += ArchiveExtractRequest(
            archivePath,
            destinationPath,
            entryPrefix,
            password,
            nameEncoding,
            resolutions
        )
        return extractArchiveResultProvider?.invoke(
            archivePath,
            destinationPath,
            entryPrefix,
            password,
            nameEncoding,
            resolutions,
            onProgress
        ) ?: Result.success(Unit)
    }

    override suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        createArchiveRequests += ArchiveCreateRequest(
            sourcePaths,
            destinationArchivePath,
            format,
            password,
            nameEncoding,
            compressionLevel
        )
        return createArchiveResultProvider?.invoke(
            sourcePaths,
            destinationArchivePath,
            format,
            password,
            nameEncoding,
            compressionLevel,
            onProgress
        ) ?: Result.success(Unit)
    }

    override suspend fun extractArchive(
        archive: StorageNodeRef,
        destination: StorageNodeRef,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        nodeExtractArchiveRequests += ArchiveNodeExtractRequest(
            archive,
            destination,
            entryPrefix,
            password,
            nameEncoding,
            resolutions
        )
        return nodeExtractArchiveResultProvider?.invoke(
            archive,
            destination,
            entryPrefix,
            password,
            nameEncoding,
            resolutions,
            onProgress
        ) ?: Result.success(Unit)
    }

    override suspend fun createArchive(
        sources: Collection<StorageNodeRef>,
        destinationArchive: StorageNodeRef,
        format: ArchiveFormat,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> {
        val sourceList = sources.toList()
        nodeCreateArchiveRequests += ArchiveNodeCreateRequest(
            sourceList,
            destinationArchive,
            format,
            password,
            nameEncoding,
            compressionLevel
        )
        return nodeCreateArchiveResultProvider?.invoke(
            sourceList,
            destinationArchive,
            format,
            password,
            nameEncoding,
            compressionLevel,
            onProgress
        ) ?: Result.success(Unit)
    }
}
