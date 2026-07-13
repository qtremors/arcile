package dev.qtremors.arcile.core.storage.domain

interface StorageCleanerScanner {
    suspend fun cachedScan(
        rootPaths: List<String>,
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): StorageCleanerResult? = null

    suspend fun scan(
        rootPaths: List<String>,
        now: Long = System.currentTimeMillis(),
        limits: StorageCleanerScanLimits = StorageCleanerScanLimits(),
        rules: StorageCleanerRules = StorageCleanerRules()
    ): StorageCleanerResult

    suspend fun invalidateStorageCleaner(paths: Collection<String> = emptyList()) = Unit
}
