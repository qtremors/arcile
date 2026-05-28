package dev.qtremors.arcile.core.storage.domain

import androidx.compose.runtime.Immutable

@Immutable
data class StorageUsageScanLimits(
    val maxDepth: Int = 6,
    val maxNodes: Int = 6_000,
    val maxChildrenPerFolder: Int = 48,
    val minChildShare: Float = 0.004f
)

enum class StorageUsageNodeKind {
    Folder,
    File,
    Grouped
}

enum class StorageUsageScanStatus {
    Ready,
    Partial,
    Unavailable
}

@Immutable
data class StorageUsageNode(
    val name: String,
    val path: String,
    val sizeBytes: Long,
    val kind: StorageUsageNodeKind,
    val childCount: Int,
    val status: StorageUsageScanStatus = StorageUsageScanStatus.Ready,
    val children: List<StorageUsageNode> = emptyList()
) {
    val isContainer: Boolean get() = kind == StorageUsageNodeKind.Folder
}

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
