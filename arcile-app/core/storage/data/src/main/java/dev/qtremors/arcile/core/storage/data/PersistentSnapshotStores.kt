package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.data.db.RecentFilesSnapshotDao
import dev.qtremors.arcile.core.storage.data.db.RecentFilesSnapshotEntity
import dev.qtremors.arcile.core.storage.data.db.StorageCleanerSnapshotDao
import dev.qtremors.arcile.core.storage.data.db.StorageCleanerSnapshotEntity
import dev.qtremors.arcile.core.storage.data.db.StorageUsageSnapshotDao
import dev.qtremors.arcile.core.storage.data.db.StorageUsageSnapshotEntity
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.CachedStorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.CleanerGroupType
import dev.qtremors.arcile.core.storage.domain.StorageCleanerResult
import dev.qtremors.arcile.core.storage.domain.StorageCleanerRules
import dev.qtremors.arcile.core.storage.domain.StorageCleanerScanLimits
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageUsageNode
import dev.qtremors.arcile.core.storage.domain.StorageUsageScanLimits
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Singleton
class RecentFilesSnapshotStore @Inject constructor(
    private val dao: RecentFilesSnapshotDao,
    private val dispatchers: ArcileDispatchers = defaultDispatchers()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(scope: StorageScope, limit: Int, minTimestamp: Long): List<FileModel>? = withContext(dispatchers.io) {
        val entity = dao.get(key(scope, limit, minTimestamp)) ?: return@withContext null
        runCatchingPreservingCancellation {
            json.decodeFromString<List<CachedFileModel>>(entity.payloadJson).map { it.toDomain() }
        }.getOrNull()
    }

    suspend fun put(scope: StorageScope, limit: Int, minTimestamp: Long, files: List<FileModel>) = withContext(dispatchers.io) {
        dao.upsert(
            RecentFilesSnapshotEntity(
                key = key(scope, limit, minTimestamp),
                payloadJson = json.encodeToString(files.map { CachedFileModel.from(it) }),
                cachedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear() = withContext(dispatchers.io) {
        dao.clear()
    }

    private fun key(scope: StorageScope, limit: Int, minTimestamp: Long): String =
        "recent:${scope.cacheKey()}:$limit:${minTimestamp.normalizedRecentWindow()}"
}

private fun Long.normalizedRecentWindow(): Long =
    if (this <= 0L) 0L else this / RECENT_WINDOW_BUCKET_MS

private const val RECENT_WINDOW_BUCKET_MS = 24L * 60L * 60L * 1000L

@Singleton
class StorageUsageSnapshotStore @Inject constructor(
    private val dao: StorageUsageSnapshotDao,
    private val dispatchers: ArcileDispatchers = defaultDispatchers()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(rootPath: String, limits: StorageUsageScanLimits): StorageUsageNode? = withContext(dispatchers.io) {
        val entity = dao.get(key(rootPath, limits)) ?: return@withContext null
        runCatchingPreservingCancellation { json.decodeFromString<CachedStorageUsageNode>(entity.payloadJson).toDomain() }.getOrNull()
    }

    suspend fun put(rootPath: String, limits: StorageUsageScanLimits, root: StorageUsageNode) = withContext(dispatchers.io) {
        val normalizedRoot = File(rootPath).absolutePath
        dao.upsert(
            StorageUsageSnapshotEntity(
                key = key(normalizedRoot, limits),
                rootPath = normalizedRoot,
                payloadJson = json.encodeToString(CachedStorageUsageNode.from(root)),
                cachedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun get(root: StorageNodeRef, limits: StorageUsageScanLimits): StorageUsageNode? =
        withContext(dispatchers.io) {
            val entity = dao.get(key(root, limits)) ?: return@withContext null
            runCatchingPreservingCancellation {
                json.decodeFromString<CachedStorageUsageNode>(entity.payloadJson).toDomain()
            }.getOrNull()
        }

    suspend fun put(root: StorageNodeRef, limits: StorageUsageScanLimits, usage: StorageUsageNode) =
        withContext(dispatchers.io) {
            dao.upsert(
                StorageUsageSnapshotEntity(
                    key = key(root, limits),
                    rootPath = root.snapshotIdentity(),
                    payloadJson = json.encodeToString(CachedStorageUsageNode.from(usage)),
                    cachedAt = System.currentTimeMillis()
                )
            )
        }

    suspend fun invalidate(paths: Collection<String>) = withContext(dispatchers.io) {
        if (paths.isEmpty()) {
            dao.clear()
        } else {
            paths.map { File(it).absolutePath.trimEnd(File.separatorChar) }.distinct().forEach { path ->
                dao.deleteForRoot(path, "$path${File.separator}%")
            }
        }
    }

    suspend fun invalidateNodes(nodes: Collection<StorageNodeRef>) = withContext(dispatchers.io) {
        if (nodes.isEmpty()) {
            dao.clear()
            return@withContext
        }
        val changedNodes = nodes.distinctBy { it.snapshotIdentity() }
        val keys = dao.getAll().filter { entity ->
            val cached = entity.rootPath.toSnapshotLocation() ?: return@filter false
            changedNodes.any { changed ->
                cached.backendId == changed.backendId &&
                    pathsOverlap(cached.path, changed.displayPath.absolutePath)
            }
        }.map { it.key }
        if (keys.isNotEmpty()) dao.deleteKeys(keys)
    }

    private fun key(rootPath: String, limits: StorageUsageScanLimits): String =
        "usage:${File(rootPath).absolutePath}:${limits.maxDepth}:${limits.maxChildrenPerFolder}:" +
            "${limits.minChildShare}:${limits.maxVisitedNodes}:${limits.maxScanDurationMillis}"

    private fun key(root: StorageNodeRef, limits: StorageUsageScanLimits): String =
        "usage:${root.snapshotIdentity()}:${limits.maxDepth}:${limits.maxChildrenPerFolder}:" +
            "${limits.minChildShare}:${limits.maxVisitedNodes}:${limits.maxScanDurationMillis}"

    private fun StorageNodeRef.snapshotIdentity(): String =
        StorageNodePersistenceIdentity.from(this).encode()

    private fun String.toSnapshotLocation(): SnapshotLocation? {
        val identity = StorageNodePersistenceIdentity.decode(this) ?: return null
        return SnapshotLocation(
            backendId = identity.backendId,
            path = identity.displayPath
        )
    }

    private fun pathsOverlap(first: String, second: String): Boolean {
        val normalizedFirst = first.trimEnd('/', '\\')
        val normalizedSecond = second.trimEnd('/', '\\')
        return normalizedFirst == normalizedSecond ||
            normalizedFirst.startsWith("$normalizedSecond/") ||
            normalizedSecond.startsWith("$normalizedFirst/")
    }

    private data class SnapshotLocation(val backendId: String, val path: String)
}

@Singleton
class StorageCleanerSnapshotStore @Inject constructor(
    private val dao: StorageCleanerSnapshotDao,
    private val dispatchers: ArcileDispatchers = defaultDispatchers()
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules,
        groupTypes: Set<CleanerGroupType> = CleanerGroupType.entries.toSet()
    ): CachedStorageCleanerResult? = withContext(dispatchers.io) {
        val entity = dao.get(key(rootPaths, limits, rules, groupTypes)) ?: return@withContext null
        runCatchingPreservingCancellation {
            CachedStorageCleanerResult(
                result = json.decodeFromString<CachedCleanerResult>(entity.payloadJson).toDomain(),
                cachedAt = entity.cachedAt
            )
        }.getOrNull()
    }

    suspend fun getNodes(
        roots: List<StorageNodeRef>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules,
        groupTypes: Set<CleanerGroupType> = CleanerGroupType.entries.toSet()
    ): CachedStorageCleanerResult? = withContext(dispatchers.io) {
        val entity = dao.get(nodeKey(roots, limits, rules, groupTypes)) ?: return@withContext null
        runCatchingPreservingCancellation {
            CachedStorageCleanerResult(
                result = json.decodeFromString<CachedCleanerResult>(entity.payloadJson).toDomain(),
                cachedAt = entity.cachedAt
            )
        }.getOrNull()
    }

    suspend fun put(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules,
        groupTypes: Set<CleanerGroupType> = CleanerGroupType.entries.toSet(),
        result: StorageCleanerResult
    ) = withContext(dispatchers.io) {
        dao.upsert(
            StorageCleanerSnapshotEntity(
                key = key(rootPaths, limits, rules, groupTypes),
                rootPathsKey = rootPathsKey(rootPaths),
                payloadJson = json.encodeToString(CachedCleanerResult.from(result)),
                cachedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun putNodes(
        roots: List<StorageNodeRef>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules,
        groupTypes: Set<CleanerGroupType> = CleanerGroupType.entries.toSet(),
        result: StorageCleanerResult
    ) = withContext(dispatchers.io) {
        dao.upsert(
            StorageCleanerSnapshotEntity(
                key = nodeKey(roots, limits, rules, groupTypes),
                rootPathsKey = rootNodesKey(roots),
                payloadJson = json.encodeToString(CachedCleanerResult.from(result)),
                cachedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun clear() = withContext(dispatchers.io) {
        dao.clear()
    }

    suspend fun invalidate(paths: Collection<String>) = withContext(dispatchers.io) {
        val normalizedPaths = paths.mapTo(hashSetOf()) { File(it).absolutePath }
        if (normalizedPaths.isEmpty()) {
            dao.markAllStale()
            return@withContext
        }
        dao.getAll().forEach { entity ->
            val cached = runCatchingPreservingCancellation {
                json.decodeFromString<CachedCleanerResult>(entity.payloadJson).toDomain()
            }.getOrNull() ?: return@forEach
            val updated = cached.copy(
                groups = cached.groups.map { group ->
                    val remaining = group.candidates.filterNot { it.absolutePath in normalizedPaths }
                    group.copy(
                        candidates = if (group.type == CleanerGroupType.Duplicates) {
                            remaining.groupBy { it.duplicateGroupKey ?: it.absolutePath }
                                .values
                                .filter { it.size > 1 }
                                .flatten()
                        } else {
                            remaining
                        }
                    )
                }
            )
            dao.upsert(
                entity.copy(
                    payloadJson = json.encodeToString(CachedCleanerResult.from(updated)),
                    cachedAt = 0L
                )
            )
        }
    }

    suspend fun invalidateNodes(nodes: Collection<StorageNodeRef>) = withContext(dispatchers.io) {
        if (nodes.isEmpty()) {
            dao.markAllStale()
            return@withContext
        }
        val changed = nodes.distinctBy { it.canonicalIdentity.value }
        dao.getAll().forEach { entity ->
            val cached = runCatchingPreservingCancellation {
                json.decodeFromString<CachedCleanerResult>(entity.payloadJson).toDomain()
            }.getOrNull() ?: return@forEach
            val updated = cached.copy(
                groups = cached.groups.map { group ->
                    val remaining = group.candidates.filterNot { candidate ->
                        val candidateNode = candidate.nodeRef ?: return@filterNot false
                        changed.any { node ->
                            node.backendId == candidateNode.backendId &&
                                pathsOverlap(
                                    node.displayPath.absolutePath,
                                    candidateNode.displayPath.absolutePath
                                )
                        }
                    }
                    group.copy(
                        candidates = if (group.type == CleanerGroupType.Duplicates) {
                            remaining.groupBy { it.duplicateGroupKey ?: it.absolutePath }
                                .values
                                .filter { it.size > 1 }
                                .flatten()
                        } else {
                            remaining
                        }
                    )
                }
            )
            dao.upsert(
                entity.copy(
                    payloadJson = json.encodeToString(CachedCleanerResult.from(updated)),
                    cachedAt = 0L
                )
            )
        }
    }

    private fun key(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules,
        groupTypes: Set<CleanerGroupType>
    ): String =
        "cleaner:${rootPathsKey(rootPaths)}:${groupTypes.map { it.name }.sorted().joinToString(",")}:" +
            "${limits.maxFiles}:${limits.maxDepth}:${limits.maxCandidatesPerGroup}:" +
            "${limits.largeFileThresholdBytes}:${limits.oldDownloadAgeMs}:${rules.normalized().stableHash()}"

    private fun nodeKey(
        roots: List<StorageNodeRef>,
        limits: StorageCleanerScanLimits,
        rules: StorageCleanerRules,
        groupTypes: Set<CleanerGroupType>
    ): String =
        "cleaner:${rootNodesKey(roots)}:${groupTypes.map { it.name }.sorted().joinToString(",")}:" +
            "${limits.maxFiles}:${limits.maxDepth}:${limits.maxCandidatesPerGroup}:" +
            "${limits.largeFileThresholdBytes}:${limits.oldDownloadAgeMs}:${rules.normalized().stableHash()}"

    private fun rootPathsKey(rootPaths: List<String>): String =
        rootPaths.map { File(it).absolutePath }.distinct().sorted().joinToString("|")

    private fun rootNodesKey(roots: List<StorageNodeRef>): String =
        roots.map { StorageNodePersistenceIdentity.from(it).encode() }
            .distinct()
            .sorted()
            .joinToString("|")

    private fun pathsOverlap(first: String, second: String): Boolean {
        val normalizedFirst = first.trimEnd('/', '\\')
        val normalizedSecond = second.trimEnd('/', '\\')
        return normalizedFirst == normalizedSecond ||
            normalizedFirst.startsWith("$normalizedSecond/") ||
            normalizedSecond.startsWith("$normalizedFirst/")
    }

    private fun StorageCleanerRules.stableHash(): String =
        Json.encodeToString(this).sha256()
}

internal fun StorageScope.cacheKey(): String =
    when (this) {
        StorageScope.AllStorage -> "all"
        is StorageScope.Volume -> "volume:$volumeId"
        is StorageScope.Path -> "path:$volumeId:$absolutePath"
        is StorageScope.Category -> "category:${volumeId.orEmpty()}:$categoryName"
    }

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}

private fun defaultDispatchers() = ArcileDispatchers(
    io = Dispatchers.IO,
    default = Dispatchers.Default,
    main = Dispatchers.Main,
    storage = Dispatchers.IO
)
