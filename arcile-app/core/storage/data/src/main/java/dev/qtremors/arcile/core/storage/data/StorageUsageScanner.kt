package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageNodeKind
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanProgress
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanner
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanState
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.LinkedHashMap
import javax.inject.Inject
import kotlin.math.max

class DefaultStorageUsageScanner @Inject constructor(
    private val dispatchers: ArcileDispatchers
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

        val progress = Progress(rootPath)
        emit(StorageUsageScanState.Loading(progress.snapshot(null)))

        val rootFile = File(normalizedRoot)
        if (!rootFile.exists() || !rootFile.isDirectory) {
            emit(StorageUsageScanState.Error("Folder is no longer available"))
            return@flow
        }

        val node = scanFile(rootFile, depth = 0, limits = limits, progress = progress) { currentPath ->
            emit(StorageUsageScanState.Loading(progress.snapshot(currentPath)))
        }
        store(normalizedRoot, limits, node)
        emit(StorageUsageScanState.Loaded(node))
    }.flowOn(dispatchers.storage)

    override fun invalidateStorageUsage(paths: Collection<String>) {
        synchronized(cacheLock) {
            if (paths.isEmpty()) {
                cachedScans.clear()
                return
            }
            val normalizedPaths = paths.map { File(it).absolutePath.trimEnd(File.separatorChar) }
            cachedScans.entries.removeIf { entry ->
                normalizedPaths.any { changed ->
                    val root = entry.key.rootPath
                    changed == root || changed.startsWith("$root${File.separator}") || root.startsWith("$changed${File.separator}")
                }
            }
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

    private suspend fun scanFile(
        file: File,
        depth: Int,
        limits: StorageUsageScanLimits,
        progress: Progress,
        publishProgress: suspend (String) -> Unit
    ): StorageUsageNode {
        currentCoroutineContext().ensureActive()
        progress.scannedNodes += 1
        if (progress.scannedNodes % PROGRESS_GRANULARITY == 0) {
            publishProgress(file.absolutePath)
        }

        if (!file.isDirectory) {
            val size = safeLength(file)
            progress.scannedBytes += size
            return StorageUsageNode(
                name = file.name.ifBlank { file.absolutePath },
                path = file.absolutePath,
                sizeBytes = size,
                kind = StorageUsageNodeKind.File,
                childCount = 0
            )
        }

        if (depth >= limits.maxDepth || progress.scannedNodes >= limits.maxNodes) {
            val childCount = try {
                file.listFiles()?.count { it.name != ".thumbnails" } ?: 0
            } catch (_: Exception) {
                0
            }
            return StorageUsageNode(
                name = file.name.ifBlank { file.absolutePath },
                path = file.absolutePath,
                sizeBytes = 0L,
                kind = StorageUsageNodeKind.Folder,
                childCount = childCount,
                status = StorageUsageScanStatus.Partial
            )
        }

        val listedChildren = try {
            file.listFiles()
        } catch (_: Exception) {
            null
        }
        val children = listedChildren?.filterNot { it.name == ".thumbnails" }.orEmpty()

        if (listedChildren == null) {
            return StorageUsageNode(
                name = file.name.ifBlank { file.absolutePath },
                path = file.absolutePath,
                sizeBytes = 0L,
                kind = StorageUsageNodeKind.Folder,
                childCount = 0,
                status = StorageUsageScanStatus.Unavailable
            )
        }

        val childNodes = mutableListOf<StorageUsageNode>()
        var status = StorageUsageScanStatus.Ready
        for (child in children) {
            if (progress.scannedNodes >= limits.maxNodes) {
                status = StorageUsageScanStatus.Partial
                break
            }
            val childNode = scanFile(child, depth + 1, limits, progress, publishProgress)
            if (childNode.status != StorageUsageScanStatus.Ready) {
                status = StorageUsageScanStatus.Partial
            }
            childNodes += childNode
        }

        val sortedChildren = groupSmallChildren(
            children = childNodes.sortedByDescending { it.sizeBytes },
            parentPath = file.absolutePath,
            limits = limits
        )
        val totalBytes = childNodes.sumOf { it.sizeBytes }

        return StorageUsageNode(
            name = file.name.ifBlank { file.absolutePath },
            path = file.absolutePath,
            sizeBytes = totalBytes,
            kind = StorageUsageNodeKind.Folder,
            childCount = childNodes.size,
            status = status,
            children = sortedChildren
        )
    }

    private fun groupSmallChildren(
        children: List<StorageUsageNode>,
        parentPath: String,
        limits: StorageUsageScanLimits
    ): List<StorageUsageNode> {
        if (children.size <= limits.maxChildrenPerFolder) return children
        val total = children.sumOf { it.sizeBytes }.coerceAtLeast(1L)
        val visible = children.take(limits.maxChildrenPerFolder).filter { child ->
            child.sizeBytes.toFloat() / total.toFloat() >= limits.minChildShare
        }
        val grouped = children.drop(visible.size)
        if (grouped.isEmpty()) return visible

        return visible + StorageUsageNode(
            name = GROUPED_NODE_NAME,
            path = "$parentPath/$GROUPED_NODE_NAME",
            sizeBytes = grouped.sumOf { it.sizeBytes },
            kind = StorageUsageNodeKind.Grouped,
            childCount = grouped.sumOf { max(1, it.childCount) },
            status = if (grouped.any { it.status != StorageUsageScanStatus.Ready }) {
                StorageUsageScanStatus.Partial
            } else {
                StorageUsageScanStatus.Ready
            }
        )
    }

    private fun safeLength(file: File): Long =
        try {
            file.length().coerceAtLeast(0L)
        } catch (_: Exception) {
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
