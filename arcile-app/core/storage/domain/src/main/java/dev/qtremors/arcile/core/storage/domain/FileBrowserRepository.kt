package dev.qtremors.arcile.core.storage.domain

import kotlinx.coroutines.flow.Flow

interface FileBrowserRepository : SelectionPropertiesRepository {
    suspend fun listFiles(path: String): Result<List<FileModel>>
    fun listFilePages(
        path: String,
        pageSize: Int = ListingPage.DEFAULT_PAGE_SIZE
    ): Flow<ListingPage>
    suspend fun listNodeFiles(directory: StorageNodeRef): Result<List<FileModel>> =
        listFiles(directory.displayPath.absolutePath)
    fun listNodePages(
        directory: StorageNodeRef,
        pageSize: Int = ListingPage.DEFAULT_PAGE_SIZE
    ): Flow<ListingPage> = listFilePages(directory.displayPath.absolutePath, pageSize)
    suspend fun getCachedFolderStats(paths: Collection<String>): Map<String, FolderStats>
    fun queueFolderStats(paths: List<String>)
    suspend fun getCachedNodeFolderStats(
        nodes: Collection<StorageNodeRef>
    ): Map<String, FolderStats> = getCachedFolderStats(
        nodes.map { it.displayPath.absolutePath }
    )
    fun queueNodeFolderStats(nodes: List<StorageNodeRef>) =
        queueFolderStats(nodes.map { it.displayPath.absolutePath })
    fun observeFolderStatUpdates(): Flow<FolderStatUpdate>
}
