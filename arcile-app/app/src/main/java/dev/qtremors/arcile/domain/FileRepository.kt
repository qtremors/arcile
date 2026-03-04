package com.qtremors.filemanager.domain

import java.io.File
import kotlinx.coroutines.flow.Flow

data class StorageInfo(
    val totalBytes: Long,
    val freeBytes: Long
)

interface FileRepository {
    suspend fun listFiles(path: String): Result<List<FileModel>>
    suspend fun createDirectory(parentPath: String, name: String): Result<FileModel>
    suspend fun deleteFile(path: String): Result<Unit>
    suspend fun renameFile(path: String, newName: String): Result<FileModel>
    suspend fun getRootDirectory(): File
    suspend fun getRecentFiles(limit: Int = 10): Result<List<FileModel>>
    suspend fun getStorageInfo(): Result<StorageInfo>
}
