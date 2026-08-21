package dev.qtremors.arcile.core.storage.domain

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
