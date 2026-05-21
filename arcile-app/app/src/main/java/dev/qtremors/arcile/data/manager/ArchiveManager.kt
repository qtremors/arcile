package dev.qtremors.arcile.data.manager

import dev.qtremors.arcile.data.MutationFinalizer
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.source.FileConflictNameGenerator
import dev.qtremors.arcile.data.util.PathSafety
import dev.qtremors.arcile.domain.ArchiveEntryModel
import dev.qtremors.arcile.domain.ArchiveFormat
import dev.qtremors.arcile.domain.ArchiveManager
import dev.qtremors.arcile.domain.ArchiveSummary
import dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.util.Date

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
    private val safetyPolicy: ArchiveSafetyPolicy = ArchiveSafetyPolicy()
) : ArchiveManager {
    private companion object {
        const val BUFFER_SIZE = 128 * 1024
    }

    override suspend fun listArchiveEntries(archivePath: String): Result<List<ArchiveEntryModel>> = withContext(Dispatchers.IO) {
        listArchiveEntries(archivePath, null)
    }

    override suspend fun listArchiveEntries(archivePath: String, password: String?): Result<List<ArchiveEntryModel>> = withContext(Dispatchers.IO) {
        runArchiveCatching {
            val archive = File(archivePath)
            validatePath(archive).getOrThrow()
            require(archive.isFile) { "Archive is not available" }
            when (ArchiveFormat.fromPath(archivePath) ?: throw IllegalArgumentException("Unsupported archive format")) {
                ArchiveFormat.ZIP -> listZipEntries(archive, password)
                ArchiveFormat.SEVEN_Z -> listSevenZEntries(archive, password)
            }
        }
    }

    override suspend fun getArchiveMetadata(archivePath: String): Result<ArchiveSummary> = withContext(Dispatchers.IO) {
        getArchiveMetadata(archivePath, null)
    }

    override suspend fun getArchiveMetadata(archivePath: String, password: String?): Result<ArchiveSummary> = withContext(Dispatchers.IO) {
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
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
                    ArchiveFormat.ZIP -> extractZip(archive, destination, entryPrefix, password, createdOutputs, onProgress)
                    ArchiveFormat.SEVEN_Z -> extractSevenZ(archive, destination, entryPrefix, password, createdOutputs, onProgress)
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
    ): Result<Unit> = withContext(Dispatchers.IO) {
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
                    ArchiveFormat.ZIP -> createZip(sources, staging, password, onProgress)
                    ArchiveFormat.SEVEN_Z -> createSevenZ(sources, staging, password, onProgress)
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

    private fun listZipEntries(archive: File, password: String?): List<ArchiveEntryModel> {
        val zip4j = Zip4jFile(archive, password?.toCharArray())
        if (zip4j.isEncrypted && password.isNullOrEmpty()) {
            throw IllegalArgumentException("A password is required or the password is incorrect")
        }
        if (zip4j.isEncrypted) {
            val safety = ArchiveSafetyTally()
            return zip4j.fileHeaders.map { entry ->
                safety.accept(entry.fileName, entry.uncompressedSize.coerceAtLeast(0L), entry.compressedSize.coerceAtLeast(0L))
                ArchiveEntryModel(
                    name = entry.fileName.substringAfterLast('/').ifBlank { entry.fileName.trimEnd('/') },
                    path = entry.fileName.normalizeEntryName(),
                    size = entry.uncompressedSize.coerceAtLeast(0L),
                    compressedSize = entry.compressedSize.coerceAtLeast(0L),
                    lastModified = entry.lastModifiedTimeEpoch.takeIf { it > 0L },
                    isDirectory = entry.isDirectory,
                    canRead = true
                )
            }
        }
        return ZipFile.builder().setFile(archive).get().use { zip ->
            val safety = ArchiveSafetyTally()
            zip.entries.asSequence().map { entry ->
                safety.accept(entry.name, entry.size.coerceAtLeast(0L), entry.compressedSize.takeIf { it >= 0L })
                ArchiveEntryModel(
                    name = entry.name.substringAfterLast('/').ifBlank { entry.name.trimEnd('/') },
                    path = entry.name.normalizeEntryName(),
                    size = entry.size.coerceAtLeast(0L),
                    compressedSize = entry.compressedSize.takeIf { it >= 0L },
                    lastModified = entry.lastModifiedDate?.time,
                    isDirectory = entry.isDirectory,
                    canRead = zip.canReadEntryData(entry)
                )
            }.toList()
        }
    }

    private fun listSevenZEntries(archive: File, password: String?): List<ArchiveEntryModel> =
        openSevenZ(archive, password).use { sevenZ ->
            val safety = ArchiveSafetyTally()
            generateSequence { sevenZ.nextEntry }.map { entry ->
                val name = entry.name ?: "unnamed"
                safety.accept(name, entry.size.coerceAtLeast(0L), null)
                ArchiveEntryModel(
                    name = name.substringAfterLast('/'),
                    path = name.normalizeEntryName(),
                    size = entry.size.coerceAtLeast(0L),
                    compressedSize = null,
                    lastModified = entry.lastModifiedDate?.time,
                    isDirectory = entry.isDirectory,
                    canRead = true
                )
            }.toList()
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

    private suspend fun extractZip(
        archive: File,
        destination: File,
        entryPrefix: String?,
        password: String?,
        createdOutputs: MutableSet<File>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val zip4j = Zip4jFile(archive, password?.toCharArray())
        if (zip4j.isEncrypted || !password.isNullOrEmpty()) {
            extractZip4j(zip4j, destination, entryPrefix, createdOutputs, onProgress)
            return
        }
        val prefix = entryPrefix?.normalizeEntryName()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        ZipFile.builder().setFile(archive).get().use { zip ->
            val safety = ArchiveSafetyTally()
            val entries = zip.entries.asSequence()
                .filter { it.name.matchesPrefix(prefix) }
                .onEach { safety.accept(it.name, it.size.coerceAtLeast(0L), it.compressedSize.takeIf { size -> size >= 0L }) }
                .toList()
            val totalBytes = entries.sumOf { it.size.coerceAtLeast(0L) }.coerceAtLeast(1L)
            var copied = 0L
            var completed = 0
            for (entry in entries) {
                currentCoroutineContext().ensureActive()
                if (!zip.canReadEntryData(entry)) throw IOException("Archive contains unsupported entries")
                val target = resolveExtractionTarget(destination, entry.name, entry.isDirectory)
                if (entry.isDirectory) {
                    rememberCreatedOutput(target, createdOutputs)
                    target.mkdirs()
                } else {
                    rememberCreatedOutput(target, createdOutputs)
                    target.parentFile?.mkdirs()
                    validateMutationPath(target).getOrThrow()
                    zip.getInputStream(entry).use { input ->
                        BufferedInputStream(input).use { buffered ->
                            BufferedOutputStream(target.outputStream()).use { output ->
                                copied += copyWithProgress(buffered::read, output::write) {
                                    onProgress?.invoke(BulkFileOperationProgress(completed, entries.size, entry.name, copied + it, totalBytes))
                                }
                            }
                        }
                    }
                    entry.lastModifiedDate?.time?.let { target.setLastModified(it) }
                }
                completed += 1
                onProgress?.invoke(BulkFileOperationProgress(completed, entries.size, entry.name, copied.coerceAtMost(totalBytes), totalBytes))
            }
        }
    }

    private suspend fun extractSevenZ(
        archive: File,
        destination: File,
        entryPrefix: String?,
        password: String?,
        createdOutputs: MutableSet<File>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val allEntries = listSevenZEntries(archive, password).filter { it.path.matchesPrefix(entryPrefix) }
        val totalBytes = allEntries.sumOf { it.size }.coerceAtLeast(1L)
        var copied = 0L
        var completed = 0
        openSevenZ(archive, password).use { sevenZ ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = sevenZ.nextEntry ?: break
                val name = (entry.name ?: "unnamed").normalizeEntryName()
                if (!name.matchesPrefix(entryPrefix)) continue
                val target = resolveExtractionTarget(destination, name, entry.isDirectory)
                if (entry.isDirectory) {
                    rememberCreatedOutput(target, createdOutputs)
                    target.mkdirs()
                } else {
                    rememberCreatedOutput(target, createdOutputs)
                    target.parentFile?.mkdirs()
                    validateMutationPath(target).getOrThrow()
                    BufferedOutputStream(target.outputStream()).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = sevenZ.read(buffer, 0, buffer.size)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            copied += read
                            onProgress?.invoke(BulkFileOperationProgress(completed, allEntries.size, name, copied, totalBytes))
                        }
                    }
                    entry.lastModifiedDate?.time?.let { target.setLastModified(it) }
                }
                completed += 1
                onProgress?.invoke(BulkFileOperationProgress(completed, allEntries.size, name, copied.coerceAtMost(totalBytes), totalBytes))
            }
        }
    }

    private fun resolveExtractionTarget(destination: File, rawEntryName: String, directory: Boolean): File {
        ArchiveSafetyTally().accept(rawEntryName, 0L, null)
        val normalized = rawEntryName.normalizeEntryName()
        require(normalized.isNotBlank()) { "Archive contains an empty entry name" }
        require(!File(normalized).isAbsolute && !normalized.startsWith("/")) { "Archive contains unsafe absolute paths" }
        require(normalized.split('/').none { it == ".." }) { "Archive contains unsafe relative paths" }

        val base = destination.canonicalFile
        val requested = File(base, normalized).canonicalFile
        require(requested.path == base.path || requested.path.startsWith(base.path + File.separator)) {
            "Archive entry escapes the destination folder"
        }

        val target = if (directory || !requested.exists()) requested else FileConflictNameGenerator.generateKeepBothTarget(
            requireNotNull(requested.parentFile) { "Archive target has no parent folder" },
            requested
        )
        validateMutationPath(target).getOrThrow()
        return target
    }

    private suspend fun createZip(
        sources: List<File>,
        target: File,
        password: String?,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        if (!password.isNullOrEmpty()) {
            createEncryptedZip(sources, target, password, onProgress)
            return
        }
        val files = sources.flatMap { it.walkArchiveFiles() }
        validateArchiveCreationEntries(files)
        val totalBytes = files.filter { it.second.isFile }.sumOf { it.second.length() }.coerceAtLeast(1L)
        var copied = 0L
        var completed = 0
        ZipArchiveOutputStream(target).use { zip ->
            for ((sourceRoot, file) in files) {
                currentCoroutineContext().ensureActive()
                val name = archiveEntryName(sourceRoot, file)
                val entry = ZipArchiveEntry(name + if (file.isDirectory) "/" else "")
                entry.time = file.lastModified()
                if (!file.isDirectory) entry.size = file.length()
                zip.putArchiveEntry(entry)
                if (file.isFile) {
                    file.inputStream().use { input ->
                        BufferedInputStream(input).use { buffered ->
                            copied += copyWithProgress(buffered::read, zip::write) {
                                onProgress?.invoke(BulkFileOperationProgress(completed, files.size, file.absolutePath, copied + it, totalBytes))
                            }
                        }
                    }
                }
                zip.closeArchiveEntry()
                completed += 1
                onProgress?.invoke(BulkFileOperationProgress(completed, files.size, file.absolutePath, copied.coerceAtMost(totalBytes), totalBytes))
            }
        }
    }

    private suspend fun createSevenZ(
        sources: List<File>,
        target: File,
        password: String?,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val files = sources.flatMap { it.walkArchiveFiles() }
        validateArchiveCreationEntries(files)
        val totalBytes = files.filter { it.second.isFile }.sumOf { it.second.length() }.coerceAtLeast(1L)
        var copied = 0L
        var completed = 0
        val archivePassword = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        SevenZOutputFile(target, archivePassword).use { sevenZ ->
            for ((sourceRoot, file) in files) {
                currentCoroutineContext().ensureActive()
                val entry = SevenZArchiveEntry().apply {
                    name = archiveEntryName(sourceRoot, file)
                    isDirectory = file.isDirectory
                    size = if (file.isFile) file.length() else 0L
                    lastModifiedDate = Date(file.lastModified())
                }
                sevenZ.putArchiveEntry(entry)
                if (file.isFile) {
                    file.inputStream().use { input ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            sevenZ.write(buffer, 0, read)
                            copied += read
                            onProgress?.invoke(BulkFileOperationProgress(completed, files.size, file.absolutePath, copied, totalBytes))
                        }
                    }
                }
                sevenZ.closeArchiveEntry()
                completed += 1
                onProgress?.invoke(BulkFileOperationProgress(completed, files.size, file.absolutePath, copied.coerceAtMost(totalBytes), totalBytes))
            }
        }
    }

    private suspend fun extractZip4j(
        zip: Zip4jFile,
        destination: File,
        entryPrefix: String?,
        createdOutputs: MutableSet<File>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val entries = zip.fileHeaders
            .filter { it.fileName.matchesPrefix(entryPrefix) }
        val safety = ArchiveSafetyTally()
        entries.forEach { safety.accept(it.fileName, it.uncompressedSize.coerceAtLeast(0L), it.compressedSize.coerceAtLeast(0L)) }
        val totalBytes = entries.sumOf { it.uncompressedSize.coerceAtLeast(0L) }.coerceAtLeast(1L)
        var copied = 0L
        var completed = 0
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            val target = resolveExtractionTarget(destination, entry.fileName, entry.isDirectory)
            if (entry.isDirectory) {
                rememberCreatedOutput(target, createdOutputs)
                target.mkdirs()
            } else {
                rememberCreatedOutput(target, createdOutputs)
                target.parentFile?.mkdirs()
                validateMutationPath(target).getOrThrow()
                zip.getInputStream(entry).use { input ->
                    BufferedInputStream(input).use { buffered ->
                        BufferedOutputStream(target.outputStream()).use { output ->
                            copied += copyWithProgress(buffered::read, output::write) {
                                onProgress?.invoke(
                                    BulkFileOperationProgress(
                                        completed,
                                        entries.size,
                                        entry.fileName,
                                        copied + it,
                                        totalBytes
                                    )
                                )
                            }
                        }
                    }
                }
                entry.lastModifiedTimeEpoch.takeIf { it > 0L }?.let { target.setLastModified(it) }
            }
            completed += 1
            onProgress?.invoke(
                BulkFileOperationProgress(
                    completed,
                    entries.size,
                    entry.fileName,
                    copied.coerceAtMost(totalBytes),
                    totalBytes
                )
            )
        }
    }

    private suspend fun createEncryptedZip(
        sources: List<File>,
        target: File,
        password: String,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val files = sources.flatMap { it.walkArchiveFiles() }
        validateArchiveCreationEntries(files)
        val totalBytes = files.filter { it.second.isFile }.sumOf { it.second.length() }.coerceAtLeast(1L)
        val zip = Zip4jFile(target, password.toCharArray())
        var copied = 0L
        var completed = 0
        for ((sourceRoot, file) in files) {
            currentCoroutineContext().ensureActive()
            val name = archiveEntryName(sourceRoot, file)
            val parameters = ZipParameters().apply {
                fileNameInZip = name + if (file.isDirectory) "/" else ""
                compressionMethod = CompressionMethod.DEFLATE
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                lastModifiedFileTime = file.lastModified()
            }
            if (file.isDirectory) {
                zip.addStream(ByteArray(0).inputStream(), parameters)
            } else {
                file.inputStream().use { input ->
                    zip.addStream(input, parameters)
                }
                copied += file.length()
            }
            completed += 1
            onProgress?.invoke(BulkFileOperationProgress(completed, files.size, file.absolutePath, copied.coerceAtMost(totalBytes), totalBytes))
        }
    }

    private fun openSevenZ(archive: File, password: String?): SevenZFile =
        if (password.isNullOrEmpty()) {
            SevenZFile.builder().setFile(archive).get()
        } else {
            SevenZFile.builder().setFile(archive).setPassword(password.toCharArray()).get()
        }

    private suspend fun copyWithProgress(
        read: (ByteArray) -> Int,
        write: (ByteArray, Int, Int) -> Unit,
        onDelta: suspend (Long) -> Unit
    ): Long {
        val buffer = ByteArray(BUFFER_SIZE)
        var copied = 0L
        while (true) {
            currentCoroutineContext().ensureActive()
            val count = read(buffer)
            if (count < 0) break
            write(buffer, 0, count)
            copied += count
            onDelta(copied)
        }
        return copied
    }

    private fun validateArchiveCreationEntries(files: List<Pair<File, File>>) {
        val safety = ArchiveSafetyTally()
        files.forEach { (sourceRoot, file) ->
            safety.accept(
                rawName = archiveEntryName(sourceRoot, file),
                uncompressedSize = if (file.isFile) file.length() else 0L,
                compressedSize = null
            )
        }
    }

    private fun rememberCreatedOutput(target: File, createdOutputs: MutableSet<File>) {
        if (!target.exists()) {
            createdOutputs += target
        }
    }

    private fun cleanupCreatedOutputs(createdOutputs: Set<File>) {
        createdOutputs.toList().asReversed().forEach { output ->
            runCatching {
                if (output.exists()) {
                    if (output.isDirectory) output.deleteRecursively() else output.delete()
                }
            }
        }
    }

    private inner class ArchiveSafetyTally {
        private var entries = 0
        private var uncompressedBytes = 0L

        fun accept(rawName: String, uncompressedSize: Long, compressedSize: Long?) {
            val normalized = rawName.normalizeEntryName()
            require(normalized.length <= safetyPolicy.maxEntryPathLength) { "Archive entry path is too long" }
            val segments = normalized.split('/').filter { it.isNotBlank() }
            require(segments.size <= safetyPolicy.maxNestedDepth) { "Archive nesting is too deep" }

            entries += 1
            require(entries <= safetyPolicy.maxEntries) { "Archive contains too many entries" }

            val safeUncompressedSize = uncompressedSize.coerceAtLeast(0L)
            uncompressedBytes = Math.addExact(uncompressedBytes, safeUncompressedSize)
            require(uncompressedBytes <= safetyPolicy.maxUncompressedBytes) { "Archive is too large to process safely" }

            if (safeUncompressedSize > 0L && compressedSize != null) {
                val safeCompressedSize = compressedSize.coerceAtLeast(0L)
                val ratio = if (safeCompressedSize == 0L) Double.POSITIVE_INFINITY else safeUncompressedSize.toDouble() / safeCompressedSize.toDouble()
                require(ratio <= safetyPolicy.maxCompressionRatio) { "Archive compression ratio is too high" }
            }
        }
    }

    private fun File.walkArchiveFiles(): List<Pair<File, File>> {
        validateMutationPath(this).getOrThrow()
        require(exists()) { "File does not exist: $name" }
        return if (isDirectory) {
            listOf(this to this) + walkTopDown().drop(1).map { this to it }
        } else {
            listOf(this to this)
        }
    }

    private fun archiveEntryName(sourceRoot: File, file: File): String =
        if (sourceRoot == file) {
            sourceRoot.name
        } else {
            sourceRoot.name + "/" + file.relativeTo(sourceRoot).path.replace(File.separatorChar, '/')
        }.normalizeEntryName()

    private fun String.normalizeEntryName(): String =
        replace('\\', '/').trimStart('/')

    private fun String.matchesPrefix(prefix: String?): Boolean {
        val normalizedPrefix = prefix?.normalizeEntryName()?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: return true
        val normalized = normalizeEntryName()
        return normalized == normalizedPrefix || normalized.startsWith("$normalizedPrefix/")
    }
}
