package dev.qtremors.arcile.data.source

import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.presentation.operations.BulkFileOperationProgress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID

class FileTransferEngine(
    private val validatePath: (File) -> Result<Unit>,
    private val rename: (File, File) -> Boolean = { source, target -> source.renameTo(target) }
) {
    private companion object {
        const val TRANSFER_ESTIMATE_NODE_LIMIT = 10_000
        const val TRANSFER_ESTIMATE_CANCELLATION_GRANULARITY = 128
    }

    suspend fun copyFiles(
        sourcePaths: List<String>,
        destination: File,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<List<String>> {
        val scannedPaths = mutableListOf<String>()
        val tracker = ProgressTracker(sourcePaths, estimateTotalBytes(sourcePaths), onProgress)

        for (path in sourcePaths) {
            ensureOperationActive()
            val sourceFile = File(path)
            validatePath(sourceFile).onFailure { return Result.failure(it) }
            if (!sourceFile.exists()) continue

            rejectNestedDirectoryTransfer(sourceFile, destination, "copy").onFailure { return Result.failure(it) }

            var targetFile = File(destination, sourceFile.name)
            validatePath(targetFile).onFailure { return Result.failure(it) }

            if (targetFile.exists() || sourceFile.absolutePath == targetFile.absolutePath) {
                when (resolutions[sourceFile.absolutePath]) {
                    ConflictResolution.SKIP -> continue
                    ConflictResolution.KEEP_BOTH -> targetFile = FileConflictNameGenerator.generateKeepBothTarget(destination, sourceFile)
                    ConflictResolution.REPLACE -> if (sourceFile.absolutePath == targetFile.absolutePath) continue
                    null -> if (sourceFile.absolutePath == targetFile.absolutePath) continue
                }
            }

            tracker.currentPath = sourceFile.absolutePath
            try {
                copyAtomically(
                    source = sourceFile,
                    target = targetFile,
                    replaceExisting = resolutions[sourceFile.absolutePath] == ConflictResolution.REPLACE,
                    onBytesCopied = tracker::onBytesCopied
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                return Result.failure(e)
            }
            scannedPaths += targetFile.absolutePath
            tracker.completeItem(targetFile.absolutePath)
        }

        return Result.success(scannedPaths)
    }

    suspend fun moveFiles(
        sourcePaths: List<String>,
        destination: File,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<List<String>> {
        val scannedPaths = mutableListOf<String>()
        val tracker = ProgressTracker(sourcePaths, estimateTotalBytes(sourcePaths), onProgress)

        for (path in sourcePaths) {
            ensureOperationActive()
            val sourceFile = File(path)
            validatePath(sourceFile).onFailure { return Result.failure(it) }
            if (!sourceFile.exists()) continue

            rejectNestedDirectoryTransfer(sourceFile, destination, "move").onFailure { return Result.failure(it) }

            var targetFile = File(destination, sourceFile.name)
            validatePath(targetFile).onFailure { return Result.failure(it) }
            if (sourceFile.absolutePath == targetFile.absolutePath) continue

            if (targetFile.exists()) {
                when (resolutions[sourceFile.absolutePath]) {
                    ConflictResolution.SKIP -> continue
                    ConflictResolution.KEEP_BOTH -> targetFile = FileConflictNameGenerator.generateKeepBothTarget(destination, sourceFile)
                    ConflictResolution.REPLACE -> Unit
                    null -> return Result.failure(Exception("Move conflict: no resolution for existing target"))
                }
            }

            val shouldReplace = resolutions[sourceFile.absolutePath] == ConflictResolution.REPLACE
            val success = if (targetFile.exists()) false else rename(sourceFile, targetFile)
            if (!success) {
                try {
                    tracker.currentPath = sourceFile.absolutePath
                    copyAtomically(sourceFile, targetFile, replaceExisting = shouldReplace, tracker::onBytesCopied)
                    if (!verifyCopyIntegrity(sourceFile, targetFile)) {
                        deleteTarget(targetFile)
                        return Result.failure(IOException("Failed to verify moved ${if (sourceFile.isDirectory) "directory" else "file"} before deleting source"))
                    }
                    ensureOperationActive()
                    val deleted = if (sourceFile.isDirectory) sourceFile.deleteRecursively() else sourceFile.delete()
                    if (!deleted) {
                        deleteTarget(targetFile)
                        return Result.failure(Exception("Failed to delete source ${if (sourceFile.isDirectory) "directory" else "file"} after copy"))
                    }
                } catch (e: Exception) {
                    deleteTarget(targetFile)
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    return Result.failure(e)
                }
            }

            scannedPaths += sourceFile.absolutePath
            scannedPaths += targetFile.absolutePath
            tracker.completeItem(targetFile.absolutePath)
        }

        return Result.success(scannedPaths)
    }

    private suspend fun copyAtomically(
        source: File,
        target: File,
        replaceExisting: Boolean,
        onBytesCopied: suspend (Long) -> Unit
    ) {
        ensureOperationActive()
        target.parentFile?.mkdirs()
        if (target.exists() && !replaceExisting) {
            throw IllegalStateException("Target already exists: ${target.name}")
        }

        val stagingTarget = createStagingTarget(target)
        try {
            validatePath(stagingTarget).getOrThrow()
            if (source.isDirectory) {
                copyDirectoryCancellable(source, stagingTarget, onBytesCopied)
            } else {
                copyFileCancellable(source, stagingTarget, onBytesCopied)
            }
            if (!verifyCopyIntegrity(source, stagingTarget)) {
                throw IOException("Failed to verify copied ${if (source.isDirectory) "directory" else "file"}")
            }
            promoteStagedTarget(stagingTarget, target, replaceExisting)
        } catch (e: Exception) {
            deleteTarget(stagingTarget)
            throw e
        }
    }

    private fun rejectNestedDirectoryTransfer(source: File, destination: File, verb: String): Result<Unit> {
        if (!source.isDirectory) return Result.success(Unit)
        val sourcePath = source.canonicalPath
        val destPath = destination.canonicalPath
        if (destPath == sourcePath || destPath.startsWith("$sourcePath${File.separator}")) {
            return Result.failure(IllegalArgumentException("Cannot $verb a directory into itself or one of its subdirectories"))
        }
        return Result.success(Unit)
    }

    private suspend fun ensureOperationActive() {
        currentCoroutineContext().ensureActive()
    }

    private suspend fun estimateTransferBytes(source: File): Long {
        if (!source.exists()) return 0L
        if (source.isFile) return source.length()
        val pending = ArrayDeque<File>()
        pending.add(source)
        var visitedNodes = 0
        var totalBytes = 0L
        while (pending.isNotEmpty() && visitedNodes < TRANSFER_ESTIMATE_NODE_LIMIT) {
            if (visitedNodes % TRANSFER_ESTIMATE_CANCELLATION_GRANULARITY == 0) {
                ensureOperationActive()
            }
            val current = pending.removeFirst()
            visitedNodes += 1
            if (current.isFile) {
                totalBytes += current.length()
            } else {
                current.listFiles()?.forEach { pending.addLast(it) }
            }
        }
        return totalBytes
    }

    private suspend fun estimateTotalBytes(sourcePaths: List<String>): Long {
        return sourcePaths.sumOf { path -> estimateTransferBytes(File(path)) }.coerceAtLeast(1L)
    }

    private suspend fun copyFileCancellable(
        source: File,
        target: File,
        onBytesCopied: suspend (Long) -> Unit
    ) {
        ensureOperationActive()
        target.parentFile?.mkdirs()

        BufferedInputStream(source.inputStream()).use { input ->
            BufferedOutputStream(target.outputStream()).use { output ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (true) {
                    ensureOperationActive()
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    onBytesCopied(read.toLong())
                }
            }
        }
        target.setLastModified(source.lastModified())
    }

    private suspend fun copyDirectoryCancellable(
        source: File,
        target: File,
        onBytesCopied: suspend (Long) -> Unit
    ) {
        ensureOperationActive()
        if (!target.mkdirs()) {
            throw IllegalStateException("Failed to create directory: ${target.absolutePath}")
        }

        source.listFiles()?.forEach { child ->
            ensureOperationActive()
            val childTarget = File(target, child.name)
            if (child.isDirectory) {
                copyDirectoryCancellable(child, childTarget, onBytesCopied)
            } else {
                copyFileCancellable(child, childTarget, onBytesCopied)
            }
        }
        target.setLastModified(source.lastModified())
    }

    private fun createStagingTarget(target: File): File {
        val parent = target.parentFile ?: throw IllegalStateException("Target has no parent directory")
        var candidate: File
        do {
            candidate = File(parent, ".${target.name}.arcile-transfer-${UUID.randomUUID()}.tmp")
        } while (candidate.exists())
        return candidate
    }

    private fun promoteStagedTarget(stagingTarget: File, target: File, replaceExisting: Boolean) {
        if (target.exists()) {
            if (!replaceExisting) throw IllegalStateException("Target already exists: ${target.name}")
            val backupTarget = File(target.parentFile, ".${target.name}.arcile-replace-${UUID.randomUUID()}.bak")
            if (!rename(target, backupTarget)) {
                throw IOException("Failed to stage existing target for replacement: ${target.name}")
            }
            try {
                if (!rename(stagingTarget, target)) {
                    rename(backupTarget, target)
                    throw IOException("Failed to promote replacement: ${target.name}")
                }
                deleteTarget(backupTarget)
            } catch (e: Exception) {
                if (!target.exists() && backupTarget.exists()) {
                    rename(backupTarget, target)
                }
                throw e
            }
        } else if (!rename(stagingTarget, target)) {
            throw IOException("Failed to promote copied file: ${target.name}")
        }
    }

    private fun verifyCopyIntegrity(source: File, target: File): Boolean {
        if (!source.exists() || !target.exists()) return false
        if (source.isFile) {
            return target.isFile &&
                source.length() == target.length() &&
                sha256(source).contentEquals(sha256(target))
        }
        if (!source.isDirectory || !target.isDirectory) return false

        val sourceFiles = source.walkTopDown().filter { it.isFile }.toList()
        val targetFiles = target.walkTopDown().filter { it.isFile }.toList()
        if (sourceFiles.size != targetFiles.size) return false

        return sourceFiles.all { sourceChild ->
            val relativePath = sourceChild.relativeTo(source).path
            val targetChild = File(target, relativePath)
            targetChild.isFile &&
                sourceChild.length() == targetChild.length() &&
                sha256(sourceChild).contentEquals(sha256(targetChild))
        }
    }

    private fun sha256(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun deleteTarget(target: File) {
        if (target.isDirectory) target.deleteRecursively() else target.delete()
    }

    private inner class ProgressTracker(
        sourcePaths: List<String>,
        totalBytes: Long,
        private val onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        private val totalItems = sourcePaths.size.coerceAtLeast(1)
        private val totalBytes = totalBytes
        private var completedItems = 0
        private var copiedBytes = 0L
        private var lastProgressEmitTime = 0L
        var currentPath: String = ""

        suspend fun onBytesCopied(delta: Long) {
            copiedBytes += delta
            val now = System.currentTimeMillis()
            if (now - lastProgressEmitTime > 200) {
                lastProgressEmitTime = now
                emit(completedItems, currentPath = currentPath, bytesCopied = copiedBytes)
            }
        }

        suspend fun completeItem(path: String) {
            completedItems += 1
            val reportedBytes = if (completedItems == totalItems) totalBytes else copiedBytes
            lastProgressEmitTime = System.currentTimeMillis()
            emit(completedItems, currentPath = path, bytesCopied = reportedBytes)
        }

        private suspend fun emit(completedItems: Int, currentPath: String, bytesCopied: Long) {
            ensureOperationActive()
            onProgress?.invoke(
                BulkFileOperationProgress(
                    completedItems = completedItems,
                    totalItems = totalItems,
                    currentPath = currentPath,
                    bytesCopied = bytesCopied,
                    totalBytes = totalBytes
                )
            )
        }
    }
}
