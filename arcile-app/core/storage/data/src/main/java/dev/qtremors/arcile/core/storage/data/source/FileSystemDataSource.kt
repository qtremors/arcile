package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.domain.FileOperationException
import android.content.Context
import android.os.Environment
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.data.db.StorageNodeDao
import dev.qtremors.arcile.core.storage.data.db.StorageNodeEntity
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.BatchMutationFailure
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
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
import java.io.FileOutputStream


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
    private val storageNodeDao: StorageNodeDao? = null,
    mutationJournal: MutationJournal = NoOpMutationJournal(),
    private val transferEngine: FileTransferEngine = FileTransferEngine(validatePath = { file ->
        PathSafety.validatePath(file, volumeProvider.activeStorageRoots)
    }, validateMutationPath = { file ->
        PathSafety.validatePath(file, volumeProvider.activeStorageRoots, PathSafety.OperationPolicy.RECURSIVE_MUTATE)
    }, mutationJournal = mutationJournal)
) : FileSystemDataSource {
    internal sealed class SecureOverwriteResult {
        data object Success : SecureOverwriteResult()
        data class Failure(val message: String, val causeType: String = "SecureOverwriteFailed") : SecureOverwriteResult()
    }

    companion object {
        internal var secureOverwriteOverrideForTest: ((File) -> SecureOverwriteResult)? = null

        internal fun resetSecureOverwriteOverrideForTest() {
            secureOverwriteOverrideForTest = null
        }
    }

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

    private fun File.toStorageNodeEntity(volumeId: String?, scannedAt: Long): StorageNodeEntity =
        StorageNodeEntity(
            path = absolutePath,
            parentPath = parentFile?.absolutePath,
            name = name,
            extension = extension.lowercase(),
            mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase()),
            sizeBytes = if (isFile) length() else 0L,
            lastModified = lastModified(),
            isDirectory = isDirectory,
            isHidden = isHidden,
            contentUri = null,
            mediaStoreId = null,
            mediaStoreVolume = null,
            volumeId = volumeId,
            width = null,
            height = null,
            dateAdded = scannedAt,
            scannedAt = scannedAt
        )

    private fun StorageNodeEntity.toFileModel(): FileModel =
        FileModel(
            name = name,
            absolutePath = path,
            size = sizeBytes,
            lastModified = lastModified,
            isDirectory = isDirectory,
            extension = extension.orEmpty(),
            isHidden = isHidden,
            mimeType = mimeType,
            nodeRef = StorageNodeRef.local(
                path = path,
                capabilities = StorageNodeCapabilities(
                    canRead = true,
                    canWrite = true,
                    canDelete = true,
                    canTrash = false,
                    canArchive = !isDirectory
                )
            )
        )

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

            storageNodeDao?.listChildren(directory.absolutePath)
                ?.takeIf { it.isNotEmpty() }
                ?.let { cachedChildren ->
                    emitPages(
                        path = path,
                        files = cachedChildren.map { it.toFileModel() },
                        pageSize = pageSize
                    ) { page -> emit(page) }
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

            val scannedAt = System.currentTimeMillis()
            storageNodeDao?.run {
                val volumes = volumeProvider.currentVolumes()
                deleteChildren(directory.absolutePath)
                upsert(children.map { child ->
                    child.toStorageNodeEntity(
                        volumeId = volumes.firstOrNull { volume -> child.absolutePath.startsWith(volume.path) }?.id,
                        scannedAt = scannedAt
                    )
                })
            }

            emitPages(
                path = path,
                files = children.asSequence()
                .sortedWith(fileListingComparator)
                .map { it.toFileModel() }
                .toList(),
                pageSize = pageSize
            ) { page -> emit(page) }
        } catch (e: SecurityException) {
            emit(ListingPage.failed(path, FileOperationException.AccessDenied(cause = e)))
        } catch (e: java.io.IOException) {
            emit(ListingPage.failed(path, FileOperationException.IOError(cause = e)))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            emit(ListingPage.failed(path, FileOperationException.Unknown(cause = e)))
        }
    }.flowOn(dispatchers.io)

    private suspend fun emitPages(
        path: StorageNodePath,
        files: List<FileModel>,
        pageSize: Int,
        emitPage: suspend (ListingPage) -> Unit
    ) {
        if (files.isEmpty()) {
            emitPage(ListingPage(path = path, files = emptyList(), pageIndex = 0, isComplete = true))
            return
        }
        val boundedPageSize = pageSize.coerceIn(1, ListingPage.MAX_PAGE_SIZE)
        files.chunked(boundedPageSize).forEachIndexed { index, chunk ->
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            emitPage(
                ListingPage(
                    path = path,
                    files = chunk,
                    pageIndex = index,
                    isComplete = (index + 1) * boundedPageSize >= files.size
                )
            )
        }
    }

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


    override suspend fun deletePermanently(paths: List<String>): Result<Unit> =
        deletePermanentlyDetailed(paths).fold(
            onSuccess = { it.requireCompleteSuccess("Permanent delete") },
            onFailure = { Result.failure(it) }
        )

    override suspend fun deletePermanentlyDetailed(paths: List<String>): Result<BatchMutationResult> = withContext(dispatchers.io) {
        try {
            val succeededPaths = mutableListOf<String>()
            val skippedPaths = mutableListOf<String>()
            val failedItems = mutableListOf<BatchMutationFailure>()
            val cleanupRequiredPaths = mutableListOf<String>()
            for (path in paths) {
                val file = File(path)
                val validation = validatedDestructiveRef(file)
                if (validation.isFailure) {
                    val error = validation.exceptionOrNull() ?: IllegalArgumentException("Invalid path")
                    failedItems += error.toBatchFailure(file)
                    continue
                }

                if (!file.exists()) {
                    skippedPaths += path
                    continue
                }

                val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!success) {
                    failedItems += BatchMutationFailure(
                        path = path,
                        displayName = file.name.ifBlank { path },
                        message = "Failed to permanently delete file: ${file.name}",
                        causeType = "DeleteFailed",
                        cleanupRequired = true
                    )
                    cleanupRequiredPaths += path
                    continue
                }
                succeededPaths.add(path)
            }
            finalizeMutation(*succeededPaths.toTypedArray())
            Result.success(
                BatchMutationResult(
                    succeededPaths = succeededPaths,
                    skippedPaths = skippedPaths,
                    failedItems = failedItems,
                    cleanupRequiredPaths = cleanupRequiredPaths
                )
            )
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun shred(paths: List<String>): Result<Unit> =
        shredDetailed(paths).fold(
            onSuccess = { it.requireCompleteSuccess("Secure shred") },
            onFailure = { Result.failure(it) }
        )

    override suspend fun shredDetailed(paths: List<String>): Result<BatchMutationResult> = withContext(dispatchers.io) {
        try {
            val succeededPaths = mutableListOf<String>()
            val skippedPaths = mutableListOf<String>()
            val failedItems = mutableListOf<BatchMutationFailure>()
            val cleanupRequiredPaths = mutableListOf<String>()
            for (path in paths) {
                val file = File(path)
                val validation = validatedDestructiveRef(file)
                if (validation.isFailure) {
                    val error = validation.exceptionOrNull() ?: IllegalArgumentException("Invalid path")
                    failedItems += error.toBatchFailure(file)
                    continue
                }

                if (!file.exists()) {
                    skippedPaths += path
                    continue
                }

                val shredFailures = runCatching { shredRecursively(file) }.getOrElse { error ->
                    listOf(error.toBatchFailure(file, cleanupRequired = true))
                }
                if (shredFailures.isNotEmpty()) {
                    failedItems += shredFailures
                    cleanupRequiredPaths += path
                    continue
                }

                val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!success) {
                    failedItems += BatchMutationFailure(
                        path = path,
                        displayName = file.name.ifBlank { path },
                        message = "Failed to securely shred file: ${file.name}",
                        causeType = "ShredDeleteFailed",
                        cleanupRequired = true
                    )
                    cleanupRequiredPaths += path
                    continue
                }
                succeededPaths.add(path)
            }
            finalizeMutation(*succeededPaths.toTypedArray())
            Result.success(
                BatchMutationResult(
                    succeededPaths = succeededPaths,
                    skippedPaths = skippedPaths,
                    failedItems = failedItems,
                    cleanupRequiredPaths = cleanupRequiredPaths
                )
            )
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    private fun Throwable.toBatchFailure(file: File, cleanupRequired: Boolean = false): BatchMutationFailure =
        BatchMutationFailure(
            path = file.absolutePath,
            displayName = file.name.ifBlank { file.absolutePath },
            message = message ?: javaClass.simpleName,
            causeType = javaClass.simpleName,
            cleanupRequired = cleanupRequired
        )

    private val fileListingComparator = compareBy<File> { !it.isDirectory }
        .thenBy { it.name.lowercase() }

    private fun shredRecursively(file: File): List<BatchMutationFailure> {
        if (file.isDirectory) {
            val children = file.listFiles() ?: return listOf(
                BatchMutationFailure(
                    path = file.absolutePath,
                    displayName = file.name.ifBlank { file.absolutePath },
                    message = "Unable to inspect directory before secure shred: ${file.name}",
                    causeType = "SecureOverwriteFailed",
                    cleanupRequired = true
                )
            )
            return children.flatMap { shredRecursively(it) }
        } else if (file.isFile) {
            return when (val result = overwriteSecurely(file)) {
                SecureOverwriteResult.Success -> emptyList()
                is SecureOverwriteResult.Failure -> listOf(
                    BatchMutationFailure(
                        path = file.absolutePath,
                        displayName = file.name.ifBlank { file.absolutePath },
                        message = result.message,
                        causeType = result.causeType,
                        cleanupRequired = true
                    )
                )
            }
        }
        return emptyList()
    }

    private fun overwriteSecurely(file: File): SecureOverwriteResult {
        secureOverwriteOverrideForTest?.let { return it(file) }

        if (!file.canWrite()) {
            return SecureOverwriteResult.Failure("Unable to securely overwrite ${file.name}: file is not writable")
        }
        val length = file.length()
        if (length <= 0) return SecureOverwriteResult.Success

        val bufferSize = 64 * 1024
        val buffer = ByteArray(bufferSize)
        return try {
            FileOutputStream(file, false).use { output ->
                var remaining = length
                while (remaining > 0) {
                    val toWrite = remaining.coerceAtMost(bufferSize.toLong()).toInt()
                    output.write(buffer, 0, toWrite)
                    remaining -= toWrite
                }
                output.flush()
                output.fd.sync()
            }
            SecureOverwriteResult.Success
        } catch (e: Exception) {
            SecureOverwriteResult.Failure(
                message = "Unable to securely overwrite ${file.name}: ${e.message ?: e.javaClass.simpleName}",
                causeType = "SecureOverwriteFailed"
            )
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
