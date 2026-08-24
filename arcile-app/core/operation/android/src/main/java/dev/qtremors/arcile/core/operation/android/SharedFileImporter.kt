package dev.qtremors.arcile.core.operation.android

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.domain.ArcileError
import dev.qtremors.arcile.core.storage.domain.BatchMutationFailure
import dev.qtremors.arcile.core.storage.domain.BatchMutationPartialFailure
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.ui.R
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.util.UUID

internal class SharedFileImporter(
    context: Context,
    private val mutationJournal: MutationJournal,
    private val mutationFinalizer: MutationFinalizer?,
    private val onCheckpoint: (
        stagedPaths: List<String>,
        finalizedPaths: List<String>,
        rollbackHints: List<String>
    ) -> Unit,
    private val spaceAllocator: ImportSpaceAllocator = AndroidImportSpaceAllocator(context),
    private val beforeCommit: () -> Unit = {}
) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver

    suspend fun import(
        request: BulkFileOperationRequest,
        onProgress: (BulkFileOperationProgress) -> Unit
    ): Result<Unit> {
        val destination = File(requireNotNull(request.destinationPath) { "Destination path is required for import" })
        require(destination.exists() && destination.isDirectory && destination.canWrite()) {
            appContext.getString(R.string.save_to_arcile_invalid_destination)
        }
        val items = request.importItems
        require(items.isNotEmpty()) { appContext.getString(R.string.save_to_arcile_no_files) }
        val knownBytes = items.fold(0L) { total, item ->
            val itemBytes = item.sizeBytes ?: return@fold total
            require(itemBytes >= 0L && itemBytes <= MAX_IMPORT_BYTES - total) {
                appContext.getString(R.string.save_to_arcile_too_large)
            }
            total + itemBytes
        }
        val reservation = try {
            spaceAllocator.reserve(
                destination = destination,
                knownContentBytes = knownBytes,
                hasUnknownSizes = items.any { it.sizeBytes == null }
            )
        } catch (error: ArcileError.InsufficientSpace) {
            return Result.failure(error)
        }

        val totalBytes = knownBytes.takeIf { it > 0L && items.all { item -> item.sizeBytes != null } }
        var copiedBytes = 0L
        var completedItems = 0
        val finalized = mutableListOf<String>()
        val failures = mutableListOf<BatchMutationFailure>()
        val pending = mutableListOf<PendingImport>()
        val reservedTargetNames = mutableSetOf<String>()

        try {
            for (item in items) {
                currentCoroutineContext().ensureActive()
                val target = keepBothTarget(destination, item.displayName, reservedTargetNames)
                reservedTargetNames += target.name
                val staged = createStagingTarget(target)
                mutationJournal.recordTemporaryPath(staged.absolutePath)
                onCheckpoint(listOf(staged.absolutePath), emptyList(), emptyList())
                try {
                    val input = contentResolver.openInputStream(item.uri.toUri())
                        ?: throw IOException(appContext.getString(R.string.save_to_arcile_failed_open_stream))
                    input.use { rawInput ->
                        BufferedInputStream(rawInput).use { bufferedInput ->
                            BufferedOutputStream(staged.outputStream()).use { output ->
                                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val read = bufferedInput.read(buffer)
                                    if (read < 0) break
                                    copiedBytes += read
                                    if (copiedBytes > MAX_IMPORT_BYTES) {
                                        throw ImportLimitExceededException(
                                            appContext.getString(R.string.save_to_arcile_too_large)
                                        )
                                    }
                                    output.write(buffer, 0, read)
                                    onProgress(
                                        progress(
                                            completedItems = completedItems,
                                            totalItems = items.size,
                                            currentPath = item.displayName,
                                            copiedBytes = copiedBytes,
                                            totalBytes = totalBytes
                                        )
                                    )
                                }
                            }
                        }
                    }
                    pending += PendingImport(item.uri, item.displayName, target, staged)
                } catch (error: Exception) {
                    cleanupStaged(staged)
                    reservedTargetNames -= target.name
                    if (error is CancellationException) throw error
                    if (error is ImportLimitExceededException) return Result.failure(error)
                    if (error.isInsufficientSpaceFailure()) {
                        return Result.failure(ArcileError.InsufficientSpace(error))
                    }
                    failures += error.toFailure(item.uri, item.displayName)
                    completedItems += 1
                    onProgress(progress(completedItems, items.size, item.displayName, copiedBytes, totalBytes))
                }
            }

            try {
                reservation.verifyBeforeCommit()
            } catch (error: ArcileError.InsufficientSpace) {
                return Result.failure(error)
            }
            beforeCommit()

            pending.toList().forEach { stagedImport ->
                currentCoroutineContext().ensureActive()
                try {
                    val committedTarget = commitToAvailableTarget(
                        destination = destination,
                        stagedImport = stagedImport,
                        reservedTargetNames = reservedTargetNames
                    )
                    mutationJournal.forgetTemporaryPath(stagedImport.staged.absolutePath)
                    pending -= stagedImport
                    finalized += committedTarget.absolutePath
                    onCheckpoint(
                        emptyList(),
                        listOf(committedTarget.absolutePath),
                        listOf("created:${committedTarget.absolutePath}")
                    )
                } catch (error: Exception) {
                    cleanupStaged(stagedImport.staged)
                    pending -= stagedImport
                    if (error is CancellationException) throw error
                    failures += error.toFailure(stagedImport.uri, stagedImport.displayName)
                }
                completedItems += 1
                onProgress(
                    progress(
                        completedItems,
                        items.size,
                        stagedImport.displayName,
                        copiedBytes,
                        totalBytes
                    )
                )
            }

            if (finalized.isNotEmpty()) mutationFinalizer?.finalize(destination.absolutePath)
            return result(finalized, failures)
        } finally {
            pending.forEach { cleanupStaged(it.staged) }
        }
    }

    private fun commitToAvailableTarget(
        destination: File,
        stagedImport: PendingImport,
        reservedTargetNames: MutableSet<String>
    ): File {
        var target = stagedImport.target
        while (true) {
            if (target.exists()) {
                target = keepBothTarget(destination, stagedImport.displayName, reservedTargetNames)
                reservedTargetNames += target.name
            }
            try {
                Files.move(stagedImport.staged.toPath(), target.toPath())
                return target
            } catch (_: FileAlreadyExistsException) {
                reservedTargetNames += target.name
                target = keepBothTarget(destination, stagedImport.displayName, reservedTargetNames)
                reservedTargetNames += target.name
            }
        }
    }

    private fun progress(
        completedItems: Int,
        totalItems: Int,
        currentPath: String,
        copiedBytes: Long,
        totalBytes: Long?
    ) = BulkFileOperationProgress(
        completedItems = completedItems,
        totalItems = totalItems,
        currentPath = currentPath,
        bytesCopied = totalBytes?.let { copiedBytes.coerceAtMost(it) } ?: copiedBytes,
        totalBytes = totalBytes
    )

    private fun result(
        finalized: List<String>,
        failures: List<BatchMutationFailure>
    ): Result<Unit> = when {
        failures.isEmpty() -> Result.success(Unit)
        finalized.isEmpty() -> Result.failure(IOException(failures.first().message))
        else -> Result.failure(
            BatchMutationPartialFailure(
                batchResult = BatchMutationResult(
                    succeededPaths = finalized,
                    failedItems = failures
                ),
                message = "Save to Arcile partially completed: ${finalized.size} succeeded, 0 skipped, " +
                    "${failures.size} failed. First failure: ${failures.first().displayName}: ${failures.first().message}"
            )
        )
    }

    private fun keepBothTarget(
        destination: File,
        requestedName: String,
        reservedNames: Set<String>
    ): File {
        val requested = File(destination, sanitizeIncomingFileName(requestedName))
        if (!requested.exists() && reservedNames.none { it.equals(requested.name, ignoreCase = true) }) {
            return requested
        }
        val baseName = requested.nameWithoutExtension
        val extension = requested.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 1
        while (true) {
            val candidate = File(destination, "$baseName ($index)$extension")
            if (!candidate.exists() && reservedNames.none { it.equals(candidate.name, ignoreCase = true) }) {
                return candidate
            }
            index += 1
        }
    }

    private fun createStagingTarget(target: File): File {
        val parent = requireNotNull(target.parentFile) { "Import target has no parent directory" }
        var candidate: File
        do {
            candidate = File(parent, ".${target.name}.arcile-import-${UUID.randomUUID()}.tmp")
        } while (candidate.exists())
        return candidate
    }

    private fun cleanupStaged(staged: File) {
        runCatching { if (staged.exists()) staged.delete() }
        mutationJournal.forgetTemporaryPath(staged.absolutePath)
    }

    private fun Exception.toFailure(uri: String, displayName: String) = BatchMutationFailure(
        path = uri,
        displayName = displayName,
        message = message ?: appContext.getString(R.string.save_to_arcile_failed_open_stream),
        causeType = this::class.java.simpleName
    )

    private fun Throwable.isInsufficientSpaceFailure(): Boolean = generateSequence(this) { it.cause }
        .mapNotNull { it.message }
        .any { message ->
            message.contains("ENOSPC", ignoreCase = true) ||
                message.contains("no space left", ignoreCase = true) ||
                message.contains("not enough space", ignoreCase = true) ||
                message.contains("insufficient space", ignoreCase = true) ||
                message.contains("disk full", ignoreCase = true)
        }

    private class ImportLimitExceededException(message: String) : IOException(message)

    private data class PendingImport(
        val uri: String,
        val displayName: String,
        val target: File,
        val staged: File
    )
}
