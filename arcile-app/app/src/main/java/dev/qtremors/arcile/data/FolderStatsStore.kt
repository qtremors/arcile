package dev.qtremors.arcile.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.domain.FolderStatUpdate
import dev.qtremors.arcile.domain.FolderStats
import dev.qtremors.arcile.domain.FolderStatsStatus
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface FolderStatsStore {
    suspend fun getCached(paths: Collection<String>): Map<String, FolderStats>
    fun observeUpdates(): Flow<FolderStatUpdate>
    fun queue(paths: List<String>)
    fun invalidate(paths: Collection<String>)
}

@Serializable
private data class FolderStatsCacheEntity(
    val path: String,
    val fileCount: Long,
    val totalBytes: Long,
    val cachedAt: Long,
    val status: String
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class DefaultFolderStatsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val onCalculationStarted: ((String) -> Unit)? = null,
    private val beforePublish: ((String) -> Unit)? = null
) : FolderStatsStore {

    companion object {
        const val FRESH_TTL_MS = 30L * 60L * 1000L
        const val FAILURE_TTL_MS = 5L * 60L * 1000L
        private const val MAX_PERSISTED_ENTRIES = 2_000
        private val EXCLUDED_DESCENDANT_FOLDERS = setOf(".thumbnails")
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir = File(context.cacheDir, "folder_stats").apply { mkdirs() }
    private val memoryCache = ConcurrentHashMap<String, FolderStats>()
    private val queuedPaths = ConcurrentHashMap.newKeySet<String>()
    private val rerunRequested = ConcurrentHashMap.newKeySet<String>()
    private val pathGenerations = ConcurrentHashMap<String, Long>()
    private val workerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(2))
    private val updates = MutableSharedFlow<FolderStatUpdate>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override suspend fun getCached(paths: Collection<String>): Map<String, FolderStats> {
        if (paths.isEmpty()) return emptyMap()
        val result = LinkedHashMap<String, FolderStats>(paths.size)
        paths.forEach { rawPath ->
            val path = normalizePath(rawPath)
            memoryCache[path]?.let {
                result[path] = it
                return@forEach
            }

            val stats = readFromDisk(path) ?: return@forEach
            memoryCache[path] = stats
            result[path] = stats
        }
        return result
    }

    override fun observeUpdates(): Flow<FolderStatUpdate> = updates.asSharedFlow()

    override fun queue(paths: List<String>) {
        paths
            .map(::normalizePath)
            .distinct()
            .forEach { path ->
                if (!queuedPaths.add(path)) {
                    rerunRequested.add(path)
                    return@forEach
                }
                workerScope.launch {
                    try {
                        do {
                            rerunRequested.remove(path)
                            val generationAtStart = pathGenerations[path] ?: 0L
                            onCalculationStarted?.invoke(path)
                            val stats = calculate(path)
                            beforePublish?.invoke(path)
                            val currentGeneration = pathGenerations[path] ?: 0L

                            if (currentGeneration == generationAtStart) {
                                memoryCache[path] = stats
                                persist(path, stats)
                                updates.emit(FolderStatUpdate(path, stats))
                            }
                        } while (rerunRequested.remove(path))
                    } finally {
                        queuedPaths.remove(path)
                        if (rerunRequested.remove(path)) {
                            queue(listOf(path))
                        }
                    }
                }
            }
    }

    override fun invalidate(paths: Collection<String>) {
        paths.map(::normalizePath).distinct().forEach { path ->
            pathGenerations.compute(path) { _, current -> (current ?: 0L) + 1L }
            rerunRequested.add(path)
            memoryCache.remove(path)
            val file = cacheFile(path)
            if (file.exists() && !file.delete()) {
                AppLogger.w("FolderStatsStore", "Failed to delete folder stats cache for $path")
            }
        }
    }

    private fun calculate(path: String): FolderStats {
        val root = File(path)
        val now = System.currentTimeMillis()
        return try {
            if (!root.exists() || !root.isDirectory) {
                FolderStats(0L, 0L, now, FolderStatsStatus.Unavailable)
            } else {
                var fileCount = 0L
                var totalBytes = 0L
                val pending = ArrayDeque<File>()
                pending.add(root)

                while (pending.isNotEmpty()) {
                    val current = pending.removeFirst()
                    val children = current.listFiles()
                        ?: throw IOException("Unable to read directory: ${current.absolutePath}")
                    children.forEach { child ->
                        if (child.isDirectory) {
                            if (child.name in EXCLUDED_DESCENDANT_FOLDERS) {
                                return@forEach
                            }
                            pending.addLast(child)
                        } else {
                            fileCount += 1L
                            totalBytes += child.length()
                        }
                    }
                }

                FolderStats(fileCount, totalBytes, now, FolderStatsStatus.Ready)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.w("FolderStatsStore", "Folder stats calculation failed for $path", e)
            FolderStats(0L, 0L, now, FolderStatsStatus.Unavailable)
        }
    }

    private fun persist(path: String, stats: FolderStats) {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val file = cacheFile(path)
            file.writeText(
                json.encodeToString(
                    FolderStatsCacheEntity(
                        path = path,
                        fileCount = stats.fileCount,
                        totalBytes = stats.totalBytes,
                        cachedAt = stats.cachedAt,
                        status = stats.status.name
                    )
                )
            )
            pruneIfNeeded()
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.w("FolderStatsStore", "Failed to persist folder stats for $path", e)
        }
    }

    private fun readFromDisk(path: String): FolderStats? {
        val file = cacheFile(path)
        if (!file.exists()) return null
        return try {
            val entity = json.decodeFromString<FolderStatsCacheEntity>(file.readText())
            if (normalizePath(entity.path) != path) {
                file.delete()
                null
            } else {
                FolderStats(
                    fileCount = entity.fileCount,
                    totalBytes = entity.totalBytes,
                    cachedAt = entity.cachedAt,
                    status = FolderStatsStatus.entries.find { it.name == entity.status } ?: FolderStatsStatus.Unavailable
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            file.delete()
            null
        }
    }

    private fun pruneIfNeeded() {
        val files = cacheDir.listFiles()?.filter { it.extension == "json" } ?: return
        if (files.size <= MAX_PERSISTED_ENTRIES) return

        files.sortedBy { it.lastModified() }
            .take(files.size - MAX_PERSISTED_ENTRIES)
            .forEach { file ->
                if (!file.delete()) {
                    AppLogger.w("FolderStatsStore", "Failed to prune old folder stats cache ${file.name}")
                }
            }
    }

    private fun cacheFile(path: String): File =
        File(cacheDir, "${hashPath(path)}.json")

    private fun hashPath(path: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(path.toByteArray(Charsets.UTF_8))
        return digest.joinToString(separator = "") { "%02x".format(it) }
    }

    private fun normalizePath(path: String): String =
        path.trimEnd('/', File.separatorChar).ifEmpty { path }
}
