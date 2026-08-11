package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegeCoordinator
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class BackendAwareFileSystemDataSource(
    private val local: FileSystemDataSource,
    private val privileged: PrivilegedFileSystemDataSource,
    private val privilegeCoordinator: PrivilegeCoordinator
) : FileSystemDataSource {
    override fun getStandardFolders(): Map<String, String?> =
        activeDataSource().getStandardFolders()

    override fun list(path: StorageNodePath, pageSize: Int): Flow<ListingPage> =
        activeDataSource().list(path, pageSize)

    override fun list(directory: StorageNodeRef, pageSize: Int): Flow<ListingPage> =
        dataSourceFor(directory).fold(
            onSuccess = { it.list(directory, pageSize) },
            onFailure = { flowOf(ListingPage.failed(directory.displayPath, it)) }
        )

    override suspend fun listFiles(path: String): Result<List<FileModel>> =
        activeDataSource().listFiles(path)

    override suspend fun listNodeFiles(directory: StorageNodeRef): Result<List<FileModel>> =
        withDataSource(directory) { it.listNodeFiles(directory) }

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> =
        activeDataSource().createDirectory(parentPath, name)

    override suspend fun createNodeDirectory(parent: StorageNodeRef, name: String): Result<FileModel> =
        withDataSource(parent) { it.createNodeDirectory(parent, name) }

    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> =
        activeDataSource().createFile(parentPath, name)

    override suspend fun createNodeFile(parent: StorageNodeRef, name: String): Result<FileModel> =
        withDataSource(parent) { it.createNodeFile(parent, name) }

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> =
        activeDataSource().deletePermanently(paths)

    override suspend fun deleteNodesPermanently(nodes: Collection<StorageNodeRef>): Result<Unit> =
        deleteNodesPermanentlyDetailed(nodes).flatMap { it.requireCompleteSuccess("Delete") }

    override suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult> =
        activeDataSource().deletePermanentlyDetailed(paths)

    override suspend fun deleteNodesPermanentlyDetailed(
        nodes: Collection<StorageNodeRef>
    ): Result<BatchMutationResult> = groupedMutation(nodes) { source, group ->
        source.deleteNodesPermanentlyDetailed(group)
    }

    override suspend fun shred(paths: List<String>): Result<Unit> =
        activeDataSource().shred(paths)

    override suspend fun shredNodes(nodes: Collection<StorageNodeRef>): Result<Unit> =
        shredNodesDetailed(nodes).flatMap { it.requireCompleteSuccess("Shred") }

    override suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> =
        activeDataSource().shredDetailed(paths)

    override suspend fun shredNodesDetailed(
        nodes: Collection<StorageNodeRef>
    ): Result<BatchMutationResult> = groupedMutation(nodes) { source, group ->
        source.shredNodesDetailed(group)
    }

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> =
        activeDataSource().renameFile(path, newName)

    override suspend fun renameNode(node: StorageNodeRef, newName: String): Result<FileModel> =
        withDataSource(node) { it.renameNode(node, newName) }

    override suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>> = activeDataSource().detectCopyConflicts(
        sourcePaths,
        destinationPath
    )

    override suspend fun detectNodeCopyConflicts(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef
    ): Result<List<FileConflict>> = sameBackendDataSource(sources, destination).fold(
        onSuccess = { it.detectNodeCopyConflicts(sources, destination) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = activeDataSource().copyFiles(
        sourcePaths,
        destinationPath,
        resolutions,
        onProgress
    )

    override suspend fun copyNodes(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = sameBackendDataSource(sources, destination).fold(
        onSuccess = { it.copyNodes(sources, destination, resolutions, onProgress) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = activeDataSource().moveFiles(
        sourcePaths,
        destinationPath,
        resolutions,
        onProgress
    )

    override suspend fun moveNodes(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = sameBackendDataSource(sources, destination).fold(
        onSuccess = { it.moveNodes(sources, destination, resolutions, onProgress) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = activeDataSource().createFakeFile(
        parentPath,
        name,
        size,
        onProgress
    )

    override suspend fun createFakeNodeFile(
        parent: StorageNodeRef,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = withDataSource(parent) {
        it.createFakeNodeFile(parent, name, size, onProgress)
    }

    private fun activeDataSource(): FileSystemDataSource {
        val state = privilegeCoordinator.state.value
        return if (state.isReady && state.activeBackend in PRIVILEGED_BACKENDS) {
            privileged
        } else {
            local
        }
    }

    private fun dataSourceFor(node: StorageNodeRef): Result<FileSystemDataSource> = when (node.backendId) {
        StorageNodeRef.LOCAL_BACKEND_ID,
        StorageNodeRef.MEDIA_STORE_BACKEND_ID -> Result.success(local)
        StorageNodeRef.ROOT_BACKEND_ID,
        StorageNodeRef.SHIZUKU_BACKEND_ID -> Result.success(privileged)
        StorageNodeRef.ONLYFILES_BACKEND_ID -> Result.failure(
            BackendRoutingFailure.DedicatedBackendRequired(node.backendId)
        )
        else -> Result.failure(BackendRoutingFailure.UnknownBackend(node.backendId))
    }

    private suspend fun <T> withDataSource(
        node: StorageNodeRef,
        operation: suspend (FileSystemDataSource) -> Result<T>
    ): Result<T> = dataSourceFor(node).fold(
        onSuccess = { operation(it) },
        onFailure = { Result.failure(it) }
    )

    private fun sameBackendDataSource(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef
    ): Result<FileSystemDataSource> {
        val backendIds = sources.mapTo(mutableSetOf(), StorageNodeRef::backendId)
        backendIds += destination.backendId
        if (backendIds.size != 1) {
            return Result.failure(BackendRoutingFailure.CrossBackendTransferRequired(backendIds))
        }
        return dataSourceFor(destination)
    }

    private suspend fun groupedMutation(
        nodes: Collection<StorageNodeRef>,
        operation: suspend (FileSystemDataSource, Collection<StorageNodeRef>) -> Result<BatchMutationResult>
    ): Result<BatchMutationResult> {
        val succeeded = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val failed = mutableListOf<dev.qtremors.arcile.core.storage.domain.BatchMutationFailure>()
        val cleanup = mutableListOf<String>()
        nodes.groupBy(StorageNodeRef::backendId).forEach { (_, group) ->
            val source = dataSourceFor(group.first()).getOrElse { return Result.failure(it) }
            val result = operation(source, group).getOrElse { return Result.failure(it) }
            succeeded += result.succeededPaths
            skipped += result.skippedPaths
            failed += result.failedItems
            cleanup += result.cleanupRequiredPaths
        }
        return Result.success(
            BatchMutationResult(
                succeededPaths = succeeded,
                skippedPaths = skipped,
                failedItems = failed,
                cleanupRequiredPaths = cleanup
            )
        )
    }

    private companion object {
        val PRIVILEGED_BACKENDS = setOf(PrivilegeBackendId.ROOT, PrivilegeBackendId.SHIZUKU)
    }
}

sealed class BackendRoutingFailure(message: String) : IllegalStateException(message) {
    class UnknownBackend(backendId: String) :
        BackendRoutingFailure("Unknown storage backend: $backendId")

    class DedicatedBackendRequired(backendId: String) :
        BackendRoutingFailure("Storage backend $backendId requires its dedicated repository")

    class CrossBackendTransferRequired(backendIds: Set<String>) : BackendRoutingFailure(
        "Cross-backend descriptor transfer is required for ${backendIds.sorted().joinToString()}"
    )
}

private inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = fold(
    onSuccess = transform,
    onFailure = { Result.failure(it) }
)
