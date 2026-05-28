package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException

internal object FolderStatsCalculator {

    const val DEFAULT_NODE_LIMIT = 100_000
    private const val CANCELLATION_CHECK_GRANULARITY = 128
    private val excludedDescendantFolders = setOf(".thumbnails")

    suspend fun calculate(
        root: File,
        now: Long = System.currentTimeMillis(),
        nodeLimit: Int = DEFAULT_NODE_LIMIT
    ): FolderStats {
        if (!root.exists() || !root.isDirectory) {
            return FolderStats(0L, 0L, now, FolderStatsStatus.Unavailable)
        }

        val pending = ArrayDeque<File>()
        val rootChildren = try {
            root.listFiles() ?: return FolderStats(0L, 0L, now, FolderStatsStatus.Unavailable)
        } catch (_: Exception) {
            return FolderStats(0L, 0L, now, FolderStatsStatus.Unavailable)
        }

        rootChildren.forEach { child ->
            if (child.isDirectory && child.name in excludedDescendantFolders) return@forEach
            pending.add(child)
        }

        var fileCount = 0L
        var totalBytes = 0L
        var encounteredLimitedAccess = false
        var visitedNodes = 0

        while (pending.isNotEmpty()) {
            if (visitedNodes % CANCELLATION_CHECK_GRANULARITY == 0) {
                currentCoroutineContext().ensureActive()
            }
            val current = pending.removeFirst()
            visitedNodes += 1
            if (visitedNodes > nodeLimit) {
                return FolderStats(fileCount, totalBytes, now, FolderStatsStatus.Partial)
            }
            if (!current.isDirectory) {
                fileCount += 1L
                totalBytes += current.length()
                continue
            }

            val children = try {
                current.listFiles() ?: throw IOException("Unable to read directory: ${current.absolutePath}")
            } catch (_: Exception) {
                encounteredLimitedAccess = true
                continue
            }

            children.forEach { child ->
                if (child.isDirectory && child.name in excludedDescendantFolders) return@forEach
                pending.addLast(child)
            }
        }

        val status = when {
            encounteredLimitedAccess -> FolderStatsStatus.Partial
            else -> FolderStatsStatus.Ready
        }
        return FolderStats(fileCount, totalBytes, now, status)
    }
}
