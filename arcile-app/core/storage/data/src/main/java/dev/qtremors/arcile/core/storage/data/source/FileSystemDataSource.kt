package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel

interface FileSystemDataSource : DirectoryListingDataSource {
    fun getStandardFolders(): Map<String, String?>
    suspend fun listFiles(path: String): Result<List<FileModel>>
    suspend fun createDirectory(parentPath: String, name: String): Result<FileModel>
    suspend fun createFile(parentPath: String, name: String): Result<FileModel>
    suspend fun deletePermanently(paths: List<String>): Result<Unit>
    suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult>
    suspend fun shred(paths: List<String>): Result<Unit>
    suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult>
    suspend fun renameFile(path: String, newName: String): Result<FileModel>
    suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>>
    suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<Unit>
    suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)? = null
    ): Result<FileModel>
}
