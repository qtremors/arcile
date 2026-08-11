package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageNodeSearchLimits
import dev.qtremors.arcile.core.storage.domain.matchesSearchFilters
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.util.Locale

/** Searches only the directory explicitly supplied by the browser and never chooses a protected root. */
internal class StorageNodeSearchEngine(
    private val fileSystemDataSource: FileSystemDataSource,
    private val nanoTime: () -> Long = System::nanoTime
) {
    suspend fun search(
        query: String,
        root: StorageNodeRef,
        filters: SearchFilters?,
        limits: StorageNodeSearchLimits
    ): Result<List<FileModel>> = try {
        val rootModel = fileSystemDataSource.inspectNode(root).getOrThrow()
        require(rootModel.isDirectory) { "Search requires a folder" }
        require(rootModel.nodeRef.capabilities.canRead) { "This folder is not readable" }

        val normalizedQuery = query.trim().lowercase(Locale.ROOT)
        val activeFilters = filters ?: SearchFilters()
        val pending = ArrayDeque<DirectoryFrame>().apply {
            addLast(DirectoryFrame(rootModel.nodeRef, depth = 0))
        }
        val visitedDirectories = hashSetOf<String>()
        val results = ArrayList<FileModel>(minOf(limits.maxResults, INITIAL_RESULT_CAPACITY))
        val startedAt = nanoTime()
        var visitedEntries = 0

        while (pending.isNotEmpty() && results.size < limits.maxResults) {
            currentCoroutineContext().ensureActive()
            if (visitedEntries >= limits.maxVisitedEntries ||
                elapsedMillis(startedAt) >= limits.maxDurationMillis
            ) {
                break
            }

            val frame = pending.removeFirst()
            if (!visitedDirectories.add(frame.node.canonicalIdentity.value)) continue
            val childrenResult = fileSystemDataSource.listNodeFiles(frame.node)
            val children = childrenResult.getOrElse { error ->
                error.rethrowIfCancellation()
                if (frame.depth == 0) throw error
                emptyList()
            }
            for (child in children) {
                currentCoroutineContext().ensureActive()
                if (visitedEntries >= limits.maxVisitedEntries ||
                    results.size >= limits.maxResults ||
                    elapsedMillis(startedAt) >= limits.maxDurationMillis
                ) {
                    break
                }
                visitedEntries += 1
                val hidden = child.isHidden || child.name.startsWith('.')
                if (!hidden || activeFilters.includeHidden) {
                    if (child.matches(normalizedQuery) && child.matchesSearchFilters(activeFilters)) {
                        results += child
                    }
                    if (child.isDirectory && frame.depth < limits.maxDepth) {
                        pending.addLast(DirectoryFrame(child.nodeRef, frame.depth + 1))
                    }
                }
            }
        }
        Result.success(results.sortedWith(searchOrder(normalizedQuery)))
    } catch (error: Throwable) {
        error.rethrowIfCancellation()
        Result.failure(error)
    }

    private fun FileModel.matches(normalizedQuery: String): Boolean =
        normalizedQuery.isEmpty() || name.lowercase(Locale.ROOT).contains(normalizedQuery)

    private fun searchOrder(query: String): Comparator<FileModel> =
        compareBy<FileModel> { file ->
            val name = file.name.lowercase(Locale.ROOT)
            when {
                query.isEmpty() -> MATCH_CONTAINS
                name == query -> MATCH_EXACT
                name.startsWith(query) -> MATCH_PREFIX
                else -> MATCH_CONTAINS
            }
        }.thenByDescending(FileModel::isDirectory)
            .thenBy { it.name.lowercase(Locale.ROOT) }
            .thenBy { it.nodeRef.canonicalIdentity.value }

    private fun elapsedMillis(startedAt: Long): Long =
        ((nanoTime() - startedAt) / NANOS_PER_MILLISECOND).coerceAtLeast(0L)

    private data class DirectoryFrame(val node: StorageNodeRef, val depth: Int)

    private companion object {
        const val INITIAL_RESULT_CAPACITY = 128
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MATCH_EXACT = 0
        const val MATCH_PREFIX = 1
        const val MATCH_CONTAINS = 2
    }
}
