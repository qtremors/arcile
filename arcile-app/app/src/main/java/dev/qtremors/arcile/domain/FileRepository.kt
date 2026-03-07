package dev.qtremors.arcile.domain

import java.io.File

interface FileRepository {
    suspend fun listFiles(path: String): Result<List<FileModel>>
    suspend fun createDirectory(parentPath: String, name: String): Result<FileModel>
    suspend fun createFile(parentPath: String, name: String): Result<FileModel>
    suspend fun deleteFile(path: String): Result<Unit>
    suspend fun renameFile(path: String, newName: String): Result<FileModel>
    suspend fun getRootDirectory(): File
    suspend fun getRecentFiles(limit: Int = 10, minTimestamp: Long = 0L): Result<List<FileModel>>
    suspend fun getStorageInfo(): Result<StorageInfo>
    suspend fun getCategoryStorageSizes(): Result<List<CategoryStorage>>
    suspend fun getFilesByCategory(categoryName: String): Result<List<FileModel>>
    suspend fun searchFiles(query: String, pathScope: String? = null, filters: Any? = null): Result<List<FileModel>>
    
    // Core Operations
    suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String): Result<Unit>
    suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String): Result<Unit>
    
    // Trash Subsystem
    suspend fun moveToTrash(paths: List<String>): Result<Unit>
    suspend fun restoreFromTrash(trashIds: List<String>): Result<Unit>
    suspend fun emptyTrash(): Result<Unit>
    suspend fun getTrashFiles(): Result<List<TrashMetadata>>
}
