package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.archive.ZipArchiveWriter
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

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
        val scan = scanArchiveCreationEntries(sources, validateMutationPath, safetyPolicy)
        val totalBytes = scan.totalBytes
        val totalItems = scan.totalItems
        var copied = 0L
        var completed = 0
        val archivePassword = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        try {
            ZipArchiveWriter.open(target.outputStream().buffered(), archivePassword, nameEncoding,
                compressionLevel, safetyPolicy).use { zip ->
                val writeEntry: suspend (ArchiveSourceEntry) -> Boolean = { source ->
                    val coroutine = currentCoroutineContext()
                    coroutine.ensureActive()
                    val file = source.file
                    zip.add(
                        archiveEntryName(source.sourceRoot, file), if (file.isDirectory) 0L else file.length(),
                        file.isDirectory, file.lastModified(), { coroutine.ensureActive() }, { count ->
                            copied += count
                            onProgress?.invoke(BulkFileOperationProgress(completed, totalItems, file.absolutePath,
                                totalBytes?.let { copied.coerceAtMost(it) } ?: copied, totalBytes))
                        }
                    ) { file.inputStream() }
                    completed++
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
