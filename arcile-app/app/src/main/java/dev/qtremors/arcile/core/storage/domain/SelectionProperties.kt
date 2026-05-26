package dev.qtremors.arcile.core.storage.domain

import androidx.compose.runtime.Immutable

enum class PropertiesAccessStatus {
    Full,
    Partial,
    Limited
}

@Immutable
data class SelectionProperties(
    val displayName: String,
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
    val folderStats: FolderStats? = null,
    val isSingleItem: Boolean = false,
    val isDirectory: Boolean? = null
)
