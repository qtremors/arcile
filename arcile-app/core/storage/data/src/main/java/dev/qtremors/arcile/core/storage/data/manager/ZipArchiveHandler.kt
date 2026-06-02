package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel as Zip4jCompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.apache.commons.compress.archivers.zip.ZipFile

internal class ZipArchiveHandler(
    private val safetyPolicy: ArchiveSafetyPolicy,
    private val validateMutationPath: (File) -> Result<Unit>
) {
    private val extractionContext = ArchiveExtractionContext(safetyPolicy, validateMutationPath)

    fun listEntries(archive: File, password: String?, nameEncoding: ArchiveNameEncoding): List<ArchiveEntryModel> {
        val zip4j = Zip4jFile(archive, password?.toCharArray())
        zip4j.setCharset(nameEncoding.charset())
        if (zip4j.isEncrypted && password.isNullOrEmpty()) {
            throw IllegalArgumentException("A password is required or the password is incorrect")
        }
        if (zip4j.isEncrypted) {
            val safety = ArchiveSafetyTally(safetyPolicy)
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
        return ZipFile.builder().setFile(archive).setCharset(nameEncoding.charset()).get().use { zip ->
            val safety = ArchiveSafetyTally(safetyPolicy)
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

    suspend fun extract(
        archive: File,
        destination: File,
        entryPrefix: String?,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        resolutions: Map<String, ConflictResolution>,
        createdOutputs: MutableSet<File>,
        replacementBackups: MutableMap<File, File>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val zip4j = Zip4jFile(archive, password?.toCharArray())
        zip4j.setCharset(nameEncoding.charset())
        if (zip4j.isEncrypted || !password.isNullOrEmpty()) {
            extractZip4j(zip4j, destination, entryPrefix, resolutions, createdOutputs, replacementBackups, onProgress)
            return
        }
        val prefix = entryPrefix?.normalizeEntryName()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        ZipFile.builder().setFile(archive).setCharset(nameEncoding.charset()).get().use { zip ->
            val safety = ArchiveSafetyTally(safetyPolicy)
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
                val target = extractionContext.resolveTarget(destination, entry.name, entry.isDirectory, resolutions)
                if (target == null) {
                    completed += 1
                    onProgress?.invoke(BulkFileOperationProgress(completed, entries.size, entry.name, copied.coerceAtMost(totalBytes), totalBytes))
                    continue
                }
                if (entry.isDirectory) {
                    rememberCreatedOutput(target, createdOutputs)
                    target.mkdirs()
                } else {
                    rememberCreatedOutput(target, createdOutputs)
                    target.parentFile?.mkdirs()
                    validateMutationPath(target).getOrThrow()
                    rememberReplacementBackup(target, replacementBackups)
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

    suspend fun create(
        sources: List<File>,
        target: File,
        password: String?,
        nameEncoding: ArchiveNameEncoding,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        if (!password.isNullOrEmpty()) {
            createEncrypted(sources, target, password, nameEncoding, compressionLevel, onProgress)
            return
        }
        val scan = scanArchiveCreationEntries(sources, validateMutationPath, safetyPolicy)
        val safety = ArchiveSafetyTally(safetyPolicy)
        val totalBytes = scan.totalBytes
        val totalItems = scan.totalItems
        var copied = 0L
        var completed = 0
        ZipArchiveOutputStream(target).use { zip ->
            zip.setEncoding(nameEncoding.charset().name())
            zip.setUseLanguageEncodingFlag(nameEncoding == ArchiveNameEncoding.UTF_8)
            zip.setLevel(compressionLevel.zipDeflateLevel())
            val writeEntry: suspend (ArchiveSourceEntry) -> Boolean = { source ->
                currentCoroutineContext().ensureActive()
                val sourceRoot = source.sourceRoot
                val file = source.file
                val name = archiveEntryName(sourceRoot, file)
                safety.accept(name, if (file.isFile) file.length() else 0L, null)
                val entry = ZipArchiveEntry(name + if (file.isDirectory) "/" else "")
                entry.time = file.lastModified()
                if (!file.isDirectory) entry.size = file.length()
                zip.putArchiveEntry(entry)
                if (file.isFile) {
                    file.inputStream().use { input ->
                        BufferedInputStream(input).use { buffered ->
                            copied += copyWithProgress(buffered::read, zip::write) {
                                onProgress?.invoke(BulkFileOperationProgress(completed, totalItems, file.absolutePath, copied + it, totalBytes))
                            }
                        }
                    }
                }
                zip.closeArchiveEntry()
                completed += 1
                onProgress?.invoke(BulkFileOperationProgress(completed, totalItems, file.absolutePath, totalBytes?.let { copied.coerceAtMost(it) } ?: copied, totalBytes))
                true
            }
            val entries = scan.entries
            if (entries != null) {
                for (entry in entries) writeEntry(entry)
            } else {
                traverseArchiveSources(sources, validateMutationPath, writeEntry)
            }
        }
    }

    private suspend fun extractZip4j(
        zip: Zip4jFile,
        destination: File,
        entryPrefix: String?,
        resolutions: Map<String, ConflictResolution>,
        createdOutputs: MutableSet<File>,
        replacementBackups: MutableMap<File, File>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val entries = zip.fileHeaders.filter { it.fileName.matchesPrefix(entryPrefix) }
        val safety = ArchiveSafetyTally(safetyPolicy)
        entries.forEach { safety.accept(it.fileName, it.uncompressedSize.coerceAtLeast(0L), it.compressedSize.coerceAtLeast(0L)) }
        val totalBytes = entries.sumOf { it.uncompressedSize.coerceAtLeast(0L) }.coerceAtLeast(1L)
        var copied = 0L
        var completed = 0
        for (entry in entries) {
            currentCoroutineContext().ensureActive()
            val target = extractionContext.resolveTarget(destination, entry.fileName, entry.isDirectory, resolutions)
            if (target == null) {
                completed += 1
                onProgress?.invoke(BulkFileOperationProgress(completed, entries.size, entry.fileName, copied.coerceAtMost(totalBytes), totalBytes))
                continue
            }
            if (entry.isDirectory) {
                rememberCreatedOutput(target, createdOutputs)
                target.mkdirs()
            } else {
                rememberCreatedOutput(target, createdOutputs)
                target.parentFile?.mkdirs()
                validateMutationPath(target).getOrThrow()
                rememberReplacementBackup(target, replacementBackups)
                zip.getInputStream(entry).use { input ->
                    BufferedInputStream(input).use { buffered ->
                        BufferedOutputStream(target.outputStream()).use { output ->
                            copied += copyWithProgress(buffered::read, output::write) {
                                onProgress?.invoke(BulkFileOperationProgress(completed, entries.size, entry.fileName, copied + it, totalBytes))
                            }
                        }
                    }
                }
                entry.lastModifiedTimeEpoch.takeIf { it > 0L }?.let { target.setLastModified(it) }
            }
            completed += 1
            onProgress?.invoke(BulkFileOperationProgress(completed, entries.size, entry.fileName, copied.coerceAtMost(totalBytes), totalBytes))
        }
    }

    private suspend fun createEncrypted(
        sources: List<File>,
        target: File,
        password: String,
        nameEncoding: ArchiveNameEncoding,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val scan = scanArchiveCreationEntries(sources, validateMutationPath, safetyPolicy)
        val safety = ArchiveSafetyTally(safetyPolicy)
        val totalBytes = scan.totalBytes
        val totalItems = scan.totalItems
        val zip = Zip4jFile(target, password.toCharArray())
        zip.setCharset(nameEncoding.charset())
        var copied = 0L
        var completed = 0
        val writeEntry: suspend (ArchiveSourceEntry) -> Boolean = { source ->
            currentCoroutineContext().ensureActive()
            val sourceRoot = source.sourceRoot
            val file = source.file
            val name = archiveEntryName(sourceRoot, file)
            safety.accept(name, if (file.isFile) file.length() else 0L, null)
            val parameters = ZipParameters().apply {
                fileNameInZip = name + if (file.isDirectory) "/" else ""
                compressionMethod = CompressionMethod.DEFLATE
                this.compressionLevel = compressionLevel.zip4jLevel()
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
                aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
                lastModifiedFileTime = file.lastModified()
            }
            if (file.isDirectory) {
                zip.addStream(ByteArray(0).inputStream(), parameters)
            } else {
                file.inputStream().use { input ->
                    var entryCopied = 0L
                    val progressInput = ProgressInputStream(input) { delta ->
                        entryCopied += delta
                        onProgress?.invoke(
                            BulkFileOperationProgress(
                                completedItems = completed,
                                totalItems = totalItems,
                                currentPath = file.absolutePath,
                                bytesCopied = totalBytes?.let { (copied + entryCopied).coerceAtMost(it) } ?: copied + entryCopied,
                                totalBytes = totalBytes
                            )
                        )
                    }
                    zip.addStream(progressInput, parameters)
                    copied += entryCopied
                }
            }
            completed += 1
            onProgress?.invoke(BulkFileOperationProgress(completed, totalItems, file.absolutePath, totalBytes?.let { copied.coerceAtMost(it) } ?: copied, totalBytes))
            true
        }
        val entries = scan.entries
        if (entries != null) {
            for (entry in entries) writeEntry(entry)
        } else {
            traverseArchiveSources(sources, validateMutationPath, writeEntry)
        }
    }

    private fun ArchiveCompressionLevel.zipDeflateLevel(): Int =
        when (this) {
            ArchiveCompressionLevel.STORE -> 0
            ArchiveCompressionLevel.FAST -> 3
            ArchiveCompressionLevel.DEFAULT -> -1
            ArchiveCompressionLevel.MAXIMUM -> 9
        }

    private fun ArchiveCompressionLevel.zip4jLevel(): Zip4jCompressionLevel =
        when (this) {
            ArchiveCompressionLevel.STORE -> Zip4jCompressionLevel.NO_COMPRESSION
            ArchiveCompressionLevel.FAST -> Zip4jCompressionLevel.FAST
            ArchiveCompressionLevel.DEFAULT -> Zip4jCompressionLevel.NORMAL
            ArchiveCompressionLevel.MAXIMUM -> Zip4jCompressionLevel.MAXIMUM
        }
}

private class ProgressInputStream(
    input: InputStream,
    private val onRead: (Long) -> Unit
) : FilterInputStream(input) {
    override fun read(): Int {
        val value = super.read()
        if (value >= 0) onRead(1L)
        return value
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val read = super.read(buffer, offset, length)
        if (read > 0) onRead(read.toLong())
        return read
    }
}
