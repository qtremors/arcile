package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef

interface FileSystemDataSource : DirectoryListingDataSource {
    fun getStandardFolders(): Map<String, String?>
    suspend fun listFiles(path: String): Result<List<FileModel>>
    suspend fun createDirectory(parentPath: String, name: String): Result<FileModel>
    suspend fun createFile(parentPath: String, name: String): Result<FileModel>
    suspend fun deletePermanently(paths: List<String>): Result<Unit>
    suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult>
    suspend fun shred(paths: List<String>): Result<Unit>
    suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult>
    suspend fun renameFile(path: String, newName: String): Result<FileModel>
    suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>>
    suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<FileModel>

    suspend fun inspectNode(node: StorageNodeRef): Result<FileModel> =
        Result.failure(UnsupportedOperationException("Node inspection is unavailable for ${node.backendId}"))

    suspend fun listNodeFiles(directory: StorageNodeRef): Result<List<FileModel>> =
        listFiles(directory.displayPath.absolutePath)

    suspend fun createNodeDirectory(parent: StorageNodeRef, name: String): Result<FileModel> =
        createDirectory(parent.displayPath.absolutePath, name)

    suspend fun createNodeFile(parent: StorageNodeRef, name: String): Result<FileModel> =
        createFile(parent.displayPath.absolutePath, name)

    suspend fun deleteNodesPermanently(nodes: Collection<StorageNodeRef>): Result<Unit> =
        deletePermanently(nodes.map { it.displayPath.absolutePath })

    suspend fun deleteNodesPermanentlyDetailed(
        nodes: Collection<StorageNodeRef>
    ): Result<BatchMutationResult> = deletePermanentlyDetailed(
        nodes.map { it.displayPath.absolutePath }
    )

    suspend fun shredNodes(nodes: Collection<StorageNodeRef>): Result<Unit> =
        shred(nodes.map { it.displayPath.absolutePath })

    suspend fun shredNodesDetailed(
        nodes: Collection<StorageNodeRef>
    ): Result<BatchMutationResult> = shredDetailed(
        nodes.map { it.displayPath.absolutePath }
    )

    suspend fun renameNode(node: StorageNodeRef, newName: String): Result<FileModel> =
        renameFile(node.displayPath.absolutePath, newName)

    suspend fun detectNodeCopyConflicts(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef
    ): Result<List<FileConflict>> = detectCopyConflicts(
        sourcePaths = sources.map { it.displayPath.absolutePath },
        destinationPath = destination.displayPath.absolutePath
    )

    suspend fun copyNodes(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit> = copyFiles(
        sourcePaths = sources.map { it.displayPath.absolutePath },
        destinationPath = destination.displayPath.absolutePath,
        resolutions = resolutions,
        onProgress = onProgress
    )

    suspend fun moveNodes(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit> = moveFiles(
        sourcePaths = sources.map { it.displayPath.absolutePath },
        destinationPath = destination.displayPath.absolutePath,
        resolutions = resolutions,
        onProgress = onProgress
    )

    suspend fun createFakeNodeFile(
        parent: StorageNodeRef,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<FileModel> = createFakeFile(
        parentPath = parent.displayPath.absolutePath,
        name = name,
        size = size,
        onProgress = onProgress
    )
}
