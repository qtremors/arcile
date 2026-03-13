package dev.qtremors.arcile.data

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.ConflictResolution
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileConflict
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.FileRepository
import dev.qtremors.arcile.domain.StorageInfo
import dev.qtremors.arcile.domain.TrashMetadata
import dev.qtremors.arcile.domain.SearchFilters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException

class LocalFileRepository(private val context: Context) : FileRepository {

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

    override suspend fun getStorageRootPath(): String = withContext(Dispatchers.IO) {
        Environment.getExternalStorageDirectory().canonicalPath
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
        // Redirection: by default, "deleting" a file now drops it into the Trash Bin seamlessly
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

    override suspend fun getRecentFiles(limit: Int, minTimestamp: Long): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
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

            // We can't use MAX(DATE_ADDED, DATE_MODIFIED) directly in MediaStore sorting easily.
            // But we can fetch more items sorted by either, then manually sort and limit.
            // Since DATE_ADDED is the most reliable for "newly appeared files" (including copies), 
            // we'll primarily rely on it, but we'll fetch a larger chunk to ensure we don't miss recently modified files.
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
            // Ensure we are filtering out directories (where MIME_TYPE is null or directory format)
            val baseSelection = "(${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL)"
            val selection = if (minTimestamp > 0) "$baseSelection AND (${MediaStore.Files.FileColumns.DATE_ADDED} >= ? OR ${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?)" else baseSelection
            val selectionArgs = if (minTimestamp > 0) arrayOf((minTimestamp / 1000).toString(), (minTimestamp / 1000).toString()) else null

            // Fetch a bit more than the limit just in case, we will sort manually
            val queryLimit = limit * 2

            // Note: Since Android 10, MediaStore does not support LIMIT clause in sortOrder directly, 
            // but we can break out of the cursor loop early.

            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                while (cursor.moveToNext() && filesList.size < queryLimit) {
                    val path = cursor.getString(dataCol)
                    val name = cursor.getString(nameCol) ?: path?.let { File(it).name } ?: ""

                    if (path != null && !name.startsWith(".")) {
                        var size = cursor.getLong(sizeCol)
                        val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                        val dateModified = cursor.getLong(dateModifiedCol) * 1000L

                        // Natively copied files might take a moment to register their size in MediaStore. Fallback to raw file length.
                        val actualFile = File(path)
                        if (size == 0L && actualFile.exists()) {
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
            
            // Sort by the max of date added/modified, then take the exact limit requested
            val finalSortedList = filesList.sortedByDescending { it.lastModified }.take(limit)

            Result.success(finalSortedList)
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
                    if (path != null) {
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

    override suspend fun getFilesByCategory(categoryName: String): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
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
                    if (path != null) {
                        val file = File(path)
                        if (extensions.contains(file.extension.lowercase())) {
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

    override suspend fun searchFiles(query: String, pathScope: String?, filters: SearchFilters?): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        val searchFilters = filters

        try {
            val filesList = mutableListOf<FileModel>()
            
            if (pathScope != null) {
                // Scoped recursive search — validate the scope root before walking
                val rootDir = File(pathScope)
                validatePath(rootDir).onFailure { return@withContext Result.failure(it) }
                val storageRootCanonical = File(storageRoot).canonicalPath
                if (rootDir.exists() && rootDir.isDirectory) {
                    rootDir.walkTopDown()
                        .onEnter { dir ->
                            // Prune symlinked / out-of-bound directories
                            val dirCanonical = dir.canonicalPath
                            dirCanonical == storageRootCanonical ||
                                dirCanonical.startsWith(storageRootCanonical + File.separator)
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
                        if (path != null) {
                            val f = File(path)
                            if (!f.name.startsWith(".")) {
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
        File(storageRoot, ".arcile").apply {
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
