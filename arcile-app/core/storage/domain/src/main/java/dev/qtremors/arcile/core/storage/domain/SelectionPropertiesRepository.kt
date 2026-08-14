package dev.qtremors.arcile.core.storage.domain

interface SelectionPropertiesRepository {
    suspend fun getSelectionProperties(paths: List<String>): Result<SelectionProperties>

    suspend fun getNodeSelectionProperties(
        nodes: List<StorageNodeRef>
    ): Result<SelectionProperties> = getSelectionProperties(
        nodes.map { it.displayPath.absolutePath }
    )
}
