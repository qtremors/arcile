package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultIndex
import dev.qtremors.arcile.core.vault.crypto.VaultIndexEntry
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNode
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultSessionLease
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

internal const val ROOT_DIRECTORY = "onlyfiles"
internal const val OBJECTS_DIRECTORY = "objects"
internal const val STAGING_PREFIX = ".creating-"

internal fun requireDirectory(index: VaultIndex, path: VaultPath) {
    if (path.isRoot) return
    if (index.entries.none { it.path == path.value && it.isDirectory }) {
        throw VaultFailure.InvalidPath("Destination folder is unavailable")
    }
}

internal fun ensureAvailable(index: VaultIndex, path: VaultPath) {
    if (index.entries.any { it.path.equals(path.value, ignoreCase = true) }) {
        throw VaultFailure.PathConflict(path)
    }
}

internal fun uniqueImportPath(index: VaultIndex, parent: VaultPath, rawName: String): VaultPath {
    val name = safeImportName(rawName)
    var candidate = parent.resolve(name)
    if (index.entries.none { it.path.equals(candidate.value, ignoreCase = true) }) return candidate
    val extension = name.substringAfterLast('.', "").takeIf { '.' in name }
    val base = if (extension == null) name else name.dropLast(extension.length + 1)
    var number = 1
    while (true) {
        val nextName = if (extension == null) "$base ($number)" else "$base ($number).$extension"
        candidate = parent.resolve(nextName)
        if (index.entries.none { it.path.equals(candidate.value, ignoreCase = true) }) return candidate
        number++
    }
}

internal fun validateVaultName(name: String): String {
    val trimmed = name.trim()
    try {
        VaultPath.validateSegment(trimmed)
    } catch (error: IllegalArgumentException) {
        throw VaultFailure.InvalidName(error.message ?: "Invalid vault name")
    }
    if (trimmed.startsWith('.')) throw VaultFailure.InvalidName("Vault name must not start with a dot")
    return trimmed
}

private fun safeImportName(name: String): String {
    val sanitized = name.replace('/', '_').replace('\\', '_').replace('\u0000', '_').trim()
    return sanitized.takeIf { it.isNotBlank() && it != "." && it != ".." } ?: "Imported file"
}

internal fun cleanupInterruptedArtifacts(vaultRoot: File) {
    vaultRoot.mkdirs()
    vaultRoot.listFiles().orEmpty().filter { it.name.startsWith(STAGING_PREFIX) }.forEach(File::deleteRecursively)
    vaultRoot.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.forEach(File::delete)
}

internal fun moveDirectory(source: File, destination: File) = moveAtomicallyWhenSupported(source, destination)
internal fun moveFile(source: File, destination: File) = moveAtomicallyWhenSupported(source, destination)

private fun moveAtomicallyWhenSupported(source: File, destination: File) {
    try {
        Files.move(source.toPath(), destination.toPath(), StandardCopyOption.ATOMIC_MOVE)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source.toPath(), destination.toPath())
    }
}

internal class VaultSessionLeaseImpl(
    private val session: VaultSessionRecord,
    private val release: (VaultSessionRecord) -> Unit
) : VaultSessionLease {
    private val closed = AtomicBoolean(false)
    fun belongsTo(vaultId: VaultId): Boolean = session.id == vaultId
    fun detachToReservation(): Boolean = closed.compareAndSet(false, true)
    override fun close() {
        if (closed.compareAndSet(false, true)) release(session)
    }
}

internal class VaultLeasedReader(
    private val delegate: VaultSeekableReader,
    private val lease: VaultSessionLease
) : VaultSeekableReader {
    override val sizeBytes: Long get() = delegate.sizeBytes
    private val closed = AtomicBoolean(false)
    override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int =
        delegate.readAt(position, target, offset, length)
    override fun close() {
        if (closed.compareAndSet(false, true)) try {
            delegate.close()
        } finally {
            lease.close()
        }
    }
}

internal fun VaultIndexEntry.toNode(): VaultNode = VaultNode(
    id = id,
    path = VaultPath.of(path),
    sizeBytes = sizeBytes,
    modifiedAtMillis = modifiedAtMillis,
    isDirectory = isDirectory,
    mimeType = mimeType
)
