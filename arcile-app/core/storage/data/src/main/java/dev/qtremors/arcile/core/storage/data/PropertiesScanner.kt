package dev.qtremors.arcile.core.storage.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File

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

internal object PropertiesScanner {
    private const val CANCELLATION_CHECK_GRANULARITY = 128

    suspend fun scan(root: File): PropertiesScanResult {
        if (!root.exists()) {
            return PropertiesScanResult(
                fileCount = 0L,
                folderCount = 0L,
                totalBytes = 0L,
                hiddenCount = 0L,
                newestModifiedAt = null,
                oldestModifiedAt = null,
                selectedDirectoryUnavailable = true,
                descendantReadFailed = false
            )
        }

        if (!root.isDirectory) {
            val modified = safeLastModified(root)
            return PropertiesScanResult(
                fileCount = 1L,
                folderCount = 0L,
                totalBytes = safeLength(root),
                hiddenCount = if (root.isHiddenName()) 1L else 0L,
                newestModifiedAt = modified,
                oldestModifiedAt = modified,
                selectedDirectoryUnavailable = false,
                descendantReadFailed = false
            )
        }

        val children = listChildren(root)
            ?: return PropertiesScanResult(
                fileCount = 0L,
                folderCount = 1L,
                totalBytes = 0L,
                hiddenCount = if (root.isHiddenName()) 1L else 0L,
                newestModifiedAt = safeLastModified(root),
                oldestModifiedAt = safeLastModified(root),
                selectedDirectoryUnavailable = true,
                descendantReadFailed = false
            )

        val pending = ArrayDeque<File>()
        children.forEach(pending::addLast)

        var fileCount = 0L
        var folderCount = 1L
        var totalBytes = 0L
        var hiddenCount = if (root.isHiddenName()) 1L else 0L
        var newestModifiedAt: Long? = safeLastModified(root)
        var oldestModifiedAt: Long? = safeLastModified(root)
        var descendantReadFailed = false
        var visitedNodes = 0

        while (pending.isNotEmpty()) {
            if (visitedNodes % CANCELLATION_CHECK_GRANULARITY == 0) {
                currentCoroutineContext().ensureActive()
            }
            val current = pending.removeFirst()
            visitedNodes += 1
            val modified = safeLastModified(current)
            newestModifiedAt = maxTimestamp(newestModifiedAt, modified)
            oldestModifiedAt = minTimestamp(oldestModifiedAt, modified)
            if (current.isHiddenName()) hiddenCount += 1L

            if (!current.isDirectory) {
                fileCount += 1L
                totalBytes += safeLength(current)
                continue
            }

            folderCount += 1L
            val nestedChildren = listChildren(current)
            if (nestedChildren == null) {
                descendantReadFailed = true
            } else {
                nestedChildren.forEach(pending::addLast)
            }
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

    private fun listChildren(file: File): Array<File>? =
        try {
            file.listFiles()
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            null
        }

    private fun safeLength(file: File): Long =
        try {
            file.length().coerceAtLeast(0L)
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            0L
        }

    private fun safeLastModified(file: File): Long? =
        try {
            file.lastModified().takeIf { it > 0L }
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            null
        }

    private fun File.isHiddenName(): Boolean = name.startsWith(".") || isHidden

    private fun maxTimestamp(left: Long?, right: Long?): Long? =
        when {
            left == null -> right
            right == null -> left
            else -> maxOf(left, right)
        }

    private fun minTimestamp(left: Long?, right: Long?): Long? =
        when {
            left == null -> right
            right == null -> left
            else -> minOf(left, right)
        }
}
