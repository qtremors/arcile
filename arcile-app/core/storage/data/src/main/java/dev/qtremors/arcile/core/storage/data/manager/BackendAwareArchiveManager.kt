package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveManager
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import java.io.File
import kotlinx.coroutines.CancellationException

/**
 * Routes archive work by stable storage-node identity.
 *
 * The existing codec manager remains responsible for format fidelity and archive
 * safety. This layer owns descriptor-backed ingress/egress, private compatibility
 * workspaces, cross-backend publication, and cleanup.
 */
internal class BackendAwareArchiveManager(
    private val codec: DefaultArchiveManager,
    private val bridge: ArchiveNodeWorkspaceBridge
) : ArchiveManager {
    override suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>> =
        codec.listArchiveEntries(archivePath)

    override suspend fun listArchiveEntries(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<ArchiveEntryModel>> = codec.listArchiveEntries(archivePath, password, nameEncoding)

    override suspend fun listArchiveEntries(
        archive: StorageNodeRef,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<ArchiveEntryModel>> = archiveResult {
        requireReadableArchive(archive)
        if (archive.isDirectLocal()) {
            codec.listArchiveEntries(archive.displayPath.absolutePath, password, nameEncoding).getOrThrow()
        } else {
            bridge.withWorkspace { workspace ->
                val staged = bridge.stageArchive(archive, workspace)
                codec.listArchiveEntries(staged.absolutePath, password, nameEncoding).getOrThrow()
            }
        }
    }

    override suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary> =
        codec.getArchiveMetadata(archivePath)

    override suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<ArchiveSummary> = codec.getArchiveMetadata(archivePath, password, nameEncoding)

    override suspend fun getArchiveMetadata(
        archive: StorageNodeRef,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<ArchiveSummary> = archiveResult {
        requireReadableArchive(archive)
        if (archive.isDirectLocal()) {
            codec.getArchiveMetadata(archive.displayPath.absolutePath, password, nameEncoding).getOrThrow()
        } else {
            bridge.withWorkspace { workspace ->
                val staged = bridge.stageArchive(archive, workspace)
                codec.getArchiveMetadata(staged.absolutePath, password, nameEncoding).getOrThrow().copy(
                    archivePath = archive.displayPath.absolutePath
                )
            }
        }
    }

    override suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = codec.extractArchive(
        archivePath,
        destinationPath,
        entryPrefix,
        password,
        nameEncoding,
        resolutions,
        onProgress
    )

    override suspend fun extractArchive(
        archive: StorageNodeRef,
        destination: StorageNodeRef,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = archiveResult {
        requireReadableArchive(archive)
        requireWritableDestination(destination)
        when {
            archive.isDirectLocal() && destination.isDirectLocal() -> codec.extractArchive(
                archive.displayPath.absolutePath,
                destination.displayPath.absolutePath,
                entryPrefix,
                password,
                nameEncoding,
                resolutions,
                onProgress
            ).getOrThrow()

            destination.isDirectLocal() -> bridge.withWorkspace { workspace ->
                val stagedArchive = bridge.stageArchive(archive, workspace)
                codec.extractArchive(
                    stagedArchive.absolutePath,
                    destination.displayPath.absolutePath,
                    entryPrefix,
                    password,
                    nameEncoding,
                    resolutions,
                    onProgress
                ).getOrThrow()
            }

            else -> bridge.withWorkspace { workspace ->
                val codecArchive = if (archive.isDirectLocal()) {
                    File(archive.displayPath.absolutePath)
                } else {
                    bridge.stageArchive(archive, workspace)
                }
                require(workspace.extractionRoot.mkdirs() || workspace.extractionRoot.isDirectory) {
                    "Could not prepare archive extraction"
                }
                codec.extractArchive(
                    codecArchive.absolutePath,
                    workspace.extractionRoot.absolutePath,
                    entryPrefix,
                    password,
                    nameEncoding,
                    emptyMap(),
                    null
                ).getOrThrow()
                bridge.publishExtraction(
                    extractedRoot = workspace.extractionRoot,
                    destination = destination,
                    resolutions = resolutions,
                    onProgress = onProgress
                )
            }
        }
    }

    override suspend fun detectArchiveConflicts(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<FileConflict>> = codec.detectArchiveConflicts(
        archivePath,
        destinationPath,
        entryPrefix,
        password,
        nameEncoding
    )

    override suspend fun detectArchiveConflicts(
        archive: StorageNodeRef,
        destination: StorageNodeRef,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<FileConflict>> = archiveResult {
        requireReadableArchive(archive)
        if (archive.isDirectLocal() && destination.isDirectLocal()) {
            codec.detectArchiveConflicts(
                archive.displayPath.absolutePath,
                destination.displayPath.absolutePath,
                entryPrefix,
                password,
                nameEncoding
            ).getOrThrow()
        } else if (destination.isDirectLocal()) {
            bridge.withWorkspace { workspace ->
                val stagedArchive = bridge.stageArchive(archive, workspace)
                codec.detectArchiveConflicts(
                    stagedArchive.absolutePath,
                    destination.displayPath.absolutePath,
                    entryPrefix,
                    password,
                    nameEncoding
                ).getOrThrow()
            }
        } else {
            val entries = listArchiveEntries(archive, password, nameEncoding).getOrThrow()
            bridge.detectConflicts(entries, destination, entryPrefix)
        }
    }

    override suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = codec.createArchive(
        sourcePaths,
        destinationArchivePath,
        format,
        password,
        nameEncoding,
        compressionLevel,
        onProgress
    )

    override suspend fun createArchive(
        sources: Collection<StorageNodeRef>,
        destinationArchive: StorageNodeRef,
        format: ArchiveFormat,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = archiveResult {
        require(sources.isNotEmpty()) { "Select at least one item to archive" }
        sources.forEach(::requireArchiveSource)
        requireWritableDestination(destinationArchive.parentReference())
        if (sources.all { it.isDirectLocal() } && destinationArchive.isDirectLocal()) {
            codec.createArchive(
                sources.map { it.displayPath.absolutePath },
                destinationArchive.displayPath.absolutePath,
                format,
                password,
                nameEncoding,
                compressionLevel,
                onProgress
            ).getOrThrow()
        } else {
            bridge.withWorkspace { workspace ->
                val stagedSources = bridge.stageSources(sources, workspace, onProgress)
                val stagedArchive = workspace.archiveOutputWithExtension(
                    destinationArchive.displayPath.absolutePath
                )
                codec.createArchive(
                    stagedSources.map(File::getAbsolutePath),
                    stagedArchive.absolutePath,
                    format,
                    password,
                    nameEncoding,
                    compressionLevel,
                    onProgress
                ).getOrThrow()
                bridge.publishArchive(stagedArchive, destinationArchive, onProgress)
            }
        }
    }

    private fun requireReadableArchive(archive: StorageNodeRef) {
        require(archive.capabilities.canRead) { "Archive is not readable" }
        require(archive.capabilities.canArchive) { "This file cannot be opened as an archive" }
    }

    private fun requireArchiveSource(source: StorageNodeRef) {
        require(source.capabilities.canRead) { "Archive source is not readable" }
        require(source.capabilities.canArchive) { "Archive source is not supported" }
    }

    private fun requireWritableDestination(destination: StorageNodeRef) {
        require(destination.capabilities.canWrite) { "Archive destination is read-only" }
    }

    private suspend fun <T> archiveResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        if (error is CancellationException) throw error
        error.rethrowIfCancellation()
        Result.failure(error.toFriendlyArchiveFailure())
    }

    private fun Throwable.toFriendlyArchiveFailure(): Throwable {
        val detail = message.orEmpty()
        return if (
            detail.contains("password", ignoreCase = true) ||
            detail.contains("encrypted", ignoreCase = true)
        ) {
            IllegalArgumentException("A password is required or the password is incorrect", this)
        } else {
            this
        }
    }
}

private fun StorageNodeRef.isDirectLocal(): Boolean =
    backendId == StorageNodeRef.LOCAL_BACKEND_ID
