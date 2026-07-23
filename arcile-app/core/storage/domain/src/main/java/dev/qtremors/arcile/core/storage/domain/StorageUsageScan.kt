package dev.qtremors.arcile.core.storage.domain

@Immutable
data class StorageUsageScanLimits(
    val maxDepth: Int = 6,
    val maxChildrenPerFolder: Int = 48,
    val minChildShare: Float = 0.0f
)

@Immutable
data class StorageUsageScanProgress(
    val rootPath: String,
    val scannedNodes: Int,
    val scannedBytes: Long,
    val currentPath: String?
)

sealed interface StorageUsageScanState {
    data object Idle : StorageUsageScanState
    data class Loading(val progress: StorageUsageScanProgress) : StorageUsageScanState
    data class Loaded(val root: StorageUsageNode) : StorageUsageScanState
    data class Error(val message: String) : StorageUsageScanState
}
