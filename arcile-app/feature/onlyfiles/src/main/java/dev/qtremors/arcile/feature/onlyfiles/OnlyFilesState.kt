package dev.qtremors.arcile.feature.onlyfiles

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

internal enum class OnlyFilesLayout { LIST, GRID }
internal enum class VaultClipboardAction { COPY, MOVE }

internal data class VaultDirectoryCrumb(val id: DirectoryId, val name: String, val path: VaultPath)
internal data class VaultClipboard(val action: VaultClipboardAction, val sources: List<VaultNodeRef>)
internal data class VaultConflictPrompt(val requestId: Long, val conflict: VaultConflict)

internal data class OnlyFilesUiState(
    val vaults: List<VaultSummary> = emptyList(),
    val selectedVaultId: VaultId? = null,
    val directoryStack: List<VaultDirectoryCrumb> = emptyList(),
    val nodes: List<VaultNodeMetadata> = emptyList(),
    val nextPageToken: String? = null,
    val searchHits: List<VaultSearchHit> = emptyList(),
    val searchNextPageToken: String? = null,
    val searchQuery: String = "",
    val recursiveSearch: Boolean = false,
    val isSearching: Boolean = false,
    val sortField: VaultSortField = VaultSortField.NAME,
    val sortDirection: VaultSortDirection = VaultSortDirection.ASCENDING,
    val layout: OnlyFilesLayout = OnlyFilesLayout.LIST,
    val selectedNodeIds: Set<String> = emptySet(),
    val clipboard: VaultClipboard? = null,
    val viewer: VaultNodeMetadata? = null,
    val properties: List<VaultNodeMetadata> = emptyList(),
    val pendingConflict: VaultConflictPrompt? = null,
    val transferProgress: VaultTransferProgress? = null,
    val healthReport: VaultHealthReport? = null,
    val activeImports: Map<VaultId, VaultImportState> = emptyMap(),
    val folderPicker: VaultFolderPickerState? = null,
    val localPicker: OnlyFilesLocalPickerState? = null,
    val screenshotProtectionEnabled: Boolean = true,
    val busy: Boolean = false,
    val message: String? = null
) {
    val selectedVault: VaultSummary? get() = vaults.firstOrNull { it.id == selectedVaultId }
    val currentDirectory: VaultDirectoryCrumb? get() = directoryStack.lastOrNull()
    val displayedNodes: List<VaultNodeMetadata>
        get() = if (searchQuery.isBlank()) nodes else searchHits.map(VaultSearchHit::metadata)
    val selectedNodes: List<VaultNodeMetadata>
        get() = displayedNodes.filter { it.ref.nodeId.value in selectedNodeIds }
}
