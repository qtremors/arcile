package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

interface FileBrowserRepository : SelectionPropertiesRepository {
    suspend fun listFiles(path: String): Result<List<FileModel>>
    fun listFilePages(
        path: String,
        pageSize: Int = ListingPage.DEFAULT_PAGE_SIZE
    ): Flow<ListingPage>
    suspend fun getCachedFolderStats(paths: Collection<String>): Map<String, FolderStats>
    fun queueFolderStats(paths: List<String>)
    fun observeFolderStatUpdates(): Flow<FolderStatUpdate>
}
