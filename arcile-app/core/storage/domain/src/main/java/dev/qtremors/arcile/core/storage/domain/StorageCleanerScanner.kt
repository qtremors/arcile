package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

enum class StorageCleanerScanPhase {
    Discovering,
    CheckingDuplicates,
    Complete
}

@Immutable
data class StorageCleanerScanProgress(
    val phase: StorageCleanerScanPhase = StorageCleanerScanPhase.Discovering,
    val scannedFiles: Int = 0,
    val progressFraction: Float? = null,
    val estimatedRemainingMillis: Long? = null,
    val completedGroups: Set<CleanerGroupType> = emptySet()
)

@Immutable
data class StorageCleanerScanUpdate(
    val progress: StorageCleanerScanProgress,
    val result: StorageCleanerResult? = null
)

@Immutable
data class CachedStorageCleanerResult(
    val result: StorageCleanerResult,
    val cachedAt: Long
)

interface StorageCleanerScanner {
    suspend fun cachedScan(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): StorageCleanerResult? = null

    suspend fun cachedScanWithMetadata(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): CachedStorageCleanerResult? = cachedScan(rootPaths, limits, rules)?.let {
        CachedStorageCleanerResult(result = it, cachedAt = 0L)
    }

    suspend fun cachedScanForGroups(
        rootPaths: List<String>,
        groupTypes: Set<CleanerGroupType>,
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): CachedStorageCleanerResult? = cachedScanWithMetadata(rootPaths, limits, rules)?.let { cached ->
        cached.copy(result = cached.result.onlyGroups(groupTypes))
    }

    suspend fun scan(
        rootPaths: List<String>,
        now: Long = System.currentTimeMillis(),
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): StorageCleanerResult

    fun scanUpdates(
        rootPaths: List<String>,
        now: Long = System.currentTimeMillis(),
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): Flow<StorageCleanerScanUpdate> = flow {
        val result = scan(rootPaths, now, limits, rules)
        emit(
            StorageCleanerScanUpdate(
                progress = StorageCleanerScanProgress(
                    phase = StorageCleanerScanPhase.Complete,
                    scannedFiles = result.scannedFiles,
                    progressFraction = 1f,
                    estimatedRemainingMillis = 0L,
                    completedGroups = CleanerGroupType.entries.toSet()
                ),
                result = result
            )
        )
    }

    fun scanGroupUpdates(
        rootPaths: List<String>,
        groupTypes: Set<CleanerGroupType>,
        now: Long = System.currentTimeMillis(),
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): Flow<StorageCleanerScanUpdate> = scanUpdates(rootPaths, now, limits, rules).map { update ->
        update.copy(
            progress = update.progress.copy(
                completedGroups = update.progress.completedGroups.intersect(groupTypes)
            ),
            result = update.result?.onlyGroups(groupTypes)
        )
    }

    suspend fun invalidateStorageCleaner(paths: Collection<String> = emptyList()) = Unit
}

private fun StorageCleanerResult.onlyGroups(groupTypes: Set<CleanerGroupType>): StorageCleanerResult =
    copy(groups = groups.filter { it.type in groupTypes })
