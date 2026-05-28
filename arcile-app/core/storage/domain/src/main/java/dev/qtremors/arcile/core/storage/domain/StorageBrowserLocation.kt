package dev.qtremors.arcile.core.storage.domain

sealed interface StorageBrowserLocation {
    data object Roots : StorageBrowserLocation
    data class Directory(val pathScope: StorageScope.Path) : StorageBrowserLocation
    data class Category(val categoryScope: StorageScope.Category) : StorageBrowserLocation
}
