package dev.qtremors.arcile.core.storage.domain

interface SearchRepository {
    suspend fun getFilesByCategory(
        scope: StorageScope,
        categoryName: String
    ): Result<List<FileModel>>
    suspend fun searchFiles(
        query: String,
        scope: StorageScope = StorageScope.AllStorage,
        filters: SearchFilters? = null
    ): Result<List<FileModel>>
}
