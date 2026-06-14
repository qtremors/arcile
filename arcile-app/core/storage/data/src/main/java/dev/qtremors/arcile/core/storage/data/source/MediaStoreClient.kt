package dev.qtremors.arcile.core.storage.data.source

import dev.qtremors.arcile.core.storage.domain.FileOperationException

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import dev.qtremors.arcile.core.storage.data.db.ArcileDatabase
import dev.qtremors.arcile.core.storage.data.db.CategorySummaryDao
import dev.qtremors.arcile.core.storage.data.db.CategorySummaryEntity
import dev.qtremors.arcile.core.storage.data.provider.VolumeProvider
import dev.qtremors.arcile.core.storage.data.util.indexedVolumesForScope
import dev.qtremors.arcile.core.storage.data.util.matchesScope
import dev.qtremors.arcile.core.storage.data.util.resolveVolumeForPath
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
import java.io.File

interface MediaStoreClient {
    suspend fun getRecentFiles(scope: StorageScope, limit: Int, offset: Int, minTimestamp: Long): Result<List<FileModel>>
    suspend fun getCategoryStorageSizes(scope: StorageScope): Result<List<CategoryStorage>>
    suspend fun getFilesByCategory(scope: StorageScope, categoryName: String): Result<List<FileModel>>
    suspend fun searchFiles(query: String, scope: StorageScope, filters: SearchFilters?): Result<List<FileModel>>
    suspend fun invalidateCache(vararg paths: String)
    suspend fun resolveInvalidationUri(uri: Uri): MediaStoreInvalidationTarget? = null
}

data class MediaStoreInvalidationTarget(
    val path: String?,
    val parentPath: String?,
    val mediaStoreId: Long?,
    val contentUri: String?,
    val volumeId: String?,
    val volumeName: String?,
    val mimeType: String?,
    val extension: String,
    val exists: Boolean
)

class DefaultMediaStoreClient(
    private val context: Context,
    private val volumeProvider: VolumeProvider,
    private val categorySummaryDao: CategorySummaryDao = ArcileDatabase.getInstance(context).categorySummaryDao(),
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
    private val CACHE_TTL_MS = 5 * 60 * 1000L // 5 minutes

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

    private fun categoryCacheKey(scope: StorageScope): String? =
        when (scope) {
            StorageScope.AllStorage -> "global"
            is StorageScope.Volume -> "volume_${scope.volumeId.replace(Regex("[^a-zA-Z0-9]"), "_")}"
            else -> null
        }

    private suspend fun saveCategorySizesToCache(scope: StorageScope, data: List<CategoryStorage>) {
        try {
            val cacheKey = categoryCacheKey(scope) ?: return
            val now = System.currentTimeMillis()
            categorySummaryDao.upsert(
                data.map { item ->
                    CategorySummaryEntity(
                        scopeKey = cacheKey,
                        categoryName = item.name,
                        sizeBytes = item.sizeBytes,
                        itemCount = 0,
                        cachedAt = now
                    )
                }
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("MediaStoreClient", "Failed to save category cache")
        }
    }

    private suspend fun getCategorySizesFromCache(scope: StorageScope): List<CategoryStorage>? {
        try {
            val cacheKey = categoryCacheKey(scope) ?: return null
            val entities = categorySummaryDao.get(cacheKey)
            if (entities.isEmpty()) return null
            if (entities.any { System.currentTimeMillis() - it.cachedAt > CACHE_TTL_MS }) return null

            val byName = entities.associateBy { it.categoryName }
            return FileCategories.all.map { category ->
                val entity = byName[category.name] ?: return null
                CategoryStorage(
                    name = category.name,
                    sizeBytes = entity.sizeBytes,
                    extensions = category.extensions
                )
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("MediaStoreClient", "Category cache read failed")
            return null
        }
    }

    override suspend fun invalidateCache(vararg paths: String) = withContext(dispatchers.io) {
        try {
            if (paths.isEmpty()) {
                categorySummaryDao.clear()
                return@withContext
            }

            val volumes = volumeProvider.currentVolumes()
            val affectedVolumeIds = paths.mapNotNull { path ->
                resolveVolumeForPath(path, volumes)?.id
            }.toSet()
            categorySummaryDao.delete(
                listOf("global") + affectedVolumeIds.map { "volume_${it.replace(Regex("[^a-zA-Z0-9]"), "_")}" }
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            AppLogger.e("MediaStoreClient", "Cache invalidation error", e)
            throw e
        }
    }

    override suspend fun resolveInvalidationUri(uri: Uri): MediaStoreInvalidationTarget? = withContext(dispatchers.io) {
        val allVolumes = volumeProvider.currentVolumes()
        val cursor = context.contentResolver.query(uri, mediaProjection(), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val row = it.readMediaStoreFileRow()
                val file = row.toFileModel(allVolumes)
                return@withContext MediaStoreInvalidationTarget(
                    path = file.absolutePath,
                    parentPath = File(file.absolutePath).parent,
                    mediaStoreId = row.id.takeIf { id -> id > 0L },
                    contentUri = row.contentUri,
                    volumeId = file.nodeRef.volumeId?.value,
                    volumeName = row.volumeName,
                    mimeType = row.mimeType,
                    extension = row.extension,
                    exists = true
                )
            }
        }
        val id = runCatching { android.content.ContentUris.parseId(uri) }.getOrNull()?.takeIf { it > 0L }
        MediaStoreInvalidationTarget(
            path = null,
            parentPath = null,
            mediaStoreId = id,
            contentUri = uri.toString(),
            volumeId = null,
            volumeName = uri.pathSegments.firstOrNull(),
            mimeType = null,
            extension = "",
            exists = false
        )
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
                        filesList.add(row.toFileModel(volumes))
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
                    if (index >= 0) {
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
                clauses.add("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?")
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
                        filesList.add(row.toFileModel(volumes))
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
                            categoryClauses.add("${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?")
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
                            filesList.add(row.toFileModel(volumes))
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

