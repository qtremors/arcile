package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.domain.FileOperationException
import android.content.Context
import android.os.Environment
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.StorageNodeCapabilities
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.File

interface DirectoryListingDataSource {
    fun list(path: StorageNodePath, pageSize: Int = ListingPage.DEFAULT_PAGE_SIZE): Flow<ListingPage>
}

interface FileSystemDataSource : DirectoryListingDataSource {
    fun getStandardFolders(): Map<String, String?>
    suspend fun listFiles(path: String): Result<List<FileModel>>
    suspend fun createDirectory(parentPath: String, name: String): Result<FileModel>
    suspend fun createFile(parentPath: String, name: String): Result<FileModel>
    suspend fun deletePermanently(paths: List<String>): Result<Unit>
    suspend fun renameFile(path: String, newName: String): Result<FileModel>
    suspend fun detectCopyConflicts(sourcePaths: List<String>, destinationPath: String): Result<List<FileConflict>>
    suspend fun copyFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>, onProgress: ((BulkFileOperationProgress) -> Unit)? = null): Result<Unit>
    suspend fun moveFiles(sourcePaths: List<String>, destinationPath: String, resolutions: Map<String, ConflictResolution>, onProgress: ((BulkFileOperationProgress) -> Unit)? = null): Result<Unit>
    suspend fun createFakeFile(parentPath: String, name: String, size: Long, onProgress: ((BulkFileOperationProgress) -> Unit)? = null): Result<FileModel>
}

