package dev.qtremors.arcile.core.storage.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsCachePolicy
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.storage.domain.StorageWorkCoordinator
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

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
    private val calculator: suspend (File) -> FolderStats = FolderStatsCalculator::calculate,
    private val onCalculationStarted: ((String) -> Unit)? = null,
    private val beforePublish: ((String) -> Unit)? = null,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO.limitedParallelism(2)
    ),
    private val workerScope: CoroutineScope = CoroutineScope(SupervisorJob() + dispatchers.storage),
    private val storageWorkCoordinator: StorageWorkCoordinator = NoOpStorageWorkCoordinator
) : FolderStatsStore, AutoCloseable {

    companion object {
        const val FRESH_TTL_MS = FolderStatsCachePolicy.FRESH_TTL_MS
        const val FAILURE_TTL_MS = FolderStatsCachePolicy.FAILURE_TTL_MS
        private const val MAX_PERSISTED_ENTRIES = 2_000
        private const val MAX_UNAVAILABLE_RETRIES = 2
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val cacheDir = File(context.cacheDir, "folder_stats").apply { mkdirs() }
    private val memoryCache = ConcurrentHashMap<String, FolderStats>()
    private val queuedPaths = ConcurrentHashMap.newKeySet<String>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val retryCounts = ConcurrentHashMap<String, Int>()
    private val pathGenerations = ConcurrentHashMap<String, Long>()
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
                val generation = nextGeneration(path)
                activeJobs.remove(path)?.cancel()
                queuedPaths.add(path)
                val job = workerScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        storageWorkCoordinator.awaitLowPrioritySlot()
                        onCalculationStarted?.invoke(path)
                        val stats = calculate(path)
                        beforePublish?.invoke(path)
                        val currentGeneration = pathGenerations[path] ?: 0L

                        if (currentGeneration == generation) {
                            memoryCache[path] = stats
                            persist(path, stats)
                            updates.emit(FolderStatUpdate(path, stats))
                            if (stats.status == FolderStatsStatus.Unavailable) {
                                retryCounts.merge(path, 1, Int::plus)
                            } else {
                                retryCounts.remove(path)
                            }
                        }
                    } finally {
                        val currentJob = coroutineContext[Job]
                        if (currentJob != null && activeJobs.remove(path, currentJob)) {
                            queuedPaths.remove(path)
                        }
                    }
                }
                activeJobs[path] = job
                job.start()
            }
    }

    override fun invalidate(paths: Collection<String>) {
        paths.map(::normalizePath).distinct().forEach { path ->
            nextGeneration(path)
            activeJobs.remove(path)?.cancel()
            queuedPaths.remove(path)
            memoryCache.remove(path)
            val file = cacheFile(path)
            if (file.exists() && !file.delete()) {
                AppLogger.w("FolderStatsStore", "Failed to delete folder stats cache for $path")
            }
        }
    }

    private fun nextGeneration(path: String): Long =
        pathGenerations.compute(path) { _, current -> (current ?: 0L) + 1L } ?: 1L

    private suspend fun calculate(path: String): FolderStats {
        return try {
            calculator(File(path))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.w("FolderStatsStore", "Folder stats calculation failed for $path", e)
            FolderStats(0L, 0L, System.currentTimeMillis(), FolderStatsStatus.Unavailable)
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

    override fun close() {
        workerScope.cancel()
    }
}
