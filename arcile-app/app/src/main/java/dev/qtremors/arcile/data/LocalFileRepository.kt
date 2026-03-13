package dev.qtremors.arcile.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageMountState
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.StorageScope
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageVolume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.Locale

class LocalFileRepository(private val context: Context) : FileRepository {

    private val appContext = context.applicationContext
    private val storageManager = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    private var activeStorageVolumes: List<StorageVolume> = discoverStorageVolumes()
    private var activeStorageRoots: List<String> = activeStorageVolumes.map { it.path }

    private fun discoverStorageVolumes(): List<StorageVolume> {
        val discovered = linkedMapOf<String, StorageVolume>()
        val primaryPath = Environment.getExternalStorageDirectory().canonicalPath

        fun addVolume(
            path: String,
            platformVolume: android.os.storage.StorageVolume? = null
        ) {
            runCatching {
                val canonicalPath = File(path).canonicalPath
                val rootFile = File(canonicalPath)
                if (!rootFile.exists()) return

                val stat = StatFs(canonicalPath)
                val totalBytes = stat.totalBytes
                val freeBytes = stat.availableBytes
                val isPrimary = canonicalPath == primaryPath
                val isRemovable = platformVolume?.isRemovable ?: !isPrimary
                val preferredName = platformVolume?.getDescription(appContext)
                val fallbackName = rootFile.name.takeIf { it.isNotBlank() }
                    ?: canonicalPath.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: if (isPrimary) "Internal Storage" else "External Storage"
                val id = buildString {
                    append(if (isPrimary) "primary" else "volume")
                    append(':')
                    append(platformVolume?.uuid ?: canonicalPath.lowercase(Locale.US))
                }

                discovered[id] = StorageVolume(
                    id = id,
                    name = preferredName?.takeIf { it.isNotBlank() } ?: fallbackName,
                    path = canonicalPath,
                    totalBytes = totalBytes,
                    freeBytes = freeBytes,
                    isPrimary = isPrimary,
                    isRemovable = isRemovable,
                    mountState = StorageMountState.MOUNTED
                )
            }
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            storageManager.storageVolumes.forEach { volume ->
                val directory = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    volume.directory
                } else {
                    null
                }
                directory?.let { addVolume(it.absolutePath, volume) }
            }
        }

        addVolume(primaryPath, storageManager.getStorageVolume(File(primaryPath)))

        ContextCompat.getExternalFilesDirs(appContext, null).forEach { file ->
            if (file == null) return@forEach
            val path = file.absolutePath
            val androidIndex = path.indexOf("/Android/data/")
            if (androidIndex == -1) return@forEach
            val volumeRoot = path.substring(0, androidIndex)
            addVolume(volumeRoot, storageManager.getStorageVolume(File(volumeRoot)))
        }

