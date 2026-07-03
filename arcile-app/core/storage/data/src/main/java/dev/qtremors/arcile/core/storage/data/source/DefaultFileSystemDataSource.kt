package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.domain.FileOperationException
import android.content.Context
import android.os.Environment
import dev.qtremors.arcile.core.storage.data.MutationFinalizer
import dev.qtremors.arcile.core.storage.data.MutationJournal
import dev.qtremors.arcile.core.storage.data.NoOpMutationJournal
import dev.qtremors.arcile.core.storage.data.db.StorageNodeDao
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.util.PathSafety
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import dev.qtremors.arcile.di.ArcileDispatchers as LegacyArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.BatchMutationFailure
import dev.qtremors.arcile.core.storage.domain.BatchMutationResult
import dev.qtremors.arcile.core.storage.domain.ConflictResolution
import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.operation.BulkFileOperationProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.withContext
import java.io.File


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
    constructor(
        context: Context,
        volumeProvider: VolumeProvider,
        mutationFinalizer: MutationFinalizer,
        dispatchers: LegacyArcileDispatchers,
        storageNodeDao: StorageNodeDao? = null,
        mutationJournal: MutationJournal = NoOpMutationJournal()
    ) : this(
        context = context,
        volumeProvider = volumeProvider,
        mutationFinalizer = mutationFinalizer,
        dispatchers = ArcileDispatchers(
            io = dispatchers.io,
            default = dispatchers.default,
            main = dispatchers.main,
            storage = dispatchers.storage
        ),
        storageNodeDao = storageNodeDao,
        mutationJournal = mutationJournal
    )

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
    private val fileListingComparator = compareBy<File> { !it.isDirectory }
        .thenBy { it.name.lowercase() }
    private val fileModelMapper = LocalFileModelMapper()
    private val secureFileEraser = SecureFileEraser {
        secureOverwriteOverrideForTest
    }
    private val transferCoordinator = LocalFileTransferCoordinator(
        dispatchers = dispatchers,
        conflictDetector = conflictDetector,
        transferEngine = transferEngine,
        validateDestination = { file -> validatedDestructiveRef(file).map { Unit } },
        finalizeMutation = { paths -> finalizeMutation(*paths.toTypedArray()) }
    )
    private val syntheticFileCreator = SyntheticFileCreator(
        dispatchers = dispatchers,
        validateName = ::validateFileName,
        validatePath = { file -> validatedDestructiveRef(file).map { Unit } },
        finalizeMutation = { path -> finalizeMutation(path) },
        fileModelMapper = fileModelMapper
    )

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

            val scannedAt = System.currentTimeMillis()
            storageNodeDao?.run {
                val volumes = volumeProvider.currentVolumes()
                deleteChildren(directory.absolutePath)
                upsert(children.map { child ->
                    fileModelMapper.toStorageNodeEntity(
                        file = child,
                        volumeId = volumes.firstOrNull { volume -> child.absolutePath.startsWith(volume.path) }?.id,
                        scannedAt = scannedAt
                    )
                })
            }

            emitListingPages(
                path = path,
                files = children.asSequence()
                .sortedWith(fileListingComparator)
                .map(fileModelMapper::toFileModel)
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
                Result.success(fileModelMapper.toFileModel(newDir))
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
                Result.success(fileModelMapper.toFileModel(newFile))
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

                val shredFailures = runCatching {
                    secureFileEraser.shredRecursively(file)
                }.getOrElse { error ->
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
                 return@withContext Result.success(fileModelMapper.toFileModel(file))
             }

             if (file.renameTo(newFile)) {
                 finalizeMutation(file.absolutePath, newFile.absolutePath)
                 Result.success(fileModelMapper.toFileModel(newFile))
             } else if (isCaseOnlyRename) {
                 val tempFile = temporaryCaseRenameTarget(file.parentFile ?: return@withContext Result.failure(Exception("Failed to rename file")), file.name)
                 validatedDestructiveRef(tempFile).onFailure { return@withContext Result.failure(it) }
                 if (!file.renameTo(tempFile)) {
                     return@withContext Result.failure(Exception("Failed to rename file"))
                 }
                 if (tempFile.renameTo(newFile)) {
                     finalizeMutation(file.absolutePath, tempFile.absolutePath, newFile.absolutePath)
                     Result.success(fileModelMapper.toFileModel(newFile))
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
    ): Result<List<FileConflict>> =
        transferCoordinator.detectCopyConflicts(sourcePaths, destinationPath)

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = transferCoordinator.copyFiles(
        sourcePaths, destinationPath, resolutions, onProgress
    )

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationPath: String,
        resolutions: Map<String, ConflictResolution>,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<Unit> = transferCoordinator.moveFiles(
        sourcePaths, destinationPath, resolutions, onProgress
    )

    override suspend fun createFakeFile(
        parentPath: String,
        name: String,
        size: Long,
        onProgress: ((BulkFileOperationProgress) -> Unit)?
    ): Result<FileModel> =
        syntheticFileCreator.create(parentPath, name, size, onProgress)
}
