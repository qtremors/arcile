package dev.qtremors.arcile.presentation.browser

import androidx.compose.runtime.Immutable
import dev.qtremors.arcile.domain.PropertiesAccessStatus
import dev.qtremors.arcile.domain.SelectionProperties

@Immutable
data class PropertiesUiModel(
    val title: String,
    val pathSummary: String,
    val itemCount: Int,
    val fileCount: Int,
    val folderCount: Int,
    val totalBytes: Long,
    val newestModifiedAt: Long?,
    val oldestModifiedAt: Long?,
    val mimeTypeSummary: String?,
    val extensionSummary: String?,
    val hiddenCount: Int,
    val accessStatus: PropertiesAccessStatus,
    val folderFileCount: Long?,
    val folderTotalBytes: Long?,
    val isSingleItem: Boolean,
    val isDirectory: Boolean?
)

internal fun SelectionProperties.toUiModel(): PropertiesUiModel = PropertiesUiModel(
    title = displayName,
    pathSummary = pathSummary,
    itemCount = itemCount,
    fileCount = fileCount,
    folderCount = folderCount,
    totalBytes = totalBytes,
    newestModifiedAt = newestModifiedAt,
    oldestModifiedAt = oldestModifiedAt,
    mimeTypeSummary = mimeTypeSummary,
    extensionSummary = extensionSummary,
    hiddenCount = hiddenCount,
    accessStatus = accessStatus,
    folderFileCount = folderStats?.fileCount,
    folderTotalBytes = folderStats?.totalBytes,
    isSingleItem = isSingleItem,
    isDirectory = isDirectory
)
