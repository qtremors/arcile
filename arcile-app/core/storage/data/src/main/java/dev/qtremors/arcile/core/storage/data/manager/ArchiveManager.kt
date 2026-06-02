package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveManager
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.di.ArcileDispatchers
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ArchiveSafetyPolicy(
    val maxEntries: Int = 10_000,
    val maxUncompressedBytes: Long = 20L * 1024L * 1024L * 1024L,
    val maxEntryPathLength: Int = 4_096,
    val maxNestedDepth: Int = 64,
    val maxCompressionRatio: Double = 1_000.0
)

class DefaultArchiveManager(
    private val volumeProvider: VolumeProvider,
    private val mutationFinalizer: MutationFinalizer,
    private val safetyPolicy: ArchiveSafetyPolicy = ArchiveSafetyPolicy(),
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    ),
    private val mutationJournal: MutationJournal = NoOpMutationJournal(),
    private val rename: (File, File) -> Boolean = { source, target -> source.renameTo(target) }
) : ArchiveManager {
    private val zipHandler by lazy { ZipArchiveHandler(safetyPolicy, ::validateMutationPath) }
    private val sevenZipHandler by lazy { SevenZipHandler(safetyPolicy, ::validateMutationPath) }
    private val tarHandler by lazy { TarArchiveHandler(safetyPolicy, ::validateMutationPath) }

    override suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>> = withContext(dispatchers.io) {
        listArchiveEntries(archivePath, null, ArchiveNameEncoding.UTF_8)
    }

    override suspend fun listArchiveEntries(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<ArchiveEntryModel>> = withContext(dispatchers.io) {
        runArchiveCatching {
            val archive = File(archivePath)
            validatePath(archive).getOrThrow()
            require(archive.isFile) { "Archive is not available" }
            val format = supportedFormat(archivePath)
            when (format) {
                ArchiveFormat.ZIP -> zipHandler.listEntries(archive, password, nameEncoding)
                ArchiveFormat.SEVEN_Z -> sevenZipHandler.listEntries(archive, password)
                ArchiveFormat.TAR,
                ArchiveFormat.TAR_GZIP,
                ArchiveFormat.TGZ,
                ArchiveFormat.TAR_BZIP2,
                ArchiveFormat.TBZ2,
                ArchiveFormat.TAR_XZ,
                ArchiveFormat.TXZ,
                ArchiveFormat.GZIP,
                ArchiveFormat.BZIP2,
                ArchiveFormat.XZ -> tarHandler.listEntries(archive, format)
                ArchiveFormat.RAR -> throw IllegalArgumentException("RAR archives are not supported in this build")
            }
        }
    }

    override suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary> = withContext(dispatchers.io) {
        getArchiveMetadata(archivePath, null, ArchiveNameEncoding.UTF_8)
    }

    override suspend fun getArchiveMetadata(
        archivePath: String,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<ArchiveSummary> = withContext(dispatchers.io) {
        runArchiveCatching {
            val archive = File(archivePath)
            validatePath(archive).getOrThrow()
            val format = supportedFormat(archivePath)
            summarize(archive, format, listArchiveEntries(archivePath, password, nameEncoding).getOrThrow())
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
    ): Result<Unit> = withContext(dispatchers.io) {
        runArchiveCatching {
            val archive = File(archivePath)
            val destination = File(destinationPath)
            validatePath(archive).getOrThrow()
            validateMutationPath(destination).getOrThrow()
            require(archive.isFile) { "Archive is not available" }
            if (!destination.exists()) destination.mkdirs()
            require(destination.isDirectory) { "Destination must be a folder" }

            val createdOutputs = linkedSetOf<File>()
            val replacementBackups = linkedMapOf<File, File>()
            try {
                val format = supportedFormat(archivePath)
                when (format) {
                    ArchiveFormat.ZIP -> zipHandler.extract(archive, destination, entryPrefix, password, nameEncoding, resolutions, createdOutputs, replacementBackups, onProgress)
                    ArchiveFormat.SEVEN_Z -> sevenZipHandler.extract(archive, destination, entryPrefix, password, resolutions, createdOutputs, replacementBackups, onProgress)
                    ArchiveFormat.TAR,
                    ArchiveFormat.TAR_GZIP,
                    ArchiveFormat.TGZ,
                    ArchiveFormat.TAR_BZIP2,
                    ArchiveFormat.TBZ2,
                    ArchiveFormat.TAR_XZ,
                    ArchiveFormat.TXZ,
                    ArchiveFormat.GZIP,
                    ArchiveFormat.BZIP2,
                    ArchiveFormat.XZ -> tarHandler.extract(archive, format, destination, entryPrefix, resolutions, createdOutputs, replacementBackups, onProgress)
                    ArchiveFormat.RAR -> throw IllegalArgumentException("RAR archives are not supported in this build")
                }
            } catch (e: Exception) {
                cleanupCreatedOutputs(createdOutputs)
                restoreReplacementBackups(replacementBackups)
                throw e
            }
            cleanupReplacementBackups(replacementBackups)
            mutationFinalizer.finalize(destination.absolutePath)
        }
    }

    override suspend fun detectArchiveConflicts(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding
    ): Result<List<FileConflict>> = withContext(dispatchers.io) {
        runArchiveCatching {
            val archive = File(archivePath)
            val destination = File(destinationPath)
            validatePath(archive).getOrThrow()
            validateMutationPath(destination).getOrThrow()
            require(archive.isFile) { "Archive is not available" }
            val entries = listArchiveEntries(archivePath, password, nameEncoding).getOrThrow()
                .filter { !it.isDirectory && it.path.matchesPrefix(entryPrefix) }
            val extractionContext = ArchiveExtractionContext(safetyPolicy, ::validateMutationPath)
            entries.mapNotNull { entry ->
                val target = extractionContext.resolveRequestedTarget(destination, entry.path)
                if (!target.exists()) return@mapNotNull null
                FileConflict(
                    sourcePath = entry.path,
                    sourceFile = FileModel(
                        name = entry.name,
                        absolutePath = entry.path,
                        size = entry.size,
                        lastModified = entry.lastModified ?: 0L,
                        isDirectory = false,
                        extension = entry.name.substringAfterLast('.', missingDelimiterValue = "").lowercase(),
                        isHidden = entry.name.startsWith(".")
                    ),
                    existingFile = target.toFileModel()
                )
            }
        }
    }

    override suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = withContext(dispatchers.io) {
        runArchiveCatching {
            require(sourcePaths.isNotEmpty()) { "Select at least one item to archive" }
            val sources = sourcePaths.distinct().map(::File)
            val target = File(destinationArchivePath)
            validateMutationPath(target).getOrThrow()
            target.parentFile?.mkdirs()
            if (target.exists()) throw IOException("Archive already exists")

            val staging = createArchiveStagingTarget(target)
            validateMutationPath(staging).getOrThrow()
            mutationJournal.recordTemporaryPath(staging.absolutePath)
            try {
                when (format) {
                    ArchiveFormat.ZIP -> zipHandler.create(sources, staging, password, nameEncoding, onProgress)
                    ArchiveFormat.SEVEN_Z -> sevenZipHandler.create(sources, staging, password, onProgress)
                    ArchiveFormat.TAR,
                    ArchiveFormat.TAR_GZIP,
                    ArchiveFormat.TGZ,
                    ArchiveFormat.TAR_BZIP2,
                    ArchiveFormat.TBZ2,
                    ArchiveFormat.TAR_XZ,
                    ArchiveFormat.TXZ -> tarHandler.create(sources, staging, format, onProgress)
                    ArchiveFormat.GZIP,
                    ArchiveFormat.BZIP2,
                    ArchiveFormat.XZ,
                    ArchiveFormat.RAR -> throw IllegalArgumentException("${format.displayName} archive creation is not supported")
                }
                if (!rename(staging, target)) throw IOException("Failed to create archive")
                mutationJournal.forgetTemporaryPath(staging.absolutePath)
            } catch (e: Exception) {
                if (staging.exists()) {
                    if (staging.isDirectory) staging.deleteRecursively() else staging.delete()
                }
                mutationJournal.forgetTemporaryPath(staging.absolutePath)
                throw e
            }
            mutationFinalizer.finalize(target.absolutePath)
        }
    }

    private fun createArchiveStagingTarget(target: File): File {
        val parent = target.parentFile ?: throw IllegalStateException("Archive target has no parent directory")
        var candidate: File
        do {
            candidate = File(parent, ".${target.name}.arcile-archive-${UUID.randomUUID()}.tmp")
        } while (candidate.exists())
        return candidate
    }

    private fun validatePath(file: File): Result<Unit> =
        PathSafety.validatePath(file, volumeProvider.activeStorageRoots)

    private fun validateMutationPath(file: File): Result<Unit> =
        PathSafety.validatePath(file, volumeProvider.activeStorageRoots, PathSafety.OperationPolicy.RECURSIVE_MUTATE)

    private fun supportedFormat(path: String): ArchiveFormat {
        val format = ArchiveFormat.fromPath(path) ?: throw IllegalArgumentException("Unsupported archive format")
        require(format.canBrowse) { "${format.displayName} archives are not supported in this build" }
        return format
    }

    private inline fun <T> runArchiveCatching(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            val message = e.message.orEmpty()
            val friendly = when {
                message.contains("password", ignoreCase = true) ||
                    message.contains("Wrong Password", ignoreCase = true) ||
                    message.contains("invalid password", ignoreCase = true) ||
                    message.contains("Cannot read encrypted", ignoreCase = true) ||
                    message.contains("encrypted", ignoreCase = true) ->
                    IllegalArgumentException("A password is required or the password is incorrect", e)
                else -> e
            }
            Result.failure(friendly)
        }

    private fun summarize(archive: File, format: ArchiveFormat, entries: List<ArchiveEntryModel>): ArchiveSummary {
        val modified = entries.mapNotNull { it.lastModified }
        return ArchiveSummary(
            archivePath = archive.absolutePath,
            format = format,
            archiveSize = archive.length(),
            totalUncompressedSize = entries.filterNot { it.isDirectory }.sumOf { it.size },
            fileCount = entries.count { !it.isDirectory },
            folderCount = entries.count { it.isDirectory },
            newestModifiedAt = modified.maxOrNull(),
            oldestModifiedAt = modified.minOrNull(),
            hasUnreadableEntries = entries.any { !it.canRead }
        )
    }

    private fun File.toFileModel(): FileModel =
        FileModel(
            name = name,
            absolutePath = absolutePath,
            size = if (isFile) length() else 0L,
            lastModified = lastModified(),
            isDirectory = isDirectory,
            extension = extension.lowercase(),
            isHidden = name.startsWith(".")
        )
}
