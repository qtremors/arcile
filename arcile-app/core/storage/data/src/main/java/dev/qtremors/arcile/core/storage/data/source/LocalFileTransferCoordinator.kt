package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileOperationException
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import kotlinx.coroutines.withContext

internal class LocalFileTransferCoordinator(
    private val dispatchers: ArcileDispatchers,
    private val conflictDetector: FileConflictDetector,
    private val transferEngine: FileTransferEngine,
    private val validateDestination: (File) -> Result<Unit>,
    private val finalizeMutation: suspend (List<String>) -> Unit
) {
    suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>> = withContext(dispatchers.io) {
        storageResult {
            val destination = validatedDirectory(destinationPath)
            conflictDetector.detectCopyConflicts(sourcePaths, destination)
        }
    }

    suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = transferFiles(
        sourcePaths = sourcePaths,
        destinationPath = destinationPath,
        transfer = { destination ->
            transferEngine.copyFiles(sourcePaths, destination, resolutions, onProgress)
        }
    )

    suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = transferFiles(
        sourcePaths = sourcePaths,
        destinationPath = destinationPath,
        transfer = { destination ->
            transferEngine.moveFiles(sourcePaths, destination, resolutions, onProgress)
        }
    )

    private suspend fun transferFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        transfer: suspend (File) -> Result<List<String>>
    ): Result<Unit> = withContext(dispatchers.io) {
        storageResult {
            val destination = validatedDirectory(destinationPath)
            val changedPaths = transfer(destination).getOrThrow()
            finalizeMutation(changedPaths)
            Unit
        }
    }

    private fun validatedDirectory(path: String): File {
        val destination = File(path)
        validateDestination(destination).getOrThrow()
        require(destination.exists() && destination.isDirectory) {
            "Destination must be a valid directory"
        }
        return destination
    }

    private inline fun <T> storageResult(block: () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: SecurityException) {
        Result.failure(FileOperationException.AccessDenied(cause = error))
    } catch (error: java.io.IOException) {
        Result.failure(FileOperationException.IOError(cause = error))
    } catch (error: Exception) {
        error.rethrowIfCancellation()
        Result.failure(FileOperationException.Unknown(cause = error))
    }
}
