package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.runtime.di.ApplicationScope
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanProgress
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanner
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.util.LinkedHashMap
import javax.inject.Inject
import kotlin.math.max

class DefaultStorageUsageScanner @Inject constructor(
    private val dispatchers: ArcileDispatchers,
    private val snapshotStore: StorageUsageSnapshotStore? = null,
    @param:ApplicationScope private val applicationScope: CoroutineScope? = null
) : StorageUsageScanner {
    private val cacheLock = Any()
    private val cachedScans = object : LinkedHashMap<CacheKey, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, CacheEntry>?): Boolean =
            size > MAX_CACHED_SCANS
    }

    override fun scanStorageUsage(
        rootPath: String,
        limits: StorageUsageScanLimits
    ): Flow<StorageUsageScanState> = flow {
        val normalizedRoot = File(rootPath).absolutePath
        cached(normalizedRoot, limits)?.let { cached ->
            emit(StorageUsageScanState.Loaded(cached))
            return@flow
        }
        var emittedSnapshot = false
        snapshotStore?.get(normalizedRoot, limits)?.let { cached ->
            emit(StorageUsageScanState.Loaded(cached))
            emittedSnapshot = true
        }

        val progress = Progress(rootPath)
        if (!emittedSnapshot) {
            emit(StorageUsageScanState.Loading(progress.snapshot(null)))
        }

        val rootFile = File(normalizedRoot)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            if (!emittedSnapshot) {
                emit(StorageUsageScanState.Error("Folder is no longer available"))
            }
            return@flow
        }

        val node = scanTree(rootFile, limits = limits, progress = progress) { currentPath ->
            if (!emittedSnapshot) {
                emit(StorageUsageScanState.Loading(progress.snapshot(currentPath)))
            }
        }
        store(normalizedRoot, limits, node)
        snapshotStore?.put(normalizedRoot, limits, node)
        emit(StorageUsageScanState.Loaded(node))
    }.flowOn(dispatchers.storage)

    override fun invalidateStorageUsage(paths: Collection<String>) {
        val normalizedPaths = if (paths.isEmpty()) {
            emptyList()
        } else {
            paths.map { File(it).absolutePath.trimEnd(File.separatorChar) }
        }
        synchronized(cacheLock) {
            if (paths.isEmpty()) {
                cachedScans.clear()
            } else {
                cachedScans.entries.removeIf { entry ->
                    normalizedPaths.any { changed ->
                        val root = entry.key.rootPath
                        changed == root || changed.startsWith("$root${File.separator}") || root.startsWith("$changed${File.separator}")
                    }
                }
            }
        }
        val store = snapshotStore ?: return
        applicationScope?.launch(dispatchers.io) {
            store.invalidate(normalizedPaths)
        } ?: runBlocking(dispatchers.io) {
            store.invalidate(normalizedPaths)
        }
    }

    private fun cached(rootPath: String, limits: StorageUsageScanLimits): StorageUsageNode? =
        synchronized(cacheLock) {
            cachedScans[CacheKey(rootPath, limits)]
                ?.takeIf { System.currentTimeMillis() - it.cachedAt <= CACHE_TTL_MS }
                ?.root
        }

    private fun store(rootPath: String, limits: StorageUsageScanLimits, root: StorageUsageNode) {
        synchronized(cacheLock) {
            cachedScans[CacheKey(rootPath, limits)] = CacheEntry(root, System.currentTimeMillis())
        }
    }

    private suspend fun scanTree(
        root: File,
        limits: StorageUsageScanLimits,
        progress: Progress,
        publishProgress: suspend (String) -> Unit
    ): StorageUsageNode {
        val startedAtNanos = System.nanoTime()
        val visitedDirectories = hashSetOf<String>()
        val stack = ArrayDeque<ScanFrame>().apply { addLast(ScanFrame(root, depth = 0)) }
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
                val elapsedMillis = (System.nanoTime() - startedAtNanos) / 1_000_000L
                if (progress.scannedNodes >= limits.maxVisitedNodes.coerceAtLeast(1) ||
                    elapsedMillis >= limits.maxScanDurationMillis.coerceAtLeast(1L)
                ) {
                    budgetExhausted = true
                    complete(frame.partialNode())
                    continue
                }
                progress.scannedNodes += 1
                if (progress.scannedNodes % PROGRESS_GRANULARITY == 0) {
                    publishProgress(frame.file.absolutePath)
                }
                if (!frame.file.isDirectory) {
                    val size = safeLength(frame.file)
                    progress.scannedBytes = progress.scannedBytes.saturatedAdd(size)
                    complete(frame.fileNode(size))
                    continue
                }
                val identity = directoryIdentity(frame.file)
                if (identity == null || !visitedDirectories.add(identity)) {
                    complete(frame.unavailableNode())
                    continue
                }
                val listed = try {
                    frame.file.listFiles()
                } catch (error: Exception) {
                    error.rethrowIfCancellation()
                    null
                }
                if (listed == null) {
                    complete(frame.unavailableNode())
                    continue
                }
                frame.children = listed.filterNot { it.name == ".thumbnails" }
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

    private fun directoryIdentity(file: File): String? =
        try {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
            attributes.fileKey()?.let { "key:$it" } ?: file.toPath().toRealPath().toString()
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            null
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
        val visibleFilePaths = visibleFiles.mapTo(mutableSetOf(), StorageUsageNode::path)
        val grouped = files.filterNot { it.path in visibleFilePaths }
        if (grouped.isEmpty()) return (folders + visibleFiles).sortedByDescending(StorageUsageNode::sizeBytes)

        return (folders + visibleFiles + StorageUsageNode(
            name = GROUPED_NODE_NAME,
            path = "$parentPath/$GROUPED_NODE_NAME",
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

    private fun safeLength(file: File): Long =
        try {
            file.length().coerceAtLeast(0L)
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            0L
        }

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
        val file: File,
        val depth: Int,
        var entered: Boolean = false,
        var children: List<File> = emptyList(),
        var nextChildIndex: Int = 0,
        val childNodes: MutableList<StorageUsageNode> = mutableListOf()
    ) {
        fun fileNode(size: Long) = StorageUsageNode(
            name = displayName(),
            path = file.absolutePath,
            sizeBytes = size,
            kind = StorageUsageNodeKind.File,
            childCount = 0
        )

        fun unavailableNode() = StorageUsageNode(
            name = displayName(),
            path = file.absolutePath,
            sizeBytes = 0L,
            kind = StorageUsageNodeKind.Folder,
            childCount = 0,
            status = StorageUsageScanStatus.Unavailable
        )

        fun partialNode() = StorageUsageNode(
            name = displayName(),
            path = file.absolutePath,
            sizeBytes = 0L,
            kind = if (file.isDirectory) StorageUsageNodeKind.Folder else StorageUsageNodeKind.File,
            childCount = 0,
            status = StorageUsageScanStatus.Partial
        )

        fun folderNode(limits: StorageUsageScanLimits, budgetExhausted: Boolean): StorageUsageNode {
            val retainChildren = depth < limits.maxDepth
            val totalBytes = childNodes.saturatedSizeSum()
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
                sizeBytes = totalBytes,
                kind = StorageUsageNodeKind.Folder,
                childCount = children.size,
                status = if (partial) StorageUsageScanStatus.Partial else StorageUsageScanStatus.Ready,
                children = visibleChildren
            )
        }

        private fun displayName(): String = file.name.ifBlank { file.absolutePath }
    }

    private companion object {
        const val GROUPED_NODE_NAME = "Other small items"
        const val PROGRESS_GRANULARITY = 96
        const val MAX_CACHED_SCANS = 8
        const val CACHE_TTL_MS = 10 * 60 * 1000L
    }

    private data class CacheKey(
        val rootPath: String,
        val limits: StorageUsageScanLimits
    )

    private data class CacheEntry(
        val root: StorageUsageNode,
        val cachedAt: Long
    )
}
