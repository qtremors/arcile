package dev.qtremors.arcile.core.storage.data.manager

import android.annotation.SuppressLint
import dev.qtremors.arcile.core.archive.ArchivePasswordRequired
import dev.qtremors.arcile.core.archive.ArchiveReadSession
import dev.qtremors.arcile.core.archive.ArchiveReadSource
import dev.qtremors.arcile.core.archive.ArchiveReader
import dev.qtremors.arcile.core.archive.isSingleStreamCompression
import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveManager
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

typealias ArchiveSafetyPolicy = dev.qtremors.arcile.core.archive.ArchiveSafetyPolicy

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
    private val tarHandler by lazy { TarAndCompressedStreamHandler(safetyPolicy, ::validateMutationPath) }
    private val archiveReader = ArchiveReader(safetyPolicy)

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
            readArchive(archive, format, password, nameEncoding) { reader ->
                reader.entries.mapNotNull { it.toModel() }
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
                readArchive(archive, format, password, nameEncoding) { reader ->
                    extractEntries(reader, format, destination, entryPrefix, resolutions, createdOutputs, replacementBackups, onProgress)
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

    private suspend fun <T> readArchive(
        archive: File,
        format: ArchiveFormat,
        password: String?,
        encoding: ArchiveNameEncoding,
        read: suspend (ArchiveReadSession) -> T
    ): T {
        val coroutine = currentCoroutineContext()
        val secret = password?.toCharArray()
        try {
            val source = ArchiveReadSource(archive.name, archive.length(), archive.lastModified().takeIf { it > 0L }) {
                Files.newByteChannel(archive.toPath())
            }
            return archiveReader.open(source, format, secret, encoding) { coroutine.ensureActive() }.use { reader ->
                if (password.isNullOrEmpty() && reader.entries.any { it.encrypted }) throw ArchivePasswordRequired()
                read(reader)
            }
        } finally {
            secret?.fill('\u0000')
        }
    }

    @SuppressLint("UsableSpace")
    private suspend fun extractEntries(
        reader: ArchiveReadSession,
        format: ArchiveFormat,
        destination: File,
        entryPrefix: String?,
        resolutions: Map<String, ConflictResolution>,
        createdOutputs: MutableSet<File>,
        replacementBackups: MutableMap<File, File>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val context = ArchiveExtractionContext(safetyPolicy, ::validateMutationPath)
        val entries = reader.entries.mapNotNull { entry ->
            val model = entry.toModel()?.takeIf { it.path.matchesPrefix(entryPrefix) } ?: return@mapNotNull null
            entry to model
        }
        val total = entries.sumOf { it.second.size }.coerceAtLeast(1L)
        var copied = 0L
        for ((index, item) in entries.withIndex()) {
            currentCoroutineContext().ensureActive()
            val (entry, model) = item
            if (!entry.canRead) throw IOException("Archive contains unsupported entries")
            val target = context.resolveTarget(destination, model.path, model.isDirectory, resolutions)
            if (target != null) {
                if (entry.isDirectory) {
                    prepareDirectoryTarget(target, createdOutputs, replacementBackups)
                } else {
                    if (format.isSingleStreamCompression) {
                        val free = destination.usableSpace
                        require(free <= 0L || entry.size <= (free - ARCHIVE_EXTRACTION_FREE_SPACE_RESERVE_BYTES).coerceAtLeast(0L)) {
                            "Not enough free space to extract this archive safely"
                        }
                    }
                    prepareFileTarget(target, createdOutputs, replacementBackups)
                    validateMutationPath(target).getOrThrow()
                    entry.openStream().buffered().use { input ->
                        target.outputStream().buffered().use { output ->
                            copied += copyWithProgress(input::read, output::write) { delta ->
                                onProgress?.invoke(BulkFileOperationProgress(index, entries.size, model.path, copied + delta, total))
                            }
                        }
                    }
                    entry.modifiedAt?.let { target.setLastModified(it) }
                }
            }
            onProgress?.invoke(BulkFileOperationProgress(index + 1, entries.size, model.path, copied, total))
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
                val target = try {
                    extractionContext.resolveRequestedTarget(destination, entry.path)
                } catch (_: UnsafeArchiveEntryException) {
                    return@mapNotNull null
                }
                if (!target.exists()) return@mapNotNull null
                FileConflict(
                    sourcePath = entry.path,
                    sourceFile = FileModel(
                        name = entry.name,
                        reference = entry.path,
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
        compressionLevel: ArchiveCompressionLevel,
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
                    ArchiveFormat.ZIP -> zipHandler.create(sources, staging, password, nameEncoding, compressionLevel, onProgress)
                    ArchiveFormat.SEVEN_Z -> sevenZipHandler.create(sources, staging, password, compressionLevel, onProgress)
                    ArchiveFormat.TAR,
                    ArchiveFormat.TAR_GZIP,
                    ArchiveFormat.TGZ,
                    ArchiveFormat.TAR_BZIP2,
                    ArchiveFormat.TBZ2,
                    ArchiveFormat.TAR_XZ,
                    ArchiveFormat.TXZ -> tarHandler.create(sources, staging, format, compressionLevel, onProgress)
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
            e.rethrowIfCancellation()
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
            reference = absolutePath,
            size = if (isFile) length() else 0L,
            lastModified = lastModified(),
            isDirectory = isDirectory,
            extension = extension.lowercase(),
            isHidden = name.startsWith(".")
        )
}
