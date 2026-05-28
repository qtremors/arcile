package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveManager
import dev.qtremors.arcile.core.storage.domain.ArchiveSummary
import dev.qtremors.arcile.di.ArcileDispatchers
import java.io.File
import java.io.IOException
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
    )
) : ArchiveManager {
    private val zipHandler by lazy { ZipArchiveHandler(safetyPolicy, ::validateMutationPath) }
    private val sevenZipHandler by lazy { SevenZipHandler(safetyPolicy, ::validateMutationPath) }

    override suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>> = withContext(dispatchers.io) {
        listArchiveEntries(archivePath, null)
    }

    override suspend fun listArchiveEntries(archivePath: String, password: String?): Result<List<ArchiveEntryModel>> = withContext(dispatchers.io) {
        runArchiveCatching {
            val archive = File(archivePath)
            validatePath(archive).getOrThrow()
            require(archive.isFile) { "Archive is not available" }
            when (ArchiveFormat.fromPath(archivePath) ?: throw IllegalArgumentException("Unsupported archive format")) {
                ArchiveFormat.ZIP -> zipHandler.listEntries(archive, password)
                ArchiveFormat.SEVEN_Z -> sevenZipHandler.listEntries(archive, password)
            }
        }
    }

    override suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary> = withContext(dispatchers.io) {
        getArchiveMetadata(archivePath, null)
    }

    override suspend fun getArchiveMetadata(archivePath: String, password: String?): Result<ArchiveSummary> = withContext(dispatchers.io) {
        runArchiveCatching {
            val archive = File(archivePath)
            validatePath(archive).getOrThrow()
            val format = ArchiveFormat.fromPath(archivePath) ?: throw IllegalArgumentException("Unsupported archive format")
            summarize(archive, format, listArchiveEntries(archivePath, password).getOrThrow())
        }
    }

    override suspend fun extractArchive(
        archivePath: String,
        destinationPath: String,
        entryPrefix: String?,
        password: String?,
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
            try {
                when (ArchiveFormat.fromPath(archivePath) ?: throw IllegalArgumentException("Unsupported archive format")) {
                    ArchiveFormat.ZIP -> zipHandler.extract(archive, destination, entryPrefix, password, createdOutputs, onProgress)
                    ArchiveFormat.SEVEN_Z -> sevenZipHandler.extract(archive, destination, entryPrefix, password, createdOutputs, onProgress)
                }
            } catch (e: Exception) {
                cleanupCreatedOutputs(createdOutputs)
                throw e
            }
            mutationFinalizer.finalize(destination.absolutePath)
        }
    }

    override suspend fun createArchive(
        sourcePaths: List<String>,
        destinationArchivePath: String,
        format: ArchiveFormat,
        password: String?,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = withContext(dispatchers.io) {
        runArchiveCatching {
            require(sourcePaths.isNotEmpty()) { "Select at least one item to archive" }
            val sources = sourcePaths.distinct().map(::File)
            val target = File(destinationArchivePath)
            validateMutationPath(target).getOrThrow()
            target.parentFile?.mkdirs()
            if (target.exists()) throw IOException("Archive already exists")

            val staging = File(target.parentFile, ".${target.name}.arcile-archive.tmp")
            validateMutationPath(staging).getOrThrow()
            if (staging.exists()) staging.delete()
            try {
                when (format) {
                    ArchiveFormat.ZIP -> zipHandler.create(sources, staging, password, onProgress)
                    ArchiveFormat.SEVEN_Z -> sevenZipHandler.create(sources, staging, password, onProgress)
                }
                if (!staging.renameTo(target)) throw IOException("Failed to create archive")
            } catch (e: Exception) {
                if (staging.exists()) staging.delete()
                throw e
            }
            mutationFinalizer.finalize(target.absolutePath)
        }
    }

    private fun validatePath(file: File): Result<Unit> =
        PathSafety.validatePath(file, volumeProvider.activeStorageRoots)

    private fun validateMutationPath(file: File): Result<Unit> =
        PathSafety.validatePath(file, volumeProvider.activeStorageRoots, PathSafety.OperationPolicy.RECURSIVE_MUTATE)

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
}
