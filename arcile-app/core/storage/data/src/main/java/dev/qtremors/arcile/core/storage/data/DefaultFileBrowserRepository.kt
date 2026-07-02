package dev.qtremors.arcile.core.storage.data

import android.webkit.MimeTypeMap
import dev.qtremors.arcile.core.storage.data.source.FileSystemDataSource
import dev.qtremors.arcile.core.storage.domain.FileBrowserRepository
import dev.qtremors.arcile.core.storage.domain.FileModel
import dev.qtremors.arcile.core.storage.domain.FolderStatUpdate
import dev.qtremors.arcile.core.storage.domain.FolderStats
import dev.qtremors.arcile.core.storage.domain.FolderStatsStatus
import dev.qtremors.arcile.core.storage.domain.ListingPage
import dev.qtremors.arcile.core.storage.domain.PropertiesAccessStatus
import dev.qtremors.arcile.core.storage.domain.SelectionProperties
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.withContext

class DefaultFileBrowserRepository(
    private val fileSystemDataSource: FileSystemDataSource,
    private val folderStatsStore: FolderStatsStore,
    private val dispatchers: ArcileDispatchers
) : FileBrowserRepository {
    override suspend fun listFiles(path: String): Result<List<FileModel>> =
        fileSystemDataSource.listFiles(path)

    override fun listFilePages(path: String, pageSize: Int): Flow<ListingPage> =
        runCatching { StorageNodePath.of(path) }
            .map { fileSystemDataSource.list(it, pageSize) }
            .getOrElse {
                flowOf(ListingPage.failed(StorageNodePath.of(File("/").absolutePath), it))
            }

    override suspend fun getCachedFolderStats(
        paths: Collection<String>
    ): Map<String, FolderStats> = folderStatsStore.getCached(paths)

    override fun queueFolderStats(paths: List<String>) {
        folderStatsStore.queue(paths)
    }

    override fun observeFolderStatUpdates(): Flow<FolderStatUpdate> =
        folderStatsStore.observeUpdates()

    override suspend fun getSelectionProperties(
        paths: List<String>
    ): Result<SelectionProperties> = withContext(dispatchers.io) {
        try {
            val selectedFiles = paths.distinct().map(::File)
            require(selectedFiles.isNotEmpty()) { "No items selected" }
            val existingFiles = selectedFiles.filter(File::exists)
            require(existingFiles.isNotEmpty()) { "Selected items are no longer available" }

            val scansByPath = existingFiles.associate { file ->
                file.absolutePath to PropertiesScanner.scan(file)
            }
            val missingSelection = existingFiles.size != selectedFiles
                .distinctBy(File::getAbsolutePath)
                .size
            val accessStatus = when {
                scansByPath.values.any { it.selectedDirectoryUnavailable } ->
                    PropertiesAccessStatus.Limited
                scansByPath.values.any { it.descendantReadFailed } || missingSelection ->
                    PropertiesAccessStatus.Partial
                else -> PropertiesAccessStatus.Full
            }
            Result.success(
                if (existingFiles.size == 1) {
                    singleSelectionProperties(
                        existingFiles.first(),
                        scansByPath.getValue(existingFiles.first().absolutePath),
                        accessStatus,
                        missingSelection
                    )
                } else {
                    multipleSelectionProperties(existingFiles, scansByPath, accessStatus)
                }
            )
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            Result.failure(error)
        }
    }

    private fun singleSelectionProperties(
        file: File,
        scan: PropertiesScanResult,
        accessStatus: PropertiesAccessStatus,
        missingSelection: Boolean
    ): SelectionProperties {
        val extension = file.extension.lowercase()
        return SelectionProperties(
            displayName = file.name,
            pathSummary = file.absolutePath,
            itemCount = 1,
            fileCount = scan.fileCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            folderCount = scan.folderCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            totalBytes = scan.totalBytes,
            newestModifiedAt = scan.newestModifiedAt,
            oldestModifiedAt = scan.oldestModifiedAt,
            mimeTypeSummary = if (file.isFile && extension.isNotEmpty()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            } else {
                null
            },
            extensionSummary = extension.ifEmpty { null },
            hiddenCount = scan.hiddenCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            accessStatus = if (file.isDirectory || missingSelection) {
                accessStatus
            } else {
                PropertiesAccessStatus.Full
            },
            folderStats = if (file.isDirectory) {
                FolderStats(
                    fileCount = scan.fileCount,
                    totalBytes = scan.totalBytes,
                    cachedAt = System.currentTimeMillis(),
                    status = when (accessStatus) {
                        PropertiesAccessStatus.Full -> FolderStatsStatus.Ready
                        PropertiesAccessStatus.Partial -> FolderStatsStatus.Partial
                        PropertiesAccessStatus.Limited -> FolderStatsStatus.Unavailable
                    }
                )
            } else {
                null
            },
            isSingleItem = true,
            isDirectory = file.isDirectory
        )
    }

    private fun multipleSelectionProperties(
        files: List<File>,
        scansByPath: Map<String, PropertiesScanResult>,
        accessStatus: PropertiesAccessStatus
    ): SelectionProperties {
        val scans = scansByPath.values
        return SelectionProperties(
            displayName = "${files.size} items",
            pathSummary = files.mapNotNull(File::getParent).distinct().singleOrNull()
                ?: files.first().parent.orEmpty(),
            itemCount = files.size,
            fileCount = scans.sumOf { it.fileCount }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            folderCount = scans.sumOf { it.folderCount }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            totalBytes = scans.sumOf { it.totalBytes },
            newestModifiedAt = scans.mapNotNull { it.newestModifiedAt }.maxOrNull(),
            oldestModifiedAt = scans.mapNotNull { it.oldestModifiedAt }.minOrNull(),
            mimeTypeSummary = null,
            extensionSummary = null,
            hiddenCount = scans.sumOf { it.hiddenCount }.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            accessStatus = accessStatus,
            folderStats = null,
            isSingleItem = false,
            isDirectory = null
        )
    }
}
