package dev.qtremors.arcile.core.operation.android

import android.content.Context
import android.net.Uri
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.operation.BulkFileOperationRequest
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
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
import java.util.UUID

internal class SharedFileImporter(
    context: Context,
    private val mutationJournal: MutationJournal,
    private val mutationFinalizer: MutationFinalizer?,
    private val onCheckpoint: (
        stagedPaths: List<String>,
        finalizedPaths: List<String>,
        rollbackHints: List<String>
    ) -> Unit
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
        val knownBytes = items.sumOf { it.sizeBytes ?: 0L }
        require(knownBytes <= MAX_IMPORT_BYTES) { appContext.getString(R.string.save_to_arcile_too_large) }
        require(destination.usableSpace <= 0L || destination.usableSpace >= knownBytes + FREE_SPACE_SAFETY_BUFFER_BYTES) {
            appContext.getString(R.string.save_to_arcile_insufficient_space)
        }

        val totalBytes = knownBytes.takeIf { it > 0L }
        var copiedBytes = 0L
        var completedItems = 0
        val finalized = mutableListOf<String>()
        val failures = mutableListOf<BatchMutationFailure>()

        for (item in items) {
            currentCoroutineContext().ensureActive()
            val target = keepBothTarget(destination, item.displayName)
            val staged = createStagingTarget(target)
            mutationJournal.recordTemporaryPath(staged.absolutePath)
            onCheckpoint(listOf(staged.absolutePath), emptyList(), emptyList())
            try {
                val input = contentResolver.openInputStream(Uri.parse(item.uri))
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
                if (!staged.renameTo(target)) throw IOException("Failed to save ${item.displayName}")
                mutationJournal.forgetTemporaryPath(staged.absolutePath)
                finalized += target.absolutePath
                onCheckpoint(
                    emptyList(),
                    listOf(target.absolutePath),
                    listOf("created:${target.absolutePath}")
                )
                completedItems += 1
                onProgress(progress(completedItems, items.size, item.displayName, copiedBytes, totalBytes))
            } catch (error: Exception) {
                runCatching { if (staged.exists()) staged.delete() }
                mutationJournal.forgetTemporaryPath(staged.absolutePath)
                if (error is CancellationException) throw error
                if (error is ImportLimitExceededException) return Result.failure(error)
                failures += BatchMutationFailure(
                    path = item.uri,
                    displayName = item.displayName,
                    message = error.message ?: appContext.getString(R.string.save_to_arcile_failed_open_stream),
                    causeType = error::class.java.simpleName
                )
                completedItems += 1
                onProgress(progress(completedItems, items.size, item.displayName, copiedBytes, totalBytes))
            }
        }

        if (finalized.isNotEmpty()) mutationFinalizer?.finalize(destination.absolutePath)
        return result(finalized, failures)
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

    private fun keepBothTarget(destination: File, requestedName: String): File {
        val requested = File(destination, sanitizeIncomingFileName(requestedName))
        if (!requested.exists()) return requested
        val baseName = requested.nameWithoutExtension
        val extension = requested.extension.takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        var index = 1
        while (true) {
            val candidate = File(destination, "$baseName ($index)$extension")
            if (!candidate.exists()) return candidate
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

    private class ImportLimitExceededException(message: String) : IOException(message)
}
