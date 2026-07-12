package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
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
    private val validateMutationPath: (File) -> Result<Unit> = validatePath,
    private val rename: (File, File) -> Boolean = { source, target -> source.renameTo(target) },
    private val checksumFile: (File) -> ByteArray = ::calculateSha256,
    private val afterCopy: (File, File) -> Unit = { _, _ -> },
    private val mutationJournal: MutationJournal = NoOpMutationJournal()
) {
    private companion object {
        const val TRANSFER_ESTIMATE_NODE_LIMIT = 10_000
        const val TRANSFER_ESTIMATE_CANCELLATION_GRANULARITY = 128
        const val METADATA_TIME_TOLERANCE_MS = 2_000L

        fun calculateSha256(file: File): ByteArray {
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
    }

    enum class VerificationPolicy {
        METADATA,
        FULL_CHECKSUM
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
                    verificationPolicy = VerificationPolicy.METADATA,
                    onBytesCopied = tracker::onBytesCopied
                )
            } catch (e: Exception) {
                e.rethrowIfCancellation()
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
                    copyAtomically(
                        source = sourceFile,
                        target = targetFile,
                        replaceExisting = shouldReplace,
                        verificationPolicy = VerificationPolicy.FULL_CHECKSUM,
                        onBytesCopied = tracker::onBytesCopied
                    )
                    if (!verifyCopyIntegrity(sourceFile, targetFile, VerificationPolicy.FULL_CHECKSUM, tracker::onVerificationProgress)) {
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
                    e.rethrowIfCancellation()
                    return Result.failure(e)
                }
            }

            scannedPaths += sourceFile.absolutePath
            scannedPaths += targetFile.absolutePath
            tracker.completeItem(targetFile.absolutePath)
        }

        return Result.success(scannedPaths)
    }

    suspend fun moveToTarget(
        source: File,
        target: File,
        attemptRename: Boolean = true,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<List<String>> {
        ensureOperationActive()
        validatePath(source).onFailure { return Result.failure(it) }
        validatePath(target).onFailure { return Result.failure(it) }
        if (!source.exists()) return Result.success(emptyList())
        if (source.absolutePath == target.absolutePath) return Result.success(emptyList())

        val tracker = ProgressTracker(listOf(source.absolutePath), estimateTotalBytes(listOf(source.absolutePath)), onProgress)
        val renameSuccess = attemptRename && !target.exists() && rename(source, target)
        if (!renameSuccess) {
            try {
                tracker.currentPath = source.absolutePath
                copyAtomically(
                    source = source,
                    target = target,
                    replaceExisting = false,
                    verificationPolicy = VerificationPolicy.FULL_CHECKSUM,
                    onBytesCopied = tracker::onBytesCopied
                )
                if (!verifyCopyIntegrity(source, target, VerificationPolicy.FULL_CHECKSUM, tracker::onVerificationProgress)) {
                    deleteTarget(target)
                    return Result.failure(IOException("Failed to verify moved ${if (source.isDirectory) "directory" else "file"} before deleting source"))
                }
                ensureOperationActive()
                val deleted = if (source.isDirectory) source.deleteRecursively() else source.delete()
                if (!deleted) {
                    deleteTarget(target)
                    return Result.failure(IOException("Failed to delete source ${if (source.isDirectory) "directory" else "file"} after copy"))
                }
            } catch (e: Exception) {
                deleteTarget(target)
                e.rethrowIfCancellation()
                return Result.failure(e)
            }
        }

        tracker.completeItem(target.absolutePath)
        return Result.success(listOf(source.absolutePath, target.absolutePath))
    }

    private suspend fun copyAtomically(
        source: File,
        target: File,
        replaceExisting: Boolean,
        verificationPolicy: VerificationPolicy,
        onBytesCopied: suspend (Long) -> Unit
    ) {
        ensureOperationActive()
        validateMutationPath(source).getOrThrow()
        target.parentFile?.let { validateMutationPath(it).getOrThrow() }
        target.parentFile?.mkdirs()
        if (target.exists() && !replaceExisting) {
            throw IllegalStateException("Target already exists: ${target.name}")
        }

        val stagingTarget = createStagingTarget(target)
        mutationJournal.recordTemporaryPath(stagingTarget.absolutePath)
        try {
            validateMutationPath(stagingTarget).getOrThrow()
            if (source.isDirectory) {
                copyDirectoryCancellable(source, stagingTarget, onBytesCopied)
            } else {
                copyFileCancellable(source, stagingTarget, onBytesCopied)
            }
            afterCopy(source, stagingTarget)
            if (!verifyCopyIntegrity(source, stagingTarget, verificationPolicy, null)) {
                throw IOException("Failed to verify copied ${if (source.isDirectory) "directory" else "file"}")
            }
            promoteStagedTarget(stagingTarget, target, replaceExisting)
            mutationJournal.forgetTemporaryPath(stagingTarget.absolutePath)
        } catch (e: Exception) {
            deleteTarget(stagingTarget)
            mutationJournal.forgetTemporaryPath(stagingTarget.absolutePath)
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
        validateMutationPath(source).getOrThrow()
        validateMutationPath(target).getOrThrow()
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
        validateMutationPath(source).getOrThrow()
        validateMutationPath(target).getOrThrow()
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
        validateMutationPath(stagingTarget).getOrThrow()
        target.parentFile?.let { validateMutationPath(it).getOrThrow() }
        validateMutationPath(target).getOrThrow()
        if (target.exists()) {
            if (!replaceExisting) throw IllegalStateException("Target already exists: ${target.name}")
            val backupTarget = File(target.parentFile, ".${target.name}.arcile-replace-${UUID.randomUUID()}.bak")
            validateMutationPath(backupTarget).getOrThrow()
            mutationJournal.recordTemporaryPath(backupTarget.absolutePath)
            if (!rename(target, backupTarget)) {
                mutationJournal.forgetTemporaryPath(backupTarget.absolutePath)
                throw IOException("Failed to stage existing target for replacement: ${target.name}")
            }
            try {
                if (!rename(stagingTarget, target)) {
                    rename(backupTarget, target)
                    mutationJournal.forgetTemporaryPath(backupTarget.absolutePath)
                    throw IOException("Failed to promote replacement: ${target.name}")
                }
                deleteTarget(backupTarget)
                mutationJournal.forgetTemporaryPath(backupTarget.absolutePath)
            } catch (e: Exception) {
                if (!target.exists() && backupTarget.exists()) {
                    rename(backupTarget, target)
                }
                mutationJournal.forgetTemporaryPath(backupTarget.absolutePath)
                throw e
            }
        } else if (!rename(stagingTarget, target)) {
            throw IOException("Failed to promote copied file: ${target.name}")
        }
    }

    private suspend fun verifyCopyIntegrity(
        source: File,
        target: File,
        policy: VerificationPolicy,
        onVerifiedFile: (suspend () -> Unit)?
    ): Boolean {
        ensureOperationActive()
        if (!source.exists() || !target.exists()) return false
        if (source.isFile) {
            return target.isFile && verifyFileIntegrity(source, target, policy).also {
                if (it) onVerifiedFile?.invoke()
            }
        }
        if (!source.isDirectory || !target.isDirectory) return false

        var sourceFileCount = 0
        val pending = ArrayDeque<File>()
        pending.add(source)
        while (pending.isNotEmpty()) {
            ensureOperationActive()
            val current = pending.removeFirst()
            val relativePath = current.relativeTo(source).path.takeUnless { it == "." }.orEmpty()
            val targetChild = if (relativePath.isBlank()) target else File(target, relativePath)
            if (current.isDirectory) {
                if (!targetChild.isDirectory) return false
                current.listFiles()?.forEach { pending.addLast(it) }
            } else {
                sourceFileCount += 1
                if (!targetChild.isFile || !verifyFileIntegrity(current, targetChild, policy)) return false
                onVerifiedFile?.invoke()
            }
        }
        return sourceFileCount == countFilesStreaming(target)
    }

    private fun verifyFileIntegrity(source: File, target: File, policy: VerificationPolicy): Boolean {
        if (source.length() != target.length()) return false
        return when (policy) {
            VerificationPolicy.METADATA -> {
                val modifiedDelta = kotlin.math.abs(source.lastModified() - target.lastModified())
                modifiedDelta <= METADATA_TIME_TOLERANCE_MS
            }
            VerificationPolicy.FULL_CHECKSUM -> checksumFile(source).contentEquals(checksumFile(target))
        }
    }

    private suspend fun countFilesStreaming(root: File): Int {
        var count = 0
        val pending = ArrayDeque<File>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            ensureOperationActive()
            val current = pending.removeFirst()
            if (current.isDirectory) {
                current.listFiles()?.forEach { pending.addLast(it) }
            } else {
                count += 1
            }
        }
        return count
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

        suspend fun onVerificationProgress() {
            emit(completedItems, currentPath = currentPath, bytesCopied = copiedBytes)
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
