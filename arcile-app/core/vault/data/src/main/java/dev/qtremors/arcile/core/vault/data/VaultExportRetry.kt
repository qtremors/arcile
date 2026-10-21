package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultExportRetry
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes

internal data class DeferredExportChild(val node: VaultNodeMetadata, val parent: List<String>)

internal data class PublishedExportFile(val relativePath: List<String>, val size: Long, val digest: String)

internal data class ExportFolderIdentity(val fileKey: String?, val createdAt: Long) {
    companion object {
        fun read(folder: File): ExportFolderIdentity {
            val attributes = Files.readAttributes(folder.toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (!attributes.isDirectory) throw VaultFailure.ConcurrentMutation()
            return ExportFolderIdentity(attributes.fileKey()?.toString(), attributes.creationTime().toMillis())
        }
    }
}

internal data class PendingVaultExport(
    val rootNode: VaultNodeMetadata,
    val outputRoot: File,
    override val move: Boolean,
    val deferred: List<DeferredExportChild>,
    val generations: Map<DirectoryId, Long>,
    val files: List<PublishedExportFile>,
    val folders: Map<List<String>, ExportFolderIdentity>
) : VaultExportRetry {
    override val source: VaultNodeRef get() = rootNode.ref
    override val displayName: String get() = rootNode.name
    override val remainingCount: Int get() = deferred.size
}

/** Collects the proof of only the vault data written by this attempt. Existing local files are excluded. */
internal class ExportRetryTrace(private val stagedRoot: File, private val trackProofs: Boolean) {
    private val skipped = mutableListOf<DeferredExportChild>()
    private val generations = linkedMapOf<DirectoryId, Long>()
    private val files = mutableListOf<PublishedExportFile>()
    private val folders = linkedMapOf<List<String>, ExportFolderIdentity>()
    private var overflow = false

    fun skipped(node: VaultNodeMetadata, parent: File) {
        record { skipped += DeferredExportChild(node, relativePath(parent)) }
    }

    fun directory(id: DirectoryId, generation: Long, destination: File) {
        if (overflow) return
        val previous = generations[id]
        if (previous != null) {
            if (generation != previous) throw VaultFailure.SourceChanged()
            return
        }
        val path = relativePath(destination)
        if (path !in folders) record { folders[path] = ExportFolderIdentity.read(destination) }
        if (trackProofs && !overflow) generations[id] = generation
    }

    fun file(destination: File, size: Long, digest: String) {
        if (trackProofs) record { files += PublishedExportFile(relativePath(destination), size, digest) }
    }

    fun snapshot(node: VaultNodeMetadata, published: File, move: Boolean): PendingVaultExport? =
        if (overflow) null
        else PendingVaultExport(node, published, move, skipped.toList(), generations.toMap(), files.toList(), folders.toMap())

    private fun relativePath(path: File): List<String> =
        path.relativeTo(stagedRoot).invariantSeparatorsPath.takeIf(String::isNotEmpty)?.split('/').orEmpty()

    private inline fun record(add: () -> Unit) {
        if (overflow) return
        if (skipped.size + files.size + folders.size >= MAX_EXPORT_RETRY_RECORDS) {
            overflow = true
            skipped.clear()
            generations.clear()
            files.clear()
            folders.clear()
        } else add()
    }
}

internal const val MAX_EXPORT_RETRY_RECORDS = 10_000
