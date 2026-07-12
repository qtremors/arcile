package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import java.util.Random
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal class SyntheticFileCreator(
    private val dispatchers: ArcileDispatchers,
    private val validateName: (String) -> Result<Unit>,
    private val validatePath: (File) -> Result<Unit>,
    private val finalizeMutation: suspend (String) -> Unit,
    private val fileModelMapper: LocalFileModelMapper
) {
    suspend fun create(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = withContext(dispatchers.io) {
        try {
            validateName(name).getOrThrow()
            val parent = File(parentPath)
            validatePath(parent).getOrThrow()
            val target = File(parent, name)
            validatePath(target).getOrThrow()
            if (target.exists()) {
                return@withContext Result.failure(Exception("File already exists"))
            }

            val buffer = ByteArray(1024 * 1024)
            val random = Random()
            target.outputStream().use { output ->
                var remaining = size
                var totalWritten = 0L
                while (remaining > 0L) {
                    ensureActive()
                    val bytesToWrite = remaining.coerceAtMost(buffer.size.toLong()).toInt()
                    random.nextBytes(buffer)
                    output.write(buffer, 0, bytesToWrite)
                    remaining -= bytesToWrite
                    totalWritten += bytesToWrite
                    onProgress?.invoke(
                        BulkFileOperationProgress(
                            completedItems = 0,
                            totalItems = 1,
                            currentPath = target.absolutePath,
                            bytesCopied = totalWritten,
                            totalBytes = size
                        )
                    )
                }
            }
            finalizeMutation(target.absolutePath)
            Result.success(fileModelMapper.toFileModel(target))
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            Result.failure(error)
        }
    }
}
