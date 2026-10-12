package dev.qtremors.arcile.core.vault.domain

import kotlinx.coroutines.flow.StateFlow

data class VaultSecuritySettings(
    val screenshotProtectionEnabled: Boolean = true,
    val viewOptions: VaultViewOptions = VaultViewOptions(),
    val folderViewOptions: Map<String, VaultFolderViewOptions> = emptyMap()
)

data class VaultFolderViewOptions(val options: VaultViewOptions, val applyToSubfolders: Boolean)

fun vaultFolderViewKey(vaultId: VaultId, directoryId: DirectoryId): String = "${vaultId.value}/${directoryId.value}"

data class VaultViewOptions(
    val grid: Boolean = false,
    val sortField: VaultSortField = VaultSortField.NAME,
    val sortDirection: VaultSortDirection = VaultSortDirection.ASCENDING,
    val recursiveSearch: Boolean = true,
    val showThumbnails: Boolean = true,
    val listZoom: Float = 1f,
    val gridMinCellSize: Float = 132f,
    val showHiddenFiles: Boolean = true,
    val foldersFirst: Boolean = true
)

interface VaultSecurityPreferences {
    val settings: StateFlow<VaultSecuritySettings>
    suspend fun setScreenshotProtectionEnabled(enabled: Boolean)
    suspend fun setViewOptions(options: VaultViewOptions)
    suspend fun setFolderViewOptions(
        vaultId: VaultId,
        directoryId: DirectoryId,
        options: VaultFolderViewOptions
    )
}

data class VaultThumbnailCacheStats(val encryptedFileCount: Int, val encryptedBytes: Long)

interface VaultThumbnailCache {
    suspend fun loadOrCreate(
        ref: VaultNodeRef,
        revision: Long,
        requestedSizePx: Int
    ): Result<ByteArray>

    suspend fun clear(): Result<VaultThumbnailCacheStats>
    suspend fun stats(): Result<VaultThumbnailCacheStats>
}
