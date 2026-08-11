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
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
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
    suspend fun getCachedNodes(nodes: Collection<StorageNodeRef>): Map<String, FolderStats> =
        getCached(nodes.map { it.displayPath.absolutePath })
    fun observeUpdates(): Flow<FolderStatUpdate>
    fun queue(paths: List<String>)
    fun queueNodes(nodes: List<StorageNodeRef>) =
        queue(nodes.map { it.displayPath.absolutePath })
    suspend fun invalidate(paths: Collection<String>)
    suspend fun invalidateNodes(nodes: Collection<StorageNodeRef>) =
        invalidate(nodes.map { it.displayPath.absolutePath })
    suspend fun clear()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class DefaultFolderStatsStore @Inject constructor(
    @ApplicationContext context: Context,
    private val folderStatsDao: FolderStatsDao = ArcileDatabase.getInstance(context).folderStatsDao(),
    private val calculator: suspend (File) -> FolderStats = FolderStatsCalculator::calculate,
    private val nodeCalculator: (suspend (StorageNodeRef) -> FolderStats)? = null,
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
        getCachedTargets(paths.map { path -> localTarget(path) })
    }

    override suspend fun getCachedNodes(
        nodes: Collection<StorageNodeRef>
    ): Map<String, FolderStats> = withContext(dispatchers.io) {
        getCachedTargets(nodes.map(::nodeTarget))
    }

    private suspend fun getCachedTargets(
        targets: Collection<FolderStatsTarget>
    ): Map<String, FolderStats> {
        if (targets.isEmpty()) return emptyMap()
        val distinctTargets = targets.distinctBy(FolderStatsTarget::cacheKey)
        val statsByKey = LinkedHashMap<String, FolderStats>(distinctTargets.size)
        val missedKeys = mutableListOf<String>()
        distinctTargets.forEach { target ->
            memoryCache[target.cacheKey]?.let { cached ->
                statsByKey[target.cacheKey] = cached
            } ?: run {
                missedKeys += target.cacheKey
            }
        }

        if (missedKeys.isNotEmpty()) {
            folderStatsDao.get(missedKeys).forEach { entity ->
                val stats = entity.toDomain()
                memoryCache[entity.path] = stats
                statsByKey[entity.path] = stats
            }
        }
        return distinctTargets.mapNotNull { target ->
            statsByKey[target.cacheKey]?.let { target.displayPath to it }
        }.toMap(LinkedHashMap())
    }

    override fun observeUpdates(): Flow<FolderStatUpdate> = updates.asSharedFlow()

    override fun queue(paths: List<String>) {
        queueTargets(paths.map(::localTarget))
    }

    override fun queueNodes(nodes: List<StorageNodeRef>) {
        queueTargets(nodes.map(::nodeTarget))
    }

    private fun queueTargets(targets: List<FolderStatsTarget>) {
        targets.distinctBy(FolderStatsTarget::cacheKey).forEach { target ->
            val cacheKey = target.cacheKey
            val generation = nextGeneration(cacheKey)
            activeJobs.remove(cacheKey)?.cancel()
            queuedPaths.add(cacheKey)
            val job = workerScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        storageWorkCoordinator.awaitLowPrioritySlot()
                        onCalculationStarted?.invoke(target.displayPath)
                        val stats = calculate(target)
                        beforePublish?.invoke(target.displayPath)
                        val currentGeneration = pathGenerations[cacheKey] ?: 0L

                        if (currentGeneration == generation) {
                            memoryCache[cacheKey] = stats
                            persist(cacheKey, stats)
                            updates.emit(FolderStatUpdate(target.displayPath, stats, target.nodeRef))
                            if (stats.status == FolderStatsStatus.Unavailable) {
                                retryCounts.merge(cacheKey, 1, Int::plus)
                            } else {
                                retryCounts.remove(cacheKey)
                            }
                        }
                    } finally {
                        val currentJob = coroutineContext[Job]
                        if (currentJob != null && activeJobs.remove(cacheKey, currentJob)) {
                            queuedPaths.remove(cacheKey)
                        }
                    }
            }
            activeJobs[cacheKey] = job
            job.start()
        }
    }

    override suspend fun invalidate(paths: Collection<String>) = withContext(dispatchers.io) {
        invalidateTargets(paths.map(::localTarget))
    }

    override suspend fun invalidateNodes(nodes: Collection<StorageNodeRef>) = withContext(dispatchers.io) {
        invalidateTargets(nodes.map(::nodeTarget))
    }

    private suspend fun invalidateTargets(targets: Collection<FolderStatsTarget>) {
        targets.distinctBy(FolderStatsTarget::cacheKey).forEach { target ->
            val cacheKey = target.cacheKey
            nextGeneration(cacheKey)
            activeJobs.remove(cacheKey)?.cancel()
            queuedPaths.remove(cacheKey)
            memoryCache.remove(cacheKey)
            runCatchingPreservingCancellation { folderStatsDao.delete(listOf(cacheKey)) }
                .onFailure { error ->
                    AppLogger.w(
                        "FolderStatsStore",
                        "Failed to delete folder stats cache for ${target.displayPath}",
                        error
                    )
                }
        }
    }

    override suspend fun clear() {
        withContext(dispatchers.io) {
            activeJobs.values.forEach { it.cancel() }
            activeJobs.clear()
            queuedPaths.clear()
            memoryCache.clear()
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

    private suspend fun calculate(target: FolderStatsTarget): FolderStats {
        return try {
            target.nodeRef?.let { node -> nodeCalculator?.invoke(node) }
                ?: calculator(File(target.displayPath))
        } catch (e: Exception) {
            e.rethrowIfCancellation()
            AppLogger.w(
                "FolderStatsStore",
                "Folder stats calculation failed for ${target.displayPath}",
                e
            )
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

    private fun localTarget(path: String): FolderStatsTarget {
        val normalized = normalizePath(path)
        return FolderStatsTarget(
            cacheKey = normalized,
            displayPath = normalized,
            nodeRef = null
        )
    }

    private fun nodeTarget(node: StorageNodeRef): FolderStatsTarget {
        val displayPath = normalizePath(node.displayPath.absolutePath)
        val cacheKey = if (node.backendId == StorageNodeRef.LOCAL_BACKEND_ID) {
            displayPath
        } else {
            "node:${node.backendId}:${node.canonicalIdentity.value}"
        }
        return FolderStatsTarget(cacheKey, displayPath, node)
    }

    override fun close() {
        workerScope.cancel()
    }
}

private data class FolderStatsTarget(
    val cacheKey: String,
    val displayPath: String,
    val nodeRef: StorageNodeRef?
)
