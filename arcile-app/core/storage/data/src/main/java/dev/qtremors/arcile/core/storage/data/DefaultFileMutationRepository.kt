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

    override suspend fun shred(paths: List<String>): Result<Unit> =
        fileSystemDataSource.shred(paths)

    override suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> =
        fileSystemDataSource.shredDetailed(paths)

    override suspend fun renameFile(
        path: String,
        newName: String
    ): Result<FileModel> = fileSystemDataSource.renameFile(path, newName)
}
