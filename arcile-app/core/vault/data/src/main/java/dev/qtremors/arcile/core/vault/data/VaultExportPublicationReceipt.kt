package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import java.nio.file.attribute.BasicFileAttributes
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultRecoveryKind
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
internal data class ExportEntryIdentity(val fileKey: String?, val createdAt: Long, val directory: Boolean, val createdAtPrecision: String? = null, val contentDigest: String? = null) {
    fun matches(file: File): Boolean = runCatching {
        val current = file.inspectDestination()
        current.isDirectory == directory && if (fileKey != null) current.fileKey == fileKey
        else contentDigest != null && current.createdAt == createdAt &&
            exportPathDigest(file, VaultCancellationSignal { Thread.currentThread().isInterrupted }) == contentDigest
    }.getOrDefault(false)

    companion object {
        fun from(entry: LocalDestinationEntry, cancellation: VaultCancellationSignal = VaultCancellationSignal { Thread.currentThread().isInterrupted }) =
            ExportEntryIdentity(entry.fileKey, entry.createdAt, entry.isDirectory, entry.createdAtPrecision,
                if (entry.fileKey == null) exportPathDigest(entry.file, cancellation) else null)
        fun read(file: File, owned: Boolean = true): ExportEntryIdentity {
            val attributes = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (owned) exportFileKey(file, attributes, createMarker = true)
            return from(file.inspectDestination())
        }
    }
}

/** Owns only UUID staging/backup entries in the authenticated destination, never arbitrary paths. */
@Serializable
internal data class VaultExportPublicationReceipt(
    val staging: String,
    val destination: String,
    val parentIdentity: ExportFolderIdentity,
    val stagedIdentity: ExportEntryIdentity,
    val backup: String? = null,
    val originalIdentity: ExportEntryIdentity? = null,
    val ready: Boolean = false,
    val partial: Boolean = false,
    val checkpoint: VaultRetryCheckpoint? = null
) {
    fun validate(plan: VaultQueuePlan, index: Int, owner: VaultId) {
        require(plan.kind == VaultRecoveryKind.EXPORT && index in plan.nodes.indices)
        val parent = File(plan.destination)
        require(File(staging).parentFile == parent && File(destination).parentFile == parent)
        require(isOwnedName(File(staging).name, ".arcile-"))
        require(parentIdentity.fileKey != null && plan.localDestinationIdentity?.let(parentIdentity::sameAs) == true)
        require((backup == null) == (originalIdentity == null))
        backup?.let { require(File(it).parentFile == parent && isOwnedName(File(it).name, ".arcile-backup-")) }
        require(checkpoint == null || ready && partial)
        checkpoint?.validate(plan, index, owner)
    }

    /** Restore a known backup before retry; delete only entries whose recorded identity still matches. */
    fun reconcile(): VaultPublicationState {
        val target = File(destination)
        val stage = File(staging)
        val parent = requireNotNull(target.parentFile)
        var parentVerified = false
        return try {
            parentVerified = parent.canonicalFile == parent && parentIdentity.matches(parent)
            if (!parentVerified) {
                VaultPublicationState.UNCERTAIN
            } else if (exists(target) && stagedIdentity.matches(target) && ready) {
                if (partial && checkpoint == null) VaultPublicationState.UNCERTAIN
                else {
                    backup?.let { path ->
                        val saved = File(path)
                        if (exists(saved)) {
                            require(requireNotNull(originalIdentity).matches(saved))
                            saved.deleteLocalTree()
                        }
                    }
                    removeStage(stage)
                    VaultPublicationState.PUBLISHED
                }
            } else {
                val saved = backup?.let(::File)
                if (saved != null && exists(saved)) {
                    require(requireNotNull(originalIdentity).matches(saved) && !exists(target))
                    Files.move(saved.toPath(), target.toPath())
                }
                if (originalIdentity != null) require(exists(target) && originalIdentity.matches(target))
                else require(!exists(target))
                removeStage(stage)
                VaultPublicationState.UNPUBLISHED
            }
        } catch (_: Exception) {
            if (parentVerified) runCatching { removeStage(stage) }
            VaultPublicationState.UNCERTAIN
        }
    }

    private fun removeStage(stage: File) {
        if (exists(stage)) {
            require(stagedIdentity.matches(stage))
            stage.deleteLocalTree()
        }
    }

    private fun exists(file: File): Boolean = Files.exists(file.toPath(), LinkOption.NOFOLLOW_LINKS)

    private fun isOwnedName(name: String, prefix: String): Boolean = name.startsWith(prefix) && name.endsWith(".tmp") &&
        runCatching { UUID.fromString(name.removePrefix(prefix).removeSuffix(".tmp")).toString() == name.removePrefix(prefix).removeSuffix(".tmp") }.getOrDefault(false)
}
