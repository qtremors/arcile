package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile

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
        val safety = ArchiveSafetyTally(safetyPolicy)
        val totalBytes = scan.totalBytes
        val totalItems = scan.totalItems
        var copied = 0L
        var completed = 0
        val archivePassword = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        SevenZOutputFile(target, archivePassword).use { sevenZ ->
            if (compressionLevel == ArchiveCompressionLevel.STORE) {
                sevenZ.setContentCompression(org.apache.commons.compress.archivers.sevenz.SevenZMethod.COPY)
            }
            val writeEntry: suspend (ArchiveSourceEntry) -> Boolean = { source ->
                currentCoroutineContext().ensureActive()
                val sourceRoot = source.sourceRoot
                val file = source.file
                safety.accept(archiveEntryName(sourceRoot, file), if (file.isFile) file.length() else 0L, null)
                val entry = sevenZ.createArchiveEntry(file, archiveEntryName(sourceRoot, file))
                sevenZ.putArchiveEntry(entry)
                if (file.isFile) {
                    file.inputStream().use { input ->
                        val buffer = ByteArray(ARCHIVE_BUFFER_SIZE)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            sevenZ.write(buffer, 0, read)
                            copied += read
                            onProgress?.invoke(BulkFileOperationProgress(completed, totalItems, file.absolutePath, copied, totalBytes))
                        }
                    }
                }
                sevenZ.closeArchiveEntry()
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
