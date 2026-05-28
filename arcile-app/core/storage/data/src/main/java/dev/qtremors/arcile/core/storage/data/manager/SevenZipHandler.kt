package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
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
        createdOutputs: MutableSet<File>,
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
                val target = extractionContext.resolveTarget(destination, name, entry.isDirectory)
                if (entry.isDirectory) {
                    rememberCreatedOutput(target, createdOutputs)
                    target.mkdirs()
                } else {
                    rememberCreatedOutput(target, createdOutputs)
                    target.parentFile?.mkdirs()
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
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        val files = sources.flatMap { it.walkArchiveFiles(validateMutationPath) }
        validateArchiveCreationEntries(files)
        val totalBytes = files.filter { it.second.isFile }.sumOf { it.second.length() }.coerceAtLeast(1L)
        var copied = 0L
        var completed = 0
        val archivePassword = password?.takeIf { it.isNotEmpty() }?.toCharArray()
        SevenZOutputFile(target, archivePassword).use { sevenZ ->
            for ((sourceRoot, file) in files) {
                currentCoroutineContext().ensureActive()
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

    private fun openSevenZ(archive: File, password: String?): SevenZFile =
        if (password.isNullOrEmpty()) {
            SevenZFile.builder().setFile(archive).get()
        } else {
            SevenZFile.builder().setFile(archive).setPassword(password.toCharArray()).get()
        }

    private fun validateArchiveCreationEntries(files: List<Pair<File, File>>) {
        val safety = ArchiveSafetyTally(safetyPolicy)
        files.forEach { (sourceRoot, file) ->
            safety.accept(
                rawName = archiveEntryName(sourceRoot, file),
                uncompressedSize = if (file.isFile) file.length() else 0L,
                compressedSize = null
            )
        }
    }
}
