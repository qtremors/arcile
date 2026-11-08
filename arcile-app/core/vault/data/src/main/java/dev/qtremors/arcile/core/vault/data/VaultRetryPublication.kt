package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
import kotlinx.serialization.Serializable

/** A small publication delta. The containing encrypted path binds this to its queue/root/sequence. */
@Serializable
internal data class VaultRetryPublication(
    val mergeChild: SavedDeferredVaultChild? = null,
    val exportChild: SavedDeferredExport? = null,
    val vault: VaultPublicationReceipt? = null,
    val local: VaultExportPublicationReceipt? = null,
    val settled: Boolean = false
) {
    fun validate(checkpoint: VaultRetryCheckpoint, plan: VaultQueuePlan, index: Int, owner: VaultId) {
        checkpoint.validate(plan, index, owner)
        require((mergeChild == null) != (exportChild == null))
        if (mergeChild != null) {
            val root = requireNotNull(checkpoint.merge)
            require(mergeChild in root.children && local == null)
            val destination = requireNotNull(mergeChild.destinationFolders.last().directory)
            requireNotNull(vault).validate(
                VaultQueuePlan(VaultRecoveryKind.PASTE, destination, false, nodes = listOf(mergeChild.source)), 0, owner
            )
        } else {
            val root = requireNotNull(checkpoint.export)
            val child = requireNotNull(exportChild)
            require(child in root.deferred && vault == null)
            val parent = child.parent.fold(File(root.output)) { current, name -> File(current, name) }
            requireNotNull(local).validate(VaultQueuePlan(
                VaultRecoveryKind.EXPORT, parent.path, false, nodes = listOf(child.node.ref),
                localDestinationIdentity = requireNotNull(root.folders.find { it.path == child.parent }).identity
            ), 0, owner)
            require(!settled || local.ready)
        }
        apply(checkpoint).validate(plan, index, owner)
    }

    fun inspect(checkpoint: VaultRetryCheckpoint, session: VaultSessionRecord): VaultPublicationState {
        vault?.let { return it.inspect(session) }
        val root = requireNotNull(checkpoint.export)
        val child = requireNotNull(exportChild)
        // Every ancestor must remain the same folder, not just the immediate destination.
        for (depth in 0..child.parent.size) {
            val parts = child.parent.take(depth)
            val expected = root.folders.find { it.path == parts }?.identity ?: return VaultPublicationState.UNCERTAIN
            val folder = parts.fold(File(root.output)) { parent, name -> File(parent, name) }
            if (!runCatching { expected.matches(folder) && folder.canonicalFile == folder }.getOrDefault(false)) {
                return VaultPublicationState.UNCERTAIN
            }
        }
        return requireNotNull(local).reconcile()
    }

    fun apply(checkpoint: VaultRetryCheckpoint): VaultRetryCheckpoint {
        val merge = checkpoint.merge
        if (merge != null) {
            val child = requireNotNull(mergeChild)
            val pending = vault?.checkpoint?.merge
            val nested = pending?.children.orEmpty().map { entry -> entry.copy(
                sourceFolders = child.sourceFolders + entry.sourceFolders,
                destinationFolders = child.destinationFolders + entry.destinationFolders
            ) }
            return checkpoint.copy(merge = merge.copy(children = merge.children - child + nested))
        }
        val root = requireNotNull(checkpoint.export)
        val child = requireNotNull(exportChild)
        val pending = local?.checkpoint?.export
        val prefix = child.parent + File(requireNotNull(local).destination).name
        return checkpoint.copy(export = root.copy(
            deferred = root.deferred - child + pending?.deferred.orEmpty().map { it.copy(parent = prefix + it.parent) },
            generations = (root.generations + pending?.generations.orEmpty()).associateBy { it.directory }.values.toList(),
            files = root.files + pending?.files.orEmpty().map { it.copy(path = prefix + it.path) },
            folders = (root.folders + pending?.folders.orEmpty().map { it.copy(path = prefix + it.path) }).associateBy { it.path }.values.toList()
        ))
    }

    fun sameChild(other: VaultRetryPublication): Boolean = mergeChild == other.mergeChild && exportChild == other.exportChild

    companion object {
        fun merge(child: DeferredVaultChild, receipt: VaultPublicationReceipt) = VaultRetryPublication(
            mergeChild = SavedDeferredVaultChild(child.sourceFolders.map(QueuedVaultNode::from),
                child.destinationFolders.map(QueuedVaultNode::from), QueuedVaultNode.from(child.source), child.sourceRevision, child.sourceName),
            vault = receipt
        )

        fun export(child: DeferredExportChild, receipt: VaultExportPublicationReceipt) = VaultRetryPublication(
            exportChild = SavedDeferredExport(SavedVaultNode.from(child.node), child.parent), local = receipt
        )
    }
}
