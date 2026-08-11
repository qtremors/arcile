package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.privilege.PrivilegedOpenMode
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.isPrivileged
import java.io.Closeable
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.ArrayDeque
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class CrossBackendTransferEngine(
    private val local: FileSystemDataSource,
    private val privileged: PrivilegedFileSystemDataSource,
    private val dispatchers: ArcileDispatchers
) {
    suspend fun detectConflicts(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef
    ): Result<List<FileConflict>> = runResult {
        requireCrossBackendInputs(sources, destination)
        val destinationModel = inspect(destination).getOrThrow()
        require(destinationModel.isDirectory) { "Destination path is not a directory" }
        val existingByName = list(destination).getOrThrow().associateBy { it.name.conflictKey() }
        sources.mapNotNull { sourceRef ->
            val source = inspect(sourceRef).getOrThrow()
            val existing = existingByName[source.name.conflictKey()] ?: return@mapNotNull null
            FileConflict(
                sourcePath = source.absolutePath,
                sourceFile = source,
                existingFile = existing
            )
        }
    }

    suspend fun copy(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit> = transfer(sources, destination, resolutions, move = false, onProgress)

    suspend fun move(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit> = transfer(sources, destination, resolutions, move = true, onProgress)

    private suspend fun transfer(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef,
        resolutions: Map<String, ConflictResolution>,
        move: Boolean,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = runResult {
        requireCrossBackendInputs(sources, destination)
        require(destination.capabilities.canWrite) { "Destination backend is read-only" }
        val destinationModel = inspect(destination).getOrThrow()
        require(destinationModel.isDirectory) { "Destination path is not a directory" }
        val existingByName = list(destination).getOrThrow()
            .associateByTo(linkedMapOf()) { it.name.conflictKey() }

        sources.forEachIndexed { index, sourceRef ->
            coroutineContext.ensureActive()
            val source = inspect(sourceRef).getOrThrow()
            require(source.nodeRef.capabilities.canCopy) {
                "${source.name} cannot be streamed across storage backends"
            }
            val existing = existingByName[source.name.conflictKey()]
            val resolution = resolutions[source.absolutePath]
                ?: resolutions[sourceRef.displayPath.absolutePath]
            if (existing != null && resolution == ConflictResolution.SKIP) {
                onProgress?.invoke(itemProgress(index + 1, sources.size, source, 0, source.size))
                return@forEachIndexed
            }
            val finalName = when {
                existing == null -> source.name
                resolution == ConflictResolution.KEEP_BOTH -> uniqueName(source.name, existingByName.keys)
                resolution == ConflictResolution.REPLACE -> source.name
                else -> throw IllegalStateException("Destination already contains ${source.name}")
            }
            val stagingName = ".${source.name}.arcile-cross-${UUID.randomUUID()}.partial"
            var staging: FileModel? = null
            var replacementBackup: FileModel? = null
            try {
                staging = copyToStaging(
                    source = source,
                    destination = destination,
                    stagingName = stagingName,
                    itemIndex = index,
                    itemCount = sources.size,
                    onProgress = onProgress
                )
                if (existing != null && resolution == ConflictResolution.REPLACE) {
                    val backupName = ".${existing.name}.arcile-cross-${UUID.randomUUID()}.backup"
                    replacementBackup = dataSourceFor(existing.nodeRef)
                        .renameNode(existing.nodeRef, backupName)
                        .getOrThrow()
                }
                val published = try {
                    dataSourceFor(staging.nodeRef)
                        .renameNode(staging.nodeRef, finalName)
                        .getOrThrow()
                } catch (publishFailure: Throwable) {
                    replacementBackup?.let { backup ->
                        dataSourceFor(backup.nodeRef).renameNode(backup.nodeRef, finalName)
                            .getOrElse { restoreFailure ->
                                publishFailure.addSuppressed(restoreFailure)
                            }
                    }
                    throw publishFailure
                }
                staging = null
                existingByName[finalName.conflictKey()] = published
                replacementBackup?.let { backup ->
                    delete(backup.nodeRef).getOrElse { cleanupFailure ->
                        throw CrossBackendReplaceCleanupFailure(
                            publishedDestination = published.nodeRef,
                            retainedBackup = backup.nodeRef,
                            cause = cleanupFailure
                        )
                    }
                    replacementBackup = null
                }

                if (move) {
                    delete(source.nodeRef).getOrElse { cleanupFailure ->
                        throw CrossBackendMoveCleanupFailure(
                            source = source.nodeRef,
                            publishedDestination = published.nodeRef,
                            cause = cleanupFailure
                        )
                    }
                }
                onProgress?.invoke(
                    itemProgress(index + 1, sources.size, source, source.size, source.size)
                )
            } catch (error: Throwable) {
                withContext(NonCancellable) {
                    staging?.let { partial -> delete(partial.nodeRef) }
                }
                error.rethrowIfCancellation()
                throw error
            }
        }
    }

    private suspend fun copyToStaging(
        source: FileModel,
        destination: StorageNodeRef,
        stagingName: String,
        itemIndex: Int,
        itemCount: Int,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): FileModel = if (source.isDirectory) {
        copyDirectoryTree(source, destination, stagingName, itemIndex, itemCount, onProgress)
    } else {
        val staging = dataSourceFor(destination)
            .createNodeFile(destination, stagingName)
            .getOrThrow()
        try {
            copyRegularFile(source, staging, itemIndex, itemCount, onProgress)
            inspect(staging.nodeRef).getOrThrow()
        } catch (error: Throwable) {
            withContext(NonCancellable) { delete(staging.nodeRef) }
            error.rethrowIfCancellation()
            throw error
        }
    }

    private suspend fun copyDirectoryTree(
        sourceRoot: FileModel,
        destination: StorageNodeRef,
        stagingName: String,
        itemIndex: Int,
        itemCount: Int,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): FileModel {
        val stagingRoot = dataSourceFor(destination)
            .createNodeDirectory(destination, stagingName)
            .getOrThrow()
        val pending = ArrayDeque<DirectoryFrame>()
        pending.addLast(DirectoryFrame(sourceRoot.nodeRef, stagingRoot.nodeRef))
        var copiedBytes = 0L
        try {
            while (pending.isNotEmpty()) {
                coroutineContext.ensureActive()
                val frame = pending.removeFirst()
                val children = list(frame.source).getOrThrow()
                children.forEach { child ->
                    coroutineContext.ensureActive()
                    require(child.nodeRef.capabilities.canCopy) {
                        "${child.name} cannot be streamed across storage backends"
                    }
                    if (child.isDirectory) {
                        val created = dataSourceFor(frame.destination)
                            .createNodeDirectory(frame.destination, child.name)
                            .getOrThrow()
                        pending.addLast(DirectoryFrame(child.nodeRef, created.nodeRef))
                    } else {
                        val created = dataSourceFor(frame.destination)
                            .createNodeFile(frame.destination, child.name)
                            .getOrThrow()
                        val copiedBeforeFile = copiedBytes
                        copyRegularFile(child, created, itemIndex, itemCount) { progress ->
                            onProgress?.invoke(
                                progress.copy(
                                    bytesCopied = copiedBeforeFile + (progress.bytesCopied ?: 0L),
                                    totalBytes = sourceRoot.size.takeIf { it > 0 }
                                )
                            )
                        }
                        copiedBytes += child.size
                    }
                }
            }
            return inspect(stagingRoot.nodeRef).getOrThrow()
        } catch (error: Throwable) {
            withContext(NonCancellable) { delete(stagingRoot.nodeRef) }
            error.rethrowIfCancellation()
            throw error
        }
    }

    private suspend fun copyRegularFile(
        source: FileModel,
        destination: FileModel,
        itemIndex: Int,
        itemCount: Int,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ) {
        require(!source.isDirectory && !destination.isDirectory)
        openInput(source.nodeRef).getOrThrow().use { input ->
            openOutput(destination.nodeRef).getOrThrow().use { output ->
                val buffer = ByteArray(COPY_BUFFER_SIZE)
                var copied = 0L
                while (true) {
                    coroutineContext.ensureActive()
                    val count = input.stream.read(buffer)
                    if (count < 0) break
                    output.stream.write(buffer, 0, count)
                    copied += count
                    onProgress?.invoke(
                        itemProgress(itemIndex, itemCount, source, copied, source.size)
                    )
                }
                output.stream.flush()
            }
        }
    }

    private suspend fun openInput(node: StorageNodeRef): Result<InputLease> = when {
        node.isPrivileged -> privileged.openNode(node, PrivilegedOpenMode.READ).mapCatching { handle ->
            InputLease(
                stream = requireNotNull(handle.input) { "Privileged read handle has no input stream" },
                owner = handle
            )
        }
        node.backendId == StorageNodeRef.LOCAL_BACKEND_ID -> runCatching {
            val stream = FileInputStream(File(node.displayPath.absolutePath))
            InputLease(stream, stream)
        }
        else -> Result.failure(UnsupportedCrossBackendNode(node.backendId))
    }

    private suspend fun openOutput(node: StorageNodeRef): Result<OutputLease> = when {
        node.isPrivileged -> privileged.openNode(node, PrivilegedOpenMode.WRITE_TRUNCATE).mapCatching { handle ->
            OutputLease(
                stream = requireNotNull(handle.output) { "Privileged write handle has no output stream" },
                owner = handle
            )
        }
        node.backendId == StorageNodeRef.LOCAL_BACKEND_ID -> runCatching {
            val stream = FileOutputStream(File(node.displayPath.absolutePath), false)
            OutputLease(stream, stream)
        }
        else -> Result.failure(UnsupportedCrossBackendNode(node.backendId))
    }

    private suspend fun inspect(node: StorageNodeRef): Result<FileModel> = when {
        node.isPrivileged -> privileged.inspectNode(node)
        node.backendId == StorageNodeRef.LOCAL_BACKEND_ID -> runResult {
            val file = File(node.displayPath.absolutePath)
            require(file.exists()) { "Path does not exist: ${file.absolutePath}" }
            LocalFileModelMapper().toFileModel(file).copy(nodeRef = node)
        }
        else -> Result.failure(UnsupportedCrossBackendNode(node.backendId))
    }

    private suspend fun list(directory: StorageNodeRef): Result<List<FileModel>> =
        dataSourceFor(directory).listNodeFiles(directory)

    private suspend fun delete(node: StorageNodeRef): Result<Unit> =
        dataSourceFor(node).deleteNodesPermanently(listOf(node))

    private fun dataSourceFor(node: StorageNodeRef): FileSystemDataSource = when {
        node.isPrivileged -> privileged
        node.backendId == StorageNodeRef.LOCAL_BACKEND_ID -> local
        else -> throw UnsupportedCrossBackendNode(node.backendId)
    }

    private fun requireCrossBackendInputs(
        sources: Collection<StorageNodeRef>,
        destination: StorageNodeRef
    ) {
        require(sources.isNotEmpty()) { "At least one source is required" }
        dataSourceFor(destination)
        sources.forEach(::dataSourceFor)
        require(sources.any { it.backendId != destination.backendId }) {
            "Cross-backend transfer requires different source and destination backends"
        }
    }

    private fun uniqueName(name: String, occupiedKeys: Set<String>): String {
        val base = name.substringBeforeLast('.', name)
        val extension = name.substringAfterLast('.', "")
            .let { if (it.isBlank() || it == name) "" else ".$it" }
        for (index in 1..10_000) {
            val candidate = "$base ($index)$extension"
            if (candidate.conflictKey() !in occupiedKeys) return candidate
        }
        throw IllegalStateException("Unable to find an available destination name")
    }

    private fun itemProgress(
        completedItems: Int,
        totalItems: Int,
        source: FileModel,
        bytesCopied: Long,
        totalBytes: Long
    ) = BulkFileOperationProgress(
        completedItems = completedItems,
        totalItems = totalItems,
        currentPath = source.absolutePath,
        bytesCopied = bytesCopied,
        totalBytes = totalBytes.takeIf { it >= 0 }
    )

    private suspend fun <T> runResult(block: suspend () -> T): Result<T> = try {
        Result.success(withContext(dispatchers.io) { block() })
    } catch (error: Throwable) {
        error.rethrowIfCancellation()
        Result.failure(error)
    }

    private fun String.conflictKey(): String = lowercase()

    private data class DirectoryFrame(
        val source: StorageNodeRef,
        val destination: StorageNodeRef
    )

    private class InputLease(
        val stream: InputStream,
        private val owner: Closeable
    ) : Closeable {
        override fun close() = owner.close()
    }

    private class OutputLease(
        val stream: OutputStream,
        private val owner: Closeable
    ) : Closeable {
        override fun close() = owner.close()
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 128 * 1024
    }
}

class UnsupportedCrossBackendNode(backendId: String) :
    IllegalArgumentException("Cross-backend transfer does not support $backendId nodes")

class CrossBackendMoveCleanupFailure(
    val source: StorageNodeRef,
    val publishedDestination: StorageNodeRef,
    cause: Throwable
) : IllegalStateException(
    "Move copied ${source.displayPath.absolutePath} but could not remove the source",
    cause
)

class CrossBackendReplaceCleanupFailure(
    val publishedDestination: StorageNodeRef,
    val retainedBackup: StorageNodeRef,
    cause: Throwable
) : IllegalStateException(
    "Replacement was published but the previous destination backup could not be removed",
    cause
)
