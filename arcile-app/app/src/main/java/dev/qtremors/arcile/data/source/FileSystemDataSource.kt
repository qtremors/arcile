package dev.qtremors.arcile.data.source

import dev.qtremors.arcile.domain.FileOperationException
import android.content.Context
import android.os.Environment
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface FileSystemDataSource {
    fun getStandardFolders(): Map<String, String?>
    suspend fun listFiles(path: String): Result<List<FileModel>>
    suspend fun createDirectory(parentPath: String, name: String): Result<FileModel>
    suspend fun createFile(parentPath: String, name: String): Result<FileModel>
    suspend fun deletePermanently(paths: List<String>): Result<Unit>
    suspend fun renameFile(path: String, newName: String): Result<FileModel>
    suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>>
    suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit>
    suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>): Result<Unit>
}

class DefaultFileSystemDataSource(
    private val context: Context,
    private val volumeProvider: VolumeProvider,
    private val mediaStoreClient: MediaStoreClient
) : FileSystemDataSource {

    private fun validatePath(file: File): Result<Unit> {
        val canonical = file.canonicalPath
        val isAllowed = volumeProvider.activeStorageRoots.any { root ->
            canonical == root || canonical.startsWith(root + File.separator)
        }

        if (!isAllowed) {
            return Result.failure(SecurityException("Access denied: path outside storage boundaries"))
        }
        return Result.success(Unit)
    }

    private fun validateFileName(name: String): Result<Unit> {
        if (name.contains('/') || name.contains('\\') || name.contains("..") || name.contains('\u0000')) {
            return Result.failure(IllegalArgumentException("Invalid file name: must not contain path separators or '..'"))
        }
        return Result.success(Unit)
    }

    private fun File.toFileModel(): FileModel {
        val ext = extension
        val mime = if (ext.isNotEmpty()) {
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
        } else null

        return FileModel(
            name = name,
            absolutePath = absolutePath,
            size = if (isFile) length() else 0L,
            lastModified = lastModified(),
            isDirectory = isDirectory,
            extension = ext,
            isHidden = isHidden,
            mimeType = mime
        )
    }

    private fun scanMediaFiles(vararg paths: String) {
        if (paths.isEmpty()) return
        android.media.MediaScannerConnection.scanFile(context.applicationContext, paths, null, null)
    }

    private suspend fun finalizeMutation(vararg paths: String) {
        mediaStoreClient.invalidateCache(*paths)
        volumeProvider.invalidateCache()
        scanMediaFiles(*paths)
    }

    override fun getStandardFolders(): Map<String, String?> {
        val root = Environment.getExternalStorageDirectory()
        return mapOf(
            "DCIM" to File(root, Environment.DIRECTORY_DCIM).absolutePath,
            "Downloads" to File(root, Environment.DIRECTORY_DOWNLOADS).absolutePath,
            "Pictures" to File(root, Environment.DIRECTORY_PICTURES).absolutePath,
            "Documents" to File(root, Environment.DIRECTORY_DOCUMENTS).absolutePath,
            "Music" to File(root, Environment.DIRECTORY_MUSIC).absolutePath,
            "Movies" to File(root, Environment.DIRECTORY_MOVIES).absolutePath,
            "All Files" to null
        )
    }

    override suspend fun listFiles(path: String): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
            val directory = File(path)
            validatePath(directory).onFailure { return@withContext Result.failure(it) }

            if (!directory.exists() || !directory.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a valid directory"))
            }

            val files = directory.listFiles()?.map { it.toFileModel() } ?: emptyList()
            val sortedFiles = files.sortedWith(
                compareBy<FileModel> { !it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
            Result.success(sortedFiles)
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = withContext(Dispatchers.IO) {
        try {
            validateFileName(name).onFailure { return@withContext Result.failure(it) }
            val newDir = File(parentPath, name)
            validatePath(newDir).onFailure { return@withContext Result.failure(it) }

            if (newDir.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Directory already exists"))
            }
            if (newDir.mkdirs()) {
                finalizeMutation(newDir.absolutePath)
                Result.success(newDir.toFileModel())
            } else {
                Result.failure(Exception("Failed to create directory"))
            }
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = withContext(Dispatchers.IO) {
        try {
            validateFileName(name).onFailure { return@withContext Result.failure(it) }
            val newFile = File(parentPath, name)
            validatePath(newFile).onFailure { return@withContext Result.failure(it) }

            if (newFile.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File already exists"))
            }
            if (newFile.createNewFile()) {
                finalizeMutation(newFile.absolutePath)
                Result.success(newFile.toFileModel())
            } else {
                Result.failure(Exception("Failed to create file"))
            }
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val scannedPaths = mutableListOf<String>()
            for (path in paths) {
                val file = File(path)
                validatePath(file).onFailure { return@withContext Result.failure(it) }

                if (!file.exists()) continue

                val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!success) {
                    if (scannedPaths.isNotEmpty()) {
                        finalizeMutation(*scannedPaths.toTypedArray())
                    }
                    return@withContext Result.failure(Exception("Failed to permanently delete file: ${file.name}"))
                }
                scannedPaths.add(path)
            }
            finalizeMutation(*scannedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = withContext(Dispatchers.IO) {
         try {
             validateFileName(newName).onFailure { return@withContext Result.failure(it) }

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
                 finalizeMutation(file.absolutePath, newFile.absolutePath)
                 Result.success(newFile.toFileModel())
             } else {
                 Result.failure(Exception("Failed to rename file"))
             }
         } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun detectCopyConflicts(
        sourcePaths: List<String>,
        destinationPath: String
    ): Result<List<FileConflict>> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destinationPath)
            validatePath(destDir).onFailure { return@withContext Result.failure(it) }

            if (!destDir.exists() || !destDir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Destination must be a valid directory"))
            }

            val conflicts = mutableListOf<FileConflict>()
            for (path in sourcePaths) {
                val sourceFile = File(path)
                if (!sourceFile.exists()) continue

                val targetFile = File(destDir, sourceFile.name)
                if (targetFile.exists()) {
                    conflicts.add(
                        FileConflict(
                            sourcePath = sourceFile.absolutePath,
                            sourceFile = sourceFile.toFileModel(),
                            existingFile = targetFile.toFileModel()
                        )
                    )
                }
            }
            Result.success(conflicts)
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destinationPath)
            validatePath(destDir).onFailure { return@withContext Result.failure(it) }

            if (!destDir.exists() || !destDir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Destination must be a valid directory"))
            }

            val scannedPaths = mutableListOf<String>()
            for (path in sourcePaths) {
                val sourceFile = File(path)
                validatePath(sourceFile).onFailure { return@withContext Result.failure(it) }

                if (!sourceFile.exists()) continue

                if (sourceFile.isDirectory) {
                    val sourcePathStr = sourceFile.canonicalPath
                    val destPathStr = destDir.canonicalPath
                    if (destPathStr == sourcePathStr || destPathStr.startsWith("$sourcePathStr${File.separator}")) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Cannot copy a directory into itself or one of its subdirectories")
                        )
                    }
                }

                var targetFile = File(destDir, sourceFile.name)
                validatePath(targetFile).onFailure { return@withContext Result.failure(it) }

                if (targetFile.exists() || sourceFile.absolutePath == targetFile.absolutePath) {
                    when (resolutions[sourceFile.absolutePath]) {
                        ConflictResolution.SKIP -> continue
                        ConflictResolution.KEEP_BOTH -> {
                            targetFile = FileConflictNameGenerator.generateKeepBothTarget(destDir, sourceFile)
                        }
                        ConflictResolution.REPLACE -> {
                            if (sourceFile.absolutePath == targetFile.absolutePath) {
                                continue
                            }
                        }
                        null -> {
                            if (sourceFile.absolutePath == targetFile.absolutePath) {
                                continue
                            }
                        }
                    }
                }

                val overwrite = resolutions[sourceFile.absolutePath] == ConflictResolution.REPLACE
                if (sourceFile.isDirectory) {
                    sourceFile.copyRecursively(targetFile, overwrite = overwrite)
                } else {
                    sourceFile.copyTo(targetFile, overwrite = overwrite)
                }
                scannedPaths.add(targetFile.absolutePath)
            }
            finalizeMutation(*scannedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val destDir = File(destinationPath)
            validatePath(destDir).onFailure { return@withContext Result.failure(it) }

            if (!destDir.exists() || !destDir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Destination must be a valid directory"))
            }

            val scannedPaths = mutableListOf<String>()
            for (path in sourcePaths) {
                val sourceFile = File(path)
                validatePath(sourceFile).onFailure { return@withContext Result.failure(it) }

                if (!sourceFile.exists()) continue

                if (sourceFile.isDirectory) {
                    val sourcePathStr = sourceFile.canonicalPath
                    val destPathStr = destDir.canonicalPath
                    if (destPathStr == sourcePathStr || destPathStr.startsWith("$sourcePathStr${File.separator}")) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Cannot move a directory into itself or one of its subdirectories")
                        )
                    }
                }

                var targetFile = File(destDir, sourceFile.name)
                validatePath(targetFile).onFailure { return@withContext Result.failure(it) }

                if (sourceFile.absolutePath == targetFile.absolutePath) continue

                if (targetFile.exists()) {
                    when (resolutions[sourceFile.absolutePath]) {
                        ConflictResolution.SKIP -> continue
                        ConflictResolution.KEEP_BOTH -> {
                            targetFile = FileConflictNameGenerator.generateKeepBothTarget(destDir, sourceFile)
                        }
                        ConflictResolution.REPLACE -> {
                            if (targetFile.isDirectory) targetFile.deleteRecursively() else targetFile.delete()
                        }
                        null -> {
                            return@withContext Result.failure(Exception("Move conflict: no resolution for existing target"))
                        }
                    }
                }

                val success = sourceFile.renameTo(targetFile)
                if (!success) {
                    val overwrite = resolutions[sourceFile.absolutePath] == ConflictResolution.REPLACE
                    try {
                        if (sourceFile.isDirectory) {
                            sourceFile.copyRecursively(targetFile, overwrite = overwrite)
                            if (!sourceFile.deleteRecursively()) {
                                targetFile.deleteRecursively()
                                return@withContext Result.failure(Exception("Failed to delete source directory after copy"))
                            }
                        } else {
                            sourceFile.copyTo(targetFile, overwrite = overwrite)
                            if (!sourceFile.delete()) {
                                targetFile.delete()
                                return@withContext Result.failure(Exception("Failed to delete source file after copy"))
                            }
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (targetFile.isDirectory) targetFile.deleteRecursively() else targetFile.delete()
                        if (scannedPaths.isNotEmpty()) {
                            finalizeMutation(*scannedPaths.toTypedArray())
                        }
                        return@withContext Result.failure(e)
                    }
                }
                scannedPaths.add(sourceFile.absolutePath)
                scannedPaths.add(targetFile.absolutePath)
            }
            finalizeMutation(*scannedPaths.toTypedArray())
            Result.success(Unit)
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }
}
