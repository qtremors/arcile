package dev.qtremors.arcile.core.storage.domain

interface SelectionPropertiesRepository {
    suspend fun getSelectionProperties(paths: List<String>): Result<SelectionProperties>
}
