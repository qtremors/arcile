package dev.qtremors.arcile.domain

import java.io.File

interface FileRepository {
    suspend fun listFiles(path: String): Result<List<FileModel>>
    suspend fun createDirectory(parentPath: String, name: String): Result<FileModel>
    suspend fun deleteFile(path: String): Result<Unit>
    suspend fun renameFile(path: String, newName: String): Result<FileModel>
    suspend fun getRootDirectory(): File
    suspend fun getRecentFiles(limit: Int = 10): Result<List<FileModel>>
    suspend fun getStorageInfo(): Result<StorageInfo>
    suspend fun getCategoryStorageSizes(): Result<List<CategoryStorage>>
    suspend fun getFilesByCategory(categoryName: String): Result<List<FileModel>>
    suspend fun searchGlobal(query: String): Result<List<FileModel>>
}
