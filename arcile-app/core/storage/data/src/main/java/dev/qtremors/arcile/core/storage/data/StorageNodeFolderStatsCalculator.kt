package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import java.util.ArrayDeque
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Computes folder totals exclusively through the node-aware storage contract. */
internal class StorageNodeFolderStatsCalculator(
    private val fileSystemDataSource: FileSystemDataSource
) {
    suspend fun calculate(
        root: StorageNodeRef,
        now: Long = System.currentTimeMillis(),
        nodeLimit: Int = FolderStatsCalculator.DEFAULT_NODE_LIMIT
    ): FolderStats {
        require(nodeLimit > 0) { "Node limit must be positive" }
        val rootModel = fileSystemDataSource.inspectNode(root).getOrElse { error ->
            error.rethrowIfCancellation()
            return unavailable(now)
        }
        if (!rootModel.isDirectory) return unavailable(now)

        val rootChildren = fileSystemDataSource.listNodeFiles(rootModel.nodeRef).getOrElse { error ->
            error.rethrowIfCancellation()
            return unavailable(now)
        }
        val pending = ArrayDeque<FileModel>()
        rootChildren.forEach { child ->
            if (!child.isExcludedDescendantFolder()) pending.addLast(child)
        }
        val visitedDirectories = hashSetOf(rootModel.nodeRef.canonicalIdentity)
        var fileCount = 0L
        var totalBytes = 0L
        var visitedNodes = 0
        var partial = false

        while (pending.isNotEmpty()) {
            if (visitedNodes % CANCELLATION_CHECK_GRANULARITY == 0) {
                currentCoroutineContext().ensureActive()
            }
            val current = pending.removeFirst()
            visitedNodes += 1
            if (visitedNodes > nodeLimit) {
                partial = true
                break
            }

            if (!current.isDirectory) {
                fileCount = fileCount.saturatedIncrement()
                totalBytes = totalBytes.saturatedAdd(current.size.coerceAtLeast(0L))
                continue
            }

            if (!visitedDirectories.add(current.nodeRef.canonicalIdentity)) {
                partial = true
                continue
            }
            fileSystemDataSource.listNodeFiles(current.nodeRef).fold(
                onSuccess = { children ->
                    children.forEach { child ->
                        if (!child.isExcludedDescendantFolder()) pending.addLast(child)
                    }
                },
                onFailure = { error ->
                    error.rethrowIfCancellation()
                    partial = true
                }
            )
        }

        return FolderStats(
            fileCount = fileCount,
            totalBytes = totalBytes,
            cachedAt = now,
            status = if (partial) FolderStatsStatus.Partial else FolderStatsStatus.Ready
        )
    }

    private fun FileModel.isExcludedDescendantFolder(): Boolean =
        isDirectory && name in EXCLUDED_DESCENDANT_FOLDERS

    private fun Long.saturatedIncrement(): Long =
        if (this == Long.MAX_VALUE) Long.MAX_VALUE else this + 1L

    private fun Long.saturatedAdd(value: Long): Long =
        if (value > Long.MAX_VALUE - this) Long.MAX_VALUE else this + value

    private fun unavailable(now: Long): FolderStats = FolderStats(
        fileCount = 0L,
        totalBytes = 0L,
        cachedAt = now,
        status = FolderStatsStatus.Unavailable
    )

    private companion object {
        const val CANCELLATION_CHECK_GRANULARITY = 128
        val EXCLUDED_DESCENDANT_FOLDERS = setOf(".thumbnails")
    }
}
