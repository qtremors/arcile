package dev.qtremors.arcile.feature.importing

import android.net.Uri
import dev.qtremors.arcile.core.operation.android.FREE_SPACE_SAFETY_BUFFER_BYTES
import dev.qtremors.arcile.core.operation.android.MAX_IMPORT_BYTES
import dev.qtremors.arcile.core.operation.android.STREAM_BUFFER_SIZE
import dev.qtremors.arcile.core.operation.android.sanitizeIncomingFileName
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.CancellationException

internal data class CopyResult(val bytesCopied: Long)

internal suspend fun saveIncomingFiles(
    destination: File,
    incoming: List<IncomingSharedFile>,
    openInputStream: (Uri) -> InputStream?,
    finalizeDestination: suspend (String) -> Unit,
    invalidDestinationMessage: String,
    insufficientSpaceMessage: String,
    failedOpenStreamMessage: String,
    usableSpaceProvider: (File) -> Long = { it.usableSpace }
): Result<SaveIncomingResult> {
    require(destination.exists() && destination.isDirectory && destination.canWrite()) {
        invalidDestinationMessage
    }
    val knownBytes = incoming.sumOf { it.sizeBytes ?: 0L }
    val usableBytes = usableSpaceProvider(destination)
    require(usableBytes <= 0L || usableBytes >= knownBytes + FREE_SPACE_SAFETY_BUFFER_BYTES) {
        insufficientSpaceMessage
    }

    var importedBytes = 0L
    val successes = mutableListOf<String>()
    val failures = mutableListOf<IncomingShareFailure>()
    incoming.forEach { item ->
        val target = keepBothTarget(destination, item.displayName)
        try {
            openInputStream(item.uri).use { input ->
                requireNotNull(input) { failedOpenStreamMessage }
                target.outputStream().buffered().use { output ->
                    importedBytes += copyWithByteLimit(
                        input = input,
                        output = output,
                        remainingBudget = MAX_IMPORT_BYTES - importedBytes
                    ).bytesCopied
                }
            }
            successes += target.absolutePath
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            runCatching { target.delete() }
            failures += IncomingShareFailure(
                uri = item.uri,
                displayName = item.displayName,
                reason = IncomingShareFailureReason.CopyFailed,
                message = error.message ?: failedOpenStreamMessage
            )
        }
    }
    if (successes.isNotEmpty()) finalizeDestination(destination.absolutePath)
    return Result.success(SaveIncomingResult(successes.size, failures))
}

private fun keepBothTarget(destination: File, requestedName: String): File {
    val safeName = sanitizeIncomingFileName(requestedName)
    val requested = File(destination, safeName)
    if (!requested.exists()) return requested

    val baseName = requested.nameWithoutExtension
    val extension = requested.extension
        .takeIf(String::isNotBlank)
        ?.let { ".$it" }
        .orEmpty()
    var index = 1
    var candidate: File
    do {
        candidate = File(destination, "$baseName ($index)$extension")
        index += 1
    } while (candidate.exists())
    return candidate
}

internal fun copyWithByteLimit(
    input: InputStream,
    output: OutputStream,
    remainingBudget: Long
): CopyResult {
    if (remainingBudget <= 0L) throw IOException("Shared import exceeds the 10 GB limit")
    val buffer = ByteArray(STREAM_BUFFER_SIZE)
    var copied = 0L
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        copied += read
        if (copied > remainingBudget) {
            throw IOException("Shared import exceeds the 10 GB limit")
        }
        output.write(buffer, 0, read)
    }
    return CopyResult(copied)
}
