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
    suspend fun deletePermanentlyDetailed(
        paths: List<String>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> = deletePermanentlyDetailed(paths).onSuccess {
        onProgress(
            FileOperationProgress(
                completedItems = paths.size,
                totalItems = paths.size,
                currentPath = paths.lastOrNull()
            )
        )
    }
    suspend fun shred(paths: List<String>): Result<Unit>
    suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> =
        shred(paths).map {
            BatchMutationResult(succeededPaths = paths)
        }
    suspend fun shredDetailed(
        paths: List<String>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> = shredDetailed(paths).onSuccess {
        onProgress(
            FileOperationProgress(
                completedItems = paths.size,
                totalItems = paths.size,
                currentPath = paths.lastOrNull()
            )
        )
    }
    suspend fun renameFile(path: String, newName: String): Result<FileModel>
    suspend fun batchRenameFiles(renames: List<Pair<String, String>>): Result<List<Pair<String, String>>> =
        Result.success(emptyList())

    suspend fun createNodeDirectory(parent: StorageNodeRef, name: String): Result<FileModel> =
        createDirectory(parent.displayPath.absolutePath, name)
    suspend fun createNodeFile(parent: StorageNodeRef, name: String): Result<FileModel> =
        createFile(parent.displayPath.absolutePath, name)
    suspend fun createFakeNodeFile(
        parent: StorageNodeRef,
        name: String,
        size: Long,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<FileModel> = createFakeFile(parent.displayPath.absolutePath, name, size, onProgress)
    suspend fun deleteNodesPermanentlyDetailed(
        nodes: List<StorageNodeRef>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> = deletePermanentlyDetailed(
        nodes.map { it.displayPath.absolutePath },
        onProgress
    )
    suspend fun shredNodesDetailed(
        nodes: List<StorageNodeRef>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> = shredDetailed(
        nodes.map { it.displayPath.absolutePath },
        onProgress
    )
    suspend fun renameNode(node: StorageNodeRef, newName: String): Result<FileModel> =
        renameFile(node.displayPath.absolutePath, newName)
}
