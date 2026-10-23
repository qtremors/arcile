package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import java.io.BufferedInputStream
import java.io.File
import java.io.FilterInputStream
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

internal class ZipArchiveHandler(
    private val safetyPolicy: ArchiveSafetyPolicy,
    private val validateMutationPath: (File) -> Result<Unit>
) {
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
                                bytesProcessed = totalBytes?.let { (copied + entryCopied).coerceAtMost(it) } ?: copied + entryCopied,
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
