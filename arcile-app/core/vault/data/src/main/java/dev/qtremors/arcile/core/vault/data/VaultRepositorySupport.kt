package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultPath
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultSessionLease
import dev.qtremors.arcile.core.vault.domain.VaultKeyLease
import dev.qtremors.arcile.core.vault.domain.VaultLeasePurpose
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean

internal const val ROOT_DIRECTORY = "onlyfiles"
internal const val OBJECTS_DIRECTORY = "objects"
internal const val MANIFESTS_DIRECTORY = "manifests"
internal const val TRANSACTIONS_DIRECTORY = "transactions"
internal const val STAGING_PREFIX = ".creating-"

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

internal fun createVaultDirectories(access: dev.qtremors.arcile.core.vault.crypto.VaultDirectoryAccess) {
    check(access.createDirectory(OBJECTS_DIRECTORY)) { "Unable to create vault object directory" }
    check(access.createDirectory(MANIFESTS_DIRECTORY)) { "Unable to create vault manifest directory" }
    check(access.createDirectory(TRANSACTIONS_DIRECTORY)) { "Unable to create vault transaction directory" }
}

internal fun cleanupInterruptedArtifacts(vaultRoot: File) {
    vaultRoot.mkdirs()
    vaultRoot.listFiles().orEmpty().filter { it.name.startsWith(STAGING_PREFIX) }.forEach(File::deleteRecursively)
    vaultRoot.walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.forEach(File::delete)
}

internal fun moveDirectory(source: File, destination: File) = moveAtomicallyWhenSupported(source, destination)

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

internal class VaultKeyLeaseImpl(
    override val vaultId: VaultId,
    override val purpose: VaultLeasePurpose,
    private val delegate: VaultSessionLease
) : VaultKeyLease {
    private val closed = AtomicBoolean(false)
    override val isClosed: Boolean get() = closed.get()
    override fun close() {
        if (closed.compareAndSet(false, true)) delegate.close()
    }
}

internal class VaultLeasedReader(
    private val delegate: VaultSeekableReader,
    private val lease: VaultSessionLease,
    private val onClosed: (VaultLeasedReader) -> Unit = {}
) : VaultSeekableReader {
    override val sizeBytes: Long get() = delegate.sizeBytes
    private val closed = AtomicBoolean(false)
    override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int =
        delegate.readAt(position, target, offset, length)
    override fun close() {
        if (closed.compareAndSet(false, true)) try {
            delegate.close()
        } finally {
            try {
                lease.close()
            } finally {
                onClosed(this)
            }
        }
    }
}
