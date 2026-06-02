package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

internal class TarArchiveHandler(
    private val safetyPolicy: ArchiveSafetyPolicy,
    private val validateMutationPath: (File) -> Result<Unit>
) {
    private val extractionContext = ArchiveExtractionContext(safetyPolicy, validateMutationPath)

    fun listEntries(archive: File, format: ArchiveFormat): List<ArchiveEntryModel> {
        if (format.isSingleStreamCompression) {
            val outputName = archive.singleStreamOutputName(format)
            ArchiveSafetyTally(safetyPolicy).accept(outputName, archive.length(), archive.length())
            return listOf(
                ArchiveEntryModel(
                    name = outputName.substringAfterLast('/'),
                    path = outputName.normalizeEntryName(),
                    size = archive.length().coerceAtLeast(0L),
                    compressedSize = archive.length().coerceAtLeast(0L),
                    lastModified = archive.lastModified().takeIf { it > 0L },
                    isDirectory = false,
                    canRead = true
                )
            )
        }
        return tarInput(archive, format).use { tar ->
            val safety = ArchiveSafetyTally(safetyPolicy)
            generateSequence { tar.nextTarEntry }.map { entry ->
                val name = entry.name.normalizeEntryName()
                safety.accept(name, entry.size.coerceAtLeast(0L), null)
                ArchiveEntryModel(
                    name = name.substringAfterLast('/').ifBlank { name.trimEnd('/') },
                    path = name,
                    size = entry.size.coerceAtLeast(0L),
                    compressedSize = null,
                    lastModified = entry.modTime?.time,
                    isDirectory = entry.isDirectory,
                    canRead = true
                )
            }.toList()
        }
    }

    suspend fun extract(
        archive: File,
        format: ArchiveFormat,
        destination: File,
        entryPrefix: String?,
        resolutions: Map<String, ConflictResolution>,
        createdOutputs: MutableSet<File>,
        replacementBackups: MutableMap<File, File>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        if (format.isSingleStreamCompression) {
            extractSingleStream(archive, format, destination, entryPrefix, resolutions, createdOutputs, replacementBackups, onProgress)
            return
        }
        val entries = listEntries(archive, format).filter { it.path.matchesPrefix(entryPrefix) }
        val totalBytes = entries.sumOf { it.size }.coerceAtLeast(1L)
        var copied = 0L
        var completed = 0
        tarInput(archive, format).use { tar ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = tar.nextTarEntry ?: break
                val name = entry.name.normalizeEntryName()
                if (!name.matchesPrefix(entryPrefix)) continue
                val target = extractionContext.resolveTarget(destination, name, entry.isDirectory, resolutions)
                if (target == null) {
                    completed += 1
                    onProgress?.invoke(BulkFileOperationProgress(completed, entries.size, name, copied.coerceAtMost(totalBytes), totalBytes))
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
                    BufferedOutputStream(target.outputStream()).use { output ->
                        copied += copyWithProgress(tar::read, output::write) {
                            onProgress?.invoke(BulkFileOperationProgress(completed, entries.size, name, copied + it, totalBytes))
                        }
                    }
                    entry.modTime?.time?.let { target.setLastModified(it) }
                }
                completed += 1
                onProgress?.invoke(BulkFileOperationProgress(completed, entries.size, name, copied.coerceAtMost(totalBytes), totalBytes))
            }
        }
    }

    suspend fun create(
        sources: List<File>,
        target: File,
        format: ArchiveFormat,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        require(!format.isSingleStreamCompression) { "${format.displayName} creation is not supported for multiple files" }
        val scan = scanArchiveCreationEntries(sources, validateMutationPath, safetyPolicy)
        val safety = ArchiveSafetyTally(safetyPolicy)
        val totalBytes = scan.totalBytes
        val totalItems = scan.totalItems
        var copied = 0L
        var completed = 0
        tarOutput(target, format).use { tar ->
            tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
            val writeEntry: suspend (ArchiveSourceEntry) -> Boolean = { source ->
                currentCoroutineContext().ensureActive()
                val file = source.file
                val name = archiveEntryName(source.sourceRoot, file)
                safety.accept(name, if (file.isFile) file.length() else 0L, null)
                val entry = TarArchiveEntry(file, name)
                tar.putArchiveEntry(entry)
                if (file.isFile) {
                    file.inputStream().use { input ->
                        BufferedInputStream(input).use { buffered ->
                            copied += copyWithProgress(buffered::read, tar::write) {
                                onProgress?.invoke(BulkFileOperationProgress(completed, totalItems, file.absolutePath, copied + it, totalBytes))
                            }
                        }
                    }
                }
                tar.closeArchiveEntry()
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

    private suspend fun extractSingleStream(
        archive: File,
        format: ArchiveFormat,
        destination: File,
        entryPrefix: String?,
        resolutions: Map<String, ConflictResolution>,
        createdOutputs: MutableSet<File>,
        replacementBackups: MutableMap<File, File>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val outputName = archive.singleStreamOutputName(format)
        if (!outputName.matchesPrefix(entryPrefix)) return
        val target = extractionContext.resolveTarget(destination, outputName, directory = false, resolutions) ?: return
        rememberCreatedOutput(target, createdOutputs)
        target.parentFile?.mkdirs()
        validateMutationPath(target).getOrThrow()
        rememberReplacementBackup(target, replacementBackups)
        val totalBytes = archive.length().coerceAtLeast(1L)
        compressedInput(archive, format).use { input ->
            BufferedInputStream(input).use { buffered ->
                BufferedOutputStream(target.outputStream()).use { output ->
                    copyWithProgress(buffered::read, output::write) { copied ->
                        onProgress?.invoke(BulkFileOperationProgress(0, 1, outputName, copied, totalBytes))
                    }
                }
            }
        }
        target.setLastModified(archive.lastModified())
        onProgress?.invoke(BulkFileOperationProgress(1, 1, outputName, totalBytes, totalBytes))
    }

    private fun tarInput(archive: File, format: ArchiveFormat): TarArchiveInputStream =
        TarArchiveInputStream(BufferedInputStream(compressedInput(archive, format)))

    private fun tarOutput(target: File, format: ArchiveFormat): TarArchiveOutputStream =
        TarArchiveOutputStream(BufferedOutputStream(compressedOutput(target, format)))

    private fun compressedInput(archive: File, format: ArchiveFormat): InputStream {
        val input = archive.inputStream()
        return when (format.compressionKind) {
            CompressionKind.NONE -> input
            CompressionKind.GZIP -> GzipCompressorInputStream(input, true)
            CompressionKind.BZIP2 -> BZip2CompressorInputStream(input, true)
            CompressionKind.XZ -> XZCompressorInputStream(input, true)
        }
    }

    private fun compressedOutput(target: File, format: ArchiveFormat): OutputStream {
        val output = target.outputStream()
        return when (format.compressionKind) {
            CompressionKind.NONE -> output
            CompressionKind.GZIP -> GzipCompressorOutputStream(output)
            CompressionKind.BZIP2 -> BZip2CompressorOutputStream(output)
            CompressionKind.XZ -> XZCompressorOutputStream(output)
        }
    }

    private fun File.singleStreamOutputName(format: ArchiveFormat): String =
        name.removeSuffix(".${format.extension}").ifBlank { nameWithoutExtension.ifBlank { "decompressed" } }
}

private enum class CompressionKind {
    NONE,
    GZIP,
    BZIP2,
    XZ
}

private val ArchiveFormat.compressionKind: CompressionKind
    get() = when (this) {
        ArchiveFormat.TAR,
        ArchiveFormat.ZIP,
        ArchiveFormat.SEVEN_Z,
        ArchiveFormat.RAR -> CompressionKind.NONE
        ArchiveFormat.TAR_GZIP,
        ArchiveFormat.TGZ,
        ArchiveFormat.GZIP -> CompressionKind.GZIP
        ArchiveFormat.TAR_BZIP2,
        ArchiveFormat.TBZ2,
        ArchiveFormat.BZIP2 -> CompressionKind.BZIP2
        ArchiveFormat.TAR_XZ,
        ArchiveFormat.TXZ,
        ArchiveFormat.XZ -> CompressionKind.XZ
    }

private val ArchiveFormat.isSingleStreamCompression: Boolean
    get() = this == ArchiveFormat.GZIP || this == ArchiveFormat.BZIP2 || this == ArchiveFormat.XZ
