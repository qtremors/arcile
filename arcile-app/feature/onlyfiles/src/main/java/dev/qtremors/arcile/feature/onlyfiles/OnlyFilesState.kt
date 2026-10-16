package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.storage.domain.SearchFilters
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultHealthReport
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultImportState
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSearchHit
import dev.qtremors.arcile.core.vault.domain.VaultSortDirection
import dev.qtremors.arcile.core.vault.domain.VaultSortField
import dev.qtremors.arcile.core.vault.domain.VaultSummary
import dev.qtremors.arcile.core.vault.domain.VaultTransferProgress
import dev.qtremors.arcile.core.vault.domain.VaultViewOptions
import dev.qtremors.arcile.core.vault.domain.VaultFolderViewOptions
import dev.qtremors.arcile.core.vault.domain.vaultFolderViewKey

internal enum class VaultClipboardAction { COPY, MOVE }

internal data class VaultDirectoryCrumb(val id: DirectoryId, val name: String, val path: VaultPath)
internal data class VaultClipboard(
    val action: VaultClipboardAction,
    val sources: List<VaultNodeRef>,
    val displayNodes: List<VaultNodeMetadata> = emptyList()
)
internal data class VaultConflictPrompt(
    val requestId: Long,
    val conflicts: List<VaultConflict>,
    val allowFuture: Boolean,
    val exporting: Boolean
)
internal data class OnlyFilesBatchProgress(
    val completedItems: Int,
    val totalItems: Int,
    val currentName: String?
)

