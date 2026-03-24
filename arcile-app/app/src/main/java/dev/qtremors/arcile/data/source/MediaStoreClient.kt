package dev.qtremors.arcile.data.source

import dev.qtremors.arcile.domain.FileOperationException

import android.content.Context
import android.os.storage.StorageManager
import android.provider.MediaStore
import dev.qtremors.arcile.data.provider.VolumeProvider
import dev.qtremors.arcile.data.util.indexedVolumesForScope
import dev.qtremors.arcile.data.util.matchesScope
import dev.qtremors.arcile.domain.CategoryStorage
import dev.qtremors.arcile.domain.FileCategories
import dev.qtremors.arcile.domain.FileModel
import dev.qtremors.arcile.domain.SearchFilters
import dev.qtremors.arcile.domain.StorageScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CategoryCacheEntity(
    val name: String,
    val size: Long,
    val extensions: List<String>
)

@Serializable
data class CacheRootEntity(
    val cachedAt: Long,
    val data: List<CategoryCacheEntity>
)

interface MediaStoreClient {
    suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>>
    suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>>
    suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>>
    suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>>
    suspend fun invalidateCache(vararg paths: String)
}

class DefaultMediaStoreClient(
    private val context: Context,
    private val volumeProvider: VolumeProvider
) : MediaStoreClient {

    private val appContext = context.applicationContext
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    private val cacheDir = File(appContext.cacheDir, "analytics")

    private fun File.toFileModel(mime: String? = null): FileModel {
        val ext = extension
        val actualMime = mime ?: if (ext.isNotEmpty()) {
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
            mimeType = actualMime
        )
    }

    private fun saveCategorySizesToCache(scope: StorageScope, data: List<CategoryStorage>) {
        try {
            if (!cacheDir.exists()) cacheDir.mkdirs()
            val cacheKey = when (scope) {
                StorageScope.AllStorage -> "global"
                is StorageScope.Volume -> "volume_${scope.volumeId.replace(Regex("[^a-zA-Z0-9]"), "_")}"
                else -> return // Only cache global and volume-wide stats
            }
            val file = File(cacheDir, "$cacheKey.json")
            
            val cacheEntities = data.map { item ->
                CategoryCacheEntity(
                    name = item.name,
                    size = item.sizeBytes,
                    extensions = item.extensions.toList()
                )
            }
            
            val rootEntity = CacheRootEntity(
                cachedAt = System.currentTimeMillis(),
                data = cacheEntities
            )
            
            val jsonFormat = Json { ignoreUnknownKeys = true }
            file.writeText(jsonFormat.encodeToString(rootEntity))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("MediaStoreClient", "Failed to save cache for $scope: ${e.message}")
        }
    }

    private fun getCategorySizesFromCache(scope: StorageScope): List<CategoryStorage>? {
        try {
            val cacheKey = when (scope) {
                StorageScope.AllStorage -> "global"
                is StorageScope.Volume -> "volume_${scope.volumeId.replace(Regex("[^a-zA-Z0-9]"), "_")}"
                else -> return null
            }
            val file = File(cacheDir, "$cacheKey.json")
            if (!file.exists()) return null
            
            val jsonFormat = Json { ignoreUnknownKeys = true }
            val rootEntity = jsonFormat.decodeFromString<CacheRootEntity>(file.readText())
            
            if (System.currentTimeMillis() - rootEntity.cachedAt > CACHE_TTL_MS) {
                return null // Stale cache
            }
            
            return rootEntity.data.map { entity ->
                CategoryStorage(
                    name = entity.name,
                    sizeBytes = entity.size,
                    extensions = entity.extensions.toSet()
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("MediaStoreClient", "Cache read failed: ${e.message}")
            return null
        }
    }

    override suspend fun invalidateCache(vararg paths: String) {
        try {
            if (paths.isEmpty()) {
                cacheDir.listFiles()?.forEach { if (it.name.endsWith(".json")) it.delete() }
                return
            }
            
            val volumes = volumeProvider.currentVolumes()
            val affectedVolumeIds = paths.mapNotNull { path -> 
                dev.qtremors.arcile.data.util.resolveVolumeForPath(path, volumes)?.id 
            }.toSet()
            
            val globalFile = File(cacheDir, "global.json")
            if (globalFile.exists() && !globalFile.delete()) {
                 android.util.Log.w("MediaStoreClient", "Failed to delete global cache")
            }
            
            affectedVolumeIds.forEach { volId ->
                val safeVolId = volId.replace(Regex("[^a-zA-Z0-9]"), "_")
                val volFile = File(cacheDir, "volume_$safeVolId.json")
                if (volFile.exists() && !volFile.delete()) {
                    android.util.Log.w("MediaStoreClient", "Failed to delete cache for volume: $volId")
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.e("MediaStoreClient", "Cache invalidation error: ${e.message}")
            throw e
        }
    }

    override suspend fun getRecentFiles(
        scope: StorageScope,
        limit: Int,
        offset: Int,
        minTimestamp: Long
    ): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
            val allVolumes = volumeProvider.currentVolumes()
            val volumes = indexedVolumesForScope(scope, allVolumes)
            if (volumes.isEmpty()) {
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

            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            val baseSelection = "(${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL)"
            val selection = if (minTimestamp > 0) "$baseSelection AND (${MediaStore.Files.FileColumns.DATE_ADDED} >= ? OR ${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?)" else baseSelection
            val selectionArgs = if (minTimestamp > 0) arrayOf((minTimestamp / 1000).toString(), (minTimestamp / 1000).toString()) else null
            context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mimeTypeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)

                var validFilesSkipped = 0

                while (cursor.moveToNext() && filesList.size < limit) {
                    val path = cursor.getString(dataCol)
                    val name = cursor.getString(nameCol) ?: path?.let { File(it).name } ?: ""

                    if (path != null && !name.startsWith(".") && !path.contains("/.") && matchesScope(path, scope, volumes)) {
                        val actualFile = File(path)
                        if (!actualFile.exists()) continue
                        
                        if (validFilesSkipped < offset) {
                            validFilesSkipped++
                            continue
                        }

                        var size = cursor.getLong(sizeCol)
                        val dateAdded = cursor.getLong(dateAddedCol) * 1000L
                        val dateModified = cursor.getLong(dateModifiedCol) * 1000L
                        val mimeType = cursor.getString(mimeTypeCol)

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
                            isHidden = false,
                            mimeType = mimeType
                        ))
                    }
                }
            }

            val finalSortedList = filesList.sortedByDescending { it.lastModified }

            Result.success(finalSortedList)
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> = withContext(Dispatchers.IO) {
        val cached = getCategorySizesFromCache(scope)
        if (cached != null) {
            return@withContext Result.success(cached)
        }

        try {
            val allVolumes = volumeProvider.currentVolumes()
            val volumes = indexedVolumesForScope(scope, allVolumes)
            if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
                return@withContext Result.success(
                    FileCategories.all.map { CategoryStorage(it.name, 0L, it.extensions) }
                )
            }

            val sizes = LongArray(FileCategories.all.size)
            val needsCalculation = BooleanArray(FileCategories.all.size) { true }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && scope is StorageScope.Volume) {
                val statsManager = appContext.getSystemService(Context.STORAGE_STATS_SERVICE) as? android.app.usage.StorageStatsManager
                val volume = volumes.find { it.id == scope.volumeId }
                
                if (statsManager != null && volume != null) {
                    try {
                        val uuid = if (volume.isPrimary) {
                            StorageManager.UUID_DEFAULT
                        } else {
                            val sm = appContext.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                            sm.storageVolumes.find { 
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) it.directory?.absolutePath == volume.path else false 
                            }?.uuid?.let { java.util.UUID.fromString(it) }
                        }

                        if (uuid != null) {
                            val stats = statsManager.queryExternalStatsForUser(uuid, android.os.Process.myUserHandle())
                            FileCategories.all.forEachIndexed { index, cat ->
                                when (cat.name) {
                                    "Images" -> { sizes[index] = stats.imageBytes; needsCalculation[index] = false }
                                    "Videos" -> { sizes[index] = stats.videoBytes; needsCalculation[index] = false }
                                    "Audio" -> { sizes[index] = stats.audioBytes; needsCalculation[index] = false }
                                }
                            }
                        }
                    } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
                        android.util.Log.e("MediaStoreClient", "StorageStatsManager query failed for ${volume.name}", e)
                    }
                }
            }

            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.MIME_TYPE
            )
            
            val selectionBuilder = StringBuilder()
            val selectionArgs = mutableListOf<String>()
            var requiresFullScan = false
            
            val clauses = mutableListOf<String>()

            FileCategories.all.forEachIndexed { index, cat ->
                if (needsCalculation[index]) {
                    if (cat.mimePrefix != null) {
                        val prefix = cat.mimePrefix
                        if (prefix.endsWith("/")) {
                            clauses.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                            selectionArgs.add("$prefix%")
                        } else {
                            clauses.add("${MediaStore.Files.FileColumns.MIME_TYPE} = ?")
                            selectionArgs.add(prefix)
                        }
                    } else {
                        requiresFullScan = true
                    }
                    
                    cat.extensions.forEach { ext ->
                        clauses.add("${MediaStore.Files.FileColumns.DATA} LIKE ?")
                        selectionArgs.add("%.${ext}")
                    }
                }
            }
            
            if (clauses.isNotEmpty()) {
                selectionBuilder.append(clauses.joinToString(" OR "))
            }
            
            val selection = if (requiresFullScan) null else selectionBuilder.toString().takeIf { it.isNotEmpty() }
            val args = if (requiresFullScan) null else selectionArgs.toTypedArray().takeIf { it.isNotEmpty() }
            
            if (requiresFullScan || selection != null) {
                context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                    
                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol)
                        val size = cursor.getLong(sizeCol)
                        val mime = cursor.getString(mimeCol)
                        
                        if (path != null && matchesScope(path, scope, volumes)) {
                            val file = File(path)
                            val cat = FileCategories.getCategoryForFile(file.extension, mime)
                            val catIndex = FileCategories.all.indexOf(cat)
                            
                            if (catIndex != -1 && needsCalculation[catIndex]) {
                                sizes[catIndex] += size
                            }
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

            saveCategorySizesToCache(scope, result)
            Result.success(result)
        } catch (e: SecurityException) {
            Result.failure(FileOperationException.AccessDenied(cause = e))
        } catch (e: java.io.IOException) {
            Result.failure(FileOperationException.IOError(cause = e))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Result.failure(FileOperationException.Unknown(cause = e))
        }
    }

    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        try {
            val allVolumes = volumeProvider.currentVolumes()
            val volumes = indexedVolumesForScope(scope, allVolumes)
            if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            val category = FileCategories.all.find { it.name == categoryName }
                ?: return@withContext Result.failure(IllegalArgumentException("Unknown category: $categoryName"))
            
            val filesList = mutableListOf<FileModel>()

            val uri = MediaStore.Files.getContentUri("external")
            val projection = arrayOf(
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )
            
            val selectionBuilder = StringBuilder()
            val selectionArgs = mutableListOf<String>()
            val clauses = mutableListOf<String>()

            category.mimePrefix?.let { prefix ->
                if (prefix.endsWith("/")) {
                    clauses.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                    selectionArgs.add("$prefix%")
                } else {
                    clauses.add("${MediaStore.Files.FileColumns.MIME_TYPE} = ?")
                    selectionArgs.add(prefix)
                }
            }

            category.extensions.forEach { ext ->
                clauses.add("${MediaStore.Files.FileColumns.DATA} LIKE ?")
                selectionArgs.add("%.${ext}")
            }

            if (clauses.isNotEmpty()) {
                selectionBuilder.append(clauses.joinToString(" OR "))
            }

            val selection = selectionBuilder.toString().takeIf { it.isNotEmpty() }
            val args = selectionArgs.toTypedArray().takeIf { it.isNotEmpty() }
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MIME_TYPE)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol)
                    val mime = cursor.getString(mimeCol)
                    
                    if (path != null && matchesScope(path, scope, volumes)) {
                        val extension = path.substringAfterLast('.', "")
                        val cat = FileCategories.getCategoryForFile(extension, mime)
                        
                        if (cat == category) {
                            val name = cursor.getString(nameCol) ?: path.substringAfterLast('/', "")
                            filesList.add(FileModel(
                                name = name,
                                absolutePath = path,
                                size = cursor.getLong(sizeCol),
                                lastModified = cursor.getLong(dateCol) * 1000L,
                                isDirectory = false,
                                extension = extension,
                                isHidden = name.startsWith("."),
                                mimeType = mime
                            ))
                        }
                    }
                }
            }
            
            val sortedFiles = filesList.sortedByDescending { it.lastModified }
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

    override suspend fun searchFiles(
        query: String,
        scope: StorageScope,
        filters: SearchFilters?
    ): Result<List<FileModel>> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        val searchFilters = filters

        try {
            val allVolumes = volumeProvider.currentVolumes()
            val volumes = when (scope) {
                is StorageScope.Path -> dev.qtremors.arcile.data.util.scopedVolumes(scope, dev.qtremors.arcile.data.util.browsableVolumes(allVolumes))
                else -> indexedVolumesForScope(scope, allVolumes)
            }
            if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            val filesList = mutableListOf<FileModel>()
            
            if (scope is StorageScope.Path) {
                val rootDir = File(scope.absolutePath)
                // Assuming path is valid
                if (rootDir.exists() && rootDir.isDirectory) {
                    rootDir.walkTopDown()
                        .onEnter { dir ->
                            !dir.name.startsWith(".") && matchesScope(dir.absolutePath, scope, volumes)
                        }
                        .filter { file ->
                            file.name.contains(query, ignoreCase = true) && !file.name.startsWith(".")
                        }
                        .take(1000)
                        .forEach { file ->
                            filesList.add(file.toFileModel())
                        }
                }
            } else {
                val uri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(MediaStore.Files.FileColumns.DATA)
                val selectionBuilder = StringBuilder("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?")
                val selectionArgs = mutableListOf("%$query%")
                
                if (scope is StorageScope.Category) {
                    val category = FileCategories.all.find { it.name == scope.categoryName }
                    if (category != null) {
                        val categoryClauses = mutableListOf<String>()
                        
                        category.mimePrefix?.let { prefix ->
                            if (prefix.endsWith("/")) {
                                categoryClauses.add("${MediaStore.Files.FileColumns.MIME_TYPE} LIKE ?")
                                selectionArgs.add("$prefix%")
                            } else {
                                categoryClauses.add("${MediaStore.Files.FileColumns.MIME_TYPE} = ?")
                                selectionArgs.add(prefix)
                            }
                        }

                        category.extensions.forEach { ext ->
                            categoryClauses.add("${MediaStore.Files.FileColumns.DATA} LIKE ?")
                            selectionArgs.add("%.${ext}")
                        }

                        if (categoryClauses.isNotEmpty()) {
                            selectionBuilder.append(" AND (${categoryClauses.joinToString(" OR ")})")
                        }
                    }
                }
                
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
                val bundle = android.os.Bundle().apply {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selectionBuilder.toString())
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs.toTypedArray())
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, 500)
                }

                val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.contentResolver.query(uri, projection, bundle, null)
                } else {
                    @Suppress("DEPRECATION")
                    context.contentResolver.query(uri, projection, selectionBuilder.toString(), selectionArgs.toTypedArray(), "$sortOrder LIMIT 500")
                }

                cursor?.use { c ->
                    val dataCol = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    while (c.moveToNext()) {
                        val path = c.getString(dataCol)
                        if (path != null && !path.contains("/.") && matchesScope(path, scope, volumes)) {
                            val f = File(path)
                            if (f.exists() && !f.name.startsWith(".")) {
                                filesList.add(f.toFileModel())
                            }
                        }
                    }
                }
            }
            
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