package dev.qtremors.arcile.core.storage.domain

sealed interface StorageScope {
    data object AllStorage : StorageScope
    data class Volume(val volumeId: String) : StorageScope {
        val typedVolumeId: StorageVolumeId get() = StorageVolumeId.of(volumeId)
    }

    data class Path(val volumeId: String, val absolutePath: String) : StorageScope {
        val typedVolumeId: StorageVolumeId get() = StorageVolumeId.of(volumeId)
        val nodePath: StorageNodePath get() = StorageNodePath.of(absolutePath)
        val nodeRef: StorageNodeRef get() = StorageNodeRef.local(absolutePath, volumeId)
    }

    data class Category(val volumeId: String? = null, val categoryName: String) : StorageScope {
        val typedVolumeId: StorageVolumeId? get() = volumeId?.takeIf { it.isNotBlank() }?.let(StorageVolumeId::of)
        val categoryId: CategoryId get() = CategoryId.of(categoryName)
    }
}
