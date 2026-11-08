package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

/**
 * Counts files and bytes under a folder. Each entry costs one directory read plus one `lstat`:
 * type and size come from a single attribute read, and symbolic links are never followed, so
 * link cycles cannot occur and no canonical-path lookups are needed.
 */
internal object FolderStatsCalculator {

    const val DEFAULT_NODE_LIMIT = 100_000
    private const val CANCELLATION_CHECK_GRANULARITY = 128
    private val excludedDescendantFolders = setOf(".thumbnails")
    private val excludedRootTrees = setOf(
        "/acct",
        "/apex",
        "/config",
        "/data",
        "/dev",
        "/metadata",
        "/mnt",
        "/odm",
        "/proc",
        "/product",
        "/sys",
        "/system",
        "/vendor"
    )

    suspend fun calculate(
        root: File,
        now: Long = System.currentTimeMillis(),
        nodeLimit: Int = DEFAULT_NODE_LIMIT
    ): FolderStats {
        val path = root.path.trimEnd('/').ifEmpty { "/" }
        if (path == "/" || excludedRootTrees.any { excluded ->
                path == excluded || path.startsWith("$excluded/")
            }
        ) {
            return unavailable(now)
        }
        val rootPath = root.toPath()
        // The requested folder itself may be reached through a link (such as /sdcard); its contents are not followed.
        if (!Files.isDirectory(rootPath)) return unavailable(now)

        val pending = ArrayDeque<Path>()
        if (!enqueueChildren(rootPath, pending)) return unavailable(now)

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
            val attributes = readAttributes(current)
            when {
                attributes == null -> encounteredLimitedAccess = true
                attributes.isSymbolicLink -> Unit
                attributes.isDirectory -> {
                    if (!enqueueChildren(current, pending)) encounteredLimitedAccess = true
                }
                else -> {
                    fileCount = fileCount.saturatedIncrement()
                    totalBytes = totalBytes.saturatedAdd(attributes.size().coerceAtLeast(0L))
                }
            }
        }

        val status = if (encounteredLimitedAccess) FolderStatsStatus.Partial else FolderStatsStatus.Ready
        return FolderStats(fileCount, totalBytes, now, status)
    }

    /** Adds [directory]'s children, skipping thumbnail caches; false when the folder cannot be read. */
    private fun enqueueChildren(directory: Path, pending: ArrayDeque<Path>): Boolean = try {
        Files.newDirectoryStream(directory).use { children ->
            children.forEach { child ->
                if (child.fileName?.toString() in excludedDescendantFolders && Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)) {
                    return@forEach
                }
                pending.addLast(child)
            }
        }
        true
    } catch (error: Exception) {
        error.rethrowIfCancellation()
        false
    }

    private fun readAttributes(path: Path): BasicFileAttributes? = try {
        Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    } catch (error: Exception) {
        error.rethrowIfCancellation()
        null
    }

    private fun unavailable(now: Long) = FolderStats(0L, 0L, now, FolderStatsStatus.Unavailable)

    private fun Long.saturatedIncrement(): Long =
        if (this == Long.MAX_VALUE) Long.MAX_VALUE else this + 1L

    private fun Long.saturatedAdd(value: Long): Long =
        if (value > Long.MAX_VALUE - this) Long.MAX_VALUE else this + value
}
