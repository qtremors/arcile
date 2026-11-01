package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.archive.SevenZipArchiveWriter
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption

internal class SevenZipHandler(
    private val safetyPolicy: ArchiveSafetyPolicy,
    private val validateMutationPath: (File) -> Result<Unit>
) {
    suspend fun create(
        sources: List<File>,
        target: File,
        password: String?,
        compressionLevel: ArchiveCompressionLevel,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val scan = scanArchiveCreationEntries(sources, validateMutationPath, safetyPolicy)
        val totalBytes = scan.totalBytes
        val totalItems = scan.totalItems
        var copied = 0L
        var completed = 0
        val archivePassword = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        try {
            val channel = FileChannel.open(target.toPath(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.READ, StandardOpenOption.WRITE)
            SevenZipArchiveWriter.open(channel, archivePassword, compressionLevel, safetyPolicy).use { sevenZ ->
                val writeEntry: suspend (ArchiveSourceEntry) -> Boolean = { source ->
                    currentCoroutineContext().ensureActive()
                    val sourceRoot = source.sourceRoot
                    val file = source.file
                    val entry = sevenZ.entryFrom(file, archiveEntryName(sourceRoot, file))
                    val coroutine = currentCoroutineContext()
                    sevenZ.add(entry, { coroutine.ensureActive() }, { count ->
                        copied += count
                        onProgress?.invoke(BulkFileOperationProgress(completed, totalItems, file.absolutePath, copied, totalBytes))
                    }) { file.inputStream() }
                    completed += 1
                    onProgress?.invoke(BulkFileOperationProgress(completed, totalItems, file.absolutePath,
                        totalBytes?.let { copied.coerceAtMost(it) } ?: copied, totalBytes))
                    true
                }
                val entries = scan.entries
                if (entries != null) for (entry in entries) writeEntry(entry)
                else traverseArchiveSources(sources, validateMutationPath, writeEntry)
            }
        } finally { archivePassword?.fill('\u0000') }
    }
}
