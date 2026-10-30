package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.*
import java.io.File
import kotlinx.serialization.Serializable

/** Metadata only. This document is stored using the journal's authenticated encryption. */
@Serializable
internal data class SavedVaultNode(
    val ref: QueuedVaultNode,
    val name: String,
    val kind: VaultNodeKind,
    val size: Long,
    val modified: Long,
    val revision: Long,
    val mime: String? = null
) {
    fun restore() = VaultNodeMetadata(ref.ref(), name, kind, size, modified, revision, mime)

    companion object {
        fun from(node: VaultNodeMetadata) = SavedVaultNode(
            QueuedVaultNode.from(node.ref), node.name, node.kind, node.sizeBytes,
            node.modifiedAtMillis, node.revision, node.mimeType
        )
    }
}

@Serializable
internal data class SavedDeferredExport(val node: SavedVaultNode, val parent: List<String>)

@Serializable
internal data class SavedExportFile(val path: List<String>, val size: Long, val digest: String)

@Serializable
internal data class SavedExportFolder(val path: List<String>, val identity: ExportFolderIdentity)

@Serializable
internal data class SavedDirectoryGeneration(val directory: String, val generation: Long)

@Serializable
internal data class SavedVaultExport(
    val root: SavedVaultNode,
    val output: String,
    val deferred: List<SavedDeferredExport>,
    val generations: List<SavedDirectoryGeneration>,
    val files: List<SavedExportFile>,
    val folders: List<SavedExportFolder>
) {
    fun restore() = PendingVaultExport(
        root.restore(), File(output), move = false,
        deferred.map { DeferredExportChild(it.node.restore(), it.parent) },
        generations.associate { DirectoryId.of(it.directory) to it.generation },
        files.map { PublishedExportFile(it.path, it.size, it.digest) },
        folders.associate { it.path to it.identity }
    )

    fun validate(plan: VaultQueuePlan) {
        root.restore()
        require(File(output).isAbsolute && File(output).parentFile == File(plan.destination))
        require(deferred.size + files.size + folders.size <= MAX_EXPORT_RETRY_RECORDS && generations.size <= MAX_EXPORT_RETRY_RECORDS)
        deferred.forEach { it.node.restore(); validateRelativePath(it.parent) }
        generations.forEach { DirectoryId.of(it.directory); require(it.generation >= 0) }
        require(generations.map { it.directory }.distinct().size == generations.size)
        files.forEach {
            validateRelativePath(it.path)
            require(it.path.isNotEmpty() && it.size >= 0 && it.digest.length == 64 && it.digest.all { char -> char in "0123456789abcdef" })
        }
        folders.forEach { validateRelativePath(it.path) }
        require(folders.map { it.path }.distinct().size == folders.size)
        require(folders.any { it.path.isEmpty() })
    }

    companion object {
        fun from(retry: PendingVaultExport) = SavedVaultExport(
            SavedVaultNode.from(retry.rootNode), retry.outputRoot.path,
            retry.deferred.map { SavedDeferredExport(SavedVaultNode.from(it.node), it.parent) },
            retry.generations.map { SavedDirectoryGeneration(it.key.value, it.value) },
            retry.files.map { SavedExportFile(it.relativePath, it.size, it.digest) },
            retry.folders.map { SavedExportFolder(it.key, it.value) }
        )
    }
}

@Serializable
internal data class SavedDeferredVaultChild(
    val sourceFolders: List<QueuedVaultNode>,
    val destinationFolders: List<QueuedVaultNode>,
    val source: QueuedVaultNode,
    val revision: Long,
    val name: String
) {
    fun restore() = DeferredVaultChild(
        sourceFolders.map { it.ref() }, destinationFolders.map { it.ref() }, source.ref(), revision, name
    )
}

@Serializable
internal data class SavedVaultMerge(
    val source: QueuedVaultNode,
    val destinationVault: String,
    val destinationDirectory: String,
    val name: String,
    val children: List<SavedDeferredVaultChild>
) {
    // Recovered moves resume as copies. No cleanup/deletion authority survives process death.
    fun restore() = PendingVaultMerge(
        source.ref(), VaultId.of(destinationVault), DirectoryId.of(destinationDirectory),
        move = false, name, children.map { it.restore() }, emptySet()
    )

    fun validate(plan: VaultQueuePlan, owner: VaultId) {
        source.ref()
        require(VaultId.of(destinationVault) == owner && destinationDirectory == plan.destination)
        VaultName.of(name)
        require(children.size <= MAX_EXPORT_RETRY_RECORDS)
        children.forEach { child ->
            require(child.sourceFolders.size <= MAX_EXPORT_RETRY_RECORDS && child.destinationFolders.size <= MAX_EXPORT_RETRY_RECORDS)
            val node = child.source.ref()
            require(node.vaultId == source.ref().vaultId && child.revision >= 0)
            VaultName.of(child.name)
            child.sourceFolders.forEach { require(it.ref().vaultId == node.vaultId && it.directory != null) }
            require(child.destinationFolders.isNotEmpty())
            child.destinationFolders.forEach { require(it.ref().vaultId == owner && it.directory != null) }
        }
    }

    companion object {
        fun from(retry: PendingVaultMerge) = SavedVaultMerge(
            QueuedVaultNode.from(retry.source), retry.destinationVault.value, retry.destinationDirectory.value,
            retry.displayName, retry.children.map { child ->
                SavedDeferredVaultChild(child.sourceFolders.map(QueuedVaultNode::from),
                    child.destinationFolders.map(QueuedVaultNode::from), QueuedVaultNode.from(child.source),
                    child.sourceRevision, child.sourceName)
            }
        )
    }
}

@Serializable
internal data class VaultRetryCheckpoint(
    val version: Int = 1,
    val merge: SavedVaultMerge? = null,
    val export: SavedVaultExport? = null,
    val completedSteps: Int = 0,
    val stepJournal: Boolean = false
) {
    val source: VaultNodeRef get() = merge?.source?.ref() ?: requireNotNull(export).root.ref.ref()

    fun validate(plan: VaultQueuePlan, index: Int, owner: VaultId) {
        require(version == 1 && completedSteps >= 0 && (merge == null) != (export == null))
        require(index in plan.nodes.indices && plan.nodes[index].ref() == source)
        if (merge != null) {
            require(plan.kind == VaultRecoveryKind.PASTE)
            merge.validate(plan, owner)
        } else {
            require(plan.kind == VaultRecoveryKind.EXPORT)
            requireNotNull(export).validate(plan)
        }
    }

    fun restore(): VaultItemResult {
        val merge = merge?.restore()
        val export = export?.restore()
        return VaultItemResult(source.backendIdentity, merge?.displayName ?: requireNotNull(export).displayName,
            VaultItemOutcome.PARTIAL, mergeRetry = merge, exportRetry = export)
    }

    companion object {
        fun from(result: VaultItemResult): VaultRetryCheckpoint? {
            if (result.outcome != VaultItemOutcome.PARTIAL) return null
            val merge = result.mergeRetry as? PendingVaultMerge
            val export = result.exportRetry as? PendingVaultExport
            return when {
                merge != null -> VaultRetryCheckpoint(merge = SavedVaultMerge.from(merge))
                export != null -> VaultRetryCheckpoint(export = SavedVaultExport.from(export))
                else -> null
            }
        }
    }
}

private fun validateRelativePath(parts: List<String>) {
    require(parts.size <= MAX_EXPORT_RETRY_RECORDS)
    parts.forEach { VaultName.of(it) }
}
