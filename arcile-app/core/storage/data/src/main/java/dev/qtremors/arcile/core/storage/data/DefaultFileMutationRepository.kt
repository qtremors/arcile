package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FileMutationRepository
import dev.qtremors.arcile.core.storage.domain.FileOperationProgress
import dev.qtremors.arcile.core.storage.domain.TrashRepository
import dev.qtremors.arcile.core.storage.domain.VolumeRepository
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
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

    override suspend fun createNodeDirectory(
        parent: StorageNodeRef,
        name: String
    ): Result<FileModel> = fileSystemDataSource.createNodeDirectory(parent, name)

    override suspend fun createNodeFile(
        parent: StorageNodeRef,
        name: String
    ): Result<FileModel> = fileSystemDataSource.createNodeFile(parent, name)

    override suspend fun createFakeNodeFile(
        parent: StorageNodeRef,
        name: String,
        size: Long,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<FileModel> = fileSystemDataSource.createFakeNodeFile(parent, name, size, onProgress)

    override suspend fun deleteNodesPermanentlyDetailed(
        nodes: List<StorageNodeRef>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> = runDetailedNodeMutationWithProgress(
        nodes = nodes,
        mutate = { fileSystemDataSource.deleteNodesPermanentlyDetailed(listOf(it)) },
        onProgress = onProgress
    )

    override suspend fun shredNodesDetailed(
        nodes: List<StorageNodeRef>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> = runDetailedNodeMutationWithProgress(
        nodes = nodes,
        mutate = { fileSystemDataSource.shredNodesDetailed(listOf(it)) },
        onProgress = onProgress
    )

    override suspend fun renameNode(
        node: StorageNodeRef,
        newName: String
    ): Result<FileModel> = fileSystemDataSource.renameNode(node, newName)

    override suspend fun batchRenameFiles(
        renames: List<Pair<String, String>>
    ): Result<List<Pair<String, String>>> = batchRenameNodes(
        renames.map { (path, name) -> StorageNodeRef.local(path) to name }
    ).map { completed ->
        completed.map { (original, renamed) ->
            original.displayPath.absolutePath to renamed.displayPath.absolutePath
        }
    }

    override suspend fun batchRenameNodes(
        renames: List<Pair<StorageNodeRef, String>>
    ): Result<List<Pair<StorageNodeRef, StorageNodeRef>>> = withContext(dispatchers.io) {
        val transaction = renames.map { (originalNode, finalName) ->
            NodeBatchRenameTransactionEntry(
                originalNode = originalNode,
                originalName = originalNode.displayPath.absolutePath.storageName(),
                finalName = finalName,
                temporaryName = ".arcile_tmp_${UUID.randomUUID()}"
            )
        }
        try {
            for (entry in transaction) {
                entry.phase = BatchRenamePhase.STAGING
                val result = fileSystemDataSource.renameNode(entry.originalNode, entry.temporaryName)
                if (result.isFailure) {
                    entry.phase = BatchRenamePhase.ORIGINAL
                    val failure = result.exceptionOrNull()
                        ?: Exception("Failed temporary rename for ${entry.originalPath}")
                    rollbackNodeBatchRename(transaction)
                    return@withContext Result.failure(failure)
                }
                entry.currentNode = result.getOrThrow().nodeRef
                entry.phase = BatchRenamePhase.STAGED
            }

            for (entry in transaction) {
                entry.phase = BatchRenamePhase.FINALIZING
                val result = fileSystemDataSource.renameNode(entry.currentNode, entry.finalName)
                if (result.isFailure) {
                    entry.phase = BatchRenamePhase.STAGED
                    val failure = result.exceptionOrNull()
                        ?: Exception("Failed final rename for ${entry.originalPath}")
                    rollbackNodeBatchRename(transaction)
                    return@withContext Result.failure(failure)
                }
                entry.currentNode = result.getOrThrow().nodeRef
                entry.phase = BatchRenamePhase.FINAL
            }

            Result.success(transaction.map { it.originalNode to it.currentNode })
        } catch (e: Exception) {
            withContext(NonCancellable) {
                rollbackNodeBatchRename(transaction)
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

    private suspend fun runDetailedNodeMutationWithProgress(
        nodes: List<StorageNodeRef>,
        mutate: suspend (StorageNodeRef) -> Result<BatchMutationResult>,
        onProgress: (FileOperationProgress) -> Unit
    ): Result<BatchMutationResult> {
        val succeeded = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<dev.qtremors.arcile.core.storage.domain.BatchMutationFailure>()
        val cleanupRequired = mutableListOf<String>()
        nodes.forEachIndexed { index, node ->
            val path = node.displayPath.absolutePath
            onProgress(
                FileOperationProgress(
                    completedItems = index,
                    totalItems = nodes.size,
                    currentPath = path
                )
            )
            val itemResult = mutate(node).getOrElse { return Result.failure(it) }
            succeeded += itemResult.succeededPaths
            skipped += itemResult.skippedPaths
            failed += itemResult.failedItems
            cleanupRequired += itemResult.cleanupRequiredPaths
            onProgress(
                FileOperationProgress(
                    completedItems = index + 1,
                    totalItems = nodes.size,
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

    private suspend fun rollbackNodeBatchRename(
        transaction: List<NodeBatchRenameTransactionEntry>
    ) {
        transaction.asReversed().forEach { entry ->
            if (entry.phase == BatchRenamePhase.FINAL) {
                fileSystemDataSource.renameNode(entry.currentNode, entry.temporaryName)
                    .onSuccess { restored ->
                        entry.currentNode = restored.nodeRef
                        entry.phase = BatchRenamePhase.STAGED
                    }
            } else if (entry.phase == BatchRenamePhase.FINALIZING) {
                val restaged = fileSystemDataSource.renameNode(
                    entry.finalNode,
                    entry.temporaryName
                )
                if (restaged.isSuccess) {
                    restaged.onSuccess { restored ->
                        entry.currentNode = restored.nodeRef
                        entry.phase = BatchRenamePhase.STAGED
                    }
                } else {
                    fileSystemDataSource.renameNode(entry.temporaryNode, entry.originalName)
                        .onSuccess { restored ->
                            entry.currentNode = restored.nodeRef
                            entry.phase = BatchRenamePhase.ORIGINAL
                        }
                }
            }
        }
        transaction.asReversed().forEach { entry ->
            if (entry.phase == BatchRenamePhase.STAGING) {
                fileSystemDataSource.renameNode(entry.temporaryNode, entry.originalName)
                    .onSuccess { restored ->
                        entry.currentNode = restored.nodeRef
                        entry.phase = BatchRenamePhase.ORIGINAL
                    }
            } else if (entry.phase == BatchRenamePhase.STAGED) {
                fileSystemDataSource.renameNode(entry.currentNode, entry.originalName)
                    .onSuccess { restored ->
                        entry.currentNode = restored.nodeRef
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

private data class NodeBatchRenameTransactionEntry(
    val originalNode: StorageNodeRef,
    val originalName: String,
    val finalName: String,
    val temporaryName: String,
    var currentNode: StorageNodeRef = originalNode,
    var phase: BatchRenamePhase = BatchRenamePhase.ORIGINAL
) {
    val originalPath: String get() = originalNode.displayPath.absolutePath
    val temporaryNode: StorageNodeRef get() = originalNode.anticipatedSibling(temporaryName)
    val finalNode: StorageNodeRef get() = originalNode.anticipatedSibling(finalName)
}

private fun String.storageName(): String =
    trimEnd('/', '\\').substringAfterLast('/').substringAfterLast('\\')

private fun StorageNodeRef.anticipatedSibling(name: String): StorageNodeRef {
    val path = displayPath.absolutePath
    val separatorIndex = maxOf(path.lastIndexOf('/'), path.lastIndexOf('\\'))
    val renamedPath = if (separatorIndex < 0) name else path.substring(0, separatorIndex + 1) + name
    return when (backendId) {
        StorageNodeRef.ROOT_BACKEND_ID,
        StorageNodeRef.SHIZUKU_BACKEND_ID -> StorageNodeRef.privileged(
            backendId = backendId,
            displayPath = renamedPath,
            remoteCanonicalIdentity = backendIdentity
                ?.let { identity ->
                    val identitySeparator = maxOf(identity.lastIndexOf('/'), identity.lastIndexOf('\\'))
                    if (identitySeparator < 0) name
                    else identity.substring(0, identitySeparator + 1) + name
                }
                ?: renamedPath,
            volumeId = volumeId?.value,
            capabilities = capabilities
        )
        else -> StorageNodeRef.local(
            path = renamedPath,
            volumeId = volumeId?.value,
            capabilities = capabilities
        )
    }
}
