package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import java.util.ArrayDeque
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal data class PropertiesScanResult(
    val fileCount: Long,
    val folderCount: Long,
    val totalBytes: Long,
    val hiddenCount: Long,
    val newestModifiedAt: Long?,
    val oldestModifiedAt: Long?,
    val selectedDirectoryUnavailable: Boolean,
    val descendantReadFailed: Boolean
)

internal data class StorageNodePropertiesScan(
    val root: FileModel,
    val aggregate: PropertiesScanResult
)

/** Iterative, backend-neutral metadata scan used by the properties workflow. */
internal class StorageNodePropertiesScanner(
    private val fileSystemDataSource: FileSystemDataSource
) {
    suspend fun scan(rootRef: StorageNodeRef): Result<StorageNodePropertiesScan> = try {
        val root = fileSystemDataSource.inspectNode(rootRef).getOrThrow()
        Result.success(
            StorageNodePropertiesScan(
                root = root,
                aggregate = if (root.isDirectory) scanDirectory(root) else scanFile(root)
            )
        )
    } catch (error: Throwable) {
        error.rethrowIfCancellation()
        Result.failure(error)
    }

    private suspend fun scanDirectory(root: FileModel): PropertiesScanResult {
        val rootModified = root.lastModified.takeIf { it > 0L }
        val initialChildren = fileSystemDataSource.listNodeFiles(root.nodeRef).getOrElse {
            it.rethrowIfCancellation()
            return PropertiesScanResult(
                fileCount = 0L,
                folderCount = 1L,
                totalBytes = 0L,
                hiddenCount = if (root.isHidden) 1L else 0L,
                newestModifiedAt = rootModified,
                oldestModifiedAt = rootModified,
                selectedDirectoryUnavailable = true,
                descendantReadFailed = false
            )
        }

        val pending = ArrayDeque<FileModel>()
        initialChildren.forEach(pending::addLast)
        val visitedDirectories = hashSetOf(root.nodeRef.canonicalIdentity)
        var fileCount = 0L
        var folderCount = 1L
        var totalBytes = 0L
        var hiddenCount = if (root.isHidden) 1L else 0L
        var newestModifiedAt = rootModified
        var oldestModifiedAt = rootModified
        var descendantReadFailed = false
        var visitedNodes = 0

        while (pending.isNotEmpty()) {
            if (visitedNodes % CANCELLATION_CHECK_GRANULARITY == 0) {
                currentCoroutineContext().ensureActive()
            }
            val current = pending.removeFirst()
            visitedNodes += 1
            val modified = current.lastModified.takeIf { it > 0L }
            newestModifiedAt = newestModifiedAt.maxTimestamp(modified)
            oldestModifiedAt = oldestModifiedAt.minTimestamp(modified)
            if (current.isHidden) hiddenCount = hiddenCount.saturatedIncrement()

            if (!current.isDirectory) {
                fileCount = fileCount.saturatedIncrement()
                totalBytes = totalBytes.saturatedAdd(current.size.coerceAtLeast(0L))
                continue
            }

            folderCount = folderCount.saturatedIncrement()
            if (!visitedDirectories.add(current.nodeRef.canonicalIdentity)) {
                descendantReadFailed = true
                continue
            }
            fileSystemDataSource.listNodeFiles(current.nodeRef).fold(
                onSuccess = { children -> children.forEach(pending::addLast) },
                onFailure = { error ->
                    error.rethrowIfCancellation()
                    descendantReadFailed = true
                }
            )
        }

        return PropertiesScanResult(
            fileCount = fileCount,
            folderCount = folderCount,
            totalBytes = totalBytes,
            hiddenCount = hiddenCount,
            newestModifiedAt = newestModifiedAt,
            oldestModifiedAt = oldestModifiedAt,
            selectedDirectoryUnavailable = false,
            descendantReadFailed = descendantReadFailed
        )
    }

    private fun scanFile(file: FileModel): PropertiesScanResult {
        val modified = file.lastModified.takeIf { it > 0L }
        return PropertiesScanResult(
            fileCount = 1L,
            folderCount = 0L,
            totalBytes = file.size.coerceAtLeast(0L),
            hiddenCount = if (file.isHidden) 1L else 0L,
            newestModifiedAt = modified,
            oldestModifiedAt = modified,
            selectedDirectoryUnavailable = false,
            descendantReadFailed = false
        )
    }

    private fun Long?.maxTimestamp(other: Long?): Long? = when {
        this == null -> other
        other == null -> this
        else -> maxOf(this, other)
    }

    private fun Long?.minTimestamp(other: Long?): Long? = when {
        this == null -> other
        other == null -> this
        else -> minOf(this, other)
    }

    private fun Long.saturatedIncrement(): Long = if (this == Long.MAX_VALUE) this else this + 1L

    private fun Long.saturatedAdd(value: Long): Long =
        if (value > Long.MAX_VALUE - this) Long.MAX_VALUE else this + value

    private companion object {
        const val CANCELLATION_CHECK_GRANULARITY = 128
    }
}
