package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultMergeRetry
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef

internal data class DeferredVaultChild(
    val sourceFolders: List<VaultNodeRef>,
    val destinationFolders: List<VaultNodeRef>,
    val source: VaultNodeRef,
    val sourceRevision: Long,
    val sourceName: String
) {
    val destination: DirectoryId get() = requireNotNull(destinationFolders.last().directoryId)

    fun nest(child: DeferredVaultChild): DeferredVaultChild = child.copy(
        sourceFolders = sourceFolders + child.sourceFolders,
        destinationFolders = destinationFolders + child.destinationFolders
    )
}

internal data class PendingVaultMerge(
    override val source: VaultNodeRef,
    override val destinationVault: VaultId,
    override val destinationDirectory: DirectoryId,
    override val move: Boolean,
    val displayName: String,
    val children: List<DeferredVaultChild>,
    val cleanupPaths: Set<List<VaultNodeRef>>
) : VaultMergeRetry {
    override val remainingCount: Int get() = children.size
}
