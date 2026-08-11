package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.privilege.MAX_DIRECTORY_PAGE_SIZE
import dev.qtremors.arcile.core.privilege.PrivilegeBackendId
import dev.qtremors.arcile.core.privilege.PrivilegedFileClient
import dev.qtremors.arcile.core.privilege.PrivilegedFileClientProvider
import dev.qtremors.arcile.core.privilege.PrivilegedFileEntry
import dev.qtremors.arcile.core.privilege.PrivilegedFileFailure
import dev.qtremors.arcile.core.privilege.PrivilegedFileHandle
import dev.qtremors.arcile.core.privilege.PrivilegedFileType
import dev.qtremors.arcile.core.privilege.PrivilegedOperationId
import dev.qtremors.arcile.core.privilege.PrivilegedOperationProgress
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.domain.BatchMutationFailure
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.isPrivileged
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext

class PrivilegedFileSystemDataSource(
    private val clientProvider: PrivilegedFileClientProvider,
    private val pathPolicy: PrivilegedPathPolicy,
    private val dispatchers: ArcileDispatchers
) : FileSystemDataSource {
    private val mapper = PrivilegedFileModelMapper(pathPolicy)

    override fun getStandardFolders(): Map<String, String?> = mapOf(
        "Storage" to "/storage",
        "Data" to "/data",
        "System" to "/system",
        "Filesystem Root" to "/"
    )

    override fun list(path: StorageNodePath, pageSize: Int): Flow<ListingPage> = flow {
        val reference = resolve(path.absolutePath).getOrElse { error ->
            emit(ListingPage.failed(path, error))
            return@flow
        }
        emitAll(list(reference, pageSize))
    }.flowOn(dispatchers.io)

    override fun list(directory: StorageNodeRef, pageSize: Int): Flow<ListingPage> = flow {
        val client = clientFor(directory).getOrElse { error ->
            emit(ListingPage.failed(directory.displayPath, error))
            return@flow
        }
        val entry = client.canonicalizeAndLstat(directory.displayPath.absolutePath).getOrElse { error ->
            emit(ListingPage.failed(directory.displayPath, error))
            return@flow
        }
        pathPolicy.validate(
            path = entry.path,
            operation = PrivilegedPathOperation.LIST,
            session = client.session,
            entry = entry
        ).getOrElse { error ->
            emit(ListingPage.failed(directory.displayPath, error))
            return@flow
        }
        if (entry.type != PrivilegedFileType.DIRECTORY) {
            emit(ListingPage.failed(directory.displayPath, IllegalArgumentException("Path is not a directory")))
            return@flow
        }
        val resolvedDirectoryRef = mapper.toFileModel(entry, client.session).nodeRef

        val boundedPageSize = pageSize.coerceIn(1, MAX_DIRECTORY_PAGE_SIZE)
        var token: String? = null
        var pageIndex = 0
        do {
            val remotePage = client.listDirectory(entry.path, token, boundedPageSize).getOrElse { error ->
                emit(ListingPage.failed(directory.displayPath, error))
                return@flow
            }
            token = remotePage.nextPageToken
            emit(
                ListingPage(
                    path = directory.displayPath,
                    files = remotePage.entries.map { mapper.toFileModel(it, client.session) },
                    pageIndex = pageIndex,
                    isComplete = token == null,
                    directoryRef = resolvedDirectoryRef
                )
            )
            pageIndex += 1
        } while (token != null)
    }.flowOn(dispatchers.io)

    override suspend fun listFiles(path: String): Result<List<FileModel>> =
        resolve(path).fold(
            onSuccess = { listNodeFiles(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun listNodeFiles(directory: StorageNodeRef): Result<List<FileModel>> =
        runResult {
            val pages = list(directory).toList()
            pages.firstNotNullOfOrNull(ListingPage::error)?.let { throw it }
            pages.flatMap(ListingPage::files)
        }

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> =
        resolve(parentPath).fold(
            onSuccess = { createNodeDirectory(it, name) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun createNodeDirectory(parent: StorageNodeRef, name: String): Result<FileModel> =
        createNode(parent, name, directory = true)

    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> =
        resolve(parentPath).fold(
            onSuccess = { createNodeFile(it, name) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun createNodeFile(parent: StorageNodeRef, name: String): Result<FileModel> =
        createNode(parent, name, directory = false)

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> =
        resolveAll(paths).fold(
            onSuccess = { deleteNodesPermanently(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun deleteNodesPermanently(nodes: Collection<StorageNodeRef>): Result<Unit> =
        deleteNodesPermanentlyDetailed(nodes).flatMap { it.requireCompleteSuccess("Delete") }

    override suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult> =
        resolveAll(paths).fold(
            onSuccess = { deleteNodesPermanentlyDetailed(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun deleteNodesPermanentlyDetailed(
        nodes: Collection<StorageNodeRef>
    ): Result<BatchMutationResult> = batchMutation(nodes) { client, entry ->
        val operation = if (entry.type == PrivilegedFileType.DIRECTORY) {
            PrivilegedPathOperation.RECURSIVE_DELETE
        } else {
            PrivilegedPathOperation.DELETE
        }
        pathPolicy.validate(entry.path, operation, client.session, entry).getOrThrow()
        if (entry.type == PrivilegedFileType.DIRECTORY) {
            client.deleteRecursively(entry.path, operationId("delete")).getOrThrow()
        } else {
            client.delete(entry.path).getOrThrow()
        }
    }

    override suspend fun shred(paths: List<String>): Result<Unit> =
        resolveAll(paths).fold(
            onSuccess = { shredNodes(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun shredNodes(nodes: Collection<StorageNodeRef>): Result<Unit> =
        shredNodesDetailed(nodes).flatMap { it.requireCompleteSuccess("Shred") }

    override suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> =
        resolveAll(paths).fold(
            onSuccess = { shredNodesDetailed(it) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun shredNodesDetailed(
        nodes: Collection<StorageNodeRef>
    ): Result<BatchMutationResult> = batchMutation(nodes) { client, entry ->
        pathPolicy.validate(
            entry.path,
            PrivilegedPathOperation.SECURE_OVERWRITE,
            client.session,
            entry
        ).getOrThrow()
        client.secureOverwrite(entry.path, operationId("shred")).getOrThrow()
        client.delete(entry.path).getOrThrow()
    }

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> =
        resolve(path).fold(
            onSuccess = { renameNode(it, newName) },
            onFailure = { Result.failure(it) }
        )

    override suspend fun renameNode(node: StorageNodeRef, newName: String): Result<FileModel> =
        runResult {
            validateName(newName)
            val client = clientFor(node).getOrThrow()
            val source = client.canonicalizeAndLstat(node.displayPath.absolutePath).getOrThrow()
            val destination = childPath(parentPath(source.path), newName)
            pathPolicy.validate(
                source.path,
                PrivilegedPathOperation.RENAME_SOURCE,
                client.session,
                source
            ).getOrThrow()
            pathPolicy.validate(
                destination,
                PrivilegedPathOperation.RENAME_DESTINATION,
                client.session
            ).getOrThrow()
            client.rename(source.path, destination).getOrThrow()
            mapper.toFileModel(
                client.canonicalizeAndLstat(destination).getOrThrow(),
                client.session
            )
        }

    override suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>> = runResult {
        val sources = resolveAll(sourcePaths).getOrThrow()
        val destination = resolve(destinationPath).getOrThrow()
        detectNodeCopyConflicts(sources, destination).getOrThrow()
    }

    override suspend fun detectNodeCopyConflicts(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef
    ): Result<List<FileConflict>> = runResult {
        val client = clientFor(destination).getOrThrow()
        sources.mapNotNull { sourceRef ->
            requireCompatibleBackend(sourceRef, destination)
            val source = client.canonicalizeAndLstat(sourceRef.displayPath.absolutePath).getOrThrow()
            val targetPath = childPath(destination.displayPath.absolutePath, source.displayName)
            val existing = client.canonicalizeAndLstat(targetPath).fold(
                onSuccess = { it },
                onFailure = { error ->
                    if (error is PrivilegedFileFailure.PathMissing) null else throw error
                }
            ) ?: return@mapNotNull null
            FileConflict(
                sourcePath = source.path,
                sourceFile = mapper.toFileModel(source, client.session),
                existingFile = mapper.toFileModel(existing, client.session)
            )
        }
    }

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = runResult {
        copyNodes(
            resolveAll(sourcePaths).getOrThrow(),
            resolve(destinationPath).getOrThrow(),
            resolutions,
            onProgress
        ).getOrThrow()
    }

    override suspend fun copyNodes(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = transfer(sources, destination, resolutions, move = false, onProgress)

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = runResult {
        moveNodes(
            resolveAll(sourcePaths).getOrThrow(),
            resolve(destinationPath).getOrThrow(),
            resolutions,
            onProgress
        ).getOrThrow()
    }

    override suspend fun moveNodes(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = transfer(sources, destination, resolutions, move = true, onProgress)

    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = resolve(parentPath).fold(
        onSuccess = { createFakeNodeFile(it, name, size, onProgress) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun createFakeNodeFile(
        parent: StorageNodeRef,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = runResult {
        require(size >= 0) { "Fake file size must not be negative" }
        val created = createNodeFile(parent, name).getOrThrow()
        val client = clientFor(created.nodeRef).getOrThrow()
        val handle = client.open(
            created.absolutePath,
            dev.qtremors.arcile.core.privilege.PrivilegedOpenMode.WRITE_TRUNCATE
        ).getOrThrow()
        try {
            val output = requireNotNull(handle.output) { "Privileged write handle has no output stream" }
            val buffer = ByteArray(32 * 1024)
            var written = 0L
            while (written < size) {
                val count = minOf(buffer.size.toLong(), size - written).toInt()
                output.write(buffer, 0, count)
                written += count
                onProgress?.invoke(
                    BulkFileOperationProgress(
                        completedItems = if (written == size) 1 else 0,
                        totalItems = 1,
                        currentPath = created.absolutePath,
                        bytesCopied = written,
                        totalBytes = size
                    )
                )
            }
            output.flush()
        } catch (error: Throwable) {
            client.delete(created.absolutePath)
            throw error
        } finally {
            handle.close()
        }
        mapper.toFileModel(
            client.canonicalizeAndLstat(created.absolutePath).getOrThrow(),
            client.session
        )
    }

    suspend fun resolve(path: String): Result<StorageNodeRef> = runResult {
        val client = clientProvider.activeClient().getOrThrow()
        val entry = client.canonicalizeAndLstat(path).getOrThrow()
        mapper.toFileModel(entry, client.session).nodeRef
    }

    private suspend fun createNode(
        parent: StorageNodeRef,
        name: String,
        directory: Boolean
    ): Result<FileModel> = runResult {
        validateName(name)
        val client = clientFor(parent).getOrThrow()
        val parentEntry = client.canonicalizeAndLstat(parent.displayPath.absolutePath).getOrThrow()
        pathPolicy.validate(
            parentEntry.path,
            if (directory) PrivilegedPathOperation.CREATE_DIRECTORY else PrivilegedPathOperation.CREATE_FILE,
            client.session,
            parentEntry
        ).getOrThrow()
        require(parentEntry.type == PrivilegedFileType.DIRECTORY) { "Parent path is not a directory" }
        val path = childPath(parentEntry.path, name)
        pathPolicy.validate(
            path,
            if (directory) PrivilegedPathOperation.CREATE_DIRECTORY else PrivilegedPathOperation.CREATE_FILE,
            client.session
        ).getOrThrow()
        val entry = if (directory) {
            client.createDirectory(path).getOrThrow()
        } else {
            client.createFile(path).getOrThrow()
        }
        mapper.toFileModel(entry, client.session)
    }

    private suspend fun transfer(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        move: Boolean,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = runResult {
        val client = clientFor(destination).getOrThrow()
        val destinationEntry = client.canonicalizeAndLstat(destination.displayPath.absolutePath).getOrThrow()
        require(destinationEntry.type == PrivilegedFileType.DIRECTORY) {
            "Destination path is not a directory"
        }
        sources.forEachIndexed { index, sourceRef ->
            requireCompatibleBackend(sourceRef, destination)
            val source = client.canonicalizeAndLstat(sourceRef.displayPath.absolutePath).getOrThrow()
            var targetPath = childPath(destinationEntry.path, source.displayName)
            val existing = client.canonicalizeAndLstat(targetPath).getOrNull()
            when (resolutions[source.path] ?: resolutions[sourceRef.displayPath.absolutePath]) {
                ConflictResolution.SKIP -> return@forEachIndexed
                ConflictResolution.KEEP_BOTH -> if (existing != null) {
                    targetPath = uniqueTarget(client, destinationEntry.path, source.displayName)
                }
                ConflictResolution.REPLACE -> if (existing != null) {
                    deleteExisting(client, existing)
                }
                null -> if (existing != null) {
                    throw PrivilegedFileFailure.PathAlreadyExists(targetPath)
                }
            }
            pathPolicy.validateTransfer(source, targetPath, move, client.session).getOrThrow()
            val operationId = operationId(if (move) "move" else "copy")
            val progress: (PrivilegedOperationProgress) -> Unit = { remote ->
                onProgress?.invoke(
                    BulkFileOperationProgress(
                        completedItems = index,
                        totalItems = sources.size,
                        currentPath = remote.currentPath ?: source.path,
                        bytesCopied = remote.completedBytes,
                        totalBytes = remote.totalBytes
                    )
                )
            }
            if (move) {
                client.move(source.path, targetPath, operationId, progress).getOrThrow()
            } else {
                val staging = childPath(
                    destinationEntry.path,
                    ".${source.displayName}.arcile-transfer-${UUID.randomUUID()}.partial"
                )
                try {
                    client.copy(source.path, staging, operationId, progress).getOrThrow()
                    client.rename(staging, targetPath).getOrThrow()
                } catch (error: Throwable) {
                    client.canonicalizeAndLstat(staging).getOrNull()?.let { deleteExisting(client, it) }
                    throw error
                }
            }
            onProgress?.invoke(
                BulkFileOperationProgress(
                    completedItems = index + 1,
                    totalItems = sources.size,
                    currentPath = source.path,
                    bytesCopied = source.size,
                    totalBytes = source.size
                )
            )
        }
    }

    private suspend fun deleteExisting(client: PrivilegedFileClient, entry: PrivilegedFileEntry) {
        val operation = if (entry.type == PrivilegedFileType.DIRECTORY) {
            PrivilegedPathOperation.RECURSIVE_DELETE
        } else {
            PrivilegedPathOperation.DELETE
        }
        pathPolicy.validate(entry.path, operation, client.session, entry).getOrThrow()
        if (entry.type == PrivilegedFileType.DIRECTORY) {
            client.deleteRecursively(entry.path, operationId("replace")).getOrThrow()
        } else {
            client.delete(entry.path).getOrThrow()
        }
    }

    private suspend fun uniqueTarget(
        client: PrivilegedFileClient,
        parent: String,
        name: String
    ): String {
        val base = name.substringBeforeLast('.', name)
        val suffix = name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" }
        for (index in 1..10_000) {
            val candidate = childPath(parent, "$base ($index)$suffix")
            val result = client.canonicalizeAndLstat(candidate)
            if (result.exceptionOrNull() is PrivilegedFileFailure.PathMissing) return candidate
            result.exceptionOrNull()?.let { throw it }
        }
        throw IllegalStateException("Unable to find an available destination name")
    }

    private suspend fun batchMutation(
        nodes: Collection<StorageNodeRef>,
        operation: suspend (PrivilegedFileClient, PrivilegedFileEntry) -> Unit
    ): Result<BatchMutationResult> = withContext(dispatchers.io) {
        val succeeded = mutableListOf<String>()
        val failures = mutableListOf<BatchMutationFailure>()
        nodes.forEach { node ->
            try {
                val client = clientFor(node).getOrThrow()
                val entry = client.canonicalizeAndLstat(node.displayPath.absolutePath).getOrThrow()
                operation(client, entry)
                succeeded += entry.path
            } catch (error: Throwable) {
                error.rethrowIfCancellation()
                failures += BatchMutationFailure(
                    path = node.displayPath.absolutePath,
                    displayName = node.displayPath.absolutePath.substringAfterLast('/'),
                    message = error.message ?: "Operation failed",
                    causeType = error::class.simpleName ?: "Unknown"
                )
            }
        }
        Result.success(BatchMutationResult(succeededPaths = succeeded, failedItems = failures))
    }

    internal suspend fun inspectNode(node: StorageNodeRef): Result<FileModel> = runResult {
        val client = clientFor(node).getOrThrow()
        val entry = client.canonicalizeAndLstat(node.displayPath.absolutePath).getOrThrow()
        mapper.toFileModel(entry, client.session)
    }

    internal suspend fun openNode(
        node: StorageNodeRef,
        mode: dev.qtremors.arcile.core.privilege.PrivilegedOpenMode
    ): Result<PrivilegedFileHandle> = runResult {
        val client = clientFor(node).getOrThrow()
        val entry = client.canonicalizeAndLstat(node.displayPath.absolutePath).getOrThrow()
        pathPolicy.validate(
            entry.path,
            if (mode == dev.qtremors.arcile.core.privilege.PrivilegedOpenMode.READ) {
                PrivilegedPathOperation.READ
            } else {
                PrivilegedPathOperation.WRITE
            },
            client.session,
            entry
        ).getOrThrow()
        client.open(entry.path, mode).getOrThrow()
    }

    internal fun clientFor(node: StorageNodeRef): Result<PrivilegedFileClient> {
        if (!node.isPrivileged) {
            return Result.failure(IllegalArgumentException("Storage node is not privileged"))
        }
        return clientProvider.activeClient().mapCatching { client ->
            val expected = when (node.backendId) {
                StorageNodeRef.ROOT_BACKEND_ID -> PrivilegeBackendId.ROOT
                StorageNodeRef.SHIZUKU_BACKEND_ID -> PrivilegeBackendId.SHIZUKU
                else -> error("Unsupported privileged backend ${node.backendId}")
            }
            check(client.session.backendId == expected) {
                "${node.backendId} is unavailable; ${client.session.backendId.value} is active"
            }
            client
        }
    }

    private suspend fun resolveAll(paths: Collection<String>): Result<List<StorageNodeRef>> = runResult {
        paths.map { resolve(it).getOrThrow() }
    }

    private fun requireCompatibleBackend(source: StorageNodeRef, destination: StorageNodeRef) {
        require(source.backendId == destination.backendId) {
            "Cross-backend transfers require descriptor streaming"
        }
    }

    private fun validateName(name: String) {
        require(name.isNotBlank()) { "Name must not be blank" }
        require('/' !in name && '\\' !in name && '\u0000' !in name) {
            "Name must not contain path separators or NUL"
        }
        require(name != "." && name != "..") { "Traversal names are not allowed" }
    }

    private fun parentPath(path: String): String =
        path.substringBeforeLast('/', missingDelimiterValue = "/").ifEmpty { "/" }

    private fun childPath(parent: String, name: String): String =
        if (parent == "/") "/$name" else "${parent.trimEnd('/')}/$name"

    private fun operationId(prefix: String): PrivilegedOperationId =
        PrivilegedOperationId.of("$prefix-${UUID.randomUUID()}")

    private suspend fun <T> runResult(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(dispatchers.io) { block() })
    } catch (error: Throwable) {
        error.rethrowIfCancellation()
        Result.failure(error)
    }
}

private inline fun <T, R> Result<T>.flatMap(transform: (T) -> Result<R>): Result<R> = fold(
    onSuccess = transform,
    onFailure = { Result.failure(it) }
)
