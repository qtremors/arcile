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

    suspend fun searchNode(
        query: String,
        root: StorageNodeRef,
        filters: SearchFilters? = null,
        limits: StorageNodeSearchLimits = StorageNodeSearchLimits()
    ): Result<List<FileModel>> = Result.failure(
        UnsupportedOperationException("Search is unavailable for ${root.backendId}")
    )
}

@Immutable
data class StorageNodeSearchLimits(
    val maxDepth: Int = 24,
    val maxVisitedEntries: Int = 100_000,
    val maxResults: Int = 2_000,
    val maxDurationMillis: Long = 20_000L
) {
    init {
        require(maxDepth >= 0) { "Search depth must not be negative" }
        require(maxVisitedEntries > 0) { "Search entry budget must be positive" }
        require(maxResults > 0) { "Search result budget must be positive" }
        require(maxDurationMillis > 0L) { "Search duration must be positive" }
    }
}
