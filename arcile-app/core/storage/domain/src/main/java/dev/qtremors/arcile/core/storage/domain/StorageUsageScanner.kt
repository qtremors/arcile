package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

interface StorageUsageScanner {
    fun scanStorageUsage(
        rootPath: String,
        limits: StorageUsageScanLimits = StorageUsageScanLimits()
    ): Flow<StorageUsageScanState>

    fun invalidateStorageUsage(paths: Collection<String> = emptyList())
}
