package dev.qtremors.arcile.core.storage.data.manager

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.privilege.PrivilegedFileFailure
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.data.CachedFileModel
import dev.qtremors.arcile.core.storage.data.CachedStorageNodeRef
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.StorageNodeFolderStatsCalculator
import dev.qtremors.arcile.core.storage.data.rethrowIfCancellation
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.data.util.resolveVolumeForPath
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.DestinationRequiredException
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageKind
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.TrashMetadata
import dev.qtremors.arcile.core.storage.domain.TrashRestoreStatus
import dev.qtremors.arcile.core.storage.domain.TrashStorageUsage
import dev.qtremors.arcile.core.storage.domain.isPrivileged
import dev.qtremors.arcile.core.storage.domain.supportsTrash
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.util.UUID

class PrivilegedTrashController internal constructor(
    private val fileSystemDataSource: FileSystemDataSource,
    private val volumeProvider: dev.qtremors.arcile.core.storage.data.provider.VolumeProvider,
    private val mutationFinalizer: MutationFinalizer,
    private val metadataStore: PrivilegedTrashMetadataStore,
    private val dispatchers: ArcileDispatchers,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {
    suspend fun moveToTrash(
        targets: List<TrashTarget>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = withContext(dispatchers.io) {
        runResult {
            require(targets.isNotEmpty()) { "No files were selected" }
            val volumes = volumeProvider.currentVolumes()
            targets.forEachIndexed { index, target ->
                currentCoroutineContext().ensureActive()
                val node = requireNotNull(target.nodeRef) { "Privileged trash requires a storage node" }
                require(node.isPrivileged) { "Storage node is not privileged" }
                require(node.capabilities.canTrash) {
                    "Trash is unavailable for this protected location. Use permanent delete instead."
                }
                onProgress?.invoke(progress(index, targets.size, node.displayPath.absolutePath))
                val sourceFile = fileSystemDataSource.inspectNode(node).getOrThrow()
                val volume = resolveTrashVolume(sourceFile.absolutePath, volumes)
                val id = allocateId()
                val container = ensureTrashContainer(node, volume, id)
                val payload = container.child(sourceFile.name)
                val record = PrivilegedTrashRecord(
                    id = id,
                    originalNode = CachedStorageNodeRef.from(sourceFile.nodeRef),
                    payloadNode = CachedStorageNodeRef.from(payload),
                    originalFile = CachedFileModel.from(sourceFile),
                    deletionTime = clock(),
                    sourceVolumeId = volume.id,
                    sourceStorageKind = volume.kind.name
                )
                metadataStore.write(record)
                val moved = fileSystemDataSource.moveNodes(
                    sources = listOf(sourceFile.nodeRef),
                    destination = container,
                    resolutions = emptyMap<String, ConflictResolution>(),
                    onProgress = null
                )
                if (moved.isFailure) {
                    val sourcePresent = fileSystemDataSource.inspectNode(sourceFile.nodeRef).isSuccess
                    val payloadPresent = fileSystemDataSource.inspectNode(payload).isSuccess
                    when {
                        payloadPresent && !sourcePresent -> Unit
                        sourcePresent && !payloadPresent -> {
                            metadataStore.delete(id)
                            cleanupContainer(container)
                            throw moved.exceptionOrNull()
                                ?: IOException("Failed to move ${sourceFile.name} to trash")
                        }
                        else -> throw moved.exceptionOrNull()
                            ?: IOException("Trash move was interrupted; reconnect to inspect its state")
                    }
                }
                val actualPayload = fileSystemDataSource.inspectNode(payload).getOrNull()?.nodeRef ?: payload
                mutationFinalizer.finalize(sourceFile.absolutePath, actualPayload.displayPath.absolutePath)
                onProgress?.invoke(progress(index + 1, targets.size, node.displayPath.absolutePath))
            }
        }
    }

    suspend fun restore(
        externalIds: List<String>,
        destinationPath: String?
    ): Result<Unit> = withContext(dispatchers.io) {
        runResult {
            val records = externalIds.distinct().map { externalId ->
                val id = externalId.requirePrivilegedId()
                metadataStore.read(id) ?: throw IOException("Trash item is no longer available")
            }
            val destinationRequired = mutableListOf<String>()
            records.forEach { record ->
                currentCoroutineContext().ensureActive()
                val original = record.originalNode.toDomain()
                val payload = record.payloadNode.toDomain()
                val originalName = record.originalFile.name
                val parent = if (destinationPath != null) {
                    original.atPath(destinationPath)
                } else {
                    original.parent()
                }
                val parentFile = fileSystemDataSource.inspectNode(parent).getOrElse { error ->
                    error.rethrowIfCancellation()
                    if (destinationPath == null && error is PrivilegedFileFailure.PathMissing) {
                        destinationRequired += record.externalId()
                        return@forEach
                    }
                    throw error
                }
                require(parentFile.isDirectory) { "Restore destination is not a folder" }

                var currentPayload = fileSystemDataSource.inspectNode(payload).getOrThrow()
                val originalTarget = parentFile.nodeRef.child(originalName)
                val conflict = fileSystemDataSource.inspectNode(originalTarget).fold(
                    onSuccess = { true },
                    onFailure = { error ->
                        error.rethrowIfCancellation()
                        if (error is PrivilegedFileFailure.PathMissing) false else throw error
                    }
                )
                if (conflict) {
                    val conflictName = conflictName(originalName, clock())
                    currentPayload = fileSystemDataSource.renameNode(currentPayload.nodeRef, conflictName).getOrThrow()
                }
                fileSystemDataSource.moveNodes(
                    sources = listOf(currentPayload.nodeRef),
                    destination = parentFile.nodeRef,
                    resolutions = emptyMap<String, ConflictResolution>(),
                    onProgress = null
                ).getOrThrow()
                cleanupContainer(record.payloadNode.toDomain().parent())
                metadataStore.delete(record.id)
                mutationFinalizer.finalize(
                    payload.displayPath.absolutePath,
                    parentFile.absolutePath.childPath(currentPayload.name)
                )
            }
            if (destinationRequired.isNotEmpty()) {
                throw DestinationRequiredException(destinationRequired)
            }
        }
    }

    suspend fun list(): Result<List<TrashMetadata>> = withContext(dispatchers.io) {
        runResult {
            metadataStore.list().mapNotNull { record -> record.toTrashMetadata() }
                .sortedByDescending(TrashMetadata::deletionTime)
        }
    }

    suspend fun delete(externalIds: List<String>): Result<Unit> = withContext(dispatchers.io) {
        runResult {
            externalIds.distinct().forEach { externalId ->
                currentCoroutineContext().ensureActive()
                val id = externalId.requirePrivilegedId()
                val record = metadataStore.read(id) ?: return@forEach
                val container = record.payloadNode.toDomain().parent()
                fileSystemDataSource.deleteNodesPermanently(listOf(container)).getOrThrow()
                metadataStore.delete(id)
                mutationFinalizer.finalize(container.displayPath.absolutePath)
            }
        }
    }

    suspend fun empty(): Result<Unit> = delete(metadataStore.list().map { it.externalId() })

    suspend fun storageUsage(): Result<TrashStorageUsage> = withContext(dispatchers.storage) {
        runResult {
            val byVolume = linkedMapOf<String, Long>()
            val calculator = StorageNodeFolderStatsCalculator(fileSystemDataSource)
            metadataStore.list().forEach { record ->
                currentCoroutineContext().ensureActive()
                val payload = record.payloadNode.toDomain()
                val file = fileSystemDataSource.inspectNode(payload).getOrNull() ?: return@forEach
                val size = if (file.isDirectory) {
                    calculator.calculate(file.nodeRef).totalBytes
                } else {
                    file.size.coerceAtLeast(0L)
                }
                byVolume[record.sourceVolumeId] = byVolume[record.sourceVolumeId]
                    .orEmptyBytes()
                    .saturatedAdd(size)
            }
            TrashStorageUsage(
                totalBytes = byVolume.values.fold(0L) { total, value -> total.saturatedAdd(value) },
                byVolumeId = byVolume.filterValues { it > 0L }
            )
        }
    }

    private suspend fun PrivilegedTrashRecord.toTrashMetadata(): TrashMetadata? {
        val payload = payloadNode.toDomain()
        val original = originalNode.toDomain()
        val payloadResult = fileSystemDataSource.inspectNode(payload)
        val payloadFile = payloadResult.getOrNull()
        val payloadError = payloadResult.exceptionOrNull()
        payloadError?.rethrowIfCancellation()
        if (payloadFile == null && payloadError is PrivilegedFileFailure.PathMissing) {
            val originalResult = fileSystemDataSource.inspectNode(original)
            val originalError = originalResult.exceptionOrNull()
            originalError?.rethrowIfCancellation()
            if (originalResult.isSuccess || originalError is PrivilegedFileFailure.PathMissing) {
                metadataStore.delete(id)
                cleanupContainer(payload.parent())
                return null
            }
        }
        val cached = originalFile.toDomain()
        val file = payloadFile ?: cached.copy(
            absolutePath = payload.displayPath.absolutePath,
            nodeRef = payload
        )
        val status = if (payloadFile == null) {
            TrashRestoreStatus.BACKEND_UNAVAILABLE
        } else {
            restoreStatus(original)
        }
        return TrashMetadata(
            id = externalId(),
            originalPath = original.displayPath.absolutePath,
            deletionTime = deletionTime,
            fileModel = file.copy(name = cached.name, extension = cached.extension),
            sourceVolumeId = sourceVolumeId,
            sourceStorageKind = StorageKind.entries.firstOrNull { it.name == sourceStorageKind }
                ?: StorageKind.EXTERNAL_UNCLASSIFIED,
            restoreStatus = status
        )
    }

    private suspend fun restoreStatus(original: StorageNodeRef): TrashRestoreStatus {
        val originalResult = fileSystemDataSource.inspectNode(original)
        if (originalResult.isSuccess) return TrashRestoreStatus.ORIGINAL_CONFLICT_RENAME
        val error = originalResult.exceptionOrNull()
        error?.rethrowIfCancellation()
        if (error !is PrivilegedFileFailure.PathMissing) return TrashRestoreStatus.BACKEND_UNAVAILABLE
        val parentResult = fileSystemDataSource.inspectNode(original.parent())
        return when {
            parentResult.isSuccess -> TrashRestoreStatus.ORIGINAL_AVAILABLE
            parentResult.exceptionOrNull() is PrivilegedFileFailure.PathMissing ->
                TrashRestoreStatus.DESTINATION_REQUIRED
            else -> TrashRestoreStatus.BACKEND_UNAVAILABLE
        }
    }

    private fun resolveTrashVolume(path: String, volumes: List<StorageVolume>): StorageVolume {
        val volume = resolveVolumeForPath(path, volumes)
            ?: throw IllegalArgumentException("Unable to resolve storage volume")
        require(volume.kind.supportsTrash) {
            "Trash is not supported on this storage. Use permanent delete instead."
        }
        return volume
    }

    private suspend fun ensureTrashContainer(
        source: StorageNodeRef,
        volume: StorageVolume,
        id: String
    ): StorageNodeRef {
        val volumeRoot = source.atPath(volume.path)
        val arcile = ensureDirectory(volumeRoot, ARCILE_DIRECTORY)
        val trash = ensureDirectory(arcile, TRASH_DIRECTORY)
        return ensureDirectory(trash, id)
    }

    private suspend fun ensureDirectory(parent: StorageNodeRef, name: String): StorageNodeRef {
        val candidate = parent.child(name)
        val existing = fileSystemDataSource.inspectNode(candidate).getOrNull()
        if (existing != null) {
            require(existing.isDirectory) { "Trash path is not a directory" }
            return existing.nodeRef
        }
        return fileSystemDataSource.createNodeDirectory(parent, name).getOrThrow().also {
            require(it.isDirectory) { "Trash directory was not created" }
        }.nodeRef
    }

    private suspend fun cleanupContainer(container: StorageNodeRef) {
        fileSystemDataSource.deleteNodesPermanently(listOf(container))
    }

    private fun StorageNodeRef.atPath(path: String): StorageNodeRef = StorageNodeRef.privileged(
        backendId = backendId,
        displayPath = path,
        remoteCanonicalIdentity = path,
        volumeId = volumeId?.value,
        capabilities = capabilities
    )

    private fun StorageNodeRef.child(name: String): StorageNodeRef =
        atPath(displayPath.absolutePath.childPath(name))

    private fun StorageNodeRef.parent(): StorageNodeRef = atPath(
        displayPath.absolutePath.substringBeforeLast('/', missingDelimiterValue = "/").ifEmpty { "/" }
    )

    private fun String.childPath(name: String): String =
        if (this == "/") "/$name" else "${trimEnd('/')}/$name"

    private fun conflictName(name: String, timestamp: Long): String {
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: -1
        val base = if (dot >= 0) name.substring(0, dot) else name
        val extension = if (dot >= 0) name.substring(dot) else ""
        return "$base.restore-conflict-$timestamp$extension"
    }

    private fun progress(completed: Int, total: Int, path: String) = BulkFileOperationProgress(
        completedItems = completed,
        totalItems = total,
        currentPath = path
    )

    private fun PrivilegedTrashRecord.externalId(): String = "$ID_PREFIX$id"

    private fun String.requirePrivilegedId(): String {
        require(startsWith(ID_PREFIX)) { "Trash item does not belong to privileged storage" }
        return removePrefix(ID_PREFIX).also(::validateId)
    }

    private fun validateId(id: String) {
        require(id.isNotBlank() && id.none { it == '/' || it == '\\' || it == '\u0000' }) {
            "Invalid trash item id"
        }
    }

    private fun allocateId(): String {
        repeat(MAX_ID_ALLOCATION_ATTEMPTS) {
            val candidate = idFactory().also(::validateId)
            if (metadataStore.read(candidate) == null) return candidate
        }
        throw IOException("Unable to allocate a unique privileged trash id")
    }

    private suspend fun <T> runResult(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: Throwable) {
        error.rethrowIfCancellation()
        Result.failure(error)
    }

    private fun Long?.orEmptyBytes(): Long = this ?: 0L

    private fun Long.saturatedAdd(other: Long): Long =
        if (other <= 0L) this else if (Long.MAX_VALUE - this < other) Long.MAX_VALUE else this + other

    companion object {
        const val ID_PREFIX = "privileged:"
        private const val ARCILE_DIRECTORY = ".arcile"
        private const val TRASH_DIRECTORY = ".trash"
        private const val MAX_ID_ALLOCATION_ATTEMPTS = 32

        fun isPrivilegedId(id: String): Boolean = id.startsWith(ID_PREFIX)
    }
}
