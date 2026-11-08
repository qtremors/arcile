package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.archive.isSingleStreamCompression
import dev.qtremors.arcile.core.archive.TarArchiveWriter
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.tar.TarArchiveEntry

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
        val totalBytes = scan.totalBytes
        val totalItems = scan.totalItems
        var copied = 0L
        var completed = 0
        val coroutine = currentCoroutineContext()
        TarArchiveWriter.open(target.outputStream().buffered(), format, compressionLevel, safetyPolicy).use { tar ->
            val writeEntry: suspend (ArchiveSourceEntry) -> Boolean = { source ->
                coroutine.ensureActive()
                val file = source.file
                val entry = TarArchiveEntry(file, archiveEntryName(source.sourceRoot, file))
                tar.add(entry, { coroutine.ensureActive() }, { count ->
                    copied += count
                    onProgress?.invoke(BulkFileOperationProgress(completed, totalItems, file.absolutePath, copied, totalBytes))
                }, file::inputStream)
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

}
