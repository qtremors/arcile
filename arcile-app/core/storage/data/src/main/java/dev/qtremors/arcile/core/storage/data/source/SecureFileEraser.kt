package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.domain.BatchMutationFailure
import java.io.File
import java.io.FileOutputStream

internal class SecureFileEraser(
    private val overwriteOverride: () -> (
        (File) -> DefaultFileSystemDataSource.SecureOverwriteResult
    )?
) {
    fun shredRecursively(file: File): List<BatchMutationFailure> {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return listOf(
                BatchMutationFailure(
                    path = file.absolutePath,
                    displayName = file.name.ifBlank { file.absolutePath },
                    message = "Unable to inspect directory before secure shred: ${file.name}",
                    causeType = "SecureOverwriteFailed",
                    cleanupRequired = true
                )
            )
            return children.flatMap(::shredRecursively)
        }
        if (!file.isFile) return emptyList()

        return when (val result = overwriteSecurely(file)) {
            DefaultFileSystemDataSource.SecureOverwriteResult.Success -> emptyList()
            is DefaultFileSystemDataSource.SecureOverwriteResult.Failure -> listOf(
                BatchMutationFailure(
                    path = file.absolutePath,
                    displayName = file.name.ifBlank { file.absolutePath },
                    message = result.message,
                    causeType = result.causeType,
                    cleanupRequired = true
                )
            )
        }
    }

    private fun overwriteSecurely(
        file: File
    ): DefaultFileSystemDataSource.SecureOverwriteResult {
        overwriteOverride()?.let { return it(file) }
        if (!file.canWrite()) {
            return DefaultFileSystemDataSource.SecureOverwriteResult.Failure(
                "Unable to securely overwrite ${file.name}: file is not writable"
            )
        }
        val length = file.length()
        if (length <= 0L) {
            return DefaultFileSystemDataSource.SecureOverwriteResult.Success
        }

        val bufferSize = 64 * 1024
        val buffer = ByteArray(bufferSize)
        return try {
            FileOutputStream(file, false).use { output ->
                var remaining = length
                while (remaining > 0L) {
                    val toWrite = remaining.coerceAtMost(bufferSize.toLong()).toInt()
                    output.write(buffer, 0, toWrite)
                    remaining -= toWrite
                }
                output.flush()
                output.fd.sync()
            }
            DefaultFileSystemDataSource.SecureOverwriteResult.Success
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            DefaultFileSystemDataSource.SecureOverwriteResult.Failure(
                message = "Unable to securely overwrite ${file.name}: " +
                    (error.message ?: error.javaClass.simpleName),
                causeType = "SecureOverwriteFailed"
            )
        }
    }
}
