package dev.qtremors.arcile.core.storage.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.IOException
import java.nio.file.Files

internal data class SourceCleanupResult(
    val remainingPaths: List<String>,
    val failure: Throwable? = null
) {
    val isComplete: Boolean
        get() = remainingPaths.isEmpty()
}

internal class SourceCleanupIncompleteException(
    val sourcePath: String,
    val destinationPath: String,
    val remainingSourcePaths: List<String>,
    cause: Throwable? = null
) : IOException(
    "The copy is safe, but ${remainingSourcePaths.size} source path(s) still need cleanup. " +
        "Destination kept at $destinationPath",
    cause
)

internal suspend fun deleteSourceTree(
    root: File,
    deleteEntry: (File) -> Boolean = { file -> file.delete() }
): SourceCleanupResult {
    if (!root.exists()) return SourceCleanupResult(emptyList())

    data class PendingEntry(val file: File, val childrenVisited: Boolean)

    val pending = ArrayDeque<PendingEntry>()
    pending.addLast(PendingEntry(root, childrenVisited = false))
    var failure: Throwable? = null

    while (pending.isNotEmpty()) {
        currentCoroutineContext().ensureActive()
        val entry = pending.removeLast()
        val file = entry.file
        if (!file.exists()) continue

        val isDirectory = file.isDirectory && !Files.isSymbolicLink(file.toPath())
        if (isDirectory && !entry.childrenVisited) {
            val children = try {
                file.listFiles()
            } catch (error: Exception) {
                error.rethrowIfCancellation()
                failure = failure ?: error
                null
            }
            if (children == null) {
                failure = failure ?: IOException("Could not enumerate source directory: ${file.absolutePath}")
                continue
            }
            pending.addLast(PendingEntry(file, childrenVisited = true))
            children.forEach { child -> pending.addLast(PendingEntry(child, childrenVisited = false)) }
            continue
        }

        try {
            if (!deleteEntry(file) && file.exists()) {
                failure = failure ?: IOException("Could not delete source path: ${file.absolutePath}")
            }
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            failure = failure ?: error
        }
    }

    return SourceCleanupResult(
        remainingPaths = collectRemainingSourcePaths(root),
        failure = failure
    )
}

private suspend fun collectRemainingSourcePaths(root: File): List<String> {
    if (!root.exists()) return emptyList()
    val remaining = mutableListOf<String>()
    val pending = ArrayDeque<File>()
    pending.addLast(root)

    while (pending.isNotEmpty() && remaining.size < MAX_REPORTED_REMAINING_PATHS) {
        currentCoroutineContext().ensureActive()
        val current = pending.removeFirst()
        if (!current.exists()) continue
        remaining += current.absolutePath
        if (current.isDirectory && !Files.isSymbolicLink(current.toPath())) {
            current.listFiles()?.forEach { child -> pending.addLast(child) }
        }
    }
    return remaining
}

private const val MAX_REPORTED_REMAINING_PATHS = 256
