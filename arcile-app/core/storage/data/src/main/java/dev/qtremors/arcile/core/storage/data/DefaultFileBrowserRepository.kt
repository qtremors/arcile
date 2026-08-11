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
import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import dev.qtremors.arcile.core.storage.domain.StorageNodePath
import dev.qtremors.arcile.core.runtime.di.ArcileDispatchers
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
        runCatchingPreservingCancellation { StorageNodePath.of(path) }
            .map { fileSystemDataSource.list(it, pageSize) }
            .getOrElse {
                flowOf(ListingPage.failed(StorageNodePath.of("/"), it))
            }

    override suspend fun listNodeFiles(
        directory: dev.qtremors.arcile.core.storage.domain.StorageNodeRef
    ): Result<List<FileModel>> = fileSystemDataSource.listNodeFiles(directory)

    override fun listNodePages(
        directory: dev.qtremors.arcile.core.storage.domain.StorageNodeRef,
        pageSize: Int
    ): Flow<ListingPage> = fileSystemDataSource.list(directory, pageSize)

    override suspend fun getCachedFolderStats(
        paths: Collection<String>
    ): Map<String, FolderStats> = folderStatsStore.getCached(paths)

    override fun queueFolderStats(paths: List<String>) {
        folderStatsStore.queue(paths)
    }

    override suspend fun getCachedNodeFolderStats(
        nodes: Collection<StorageNodeRef>
    ): Map<String, FolderStats> = folderStatsStore.getCachedNodes(nodes)

    override fun queueNodeFolderStats(nodes: List<StorageNodeRef>) {
        folderStatsStore.queueNodes(nodes)
    }

    override fun observeFolderStatUpdates(): Flow<FolderStatUpdate> =
        folderStatsStore.observeUpdates()

    override suspend fun getSelectionProperties(
        paths: List<String>
    ): Result<SelectionProperties> = runCatchingPreservingCancellation {
        paths.distinct().map(StorageNodeRef::local)
    }.fold(
        onSuccess = { getNodeSelectionProperties(it) },
        onFailure = { Result.failure(it) }
    )

    override suspend fun getNodeSelectionProperties(
        nodes: List<StorageNodeRef>
    ): Result<SelectionProperties> = withContext(dispatchers.io) {
        try {
            val selectedNodes = nodes.distinctBy { it.canonicalIdentity }
            require(selectedNodes.isNotEmpty()) { "No items selected" }
            val scanner = StorageNodePropertiesScanner(fileSystemDataSource)
            val completedScans = buildList {
                selectedNodes.forEach { node ->
                    scanner.scan(node).getOrNull()?.let(::add)
                }
            }
            require(completedScans.isNotEmpty()) { "Selected items are no longer available" }

            val missingSelection = completedScans.size != selectedNodes.size
            val scans = completedScans.map(StorageNodePropertiesScan::aggregate)
            val accessStatus = when {
                scans.any { it.selectedDirectoryUnavailable } ->
                    PropertiesAccessStatus.Limited
                scans.any { it.descendantReadFailed } || missingSelection ->
                    PropertiesAccessStatus.Partial
                else -> PropertiesAccessStatus.Full
            }
            Result.success(
                if (completedScans.size == 1) {
                    singleSelectionProperties(
                        completedScans.first().root,
                        completedScans.first().aggregate,
                        accessStatus,
                        missingSelection
                    )
                } else {
                    multipleSelectionProperties(completedScans, accessStatus)
                }
            )
        } catch (error: Exception) {
            error.rethrowIfCancellation()
            Result.failure(error)
        }
    }

    private fun singleSelectionProperties(
        file: FileModel,
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
            mimeTypeSummary = if (!file.isDirectory) {
                file.mimeType ?: extension.takeIf(String::isNotEmpty)?.let {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(it)
                }
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
        completedScans: List<StorageNodePropertiesScan>,
        accessStatus: PropertiesAccessStatus
    ): SelectionProperties {
        val files = completedScans.map(StorageNodePropertiesScan::root)
        val scans = completedScans.map(StorageNodePropertiesScan::aggregate)
        return SelectionProperties(
            displayName = "${files.size} items",
            pathSummary = files.mapNotNull { it.absolutePath.storageParentPath() }
                .distinct()
                .singleOrNull()
                ?: files.first().absolutePath.storageParentPath().orEmpty(),
            itemCount = files.size,
            fileCount = scans.saturatedSumOf(PropertiesScanResult::fileCount)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            folderCount = scans.saturatedSumOf(PropertiesScanResult::folderCount)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            totalBytes = scans.saturatedSumOf(PropertiesScanResult::totalBytes),
            newestModifiedAt = scans.mapNotNull { it.newestModifiedAt }.maxOrNull(),
            oldestModifiedAt = scans.mapNotNull { it.oldestModifiedAt }.minOrNull(),
            mimeTypeSummary = null,
            extensionSummary = null,
            hiddenCount = scans.saturatedSumOf(PropertiesScanResult::hiddenCount)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            accessStatus = accessStatus,
            folderStats = null,
            isSingleItem = false,
            isDirectory = null
        )
    }

    private fun List<PropertiesScanResult>.saturatedSumOf(
        value: (PropertiesScanResult) -> Long
    ): Long = fold(0L) { total, scan ->
        val next = value(scan).coerceAtLeast(0L)
        if (next > Long.MAX_VALUE - total) Long.MAX_VALUE else total + next
    }
}

private fun String.storageParentPath(): String? {
    val normalized = trimEnd('/', '\\')
    val separator = maxOf(normalized.lastIndexOf('/'), normalized.lastIndexOf('\\'))
    if (separator < 0) return null
    return normalized.substring(0, separator).ifEmpty { normalized.substring(0, 1) }
}
