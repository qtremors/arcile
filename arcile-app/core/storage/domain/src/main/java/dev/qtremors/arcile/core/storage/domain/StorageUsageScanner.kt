package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

interface StorageUsageScanner {
    fun scanStorageUsage(
        rootPath: String,
        limits: StorageUsageScanLimits = StorageUsageScanLimits()
    ): Flow<StorageUsageScanState>

    fun scanStorageUsage(
        root: StorageNodeRef,
        limits: StorageUsageScanLimits = StorageUsageScanLimits()
    ): Flow<StorageUsageScanState> = scanStorageUsage(root.displayPath.absolutePath, limits)

    fun invalidateStorageUsage(paths: Collection<String> = emptyList())

    fun invalidateStorageUsageNodes(nodes: Collection<StorageNodeRef> = emptyList()) {
        invalidateStorageUsage(nodes.map { it.displayPath.absolutePath })
    }
}
