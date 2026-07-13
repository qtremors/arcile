package dev.qtremors.arcile.core.storage.domain

interface FileMutationRepository {
    suspend fun createDirectory(parentPath: String, name: String): Result<FileModel>
    suspend fun createFile(parentPath: String, name: String): Result<FileModel>
    suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<FileModel>
    suspend fun deleteFile(path: String): Result<Unit>
    suspend fun deletePermanently(paths: List<String>): Result<Unit>
    suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult> =
        deletePermanently(paths).map {
            BatchMutationResult(succeededPaths = paths)
        }
    suspend fun shred(paths: List<String>): Result<Unit>
    suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> =
        shred(paths).map {
            BatchMutationResult(succeededPaths = paths)
        }
    suspend fun renameFile(path: String, newName: String): Result<FileModel>
}