class DefaultFileSystemDataSource(
    private val context: Context,
    private val volumeProvider: VolumeProvider,
    private val mutationFinalizer: MutationFinalizer,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    ),
    private val conflictDetector: FileConflictDetector = FileConflictDetector(),
    mutationJournal: MutationJournal = NoOpMutationJournal(),
    private val transferEngine: FileTransferEngine = FileTransferEngine(validatePath = { file ->
        PathSafety.validatePath(file, volumeProvider.activeStorageRoots)
    }, validateMutationPath = { file ->
        PathSafety.validatePath(file, volumeProvider.activeStorageRoots, PathSafety.OperationPolicy.RECURSIVE_MUTATE)
    }, mutationJournal = mutationJournal)
) : FileSystemDataSource {
    private val listingComparator = compareBy<FileModel> { !it.isDirectory }
        .thenBy { it.name.lowercase() }

    private fun validatePath(file: File): Result<Unit> {
        return PathSafety.validatePath(file, volumeProvider.activeStorageRoots)
    }

    private fun validateDestructivePath(file: File): Result<Unit> {
        return PathSafety.validatePath(file, volumeProvider.activeStorageRoots, PathSafety.OperationPolicy.RECURSIVE_MUTATE)
    }

    private fun validatedDestructiveRef(file: File): Result<StorageNodeRef> =
        runCatching { StorageNodeRef.local(file.absolutePath) }
            .fold(
                onSuccess = { ref ->
                    validateDestructivePath(file).map { ref }
                },
                onFailure = { Result.failure(it) }
            )

    private fun validateFileName(name: String): Result<Unit> {
        if (name.contains('/') || name.contains('\\') || name.contains("..") || name.contains('\u0000')) {
            return Result.failure(IllegalArgumentException("Invalid file name: must not contain path separators or '..'"))
        }
        return Result.success(Unit)
    }

    private fun File.normalizedPath(): String =
        absoluteFile.normalize().absolutePath.trimEnd(File.separatorChar).lowercase()

    private fun temporaryCaseRenameTarget(parent: File, originalName: String): File {
        repeat(100) { index ->
            val suffix = if (index == 0) "" else "-$index"
            val candidate = File(parent, ".$originalName.arcile-case-rename$suffix.tmp")
            if (!candidate.exists()) return candidate
        }
        throw IllegalStateException("Unable to reserve temporary rename target")
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
            mimeType = mime,
            nodeRef = StorageNodeRef.local(
                path = absolutePath,
                capabilities = StorageNodeCapabilities(
                    canRead = true,
                    canWrite = true,
                    canDelete = true,
                    canTrash = false,
                    canArchive = isFile
                )
            )
        )
    }

    private suspend fun finalizeMutation(vararg paths: String) {
        mutationFinalizer.finalize(*paths)
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

    override fun list(path: StorageNodePath, pageSize: Int): Flow<ListingPage> = flow {
        try {
            val directory = File(path.absolutePath)
            validatePath(directory).onFailure {
                emit(ListingPage.failed(path, it))
                return@flow
            }

            if (!directory.exists() || !directory.isDirectory) {
                emit(ListingPage.failed(path, IllegalArgumentException("Path is not a valid directory")))
                return@flow
            }

            val children = directory.listFiles()
                ?: run {
                    emit(ListingPage(path = path, files = emptyList(), pageIndex = 0, isComplete = true))
                    return@flow
                }

            if (children.isEmpty()) {
                emit(ListingPage(path = path, files = emptyList(), pageIndex = 0, isComplete = true))
                return@flow
            }

            val boundedPageSize = pageSize.coerceIn(1, ListingPage.MAX_PAGE_SIZE)
            children.asSequence()
                .chunked(boundedPageSize)
                .forEachIndexed { index, chunk ->
                    kotlinx.coroutines.currentCoroutineContext().ensureActive()
                    val files = chunk.map { it.toFileModel() }.sortedWith(listingComparator)
                    val complete = (index + 1) * boundedPageSize >= children.size
                    emit(
                        ListingPage(
                            path = path,
                            files = files,
                            pageIndex = index,
                            isComplete = complete
                        )
                    )
                }
        } catch (e: SecurityException) {
            emit(ListingPage.failed(path, FileOperationException.AccessDenied(cause = e)))
        } catch (e: java.io.IOException) {
            emit(ListingPage.failed(path, FileOperationException.IOError(cause = e)))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(ListingPage.failed(path, FileOperationException.Unknown(cause = e)))
        }
    }.flowOn(dispatchers.io)

    override suspend fun listFiles(path: String): Result<List<FileModel>> = withContext(dispatchers.io) {
        try {
            val nodePath = StorageNodePath.of(path)
            val pages = list(nodePath).toList()
            pages.firstOrNull { it.error != null }?.error?.let { error ->
                return@withContext Result.failure(error)
            }
            Result.success(pages.flatMap { it.files }.sortedWith(listingComparator))
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun createDirectory(parentPath: String, name: String): Result<FileModel> = withContext(dispatchers.io) {
        try {
            validateFileName(name).onFailure { return@withContext Result.failure(it) }
            val newDir = File(parentPath, name)
            validatedDestructiveRef(newDir).onFailure { return@withContext Result.failure(it) }

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

    override suspend fun createFile(parentPath: String, name: String): Result<FileModel> = withContext(dispatchers.io) {
        try {
            validateFileName(name).onFailure { return@withContext Result.failure(it) }
            val newFile = File(parentPath, name)
            validatedDestructiveRef(newFile).onFailure { return@withContext Result.failure(it) }

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


    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = withContext(dispatchers.io) {
        try {
            val scannedPaths = mutableListOf<String>()
            for (path in paths) {
                val file = File(path)
                validatedDestructiveRef(file).onFailure { return@withContext Result.failure(it) }

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

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = withContext(dispatchers.io) {
         try {
             validateFileName(newName).onFailure { return@withContext Result.failure(it) }

             val file = File(path)
             validatedDestructiveRef(file).onFailure { return@withContext Result.failure(it) }

             val newFile = File(file.parent, newName)
             validatedDestructiveRef(newFile).onFailure { return@withContext Result.failure(it) }

             if (!file.exists()) {
                 return@withContext Result.failure(IllegalArgumentException("File does not exist"))
             }
             val isSamePathTarget = file.normalizedPath() == newFile.normalizedPath()
             val isCaseOnlyRename = isSamePathTarget && file.name != newName && file.name.equals(newName, ignoreCase = true)
             if (newFile.exists() && !isSamePathTarget) {
                 return@withContext Result.failure(IllegalArgumentException("File with that name already exists"))
             }

             if (file.name == newName) {
                 return@withContext Result.success(file.toFileModel())
             }

             if (file.renameTo(newFile)) {
                 finalizeMutation(file.absolutePath, newFile.absolutePath)
                 Result.success(newFile.toFileModel())
             } else if (isCaseOnlyRename) {
                 val tempFile = temporaryCaseRenameTarget(file.parentFile ?: return@withContext Result.failure(Exception("Failed to rename file")), file.name)
                 validatedDestructiveRef(tempFile).onFailure { return@withContext Result.failure(it) }
                 if (!file.renameTo(tempFile)) {
                     return@withContext Result.failure(Exception("Failed to rename file"))
                 }
                 if (tempFile.renameTo(newFile)) {
                     finalizeMutation(file.absolutePath, tempFile.absolutePath, newFile.absolutePath)
                     Result.success(newFile.toFileModel())
                 } else {
                     tempFile.renameTo(file)
                     Result.failure(Exception("Failed to rename file"))
                 }
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
    ): Result<List<FileConflict>> = withContext(dispatchers.io) {
        try {
            val destDir = File(destinationPath)
            validatedDestructiveRef(destDir).onFailure { return@withContext Result.failure(it) }

            if (!destDir.exists() || !destDir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Destination must be a valid directory"))
            }

            Result.success(conflictDetector.detectCopyConflicts(sourcePaths, destDir))
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
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val destDir = File(destinationPath)
            validatedDestructiveRef(destDir).onFailure { return@withContext Result.failure(it) }

            if (!destDir.exists() || !destDir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Destination must be a valid directory"))
            }

            val scannedPaths = transferEngine.copyFiles(sourcePaths, destDir, resolutions, onProgress)
                .getOrElse { return@withContext Result.failure(it) }
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
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = withContext(dispatchers.io) {
        try {
            val destDir = File(destinationPath)
            validatedDestructiveRef(destDir).onFailure { return@withContext Result.failure(it) }

            if (!destDir.exists() || !destDir.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Destination must be a valid directory"))
            }

            val scannedPaths = transferEngine.moveFiles(sourcePaths, destDir, resolutions, onProgress)
                .getOrElse { return@withContext Result.failure(it) }
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

    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> = withContext(dispatchers.io) {
        try {
            validateFileName(name).getOrThrow()
            val parentFile = File(parentPath)
            validatedDestructiveRef(parentFile).getOrThrow()

            val targetFile = File(parentFile, name)
            validatedDestructiveRef(targetFile).getOrThrow()
            if (targetFile.exists()) {
                return@withContext Result.failure(Exception("File already exists"))
            }

            val bufferSize = 1024 * 1024 // 1MB
            val buffer = ByteArray(bufferSize)
            val random = java.util.Random()
            
            targetFile.outputStream().use { output ->
                var remaining = size
                var totalWritten = 0L
                
                while (remaining > 0) {
                    ensureActive()
                    val toWrite = remaining.coerceAtMost(bufferSize.toLong()).toInt()
                    random.nextBytes(buffer) // Pseudo-randomize
                    output.write(buffer, 0, toWrite)
                    
                    remaining -= toWrite
                    totalWritten += toWrite
                    
                    onProgress?.invoke(
                        BulkFileOperationProgress(
                            completedItems = 0,
                            totalItems = 1,
                            currentPath = targetFile.absolutePath,
                            bytesCopied = totalWritten,
                            totalBytes = size
                        )
                    )
                }
            }

            finalizeMutation(targetFile.absolutePath)
            Result.success(targetFile.toFileModel())
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(e)
        }
    }
}
