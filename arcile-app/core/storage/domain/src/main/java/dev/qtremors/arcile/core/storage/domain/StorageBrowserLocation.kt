package dev.qtremors.arcile.core.storage.domain

sealed interface StorageBrowserLocation {
    data object Roots : StorageBrowserLocation
    data class Directory(val pathScope: StorageScope.Path) : StorageBrowserLocation
    data class DirectDirectory(
        val path: StorageNodePath,
        val isRootStorageScope: Boolean = false
    ) : StorageBrowserLocation
    data class Category(val categoryScope: StorageScope.Category) : StorageBrowserLocation
    data class Archive(
        val archivePath: String,
        val entryPrefix: String?,
        val isRootStorageScope: Boolean = false
    ) : StorageBrowserLocation
}