internal data class OnlyFilesUiState(
    val vaults: List<VaultSummary> = emptyList(),
    val selectedVaultId: VaultId? = null,
    val mountGeneration: Long = 0L,
    val directoryStack: List<VaultDirectoryCrumb> = emptyList(),
    val nodes: List<VaultNodeMetadata> = emptyList(),
    val nextPageToken: String? = null,
    val listingRevision: Long = 0L,
    val searchHits: List<VaultSearchHit> = emptyList(),
    val searchNextPageToken: String? = null,
    val searchQuery: String = "",
    val searchFilters: SearchFilters = SearchFilters(),
    val searchScope: List<VaultDirectoryCrumb>? = null,
    val revealNodeId: String? = null,
    val revealHiddenNodeId: String? = null,
    val locationPicker: OnlyFilesLocationPickerState? = null,
    val pinnedDirectoryIds: Set<DirectoryId> = emptySet(),
    val isSearching: Boolean = false,
    val isListing: Boolean = false,
    val defaultViewOptions: VaultViewOptions = VaultViewOptions(),
    val folderViewOptions: Map<String, VaultFolderViewOptions> = emptyMap(),
    val selectedNodeIds: Set<String> = emptySet(),
    val clipboard: VaultClipboard? = null,
    val viewer: VaultNodeMetadata? = null,
    val viewerQueue: List<VaultNodeMetadata> = emptyList(),
    val properties: List<VaultNodeMetadata> = emptyList(),
    val pendingConflict: VaultConflictPrompt? = null,
    val transferProgress: VaultTransferProgress? = null,
    val transferMountGeneration: Long? = null,
    val pasteQueueNames: List<String> = emptyList(),
    val healthReport: VaultHealthReport? = null,
    val activeImports: Map<VaultId, VaultImportState> = emptyMap(),
    val biometricVaultIds: Set<VaultId> = emptySet(),
    val folderPicker: VaultFolderPickerState? = null,
    val localPicker: OnlyFilesLocalPickerState? = null,
    val screenshotProtectionEnabled: Boolean = true,
    val encryptedThumbnailFiles: Int = 0,
    val encryptedThumbnailBytes: Long = 0L,
    val activeExternalGrants: Int = 0,
    val isSettingsBusy: Boolean = false,
    val showSettingsSheet: Boolean = false,
    val showSecurityDisclosure: Boolean = false,
    val busy: Boolean = false,
    val isSelectingRows: Boolean = false,
    val batchProgress: OnlyFilesBatchProgress? = null,
    val archiveProgress: OnlyFilesArchiveProgress? = null,
    val completedArchive: VaultNodeMetadata? = null,
    val message: String? = null
) {
    val selectedVault: VaultSummary? get() = vaults.firstOrNull { it.id == selectedVaultId }
    val currentDirectory: VaultDirectoryCrumb? get() = directoryStack.lastOrNull()
    val hasExactFolderView: Boolean
        get() {
            val vault = selectedVaultId ?: return false
            val directory = currentDirectory ?: return false
            return vaultFolderViewKey(vault, directory.id) in folderViewOptions
        }
    private val activeFolderOptions: VaultFolderViewOptions?
        get() {
            val vault = selectedVaultId ?: return null
            for (index in directoryStack.indices.reversed()) {
                val folder = folderViewOptions[vaultFolderViewKey(vault, directoryStack[index].id)] ?: continue
                if (index == directoryStack.lastIndex || folder.applyToSubfolders) return folder
            }
            return null
        }
    val viewOptions: VaultViewOptions
        get() = activeFolderOptions?.options?.copy(
            recursiveSearch = defaultViewOptions.recursiveSearch,
            showHiddenFiles = defaultViewOptions.showHiddenFiles
        ) ?: defaultViewOptions
    val viewOptionsApplyToSubfolders: Boolean get() = activeFolderOptions?.applyToSubfolders == true
    val recursiveSearch: Boolean get() = viewOptions.recursiveSearch
    val sortField: VaultSortField get() = viewOptions.sortField
    val sortDirection: VaultSortDirection get() = viewOptions.sortDirection
    val hasSearch: Boolean get() = searchQuery.isNotBlank() || searchFilters.hasActiveFilters || searchScope != null
    val isLoadingRows: Boolean get() = if (hasSearch) isSearching else isListing
    val hasMoreRows: Boolean get() = if (hasSearch) searchNextPageToken != null else nextPageToken != null
    val displayedNodes: List<VaultNodeMetadata>
        get() = if (hasSearch) searchHits.map(VaultSearchHit::metadata) else nodes
    val selectedNodes: List<VaultNodeMetadata>
        get() = displayedNodes.filter { it.ref.nodeId.value in selectedNodeIds }

    fun withUpdatedViewerNode(updated: VaultNodeMetadata): OnlyFilesUiState {
        if (viewer?.ref != updated.ref && viewerQueue.none { it.ref == updated.ref }) return this
        return copy(
            viewer = if (viewer?.ref == updated.ref) updated else viewer,
            viewerQueue = viewerQueue.map { if (it.ref == updated.ref) updated else it }
        )
    }

    fun propertiesLocation(items: List<VaultNodeMetadata>): String {
        val vaultName = selectedVault?.name.orEmpty()
        val first = items.firstOrNull() ?: return vaultName
        if (items.any { it.ref.parentId != first.ref.parentId }) return vaultName
        val resultRoot = searchScope ?: directoryStack
        val ancestors = when {
            first.ref.parentId == currentDirectory?.id -> emptyList()
            else -> searchHits.firstOrNull { it.metadata.ref.parentId == first.ref.parentId }?.parentNames
                ?: return vaultName
        }
        val stack = if (first.ref.parentId == currentDirectory?.id) directoryStack else resultRoot
        return (listOf(vaultName) + stack.drop(1).map(VaultDirectoryCrumb::name) + ancestors)
            .joinToString(" / ")
    }
}

internal fun List<VaultSummary>.withCurrentUnlockState(
    unlockedVaultIds: Set<VaultId>
): List<VaultSummary> = map { vault ->
    val isUnlocked = vault.id in unlockedVaultIds
    if (vault.isUnlocked == isUnlocked) vault else vault.copy(isUnlocked = isUnlocked)
}

internal fun pendingUnlockVault(
    requestedId: VaultId?,
    selectedId: VaultId?,
    vaults: List<VaultSummary>
): VaultSummary? {
    if (requestedId == null || requestedId == selectedId) return null
    return vaults.firstOrNull { vault -> vault.id == requestedId && !vault.isUnlocked }
}
