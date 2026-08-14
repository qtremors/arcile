package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanProgress
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlin.math.max

/** Builds a bounded usage tree without resolving a remote path in the app process. */
internal class StorageNodeUsageTreeScanner(
    private val fileSystemDataSource: FileSystemDataSource,
    private val nanoTime: () -> Long = System::nanoTime
) {
    suspend fun scan(
        root: StorageNodeRef,
        limits: StorageUsageScanLimits,
        onProgress: suspend (StorageUsageScanProgress) -> Unit
    ): StorageUsageNode {
        val rootFile = fileSystemDataSource.inspectNode(root).getOrThrow()
        require(rootFile.isDirectory) { "Storage usage requires a folder" }

        val progress = Progress(root.displayPath.absolutePath)
        val startedAtNanos = nanoTime()
        val visitedDirectories = hashSetOf<String>()
        val stack = ArrayDeque<ScanFrame>().apply {
            addLast(ScanFrame(rootFile, depth = 0))
        }
        var rootNode: StorageUsageNode? = null
        var budgetExhausted = false

        fun complete(node: StorageUsageNode) {
            stack.removeLast()
            stack.lastOrNull()?.childNodes?.add(node) ?: run { rootNode = node }
        }

        while (stack.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val frame = stack.last()
            if (!frame.entered) {
                val elapsedMillis = (nanoTime() - startedAtNanos) / NANOS_PER_MILLISECOND
                if (progress.scannedNodes >= limits.maxVisitedNodes.coerceAtLeast(1) ||
                    elapsedMillis >= limits.maxScanDurationMillis.coerceAtLeast(1L)
                ) {
                    budgetExhausted = true
                    complete(frame.partialNode())
                    continue
                }

                progress.scannedNodes += 1
                if (progress.scannedNodes % PROGRESS_GRANULARITY == 0) {
                    onProgress(progress.snapshot(frame.file.absolutePath))
                }
                if (!frame.file.isDirectory) {
                    val size = frame.file.size.coerceAtLeast(0L)
                    progress.scannedBytes = progress.scannedBytes.saturatedAdd(size)
                    complete(frame.fileNode(size))
                    continue
                }

                val identity = frame.file.nodeRef.canonicalIdentity.value
                if (!visitedDirectories.add(identity)) {
                    complete(frame.unavailableNode())
                    continue
                }
                val listed = fileSystemDataSource.listNodeFiles(frame.file.nodeRef).getOrElse { error ->
                    error.rethrowIfCancellation()
                    null
                }
                if (listed == null) {
                    complete(frame.unavailableNode())
                    continue
                }
                frame.children = listed.filterNot { it.name == THUMBNAILS_DIRECTORY }
                frame.entered = true
            }

            if (budgetExhausted || frame.nextChildIndex >= frame.children.size) {
                complete(frame.folderNode(limits, budgetExhausted))
            } else {
                val child = frame.children[frame.nextChildIndex++]
                stack.addLast(ScanFrame(child, frame.depth + 1))
            }
        }
        return requireNotNull(rootNode)
    }

    private fun groupSmallChildren(
        children: List<StorageUsageNode>,
        parentPath: String,
        limits: StorageUsageScanLimits
    ): List<StorageUsageNode> {
        if (children.size <= limits.maxChildrenPerFolder) return children
        val folders = children.filter { it.kind == StorageUsageNodeKind.Folder }
        val files = children.filterNot { it.kind == StorageUsageNodeKind.Folder }
        val availableFileSlots = (limits.maxChildrenPerFolder - folders.size).coerceAtLeast(0)
        val visibleFiles = if (limits.minChildShare > 0.0f) {
            val total = children.saturatedSizeSum().coerceAtLeast(1L)
            files.take(availableFileSlots).filter { child ->
                child.sizeBytes.toDouble() / total.toDouble() >= limits.minChildShare.toDouble()
            }
        } else {
            files.take(availableFileSlots)
        }
        val visibleIdentities = visibleFiles.mapNotNullTo(mutableSetOf()) {
            it.nodeRef?.canonicalIdentity?.value
        }
        val grouped = files.filterNot { child ->
            child.nodeRef?.canonicalIdentity?.value in visibleIdentities
        }
        if (grouped.isEmpty()) {
            return (folders + visibleFiles).sortedByDescending(StorageUsageNode::sizeBytes)
        }

        return (folders + visibleFiles + StorageUsageNode(
            name = GROUPED_NODE_NAME,
            path = parentPath.trimEnd('/', '\\') + "/" + GROUPED_NODE_NAME,
            sizeBytes = grouped.saturatedSizeSum(),
            kind = StorageUsageNodeKind.Grouped,
            childCount = grouped.saturatedChildCount(),
            status = if (grouped.any { it.status != StorageUsageScanStatus.Ready }) {
                StorageUsageScanStatus.Partial
            } else {
                StorageUsageScanStatus.Ready
            }
        )).sortedByDescending(StorageUsageNode::sizeBytes)
    }

    private fun List<StorageUsageNode>.saturatedSizeSum(): Long =
        fold(0L) { total, node -> total.saturatedAdd(node.sizeBytes) }

    private fun List<StorageUsageNode>.saturatedChildCount(): Int =
        fold(0) { total, node ->
            val contribution = max(1, node.childCount)
            if (Int.MAX_VALUE - total < contribution) Int.MAX_VALUE else total + contribution
        }

    private fun Long.saturatedAdd(value: Long): Long =
        if (value <= 0L) this else if (Long.MAX_VALUE - this < value) Long.MAX_VALUE else this + value

    private data class Progress(
        val rootPath: String,
        var scannedNodes: Int = 0,
        var scannedBytes: Long = 0L
    ) {
        fun snapshot(currentPath: String?) = StorageUsageScanProgress(
            rootPath = rootPath,
            scannedNodes = scannedNodes,
            scannedBytes = scannedBytes,
            currentPath = currentPath
        )
    }

    private inner class ScanFrame(
        val file: FileModel,
        val depth: Int,
        var entered: Boolean = false,
        var children: List<FileModel> = emptyList(),
        var nextChildIndex: Int = 0,
        val childNodes: MutableList<StorageUsageNode> = mutableListOf()
    ) {
        fun fileNode(size: Long) = StorageUsageNode(
            name = displayName(),
            path = file.absolutePath,
            sizeBytes = size,
            kind = StorageUsageNodeKind.File,
            childCount = 0,
            nodeRef = file.nodeRef
        )

        fun unavailableNode() = StorageUsageNode(
            name = displayName(),
            path = file.absolutePath,
            sizeBytes = 0L,
            kind = StorageUsageNodeKind.Folder,
            childCount = 0,
            status = StorageUsageScanStatus.Unavailable,
            nodeRef = file.nodeRef
        )

        fun partialNode() = StorageUsageNode(
            name = displayName(),
            path = file.absolutePath,
            sizeBytes = 0L,
            kind = if (file.isDirectory) StorageUsageNodeKind.Folder else StorageUsageNodeKind.File,
            childCount = 0,
            status = StorageUsageScanStatus.Partial,
            nodeRef = file.nodeRef
        )

        fun folderNode(limits: StorageUsageScanLimits, budgetExhausted: Boolean): StorageUsageNode {
            val retainChildren = depth < limits.maxDepth
            val visibleChildren = if (retainChildren) {
                groupSmallChildren(
                    children = childNodes.sortedByDescending(StorageUsageNode::sizeBytes),
                    parentPath = file.absolutePath,
                    limits = limits
                )
            } else {
                emptyList()
            }
            val partial = budgetExhausted || nextChildIndex < children.size ||
                childNodes.any { it.status != StorageUsageScanStatus.Ready } ||
                (!retainChildren && children.isNotEmpty())
            return StorageUsageNode(
                name = displayName(),
                path = file.absolutePath,
                sizeBytes = childNodes.saturatedSizeSum(),
                kind = StorageUsageNodeKind.Folder,
                childCount = children.size,
                status = if (partial) StorageUsageScanStatus.Partial else StorageUsageScanStatus.Ready,
                children = visibleChildren,
                nodeRef = file.nodeRef
            )
        }

        private fun displayName(): String = file.name.ifBlank { file.absolutePath }
    }

    private companion object {
        const val THUMBNAILS_DIRECTORY = ".thumbnails"
        const val GROUPED_NODE_NAME = "Other small items"
        const val PROGRESS_GRANULARITY = 96
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
