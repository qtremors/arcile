package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.archive.isSingleStreamCompression
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.OutputStream
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

internal class TarAndCompressedStreamHandler(
    private val safetyPolicy: ArchiveSafetyPolicy,
    private val validateMutationPath: (File) -> Result<Unit>
) {
    suspend fun create(
        sources: List<File>,
        target: File,
        format: ArchiveFormat,
        compressionLevel: ArchiveCompressionLevel,
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

    private fun tarOutput(target: File, format: ArchiveFormat): TarArchiveOutputStream =
        TarArchiveOutputStream(BufferedOutputStream(compressedOutput(target, format)))

    private fun compressedOutput(target: File, format: ArchiveFormat): OutputStream {
        val output = target.outputStream()
        return when (format.compressionKind) {
            CompressionKind.NONE -> output
            CompressionKind.GZIP -> GzipCompressorOutputStream(output)
            CompressionKind.BZIP2 -> BZip2CompressorOutputStream(output)
            CompressionKind.XZ -> XZCompressorOutputStream(output)
        }
    }


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
