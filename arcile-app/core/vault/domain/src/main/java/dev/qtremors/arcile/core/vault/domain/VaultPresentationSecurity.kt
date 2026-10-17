package dev.qtremors.arcile.core.vault.domain

import kotlinx.coroutines.flow.StateFlow

data class VaultSecuritySettings(
    val screenshotProtectionEnabled: Boolean = true,
    val viewOptions: VaultViewOptions = VaultViewOptions(),
    val folderViewOptions: Map<String, VaultFolderViewOptions> = emptyMap(),
    val lastDirectories: Map<VaultId, DirectoryId> = emptyMap(),
    val pinnedDirectories: Map<VaultId, Set<DirectoryId>> = emptyMap(),
    val workspaceTabsEnabled: Boolean = false,
    val pinnedWorkspaceTabs: Map<VaultId, List<DirectoryId>> = emptyMap()
)

const val VAULT_MAX_WORKSPACE_TABS = 8

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
    suspend fun setLastDirectory(vaultId: VaultId, directoryId: DirectoryId)
    suspend fun setDirectoryPinned(vaultId: VaultId, directoryId: DirectoryId, pinned: Boolean)
    suspend fun resetFolderViewOptions(vaultId: VaultId, directoryId: DirectoryId)
    suspend fun setWorkspaceTabsEnabled(enabled: Boolean)
    suspend fun setPinnedWorkspaceTabs(vaultId: VaultId, directories: List<DirectoryId>)
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
