package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.VaultCancellationSignal
import dev.qtremors.arcile.core.vault.domain.VaultConflict
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.UUID

internal const val VAULT_LOCAL_EXPORT_BUFFER_SIZE = 256 * 1024

internal fun exportFolder(retry: PendingVaultExport, segments: List<String>): File {
    var directory = retry.outputRoot
    if (directory.canonicalFile != directory ||
        retry.folders[emptyList()]?.matches(directory) != true
    ) throw VaultFailure.ConcurrentMutation()
    for ((index, segment) in segments.withIndex()) {
        if (segment.isEmpty() || segment == "." || segment == ".." || '/' in segment || '\\' in segment) {
            throw VaultFailure.InvalidPath("The export folder changed")
        }
        directory = File(directory, segment)
        if (retry.folders[segments.take(index + 1)]?.matches(directory) != true) {
            throw VaultFailure.ConcurrentMutation()
        }
    }
    return directory
}

internal fun localDigest(file: File, cancellation: VaultCancellationSignal): String {
    val digest = MessageDigest.getInstance("SHA-256")
    Files.newInputStream(file.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
        val buffer = ByteArray(VAULT_LOCAL_EXPORT_BUFFER_SIZE)
        try {
            while (true) {
                cancellation.throwIfCancelled()
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        } finally {
            buffer.fill(0)
        }
    }
    return digest.digest().toHexDigest()
}

internal fun ByteArray.toHexDigest(): String = joinToString("") { "%02x".format(it) }.also { fill(0) }

internal fun copyLocalDirectory(source: File, destination: File, cancellation: VaultCancellationSignal) {
    val pending = ArrayDeque<Pair<File, File>>()
    pending += source to destination
    while (pending.isNotEmpty()) {
        val (from, to) = pending.removeLast()
        if (!from.inspectDestination().isDirectory) throw VaultFailure.ConcurrentMutation()
        from.listFiles()?.forEach { child ->
            cancellation.throwIfCancelled()
            val target = File(to, child.name)
            if (child.inspectDestination().isDirectory) {
                check(target.mkdir())
                pending += child to target
            } else {
                Files.newInputStream(child.toPath(), LinkOption.NOFOLLOW_LINKS).use { input ->
                    target.outputStream().use(input::copyTo)
                }
            }
        } ?: throw VaultFailure.Unavailable("Unable to read existing destination")
    }
}

internal fun publishLocal(staging: File, destination: File, replacing: LocalDestinationEntry?, preparedBackup: File? = null) {
    val parent = requireNotNull(destination.parentFile)
    if (!parent.inspectDestination().isDirectory) throw VaultFailure.ConcurrentMutation()
    val current = parent.readLocalChildren().findLocalChild(destination.name)
    if (replacing == null) {
        if (current != null) throw VaultFailure.ConcurrentMutation()
        Files.move(staging.toPath(), destination.toPath())
        return
    }
    if (current?.inspectDestination() != replacing) throw VaultFailure.ConcurrentMutation()
    val backup = preparedBackup ?: File(parent, ".arcile-backup-${UUID.randomUUID()}.tmp")
    if (backup.parentFile != parent || Files.exists(backup.toPath(), LinkOption.NOFOLLOW_LINKS)) throw VaultFailure.ConcurrentMutation()
    Files.move(replacing.file.toPath(), backup.toPath())
    try {
        Files.move(staging.toPath(), destination.toPath())
    } catch (error: Exception) {
        try {
            Files.move(backup.toPath(), replacing.file.toPath())
        } catch (restore: Exception) {
            error.addSuppressed(restore)
        }
        throw error
    }
    backup.deleteLocalTree()
}

internal fun File.deleteLocalTree() {
    val root = toPath()
    if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return
    Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::delete) }
}

internal fun uniqueLocalName(parent: File, name: String): String {
    val dot = name.lastIndexOf('.').takeIf { it > 0 }
    val stem = dot?.let { name.substring(0, it) } ?: name
    val suffix = dot?.let(name::substring).orEmpty()
    for (index in 1..10_000) {
        val candidate = "$stem ($index)$suffix"
        if (parent.listFiles()?.none { it.name.equals(candidate, true) } == true) return candidate
    }
    throw VaultFailure.NameConflict(name)
}

internal fun File.readLocalChildren(): Array<File> =
    listFiles() ?: throw VaultFailure.Unavailable("Unable to read export destination")

internal fun Array<File>.findLocalChild(name: String): File? =
    firstOrNull { it.name == name } ?: firstOrNull { it.name.equals(name, ignoreCase = true) }

internal fun File.inspectDestination(): LocalDestinationEntry {
    val attributes = Files.readAttributes(toPath(), BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
    if (attributes.isSymbolicLink) throw VaultFailure.SymlinkRejected(name)
    if (!attributes.isRegularFile && !attributes.isDirectory) {
        throw VaultFailure.Unavailable("The export destination contains an unsupported item")
    }
    return LocalDestinationEntry(
        this, attributes.isDirectory, attributes.size(), attributes.lastModifiedTime().toMillis(),
        attributes.creationTime().toMillis(), exportFileKey(this, attributes), attributes.creationTime().toString()
    )
}

internal data class LocalDestinationEntry(
    val file: File,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedAt: Long,
    val createdAt: Long,
    val fileKey: String?,
    val createdAtPrecision: String? = null
) {
    fun conflictWith(source: VaultNodeMetadata) = VaultConflict(
        sourceName = source.name,
        destinationName = file.name,
        sourceIsDirectory = source.isDirectory,
        destinationIsDirectory = isDirectory,
        sourceIdentity = "${source.ref.vaultId.value}:${source.ref.nodeId.value}:${source.revision}",
        destinationIdentity = "$file:$fileKey:$createdAt",
        sourceSize = source.sizeBytes,
        destinationSize = if (isDirectory) 0L else size,
        sourceModifiedAt = source.modifiedAtMillis,
        destinationModifiedAt = modifiedAt
    )
}
