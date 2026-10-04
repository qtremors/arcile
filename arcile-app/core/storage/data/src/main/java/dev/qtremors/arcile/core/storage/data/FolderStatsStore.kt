package dev.qtremors.arcile.core.storage.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.data.db.ArcileDatabase
import dev.qtremors.arcile.core.storage.data.db.FolderStatsDao
import dev.qtremors.arcile.core.storage.data.db.FolderStatsEntity
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsCachePolicy
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.storage.domain.StorageWorkCoordinator
import dev.qtremors.arcile.core.runtime.logging.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

interface FolderStatsStore {
    suspend fun getCached(paths: Collection<String>): Map<String, FolderStats>
    fun observeUpdates(): Flow<FolderStatUpdate>
    fun queue(paths: List<String>)
    suspend fun invalidate(paths: Collection<String>)
    suspend fun invalidateAll() = clear()
    suspend fun clear()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class DefaultFolderStatsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val folderStatsDao: FolderStatsDao = ArcileDatabase.getInstance(context).folderStatsDao(),
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

    private val memoryCache = ConcurrentHashMap<String, FolderStats>()
    private val refreshedThisSession = ConcurrentHashMap.newKeySet<String>()
    private val queuedPaths = ConcurrentHashMap.newKeySet<String>()
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val retryCounts = ConcurrentHashMap<String, Int>()
    private val pathGenerations = ConcurrentHashMap<String, Long>()
    private val updates = MutableSharedFlow<FolderStatUpdate>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override suspend fun getCached(paths: Collection<String>): Map<String, FolderStats> = withContext(dispatchers.io) {
        if (paths.isEmpty()) return@withContext emptyMap()
        val result = LinkedHashMap<String, FolderStats>(paths.size)
        val normalizedPaths = paths.map(::normalizePath).distinct()
        val missedPaths = mutableListOf<String>()
        normalizedPaths.forEach { path ->
            memoryCache[path]?.let {
                result[path] = staleUntilRefreshed(path, it)
                return@forEach
            }
            missedPaths += path
        }

        if (missedPaths.isNotEmpty()) {
            folderStatsDao.get(missedPaths).forEach { entity ->
                val stats = staleUntilRefreshed(entity.path, entity.toDomain())
                memoryCache[entity.path] = stats
                result[entity.path] = stats
            }
        }
        result
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
                        val calculated = calculate(path)
                        // A failed refresh must not erase the last usable counts, including after restart.
                        val previous = if (calculated.status == FolderStatsStatus.Unavailable) {
                            getCached(listOf(path))[path]
                        } else null
                        val stats = previous?.takeIf { it.status != FolderStatsStatus.Unavailable }
                            ?.copy(cachedAt = 0L) ?: calculated
                        beforePublish?.invoke(path)
                        val currentGeneration = pathGenerations[path] ?: 0L

                        if (currentGeneration == generation) {
                            refreshedThisSession.add(path)
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

    override suspend fun invalidate(paths: Collection<String>) = withContext(dispatchers.io) {
        paths.map(::normalizePath).distinct().forEach { path ->
            nextGeneration(path)
            activeJobs.remove(path)?.cancel()
            queuedPaths.remove(path)
            memoryCache.computeIfPresent(path) { _, stats -> stats.copy(cachedAt = 0L) }
            runCatchingPreservingCancellation { folderStatsDao.markStale(listOf(path)) }
                .onFailure { error ->
                    AppLogger.w("FolderStatsStore", "Failed to mark folder stats stale for $path", error)
                }
        }
    }

    override suspend fun invalidateAll() = withContext(dispatchers.io) {
        (activeJobs.keys + memoryCache.keys).forEach(::nextGeneration)
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        queuedPaths.clear()
        memoryCache.replaceAll { _, stats -> stats.copy(cachedAt = 0L) }
        folderStatsDao.markAllStale()
    }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
            queuedPaths.clear()
            memoryCache.clear()
            refreshedThisSession.clear()
            retryCounts.clear()
            pathGenerations.clear()
            runCatchingPreservingCancellation { folderStatsDao.clear() }
                .onFailure { error ->
                    AppLogger.w("FolderStatsStore", "Failed to clear folder stats cache", error)
                }
        }
    }

    private fun nextGeneration(path: String): Long =
        pathGenerations.compute(path) { _, current -> (current ?: 0L) + 1L } ?: 1L

    private suspend fun calculate(path: String): FolderStats {
        return try {
            calculator(File(path))
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            AppLogger.w("FolderStatsStore", "Folder stats calculation failed for $path", e)
            FolderStats(0L, 0L, System.currentTimeMillis(), FolderStatsStatus.Unavailable)
        }
    }

    private suspend fun persist(path: String, stats: FolderStats) {
        try {
            folderStatsDao.upsert(FolderStatsEntity.from(path, stats))
            pruneIfNeeded()
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            AppLogger.w("FolderStatsStore", "Failed to persist folder stats for $path", e)
        }
    }

    private suspend fun pruneIfNeeded() {
        val count = folderStatsDao.count()
        if (count <= MAX_PERSISTED_ENTRIES) return
        val overflow = count - MAX_PERSISTED_ENTRIES
        val oldestPaths = folderStatsDao.oldestPaths(overflow)
        if (oldestPaths.isNotEmpty()) {
            folderStatsDao.delete(oldestPaths)
            oldestPaths.forEach(memoryCache::remove)
        }
    }

    private fun normalizePath(path: String): String =
        path.trimEnd('/', File.separatorChar).ifEmpty { path }

    private fun staleUntilRefreshed(path: String, stats: FolderStats): FolderStats =
        if (path !in refreshedThisSession && stats.cachedAt != 0L) {
            stats.copy(cachedAt = 0L)
        } else {
            stats
        }

    override fun close() {
        workerScope.cancel()
    }
}
