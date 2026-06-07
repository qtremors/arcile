package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import java.io.BufferedOutputStream
import java.io.File
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile

internal class SevenZipHandler(
    private val safetyPolicy: ArchiveSafetyPolicy,
    private val validateMutationPath: (File) -> Result<Unit>
) {
    private val extractionContext = ArchiveExtractionContext(safetyPolicy, validateMutationPath)

    fun listEntries(archive: File, password: String?): List<ArchiveEntryModel> =
        openSevenZ(archive, password).use { sevenZ ->
            val safety = ArchiveSafetyTally(safetyPolicy)
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

    suspend fun extract(
        archive: File,
        destination: File,
        entryPrefix: String?,
        password: String?,
        resolutions: Map<String, ConflictResolution>,
        createdOutputs: MutableSet<File>,
        replacementBackups: MutableMap<File, File>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val allEntries = listEntries(archive, password).filter { it.path.matchesPrefix(entryPrefix) }
        val totalBytes = allEntries.sumOf { it.size }.coerceAtLeast(1L)
        var copied = 0L
        var completed = 0
        openSevenZ(archive, password).use { sevenZ ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val entry = sevenZ.nextEntry ?: break
                val name = (entry.name ?: "unnamed").normalizeEntryName()
                if (!name.matchesPrefix(entryPrefix)) continue
                val target = extractionContext.resolveTarget(destination, name, entry.isDirectory, resolutions)
                if (target == null) {
                    completed += 1
                    onProgress?.invoke(BulkFileOperationProgress(completed, allEntries.size, name, copied.coerceAtMost(totalBytes), totalBytes))
                    continue
                }
                if (entry.isDirectory) {
                    prepareDirectoryTarget(target, createdOutputs, replacementBackups)
                } else {
                    prepareFileTarget(target, createdOutputs, replacementBackups)
                    validateMutationPath(target).getOrThrow()
                    BufferedOutputStream(target.outputStream()).use { output ->
                        val buffer = ByteArray(ARCHIVE_BUFFER_SIZE)
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

    private fun openSevenZ(archive: File, password: String?): SevenZFile =
        if (password.isNullOrEmpty()) {
            SevenZFile.builder().setFile(archive).get()
        } else {
            SevenZFile.builder().setFile(archive).setPassword(password.toCharArray()).get()
        }

}
