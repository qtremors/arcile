package dev.qtremors.arcile.domain

sealed interface StorageScope {
    data object AllStorage : StorageScope
    data class Volume(val volumeId: String) : StorageScope
    data class Path(val volumeId: String, val absolutePath: String) : StorageScope
    data class Category(val volumeId: String? = null, val categoryName: String) : StorageScope
}
