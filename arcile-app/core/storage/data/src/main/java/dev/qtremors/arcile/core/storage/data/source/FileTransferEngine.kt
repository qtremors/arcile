package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.data.runCatchingPreservingCancellation
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.data.MutationPathIdentity
import dev.qtremors.arcile.core.storage.data.SourceCleanupIncompleteException
import dev.qtremors.arcile.core.storage.data.deleteSourceTree
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID

class FileTransferEngine(
    private val validatePath: (File) -> Result<Unit>,
    private val validateMutationPath: (File) -> Result<Unit> = validatePath,
    private val rename: (File, File) -> Boolean = { source, target -> source.renameTo(target) },
    private val checksumFile: (File) -> ByteArray = ::calculateSha256,
    private val afterCopy: (File, File) -> Unit = { _, _ -> },
    private val deleteSourceEntry: (File) -> Boolean = { file -> file.delete() },
    private val mutationJournal: MutationJournal = NoOpMutationJournal(),
    private val deleteTarget: (File) -> Boolean = { target ->
        if (!target.exists()) true else if (target.isDirectory) target.deleteRecursively() else target.delete()
    }
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
            var publication: PublishedTarget? = null
            try {
                publication = copyAtomically(
                    source = sourceFile,
                    target = targetFile,
                    replaceExisting = resolutions[sourceFile.absolutePath] == ConflictResolution.REPLACE,
                    verificationPolicy = VerificationPolicy.METADATA,
                    onBytesCopied = tracker::onBytesCopied
                )
                publication.finish()
            } catch (e: Exception) {
                publication?.rollback()
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
                var sourceCleanupStarted = false
                var publication: PublishedTarget? = null
                try {
                    tracker.currentPath = sourceFile.absolutePath
                    publication = copyAtomically(
                        source = sourceFile,
                        target = targetFile,
                        replaceExisting = shouldReplace,
                        verificationPolicy = VerificationPolicy.FULL_CHECKSUM,
                        onBytesCopied = tracker::onBytesCopied,
                        sourceCleanupPending = true
                    )
                    if (!verifyCopyIntegrity(sourceFile, targetFile, VerificationPolicy.FULL_CHECKSUM, tracker::onVerificationProgress)) {
                        throw IOException("Failed to verify moved ${if (sourceFile.isDirectory) "directory" else "file"} before deleting source")
                    }
                    ensureOperationActive()
                    mutationJournal.recordSourceCleanup(sourceFile.absolutePath, targetFile.absolutePath)
                    sourceCleanupStarted = true
                    val cleanup = deleteSourceTree(sourceFile, deleteSourceEntry)
                    if (!cleanup.isComplete) {
                        throw SourceCleanupIncompleteException(
                            sourcePath = sourceFile.absolutePath,
                            destinationPath = targetFile.absolutePath,
                            remainingSourcePaths = cleanup.remainingPaths,
                            cause = cleanup.failure
                        )
                    }
                    publication.finish()
                    mutationJournal.forgetSourceCleanup(sourceFile.absolutePath, targetFile.absolutePath)
                } catch (e: Exception) {
                    if (!sourceCleanupStarted) publication?.rollback()
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
            var sourceCleanupStarted = false
            var publication: PublishedTarget? = null
            try {
                tracker.currentPath = source.absolutePath
                publication = copyAtomically(
                    source = source,
                    target = target,
                    replaceExisting = false,
                    verificationPolicy = VerificationPolicy.FULL_CHECKSUM,
                    onBytesCopied = tracker::onBytesCopied,
                    sourceCleanupPending = true
                )
                if (!verifyCopyIntegrity(source, target, VerificationPolicy.FULL_CHECKSUM, tracker::onVerificationProgress)) {
                    throw IOException("Failed to verify moved ${if (source.isDirectory) "directory" else "file"} before deleting source")
                }
                ensureOperationActive()
                mutationJournal.recordSourceCleanup(source.absolutePath, target.absolutePath)
                sourceCleanupStarted = true
                val cleanup = deleteSourceTree(source, deleteSourceEntry)
                if (!cleanup.isComplete) {
                    throw SourceCleanupIncompleteException(
                        sourcePath = source.absolutePath,
                        destinationPath = target.absolutePath,
                        remainingSourcePaths = cleanup.remainingPaths,
                        cause = cleanup.failure
                    )
                }
                publication.finish()
                mutationJournal.forgetSourceCleanup(source.absolutePath, target.absolutePath)
            } catch (e: Exception) {
                if (!sourceCleanupStarted) publication?.rollback()
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
        onBytesCopied: suspend (Long) -> Unit,
        sourceCleanupPending: Boolean = false
    ): PublishedTarget {
        ensureOperationActive()
        validateMutationPath(source).getOrThrow()
        target.parentFile?.let { validateMutationPath(it).getOrThrow() }
        target.parentFile?.mkdirs()
        if (target.exists() && !replaceExisting) {
            throw IllegalStateException("Target already exists: ${target.name}")
        }

        val stagingTarget = createStagingTarget(target)
        mutationJournal.recordTemporaryPath(stagingTarget.absolutePath)
        var publication: PublishedTarget? = null
        try {
            validateMutationPath(stagingTarget).getOrThrow()
            if (source.isDirectory) {
                copyDirectoryCancellable(source, stagingTarget, onBytesCopied)
            } else {
                copyFileCancellable(source, stagingTarget, onBytesCopied)
            }
            afterCopy(source, stagingTarget)
            val requiredVerification = if (replaceExisting) VerificationPolicy.FULL_CHECKSUM else verificationPolicy
            if (!verifyCopyIntegrity(source, stagingTarget, requiredVerification, null)) {
                throw IOException("Failed to verify copied ${if (source.isDirectory) "directory" else "file"}")
            }
            publication = promoteStagedTarget(stagingTarget, target, replaceExisting, source.takeIf { sourceCleanupPending })
            mutationJournal.forgetTemporaryPath(stagingTarget.absolutePath)
            return publication
        } catch (e: Exception) {
            publication?.rollback()
            if (deleteTarget(stagingTarget)) mutationJournal.forgetTemporaryPath(stagingTarget.absolutePath)
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
        val pending = ArrayDeque<Pair<File, File>>()
        val copiedDirectories = mutableListOf<Pair<File, File>>()
        val visitedDirectories = hashSetOf<String>()
        pending.addLast(source to target)

        while (pending.isNotEmpty()) {
            ensureOperationActive()
            val (sourceDirectory, targetDirectory) = pending.removeLast()
            validateMutationPath(sourceDirectory).getOrThrow()
            validateMutationPath(targetDirectory).getOrThrow()
            val identity = directoryIdentity(sourceDirectory)
            if (!visitedDirectories.add(identity)) {
                throw IOException("Cannot copy a directory tree containing a cycle: ${sourceDirectory.absolutePath}")
            }
            if (!targetDirectory.mkdirs() && !targetDirectory.isDirectory) {
                throw IOException("Failed to create directory: ${targetDirectory.absolutePath}")
            }
            copiedDirectories += sourceDirectory to targetDirectory
            val children = sourceDirectory.listFiles()
                ?: throw IOException("Unable to read directory while copying: ${sourceDirectory.absolutePath}")
            children.forEach { child ->
                ensureOperationActive()
                val childTarget = File(targetDirectory, child.name)
                if (child.isDirectory) {
                    pending.addLast(child to childTarget)
                } else {
                    copyFileCancellable(child, childTarget, onBytesCopied)
                }
            }
        }
        copiedDirectories.asReversed().forEach { (sourceDirectory, targetDirectory) ->
            ensureOperationActive()
            targetDirectory.setLastModified(sourceDirectory.lastModified())
        }
    }

    private fun createStagingTarget(target: File): File {
        val parent = target.parentFile ?: throw IllegalStateException("Target has no parent directory")
        var candidate: File
        do {
            candidate = File(parent, ".${target.name}.arcile-transfer-${UUID.randomUUID()}.tmp")
        } while (candidate.exists())
        return candidate
    }

    private fun promoteStagedTarget(stagingTarget: File, target: File, replaceExisting: Boolean, moveSource: File?): PublishedTarget {
        validateMutationPath(stagingTarget).getOrThrow()
        target.parentFile?.let { validateMutationPath(it).getOrThrow() }
        validateMutationPath(target).getOrThrow()
        val identity = MutationPathIdentity.read(stagingTarget)
        if (target.exists()) {
            if (!replaceExisting) throw IllegalStateException("Target already exists: ${target.name}")
            val backupTarget = File(target.parentFile, ".${target.name}.arcile-replace-${UUID.randomUUID()}.bak")
            validateMutationPath(backupTarget).getOrThrow()
            val originalIdentity = MutationPathIdentity.read(target)
            mutationJournal.recordReplacement(target.absolutePath, stagingTarget.absolutePath, backupTarget.absolutePath, moveSource?.absolutePath)
            val publication = PublishedTarget(target, identity, backupTarget, originalIdentity)
            try {
                if (!rename(target, backupTarget)) {
                    mutationJournal.forgetReplacement(target.absolutePath, backupTarget.absolutePath)
                    throw IOException("Failed to stage existing target for replacement: ${target.name}")
                }
                publication.originalIdentity = MutationPathIdentity.read(backupTarget)
                if (!rename(stagingTarget, target)) {
                    throw IOException("Failed to promote replacement: ${target.name}")
                }
                publication.identity = MutationPathIdentity.read(target)
                mutationJournal.markReplacementPublished(target.absolutePath, backupTarget.absolutePath)
                return publication
            } catch (e: Exception) {
                publication.rollback()
                throw e
            }
        }
        if (!rename(stagingTarget, target)) {
            throw IOException("Failed to promote copied file: ${target.name}")
        }
        return PublishedTarget(target, MutationPathIdentity.read(target))
    }

    private inner class PublishedTarget(
        private val target: File,
        var identity: MutationPathIdentity,
        private val backup: File? = null,
        var originalIdentity: MutationPathIdentity? = null
    ) {
        private var backupCleanupStarted = false

        fun finish() {
            if (backup == null) return
            // Recursive cleanup may destroy only part of the original tree.
            // Keep the verified publication and journal if cleanup fails.
            backupCleanupStarted = true
            if (backup.exists() && (originalIdentity?.matchesOwned(backup) != true || !deleteTarget(backup))) {
                throw IOException("Replacement is published, but its original backup still needs recovery")
            }
            mutationJournal.forgetReplacement(target.absolutePath, backup.absolutePath)
        }

        fun rollback() {
            if (backupCleanupStarted) return
            runCatchingPreservingCancellation {
                if (backup == null) {
                    if (identity.matchesOwned(target)) deleteTarget(target)
                } else if (backup.exists() && originalIdentity?.matchesOwned(backup) == true) {
                    if ((!target.exists() || (identity.matchesOwned(target) && deleteTarget(target))) && rename(backup, target)) {
                        mutationJournal.forgetReplacement(target.absolutePath, backup.absolutePath)
                    }
                } else if (!backup.exists() && originalIdentity?.matchesOwned(target) == true) {
                    mutationJournal.forgetReplacement(target.absolutePath, backup.absolutePath)
                }
            }
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
        val visitedDirectories = hashSetOf<String>()
        pending.add(source)
        while (pending.isNotEmpty()) {
            ensureOperationActive()
            val current = pending.removeFirst()
            val relativePath = current.relativeTo(source).path.takeUnless { it == "." }.orEmpty()
            val targetChild = if (relativePath.isBlank()) target else File(target, relativePath)
            if (current.isDirectory) {
                val identity = runCatchingPreservingCancellation {
                    directoryIdentity(current)
                }.getOrElse { return false }
                if (!visitedDirectories.add(identity)) return false
                if (!targetChild.isDirectory) return false
                val children = current.listFiles() ?: return false
                children.forEach { pending.addLast(it) }
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
        val visitedDirectories = hashSetOf<String>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            ensureOperationActive()
            val current = pending.removeFirst()
            if (current.isDirectory) {
                val identity = directoryIdentity(current)
                if (!visitedDirectories.add(identity)) {
                    throw IOException("Directory cycle detected while verifying: ${current.absolutePath}")
                }
                val children = current.listFiles()
                    ?: throw IOException("Unable to read directory while verifying: ${current.absolutePath}")
                children.forEach { pending.addLast(it) }
            } else {
                count += 1
            }
        }
        return count
    }

    private fun directoryIdentity(directory: File): String =
        try {
            val attributes = Files.readAttributes(directory.toPath(), BasicFileAttributes::class.java)
            attributes.fileKey()?.let { "key:$it" } ?: directory.toPath().toRealPath().toString()
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            throw IOException("Unable to identify directory: ${directory.absolutePath}", error)
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
                emit(completedItems, currentPath = currentPath, bytesProcessed = copiedBytes)
            }
        }

        suspend fun completeItem(path: String) {
            completedItems += 1
            val reportedBytes = if (completedItems == totalItems) totalBytes else copiedBytes
            lastProgressEmitTime = System.currentTimeMillis()
            emit(completedItems, currentPath = path, bytesProcessed = reportedBytes)
        }

        suspend fun onVerificationProgress() {
            emit(completedItems, currentPath = currentPath, bytesProcessed = copiedBytes)
        }

        private suspend fun emit(completedItems: Int, currentPath: String, bytesProcessed: Long) {
            ensureOperationActive()
            onProgress?.invoke(
                BulkFileOperationProgress(
                    completedItems = completedItems,
                    totalItems = totalItems,
                    currentPath = currentPath,
                    bytesProcessed = bytesProcessed,
                    totalBytes = totalBytes
                )
            )
        }
    }
}
