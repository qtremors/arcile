package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.FileOperationProgress
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.supportsTrash
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.NonCancellable
import java.util.UUID

class DefaultFileMutationRepository(
    private val fileSystemDataSource: FileSystemDataSource,
    private val volumeRepository: VolumeRepository,
    private val trashRepository: TrashRepository,
    private val dispatchers: ArcileDispatchers
) : FileMutationRepository {
    override suspend fun createDirectory(
        parentPath: String,
        name: String
    ): Result<FileModel> = fileSystemDataSource.createDirectory(parentPath, name)

    override suspend fun createFile(
        parentPath: String,
        name: String
    ): Result<FileModel> = fileSystemDataSource.createFile(parentPath, name)

    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<FileModel> =
        fileSystemDataSource.createFakeFile(parentPath, name, size, onProgress)

    override suspend fun deleteFile(path: String): Result<Unit> =
        withContext(dispatchers.io) {
            val volume = volumeRepository.getVolumeForPath(path).getOrNull()
                ?: return@withContext Result.failure(
                    IllegalArgumentException("Unable to resolve storage volume")
                )
            if (volume.kind.supportsTrash) {
                trashRepository.moveToTrash(listOf(path))
            } else {
                fileSystemDataSource.deletePermanently(listOf(path))
            }
        }

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> =
        fileSystemDataSource.deletePermanently(paths)

    override suspend fun deletePermanentlyDetailed(
        paths: List<String>
    ): Result<BatchMutationResult> =
        fileSystemDataSource.deletePermanentlyDetailed(paths)

    override suspend fun deletePermanentlyDetailed(
        paths: List<String>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> = runDetailedMutationWithProgress(
        paths = paths,
        mutate = { fileSystemDataSource.deletePermanentlyDetailed(listOf(it)) },
        onProgress = onProgress
    )

    override suspend fun shred(paths: List<String>): Result<Unit> =
        fileSystemDataSource.shred(paths)

    override suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> =
        fileSystemDataSource.shredDetailed(paths)

    override suspend fun shredDetailed(
        paths: List<String>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> = runDetailedMutationWithProgress(
        paths = paths,
        mutate = { fileSystemDataSource.shredDetailed(listOf(it)) },
        onProgress = onProgress
    )

    override suspend fun renameFile(
        path: String,
        newName: String
    ): Result<FileModel> = fileSystemDataSource.renameFile(path, newName)

    override suspend fun batchRenameFiles(
        renames: List<Pair<String, String>>
    ): Result<List<Pair<String, String>>> = withContext(dispatchers.io) {
        val transaction = renames.map { (originalPath, finalName) ->
            val parent = originalPath.substringBeforeLast('/', "")
            val temporaryName = ".arcile_tmp_${UUID.randomUUID()}"
            BatchRenameTransactionEntry(
                originalPath = originalPath,
                originalName = originalPath.substringAfterLast('/'),
                finalName = finalName,
                temporaryName = temporaryName,
                temporaryPath = if (parent.isEmpty()) temporaryName else "$parent/$temporaryName"
            )
        }
        try {
            for (entry in transaction) {
                entry.phase = BatchRenamePhase.STAGING
                val result = fileSystemDataSource.renameFile(entry.originalPath, entry.temporaryName)
                if (result.isFailure) {
                    entry.phase = BatchRenamePhase.ORIGINAL
                    val failure = result.exceptionOrNull()
                        ?: Exception("Failed temporary rename for ${entry.originalPath}")
                    rollbackBatchRename(transaction)
                    return@withContext Result.failure(failure)
                }
                entry.currentPath = result.getOrThrow().absolutePath
                entry.phase = BatchRenamePhase.STAGED
            }

            for (entry in transaction) {
                entry.phase = BatchRenamePhase.FINALIZING
                val result = fileSystemDataSource.renameFile(entry.currentPath, entry.finalName)
                if (result.isFailure) {
                    entry.phase = BatchRenamePhase.STAGED
                    val failure = result.exceptionOrNull()
                        ?: Exception("Failed final rename for ${entry.originalPath}")
                    rollbackBatchRename(transaction)
                    return@withContext Result.failure(failure)
                }
                entry.currentPath = result.getOrThrow().absolutePath
                entry.phase = BatchRenamePhase.FINAL
            }

            Result.success(transaction.map { it.originalPath to it.currentPath })
        } catch (e: Exception) {
            withContext(NonCancellable) {
                rollbackBatchRename(transaction)
            }
            e.rethrowIfCancellation()
            Result.failure(e)
        }
    }

    private suspend fun runDetailedMutationWithProgress(
        paths: List<String>,
        mutate: suspend (String) -> Result<BatchMutationResult>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> {
        val succeeded = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<dev.qtremors.arcile.core.storage.domain.BatchMutationFailure>()
        val cleanupRequired = mutableListOf<String>()
        paths.forEachIndexed { index, path ->
            onProgress(
                FileOperationProgress(
                    completedItems = index,
                    totalItems = paths.size,
                    currentPath = path
                )
            )
            val itemResult = mutate(path).getOrElse { return Result.failure(it) }
            succeeded += itemResult.succeededPaths
            skipped += itemResult.skippedPaths
            failed += itemResult.failedItems
            cleanupRequired += itemResult.cleanupRequiredPaths
            onProgress(
                FileOperationProgress(
                    completedItems = index + 1,
                    totalItems = paths.size,
                    currentPath = path
                )
            )
        }
        return Result.success(
            BatchMutationResult(
                succeededPaths = succeeded,
                skippedPaths = skipped,
                failedItems = failed,
                cleanupRequiredPaths = cleanupRequired
            )
        )
    }

    private suspend fun rollbackBatchRename(transaction: List<BatchRenameTransactionEntry>) {
        transaction.asReversed().forEach { entry ->
            if (entry.phase == BatchRenamePhase.FINAL) {
                fileSystemDataSource.renameFile(entry.currentPath, entry.temporaryName)
                    .onSuccess { restored ->
                        entry.currentPath = restored.absolutePath
                        entry.phase = BatchRenamePhase.STAGED
                    }
            } else if (entry.phase == BatchRenamePhase.FINALIZING) {
                val restaged = fileSystemDataSource.renameFile(entry.finalPath, entry.temporaryName)
                if (restaged.isSuccess) {
                    restaged.onSuccess { restored ->
                        entry.currentPath = restored.absolutePath
                        entry.phase = BatchRenamePhase.STAGED
                    }
                } else {
                    fileSystemDataSource.renameFile(entry.temporaryPath, entry.originalName)
                        .onSuccess { restored ->
                            entry.currentPath = restored.absolutePath
                            entry.phase = BatchRenamePhase.ORIGINAL
                        }
                }
            }
        }
        transaction.asReversed().forEach { entry ->
            if (entry.phase == BatchRenamePhase.STAGING) {
                fileSystemDataSource.renameFile(entry.temporaryPath, entry.originalName)
                entry.phase = BatchRenamePhase.ORIGINAL
            } else if (entry.phase == BatchRenamePhase.STAGED) {
                fileSystemDataSource.renameFile(entry.currentPath, entry.originalName)
                    .onSuccess { restored ->
                        entry.currentPath = restored.absolutePath
                        entry.phase = BatchRenamePhase.ORIGINAL
                    }
            }
        }
    }
}

private enum class BatchRenamePhase {
    ORIGINAL,
    STAGING,
    STAGED,
    FINALIZING,
    FINAL
}

private data class BatchRenameTransactionEntry(
    val originalPath: String,
    val originalName: String,
    val finalName: String,
    val temporaryName: String,
    val temporaryPath: String,
    val finalPath: String = originalPath.substringBeforeLast('/', "").let { parent ->
        if (parent.isEmpty()) finalName else "$parent/$finalName"
    },
    var currentPath: String = originalPath,
    var phase: BatchRenamePhase = BatchRenamePhase.ORIGINAL
)
