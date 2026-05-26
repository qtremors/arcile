package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.domain.FileOperationException

import android.content.Context
import android.os.storage.StorageManager
import android.provider.MediaStore
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.util.indexedVolumesForScope
import dev.qtremors.arcile.core.storage.data.util.matchesScope
import dev.qtremors.arcile.di.ArcileDispatchers
import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import dev.qtremors.arcile.core.storage.domain.FileCategories
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.storage.domain.StorageScope
import dev.qtremors.arcile.core.storage.domain.StorageVolume
import dev.qtremors.arcile.core.storage.domain.matchesSearchFilters
import dev.qtremors.arcile.utils.AppLogger
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
    private val volumeProvider: VolumeProvider,
    private val dispatchers: ArcileDispatchers = ArcileDispatchers(
        io = Dispatchers.IO,
        default = Dispatchers.Default,
        main = Dispatchers.Main,
        storage = Dispatchers.IO
    )
) : MediaStoreClient {
    private companion object {
        const val MAX_PATH_SEARCH_RESULTS = 1000
        const val MAX_PATH_SEARCH_NODES = 10_000
        const val PATH_SEARCH_CANCELLATION_GRANULARITY = 32
        const val RECENT_FILES_QUERY_MULTIPLIER = 4
    }

    private val appContext = context.applicationContext
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes
    private val cacheDir = File(appContext.cacheDir, "analytics")

    private data class MediaStoreFileRow(
        val rawPath: String?,
        val displayName: String,
        val relativePath: String?,
        val volumeName: String?,
        val size: Long,
        val lastModified: Long,
        val mimeType: String?
    ) {
        val extension: String
            get() = rawPath?.substringAfterLast('.', "")
                ?: displayName.substringAfterLast('.', "")

        fun displayPath(
            volumes: List<StorageVolume>,
            mediaStoreVolumeNames: (StorageVolume) -> Set<String>
        ): String {
            rawPath?.let { return it }
            val volume = volumeName?.let { name ->
                volumes.firstOrNull { mediaStoreVolumeNames(it).contains(name) }
            } ?: volumes.firstOrNull { it.isPrimary } ?: volumes.firstOrNull()

            val root = volume?.path?.trimEnd('/')
            val relative = relativePath.orEmpty().trim('/').trimEnd('/')
            return when {
                root == null -> listOf(relative, displayName).filter { it.isNotBlank() }.joinToString("/")
                relative.isBlank() -> File(root, displayName).path
                else -> File(File(root, relative), displayName).path
            }
        }

        fun toFileModel(
            volumes: List<StorageVolume>,
            mediaStoreVolumeNames: (StorageVolume) -> Set<String>
        ): FileModel {
            val path = displayPath(volumes, mediaStoreVolumeNames)
            return FileModel(
                name = displayName,
                absolutePath = path,
                size = size,
                lastModified = lastModified,
                isDirectory = false,
                extension = extension,
                isHidden = displayName.startsWith(".") || path.contains("/."),
                mimeType = mimeType
            )
        }
    }

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
            AppLogger.e("MediaStoreClient", "Failed to save category cache")
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
            AppLogger.e("MediaStoreClient", "Category cache read failed")
            return null
        }
    }

    private fun StorageVolume.mediaStoreVolumeNames(): Set<String> {
        val names = mutableSetOf<String>()
        if (isPrimary) names += MediaStore.VOLUME_EXTERNAL_PRIMARY
        storageKey.removePrefix("uuid:")
            .takeIf { it != storageKey && it.isNotBlank() }
            ?.let {
                names += it
                names += it.lowercase()
                names += it.uppercase()
            }
        path.substringAfterLast('/', "")
            .takeIf { it.isNotBlank() }
            ?.let {
                names += it
                names += it.lowercase()
                names += it.uppercase()
            }
        return names
    }

    private fun mediaProjection(): Array<String> = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.DATA,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Files.FileColumns.SIZE,
        MediaStore.Files.FileColumns.DATE_MODIFIED,
        MediaStore.Files.FileColumns.DATE_ADDED,
        MediaStore.Files.FileColumns.MIME_TYPE,
        MediaStore.MediaColumns.RELATIVE_PATH,
        MediaStore.MediaColumns.VOLUME_NAME
    )

    private fun android.database.Cursor.optionalColumn(name: String): Int =
        getColumnIndex(name).takeIf { it >= 0 } ?: -1

    private fun android.database.Cursor.getOptionalString(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun android.database.Cursor.getOptionalLong(index: Int, default: Long = 0L): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else default

    private fun android.database.Cursor.readMediaStoreFileRow(): MediaStoreFileRow {
        val dataCol = optionalColumn(MediaStore.Files.FileColumns.DATA)
        val nameCol = optionalColumn(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val sizeCol = optionalColumn(MediaStore.Files.FileColumns.SIZE)
        val dateAddedCol = optionalColumn(MediaStore.Files.FileColumns.DATE_ADDED)
        val dateModifiedCol = optionalColumn(MediaStore.Files.FileColumns.DATE_MODIFIED)
        val mimeTypeCol = optionalColumn(MediaStore.Files.FileColumns.MIME_TYPE)
        val relativePathCol = optionalColumn(MediaStore.MediaColumns.RELATIVE_PATH)
        val volumeNameCol = optionalColumn(MediaStore.MediaColumns.VOLUME_NAME)

        val path = getOptionalString(dataCol)
        val name = getOptionalString(nameCol)
            ?: path?.let { File(it).name }
            ?: ""
        val dateAdded = getOptionalLong(dateAddedCol) * 1000L
        val dateModified = getOptionalLong(dateModifiedCol) * 1000L

        return MediaStoreFileRow(
            rawPath = path,
            displayName = name,
            relativePath = getOptionalString(relativePathCol),
            volumeName = getOptionalString(volumeNameCol),
            size = getOptionalLong(sizeCol),
            lastModified = maxOf(dateAdded, dateModified),
            mimeType = getOptionalString(mimeTypeCol)
        )
    }

    private fun rowMatchesScope(row: MediaStoreFileRow, scope: StorageScope, volumes: List<StorageVolume>): Boolean {
        if (row.displayName.startsWith(".")) return false
        row.rawPath?.let { path ->
            return !path.contains("/.") && matchesScope(path, scope, volumes)
        }

        val volumeName = row.volumeName
        return when (scope) {
            StorageScope.AllStorage -> volumeName == null || volumes.any { it.mediaStoreVolumeNames().contains(volumeName) }
            is StorageScope.Volume -> {
                val volume = volumes.find { it.id == scope.volumeId } ?: return false
                volumeName == null || volume.mediaStoreVolumeNames().contains(volumeName)
            }
            is StorageScope.Category -> {
                if (!scope.volumeId.isNullOrEmpty()) {
                    val volume = volumes.find { it.id == scope.volumeId } ?: return false
                    volumeName == null || volume.mediaStoreVolumeNames().contains(volumeName)
                } else {
                    volumeName == null || volumes.any { it.mediaStoreVolumeNames().contains(volumeName) }
                }
            }
            is StorageScope.Path -> matchesScope(row.displayPath(volumes) { it.mediaStoreVolumeNames() }, scope, volumes)
        }
    }

    private fun appendVolumeSelection(
        selectionParts: MutableList<String>,
        selectionArgs: MutableList<String>,
        volumes: List<StorageVolume>
    ) {
        if (volumes.isEmpty()) return
        val clauses = mutableListOf<String>()
        volumes.forEach { volume ->
            volume.mediaStoreVolumeNames().forEach { volumeName ->
                clauses += "${MediaStore.MediaColumns.VOLUME_NAME} = ?"
                selectionArgs += volumeName
            }
            clauses += "${MediaStore.Files.FileColumns.DATA} LIKE ?"
            selectionArgs += volume.path.trimEnd('/') + "/%"
        }
        if (clauses.isNotEmpty()) {
            selectionParts += clauses.joinToString(
                separator = " OR ",
                prefix = "(",
                postfix = ")"
            )
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
                dev.qtremors.arcile.core.storage.data.util.resolveVolumeForPath(path, volumes)?.id 
            }.toSet()
            
            val globalFile = File(cacheDir, "global.json")
            if (globalFile.exists() && !globalFile.delete()) {
                 AppLogger.w("MediaStoreClient", "Failed to delete global cache")
            }
            
            affectedVolumeIds.forEach { volId ->
                val safeVolId = volId.replace(Regex("[^a-zA-Z0-9]"), "_")
                val volFile = File(cacheDir, "volume_$safeVolId.json")
                if (volFile.exists() && !volFile.delete()) {
                    AppLogger.w("MediaStoreClient", "Failed to delete volume cache")
                }
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("MediaStoreClient", "Cache invalidation error", e)
            throw e
        }
    }

    override suspend fun getRecentFiles(
        scope: StorageScope,
        limit: Int,
        offset: Int,
        minTimestamp: Long
    ): Result<List<FileModel>> = withContext(dispatchers.io) {
        try {
            val allVolumes = volumeProvider.currentVolumes()
            val volumes = indexedVolumesForScope(scope, allVolumes)
            if (volumes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            val filesList = mutableListOf<FileModel>()
            val uri = MediaStore.Files.getContentUri("external")
            val projection = mediaProjection()

            val sortOrder = "MAX(${MediaStore.Files.FileColumns.DATE_MODIFIED}, ${MediaStore.Files.FileColumns.DATE_ADDED}) DESC"
            val selectionParts = mutableListOf("(${MediaStore.Files.FileColumns.MIME_TYPE} IS NOT NULL)")
            val selectionArgs = mutableListOf<String>()
            if (minTimestamp > 0) {
                selectionParts += "(${MediaStore.Files.FileColumns.DATE_ADDED} >= ? OR ${MediaStore.Files.FileColumns.DATE_MODIFIED} >= ?)"
                selectionArgs += (minTimestamp / 1000).toString()
                selectionArgs += (minTimestamp / 1000).toString()
            }
            appendVolumeSelection(selectionParts, selectionArgs, volumes)
            val selection = selectionParts.joinToString(" AND ")
            val args = selectionArgs.toTypedArray()
            val queryLimit = (limit * RECENT_FILES_QUERY_MULTIPLIER).coerceAtLeast(limit)
            val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val bundle = android.os.Bundle().apply {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, args)
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, queryLimit)
                    putInt(android.content.ContentResolver.QUERY_ARG_OFFSET, offset)
                }
                context.contentResolver.query(uri, projection, bundle, null)
            } else {
                @Suppress("DEPRECATION")
                context.contentResolver.query(uri, projection, selection, args, "$sortOrder LIMIT $queryLimit OFFSET $offset")
            }
            cursor?.use { cursor ->
                while (cursor.moveToNext() && filesList.size < limit) {
                    val row = cursor.readMediaStoreFileRow()
                    if (rowMatchesScope(row, scope, volumes)) {
                        filesList.add(row.toFileModel(volumes) { it.mediaStoreVolumeNames() })
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

    override suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>> = withContext(dispatchers.io) {
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
                        AppLogger.e("MediaStoreClient", "StorageStatsManager query failed", e)
                    }
                }
            }

            val uri = MediaStore.Files.getContentUri("external")
            val projection = mediaProjection()
            val selectionParts = mutableListOf<String>()
            val selectionArgs = mutableListOf<String>()
            appendVolumeSelection(selectionParts, selectionArgs, volumes)
            val selection = selectionParts.joinToString(" AND ").takeIf { it.isNotEmpty() }
            val args = selectionArgs.toTypedArray().takeIf { it.isNotEmpty() }

            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    currentCoroutineContext().ensureActive()
                    val row = cursor.readMediaStoreFileRow()
                    if (!rowMatchesScope(row, scope, volumes)) continue
                    val matchedCategory = FileCategories.getCategoryForFile(row.extension, row.mimeType) ?: continue
                    val index = FileCategories.all.indexOf(matchedCategory)
                    if (index >= 0 && needsCalculation[index]) {
                        sizes[index] += row.size
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

    override suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>> = withContext(dispatchers.io) {
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
            val projection = mediaProjection()
            
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
                clauses.add("(${MediaStore.Files.FileColumns.DATA} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?)")
                selectionArgs.add("%.${ext}")
                selectionArgs.add("%.${ext}")
            }

            if (clauses.isNotEmpty()) {
                selectionBuilder.append(clauses.joinToString(" OR "))
            }

            if (volumes.isNotEmpty()) {
                val volumeParts = mutableListOf<String>()
                val volumeArgs = mutableListOf<String>()
                appendVolumeSelection(volumeParts, volumeArgs, volumes)
                if (selectionBuilder.isNotEmpty()) {
                    selectionBuilder.insert(0, "(")
                    selectionBuilder.append(") AND ")
                }
                selectionBuilder.append(volumeParts.joinToString(" AND "))
                selectionArgs += volumeArgs
            }

            val selection = selectionBuilder.toString().takeIf { it.isNotEmpty() }
            val args = selectionArgs.toTypedArray().takeIf { it.isNotEmpty() }
            context.contentResolver.query(uri, projection, selection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val row = cursor.readMediaStoreFileRow()
                    if (rowMatchesScope(row, scope, volumes) &&
                        FileCategories.getCategoryForFile(row.extension, row.mimeType) == category
                    ) {
                        filesList.add(row.toFileModel(volumes) { it.mediaStoreVolumeNames() })
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
    ): Result<List<FileModel>> = withContext(dispatchers.io) {
        if (query.isBlank()) return@withContext Result.success(emptyList())
        val searchFilters = filters

        try {
            val allVolumes = volumeProvider.currentVolumes()
            val volumes = when (scope) {
                is StorageScope.Path -> dev.qtremors.arcile.core.storage.data.util.scopedVolumes(scope, dev.qtremors.arcile.core.storage.data.util.browsableVolumes(allVolumes))
                else -> indexedVolumesForScope(scope, allVolumes)
            }
            if (scope !is StorageScope.AllStorage && volumes.isEmpty()) {
                return@withContext Result.success(emptyList())
            }
            val filesList = mutableListOf<FileModel>()
            
            if (scope is StorageScope.Path) {
                val rootDir = File(scope.absolutePath)
                if (rootDir.exists() && rootDir.isDirectory) {
                    val pending = ArrayDeque<File>()
                    pending.add(rootDir)
                    var visitedNodes = 0

                    while (pending.isNotEmpty() && filesList.size < MAX_PATH_SEARCH_RESULTS && visitedNodes < MAX_PATH_SEARCH_NODES) {
                        currentCoroutineContext().ensureActive()
                        val current = pending.removeFirst()
                        visitedNodes += 1

                        if (visitedNodes % PATH_SEARCH_CANCELLATION_GRANULARITY == 0) {
                            currentCoroutineContext().ensureActive()
                        }

                        if (!matchesScope(current.absolutePath, scope, volumes) ||
                            (!searchFilters?.includeHidden.orFalse() && current != rootDir && current.name.startsWith("."))
                        ) {
                            continue
                        }

                        if (current != rootDir &&
                            current.name.contains(query, ignoreCase = true) &&
                            (searchFilters?.includeHidden == true || !current.name.startsWith("."))
                        ) {
                            filesList.add(current.toFileModel())
                            if (filesList.size >= MAX_PATH_SEARCH_RESULTS) {
                                break
                            }
                        }

                        if (current.isDirectory) {
                            current.listFiles()
                                ?.asSequence()
                                ?.filter { searchFilters?.includeHidden == true || !it.name.startsWith(".") }
                                ?.sortedBy { !it.isDirectory }
                                ?.forEach { child ->
                                    pending.addLast(child)
                                }
                        }
                    }
                }
            } else {
                val uri = MediaStore.Files.getContentUri("external")
                val projection = mediaProjection()
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
                            categoryClauses.add("(${MediaStore.Files.FileColumns.DATA} LIKE ? OR ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?)")
                            selectionArgs.add("%.${ext}")
                            selectionArgs.add("%.${ext}")
                        }

                        if (categoryClauses.isNotEmpty()) {
                            selectionBuilder.append(" AND (${categoryClauses.joinToString(" OR ")})")
                        }
                    }
                }
                
                val sortOrder = "MAX(${MediaStore.Files.FileColumns.DATE_MODIFIED}, ${MediaStore.Files.FileColumns.DATE_ADDED}) DESC"
                val bundle = android.os.Bundle().apply {
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION, selectionBuilder.toString())
                    putStringArray(android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs.toTypedArray())
                    putString(android.content.ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortOrder)
                    putInt(android.content.ContentResolver.QUERY_ARG_LIMIT, MAX_PATH_SEARCH_RESULTS)
                }

                val cursor = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    context.contentResolver.query(uri, projection, bundle, null)
                } else {
                    @Suppress("DEPRECATION")
                    context.contentResolver.query(uri, projection, selectionBuilder.toString(), selectionArgs.toTypedArray(), "$sortOrder LIMIT $MAX_PATH_SEARCH_RESULTS")
                }

                cursor?.use { c ->
                    while (c.moveToNext()) {
                        val row = c.readMediaStoreFileRow()
                        if (rowMatchesScope(row, scope, volumes)) {
                            filesList.add(row.toFileModel(volumes) { it.mediaStoreVolumeNames() })
                        }
                    }
                }
            }
            
            var resultList = filesList.toList()
            
            searchFilters?.let { sf ->
                resultList = resultList.filter { it.matchesSearchFilters(sf, allVolumes) }
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

private fun Boolean?.orFalse(): Boolean = this == true