        val volumes = discovered.values.sortedWith(
            compareBy<StorageVolume> { !it.isPrimary }.thenBy { it.name.lowercase(Locale.US) }
        )
        activeStorageVolumes = volumes
        activeStorageRoots = volumes.map { it.path }
        return volumes
    }

    private fun currentVolumes(): List<StorageVolume> = discoverStorageVolumes()

    override fun observeStorageVolumes(): Flow<List<StorageVolume>> = callbackFlow {
        fun emitVolumes() {
            trySend(currentVolumes())
        }

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                emitVolumes()
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }

        emitVolumes()
        appContext.registerReceiver(receiver, filter)
        awaitClose { appContext.unregisterReceiver(receiver) }
    }.distinctUntilChanged()

    private fun resolveVolumeForPath(path: String, volumes: List<StorageVolume> = currentVolumes()): StorageVolume? {
        val canonicalPath = runCatching { File(path).canonicalPath }.getOrElse { return null }
        return volumes
            .sortedByDescending { it.path.length }
            .firstOrNull { canonicalPath == it.path || canonicalPath.startsWith(it.path + File.separator) }
    }

    private fun scopedVolumes(scope: StorageScope, volumes: List<StorageVolume> = currentVolumes()): List<StorageVolume> =
        when (scope) {
            StorageScope.AllStorage -> volumes
            is StorageScope.Volume -> volumes.filter { it.id == scope.volumeId }
            is StorageScope.Path -> volumes.filter { it.id == scope.volumeId }
            is StorageScope.Category -> volumes.filter { it.id == scope.volumeId }
        }

    private fun scopeRoot(scope: StorageScope, volumes: List<StorageVolume>): String? =
        when (scope) {
            StorageScope.AllStorage -> null
            is StorageScope.Volume -> volumes.find { it.id == scope.volumeId }?.path
            is StorageScope.Path -> scope.absolutePath
            is StorageScope.Category -> volumes.find { it.id == scope.volumeId }?.path
        }

    private fun matchesScope(path: String, scope: StorageScope, volumes: List<StorageVolume> = currentVolumes()): Boolean {
        val canonicalPath = runCatching { File(path).canonicalPath }.getOrNull() ?: return false
        return when (scope) {
            StorageScope.AllStorage -> resolveVolumeForPath(canonicalPath, volumes) != null
            is StorageScope.Volume -> {
                val volume = volumes.find { it.id == scope.volumeId } ?: return false
                canonicalPath == volume.path || canonicalPath.startsWith(volume.path + File.separator)
            }
            is StorageScope.Path -> {
                val volume = volumes.find { it.id == scope.volumeId } ?: return false
                (canonicalPath == scope.absolutePath || canonicalPath.startsWith(scope.absolutePath + File.separator)) &&
                    (canonicalPath == volume.path || canonicalPath.startsWith(volume.path + File.separator))
            }
            is StorageScope.Category -> {
                val volume = volumes.find { it.id == scope.volumeId } ?: return false
                canonicalPath == volume.path || canonicalPath.startsWith(volume.path + File.separator)
            }
        }
    }

    // path traversal guard — rejects paths that escape all known storage boundaries
    private fun validatePath(file: File): Result<Unit> {
        val canonical = file.canonicalPath
        val isAllowed = activeStorageRoots.any { root ->
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
        return FileModel(
            name = name,
            absolutePath = absolutePath,
            size = if (isFile) length() else 0L,
            lastModified = lastModified(),
            isDirectory = isDirectory,
            extension = extension,
            isHidden = isHidden
        )
    }

    override suspend fun getStorageVolumes(): Result<List<StorageVolume>> = withContext(Dispatchers.IO) {
        try {
            Result.success(currentVolumes())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listFiles(path: String): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
            val directory = File(path)
            validatePath(directory).onFailure { return@withContext Result.failure(it) }

            if (!directory.exists() || !directory.isDirectory) {
                return@withContext Result.failure(IllegalArgumentException("Path is not a valid directory"))
            }

            val files = directory.listFiles()?.map { it.toFileModel() } ?: emptyList()
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
            validateFileName(name).onFailure { return@withContext Result.failure(it) }
            val newDir = File(parentPath, name)
            validatePath(newDir).onFailure { return@withContext Result.failure(it) }

            if (newDir.exists()) {
                return@withContext Result.failure(IllegalArgumentException("Directory already exists"))
            }
            if (newDir.mkdirs()) {
                Result.success(newDir.toFileModel())
            } else {
                Result.failure(Exception("Failed to create directory"))
            }
        } catch (e: Exception) {
            Result.failure(e)
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
                Result.success(newFile.toFileModel())
            } else {
                Result.failure(Exception("Failed to create file"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deleteFile(path: String): Result<Unit> = withContext(Dispatchers.IO) {
        moveToTrash(listOf(path))
    }

    override suspend fun deletePermanently(paths: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            for (path in paths) {
                val file = File(path)
                validatePath(file).onFailure { return@withContext Result.failure(it) }

                if (!file.exists()) continue

                val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!success) {
                    return@withContext Result.failure(Exception("Failed to permanently delete file: ${file.name}"))
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renameFile(path: String, newName: String): Result<FileModel> = withContext(Dispatchers.IO) {
         try {
             // reject names containing path separators or traversal sequences
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
                 Result.success(newFile.toFileModel())
             } else {
                 Result.failure(Exception("Failed to rename file"))
             }
         } catch (e: Exception) {
             Result.failure(e)
         }
    }

    override suspend fun getRecentFiles(
        scope: StorageScope,
        limit: Int,
        minTimestamp: Long
    ): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
            val volumes = scopedVolumes(scope)
            if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            val filesList = mutableListOf<FileModel>()
            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Files.FileColumns.MIME_TYPE
            )

            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            val baseSelection = "(${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL)"
            val selection = if (minTimestamp > 0) "$baseSelection AND (${MediaStore.Files.FileColumns.DATE_ADDED} >= ? OR ${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?)" else baseSelection
            val selectionArgs = if (minTimestamp > 0) arrayOf((minTimestamp / 1000).toString(), (minTimestamp / 1000).toString()) else null
            val queryLimit = limit * 2

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext() && filesList.size < queryLimit) {
                    val path = cursor.getString(dataCol)
                    val name = cursor.getString(nameCol) ?: path?.let { File(it).name } ?: ""

                    if (path != null && !name.startsWith(".") && matchesScope(path, scope, volumes)) {
                        var size = cursor.getLong(sizeCol)
                        val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                        val dateModified = cursor.getLong(dateModifiedCol) * 1000L

                        val actualFile = File(path)
                        if (!actualFile.exists()) continue
                        if (size == 0L) {
                            size = actualFile.length()
                        }

                        val extension = path.substringAfterLast('.', "")

                        filesList.add(FileModel(
                            name = name,
                            absolutePath = path,
                            size = size,
                            lastModified = maxOf(dateAdded, dateModified), // Prioritize whichever is newer
                            isDirectory = false,
                            extension = extension,
                            isHidden = false
                        ))
                    }
                }
            }

            val finalSortedList = filesList.sortedByDescending { it.lastModified }.take(limit)

            Result.success(finalSortedList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStorageInfo(scope: StorageScope): Result<StorageInfo> = withContext(Dispatchers.IO) {
        getStorageVolumes().map { volumes -> StorageInfo(scopedVolumes(scope, volumes)) }
    }

    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> = withContext(Dispatchers.IO) {
        try {
            val volumes = scopedVolumes(scope)
            if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
                return@withContext Result.success(
                    FileCategories.all.map { CategoryStorage(it.name, 0L, it.extensions) }
                )
            }
            val extToCategoryIndex = mutableMapOf<String, Int>()
            FileCategories.all.forEachIndexed { index, cat ->
                cat.extensions.forEach { ext ->
                    extToCategoryIndex[ext] = index
                }
            }

            val sizes = LongArray(FileCategories.all.size)

            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.SIZE)
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol)
                    val size = cursor.getLong(sizeCol)
                    if (path != null && matchesScope(path, scope, volumes)) {
                        val ext = File(path).extension.lowercase()
                        val catIndex = extToCategoryIndex[ext]
                        if (catIndex != null) {
                            sizes[catIndex] += size
                        }
                    }
                }
            }

            val result = FileCategories.all.mapIndexed { index, cat ->
                CategoryStorage(
                    name = cat.name,
                    sizeBytes = sizes[index],
                    extensions = cat.extensions
                )
            }

            Result.success(result)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
            val volumes = scopedVolumes(scope)
            if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            val category = FileCategories.all.find { it.name == categoryName }
                ?: return@withContext Result.failure(IllegalArgumentException("Unknown category: $categoryName"))
            
            val extensions = category.extensions.map { it.lowercase() }.toSet()
            val filesList = mutableListOf<FileModel>()

            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
            
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol)
                    if (path != null && matchesScope(path, scope, volumes)) {
                        val file = File(path)
                        if (file.exists() && extensions.contains(file.extension.lowercase())) {
                            filesList.add(file.toFileModel())
                        }
                    }
                }
            }
            
            val sortedFiles = filesList.sortedBy { it.name.lowercase() }
            Result.success(sortedFiles)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchFiles(
        query: String,
        scope: StorageScope,
        filters: SearchFilters?
    ): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        val searchFilters = filters

        try {
            val volumes = scopedVolumes(scope)
            if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            val filesList = mutableListOf<FileModel>()
            
            if (scope is StorageScope.Path) {
                val rootDir = File(scope.absolutePath)
                validatePath(rootDir).onFailure { return@withContext Result.failure(it) }
                
                if (rootDir.exists() && rootDir.isDirectory) {
                    rootDir.walkTopDown()
                        .onEnter { dir ->
                            val dirCanonical = dir.canonicalPath
                            matchesScope(dirCanonical, scope, volumes)
                        }
                        .forEach { file ->
                            if (file.name.contains(query, ignoreCase = true) && !file.name.startsWith(".")) {
                                filesList.add(file.toFileModel())
                            }
                        }
                }
            } else {
                // Global MediaStore search
                val uri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
                val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
                val selectionArgs = arrayOf("%$query%")
                
                context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol)
                        if (path != null && matchesScope(path, scope, volumes)) {
                            val f = File(path)
                            if (f.exists() && !f.name.startsWith(".")) {
                                filesList.add(f.toFileModel())
                            }
                        }
                    }
                }
            }
            
            // Apply Filters sequentially if present
            var resultList = filesList.toList()
            
            searchFilters?.let { sf ->
                if (sf.itemType == "Files") {
                    resultList = resultList.filter { !it.isDirectory }
                } else if (sf.itemType == "Folders") {
                    resultList = resultList.filter { it.isDirectory }
                }
                
                if (sf.fileType != null && sf.fileType != "All") {
                    val category = FileCategories.all.find { it.name == sf.fileType }
                    if (category != null) {
                        val exts = category.extensions.map { it.lowercase() }.toSet()
                        resultList = resultList.filter { !it.isDirectory && exts.contains(it.name.substringAfterLast('.').lowercase()) }
                    }
                }
                
                if (sf.minSize != null) {
                    resultList = resultList.filter { !it.isDirectory && it.size >= sf.minSize }
                }
                
                if (sf.maxSize != null) {
                    resultList = resultList.filter { !it.isDirectory && it.size <= sf.maxSize }
                }
                
                if (sf.minDateMillis != null) {
                    resultList = resultList.filter { it.lastModified >= sf.minDateMillis }
                }
                
                if (sf.maxDateMillis != null) {
                    resultList = resultList.filter { it.lastModified <= sf.maxDateMillis }
                }
            }
            
            Result.success(resultList)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Core Operations ---

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

                // Treat same-folder paste as a conflict, so the user sees the dialog
                val conflictTarget = if (sourceFile.absolutePath == targetFile.absolutePath) {
                    targetFile
                } else {
                    targetFile
                }

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
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generates a unique "Keep Both" filename like `photo (1).jpg`, `photo (2).jpg`, etc.
     */
    private fun generateKeepBothName(destDir: File, sourceFile: File): File {
        val originalName = sourceFile.nameWithoutExtension
        val ext = if (sourceFile.extension.isNotEmpty()) ".${sourceFile.extension}" else ""
        
        // Match " (X)" at the end of the filename
        val copyPattern = Regex("""^(.*?)(?: \((\d+)\))?$""")
        val matchResult = copyPattern.matchEntire(originalName)

        val baseName: String
        var copyIndex = 1

        if (matchResult != null) {
            val matchedBase = matchResult.groupValues[1]
            val matchedNumber = matchResult.groupValues[2]
            
            if (matchedNumber.isNotEmpty()) {
                baseName = matchedBase
                copyIndex = matchedNumber.toInt() + 1
            } else {
                baseName = originalName
            }
        } else {
            baseName = originalName
        }

        var target: File
        do {
            val suffix = " ($copyIndex)"
            target = File(destDir, "$baseName$suffix$ext")
            copyIndex++
        } while (target.exists())
        
        return target
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

            for (path in sourcePaths) {
                val sourceFile = File(path)
                validatePath(sourceFile).onFailure { return@withContext Result.failure(it) }

                if (!sourceFile.exists()) continue

                // Reject copies where the destination is inside the source tree
                if (sourceFile.isDirectory) {
                    val sourcePath = sourceFile.canonicalFile.toPath()
                    val destPath = destDir.canonicalFile.toPath()
                    if (destPath.startsWith(sourcePath)) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Cannot copy a directory into itself or one of its subdirectories")
                        )
                    }
                }

                var targetFile = File(destDir, sourceFile.name)
                validatePath(targetFile).onFailure { return@withContext Result.failure(it) }

                if (targetFile.exists() || sourceFile.absolutePath == targetFile.absolutePath) {
                    // Conflict exists — consult the resolution map
                    when (resolutions[sourceFile.absolutePath]) {
                        ConflictResolution.SKIP -> continue
                        ConflictResolution.KEEP_BOTH -> {
                            targetFile = generateKeepBothName(destDir, sourceFile)
                        }
                        ConflictResolution.REPLACE -> {
                            // If user tries to REPLACE a file with itself (same-folder paste), it's a no-op.
                            if (sourceFile.absolutePath == targetFile.absolutePath) {
                                continue // skip to avoid crashing `copyTo/copyRecursively`
                            }
                            // else overwrite happens below
                        }
                        null -> {
                            // No resolution provided? Do not overwrite by default on same-folder.
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
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
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

            for (path in sourcePaths) {
                val sourceFile = File(path)
                validatePath(sourceFile).onFailure { return@withContext Result.failure(it) }

                if (!sourceFile.exists()) continue

                // Reject moves where the destination is inside the source tree
                if (sourceFile.isDirectory) {
                    val sourcePath = sourceFile.canonicalFile.toPath()
                    val destPath = destDir.canonicalFile.toPath()
                    if (destPath.startsWith(sourcePath)) {
                        return@withContext Result.failure(
                            IllegalArgumentException("Cannot move a directory into itself or one of its subdirectories")
                        )
                    }
                }

                var targetFile = File(destDir, sourceFile.name)
                validatePath(targetFile).onFailure { return@withContext Result.failure(it) }

                if (sourceFile.absolutePath == targetFile.absolutePath) continue // moving to same location

                if (targetFile.exists()) {
                    when (resolutions[sourceFile.absolutePath]) {
                        ConflictResolution.SKIP -> continue
                        ConflictResolution.KEEP_BOTH -> {
                            targetFile = generateKeepBothName(destDir, sourceFile)
                        }
                        ConflictResolution.REPLACE -> {
                            // Delete the existing file/folder before moving
                            if (targetFile.isDirectory) targetFile.deleteRecursively() else targetFile.delete()
                        }
                        null -> { /* no resolution — attempt move, may fail if target exists */ }
                    }
                }

                // Try simple rename first (fast moving on same mount)
                val success = sourceFile.renameTo(targetFile)
                if (!success) {
                    // Fallback to copy+delete
                    val overwrite = resolutions[sourceFile.absolutePath] == ConflictResolution.REPLACE
                    try {
                        if (sourceFile.isDirectory) {
                            sourceFile.copyRecursively(targetFile, overwrite = overwrite)
                            if (!sourceFile.deleteRecursively()) {
                                targetFile.deleteRecursively() // revert
                                return@withContext Result.failure(Exception("Failed to delete source directory after copy"))
                            }
                        } else {
                            sourceFile.copyTo(targetFile, overwrite = overwrite)
                            if (!sourceFile.delete()) {
                                targetFile.delete() // revert
                                return@withContext Result.failure(Exception("Failed to delete source file after copy"))
                            }
                        }
                    } catch (e: Exception) {
                        if (targetFile.isDirectory) targetFile.deleteRecursively() else targetFile.delete()
                        return@withContext Result.failure(e)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- Trash Subsystem ---

    private val trashDir: File by lazy {
        File(Environment.getExternalStorageDirectory(), ".arcile").apply {
            if (!exists()) {
                mkdirs()
                File(this, ".nomedia").createNewFile() // Hide from gallery
            }
        }
    }

    private val trashMetadataDir: File by lazy {
        File(trashDir, ".metadata").apply {
            if (!exists()) mkdirs()
        }
    }

    /**
     * Moves the specified files to the trash directory.
     * Note: The trash directory (`.arcile`) is stored on shared external storage with no encryption.
     * Any app with `MANAGE_EXTERNAL_STORAGE` permission can access these "deleted" files.
     */
    override suspend fun moveToTrash(paths: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (!trashDir.exists()) trashDir.mkdirs()
            if (!trashMetadataDir.exists()) trashMetadataDir.mkdirs()

            for (path in paths) {
                val file = File(path)
                validatePath(file).onFailure { return@withContext Result.failure(it) }

                if (!file.exists()) continue
                val sourceVolume = resolveVolumeForPath(file.absolutePath)
                    ?: return@withContext Result.failure(IllegalArgumentException("Unable to resolve storage volume"))
                if (sourceVolume.isRemovable) {
                    return@withContext Result.failure(
                        IllegalStateException("Trash is only supported on internal storage. Use permanent delete for SD card and USB files.")
                    )
                }
                // Don't trash the trash
                if (file.absolutePath.startsWith(trashDir.absolutePath)) continue

                val trashId = java.util.UUID.randomUUID().toString()
                val targetTrashFile = File(trashDir, trashId)
                
                // Write metadata JSON
                val metadataJson = JSONObject().apply {
                    put("id", trashId)
                    put("originalPath", file.absolutePath)
                    put("deletionTime", System.currentTimeMillis())
                }
                File(trashMetadataDir, "$trashId.json").writeText(metadataJson.toString())

                // Move abstracting name
                val success = file.renameTo(targetTrashFile)
                if (!success) {
                    // Revert metadata
                    File(trashMetadataDir, "$trashId.json").delete()
                    // Fallback
                    if (file.isDirectory) {
                        file.copyRecursively(targetTrashFile, overwrite = true)
                        file.deleteRecursively()
                    } else {
                        file.copyTo(targetTrashFile, overwrite = true)
                        file.delete()
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun restoreFromTrash(trashIds: List<String>): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            for (id in trashIds) {
                val metadataFile = File(trashMetadataDir, "$id.json")
                val trashedFile = File(trashDir, id)
                
                if (!metadataFile.exists() || !trashedFile.exists()) continue

                val json = JSONObject(metadataFile.readText())
                val originalPath = json.getString("originalPath")
                
                var originalFile = File(originalPath)
                validatePath(originalFile).onFailure { continue } // Skip invalid restores

                // Non-destructive restore: if the target already exists, use a conflict name
                if (originalFile.exists()) {
                    val timestamp = System.currentTimeMillis()
                    val conflictName = "${originalFile.nameWithoutExtension}.restore-conflict-$timestamp" +
                        (if (originalFile.extension.isNotEmpty()) ".${originalFile.extension}" else "")
                    originalFile = File(originalFile.parentFile, conflictName)
                }

                // Ensure target parent directory exists
                originalFile.parentFile?.mkdirs()

                // Restore
                val success = trashedFile.renameTo(originalFile)
                if (!success) {
                    if (trashedFile.isDirectory) {
                        trashedFile.copyRecursively(originalFile, overwrite = true)
                        trashedFile.deleteRecursively()
                    } else {
                        trashedFile.copyTo(originalFile, overwrite = true)
                        trashedFile.delete()
                    }
                }

                // Clean up metadata only once the file was actually created by this restore
                if (originalFile.exists()) {
                    metadataFile.delete()
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun emptyTrash(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (trashDir.exists()) {
                trashDir.listFiles()?.forEach { file ->
                    if (file.name != ".metadata" && file.name != ".nomedia") {
                        file.deleteRecursively()
                    }
                }
                trashMetadataDir.listFiles()?.forEach { it.delete() }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrashFiles(): Result<List<TrashMetadata>> = withContext(Dispatchers.IO) {
        try {
            val list = mutableListOf<TrashMetadata>()
            if (trashDir.exists() && trashMetadataDir.exists()) {
                trashMetadataDir.listFiles()?.forEach { metadataFile ->
                    if (metadataFile.isFile && metadataFile.extension == "json") {
                        try {
                            val json = JSONObject(metadataFile.readText())
                            val id = json.getString("id")
                            val originalPath = json.getString("originalPath")
                            val deletionTime = json.getLong("deletionTime")
                            
                            val trashedFile = File(trashDir, id)
                            if (trashedFile.exists()) {
                                // Provide a faked FileModel where the literal underlying object is the hash blob, but its name appears as the original
                                val originalFileContext = File(originalPath)
                                val spoofedModel = FileModel(
                                   name = originalFileContext.name,
                                   absolutePath = trashedFile.absolutePath,
                                   size = if (trashedFile.isFile) trashedFile.length() else 0L,
                                   lastModified = trashedFile.lastModified(),
                                   isDirectory = trashedFile.isDirectory,
                                   extension = originalFileContext.extension,
                                   isHidden = false
                                )
                                
                                list.add(TrashMetadata(id, originalPath, deletionTime, spoofedModel))
                            } else {
                                // Orphaned metadata
                                android.util.Log.w("LocalFileRepository", "Deleting orphaned trash metadata for file $originalPath")
                                metadataFile.delete() 

                            }
                        } catch (e: Exception) {
                            // Skip corrupted metadata
                        }
                    }
                }
            }
            Result.success(list.sortedByDescending { it.deletionTime })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
