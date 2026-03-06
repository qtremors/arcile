package dev.qtremors.arcile.data

import android.os.Environment
import android.os.StatFs
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class LocalFileRepository : FileRepository {

    private val storageRoot: String by lazy {
        Environment.getExternalStorageDirectory().canonicalPath
    }

    // path traversal guard — rejects paths that escape external storage
    private fun validatePath(file: File): Result<Unit> {
        val canonical = file.canonicalPath
        if (canonical != storageRoot && !canonical.startsWith(storageRoot + File.separator)) {
            return Result.failure(SecurityException("Access denied: path outside storage boundary"))
        }
        return Result.success(Unit)
    }

    override suspend fun getRootDirectory(): File = withContext(Dispatchers.IO) {
        Environment.getExternalStorageDirectory()
    }

    override suspend fun listFiles(path: String): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
            val directory = File(path)
            validatePath(directory).onFailure { return@withContext Result.failure(it) }

            if (!directory.exists() || !directory.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a valid directory"))
            }

            val files = directory.listFiles()?.map { FileModel(it) } ?: emptyList()
            // sort: directories first, then alphabetically
            val sortedFiles = files.sortedWith(
                compareBy<FileModel> { !it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
            Result.success(sortedFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = withContext(Dispatchers.IO) {
        try {
            val newDir = File(parentPath, name)
            validatePath(newDir).onFailure { return@withContext Result.failure(it) }

            if (newDir.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Directory already exists"))
            }
            if (newDir.mkdirs()) {
                Result.success(FileModel(newDir))
            } else {
                Result.failure(Exception("Failed to create directory"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val file = File(path)
            validatePath(file).onFailure { return@withContext Result.failure(it) }

            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File does not exist"))
            }
            val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
            if (success) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("Failed to delete file or directory"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = withContext(Dispatchers.IO) {
         try {
             // reject names containing path separators or traversal sequences
             if (newName.contains('/') || newName.contains('\\') ||
                 newName.contains("..") || newName.contains('\u0000')) {
                 return@withContext Result.failure(
                     IllegalArgumentException("Invalid file name: must not contain path separators or '..'")
                 )
             }

             val file = File(path)
             validatePath(file).onFailure { return@withContext Result.failure(it) }

             val newFile = File(file.parent, newName)
             validatePath(newFile).onFailure { return@withContext Result.failure(it) }

             if (!file.exists()) {
                 return@withContext Result.failure(IllegalArgumentException("File does not exist"))
             }
             if (newFile.exists()) {
                 return@withContext Result.failure(IllegalArgumentException("File with that name already exists"))
             }
             
             if (file.renameTo(newFile)) {
                 Result.success(FileModel(newFile))
             } else {
                 Result.failure(Exception("Failed to rename file"))
             }
         } catch (e: Exception) {
             Result.failure(e)
         }
    }

    override suspend fun getRecentFiles(limit: Int): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
            val root = Environment.getExternalStorageDirectory()
            val targetDirs = listOf(
                File(root, Environment.DIRECTORY_DOWNLOADS),
                File(root, Environment.DIRECTORY_DOCUMENTS),
                File(root, Environment.DIRECTORY_PICTURES),
                File(root, Environment.DIRECTORY_DCIM)
            )

            val recentFiles = mutableListOf<File>()
            for (dir in targetDirs) {
                if (dir.exists() && dir.isDirectory) {
                    dir.walkTopDown()
                        .maxDepth(3)
                        .filter { it.isFile && !it.isHidden }
                        .forEach { recentFiles.add(it) }
                }
            }

            val topRecent = recentFiles
                .sortedByDescending { it.lastModified() }
                .take(limit)
                .map { FileModel(it) }

            Result.success(topRecent)
        } catch (e: Exception) {
             Result.failure(e)
        }
    }

    override suspend fun getStorageInfo(): Result<StorageInfo> = withContext(Dispatchers.IO) {
        try {
            val path = Environment.getExternalStorageDirectory()
            val stat = StatFs(path.path)
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong

            Result.success(
                StorageInfo(
                    totalBytes = totalBlocks * blockSize,
                    freeBytes = availableBlocks * blockSize
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCategoryStorageSizes(): Result<List<CategoryStorage>> = withContext(Dispatchers.IO) {
        try {
            val root = Environment.getExternalStorageDirectory()

            // build extension-to-category lookup
            val extToCategoryIndex = mutableMapOf<String, Int>()
            FileCategories.all.forEachIndexed { index, cat ->
                cat.extensions.forEach { ext ->
                    extToCategoryIndex[ext] = index
                }
            }

            // accumulate sizes per category
            val sizes = LongArray(FileCategories.all.size)

            root.walkTopDown()
                .filter { it.isFile && !it.isHidden }
                .forEach { file ->
                    val ext = file.extension.lowercase()
                    val catIndex = extToCategoryIndex[ext]
                    if (catIndex != null) {
                        sizes[catIndex] += file.length()
                    }
                }

            val result = FileCategories.all.mapIndexed { index, cat ->
                CategoryStorage(
                    name = cat.name,
                    color = cat.color,
                    sizeBytes = sizes[index],
                    extensions = cat.extensions
                )
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
