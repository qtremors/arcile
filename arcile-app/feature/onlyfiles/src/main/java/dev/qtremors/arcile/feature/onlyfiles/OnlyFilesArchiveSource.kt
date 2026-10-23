package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.archive.ArchiveReadSession
import dev.qtremors.arcile.core.archive.ArchiveReadSource
import dev.qtremors.arcile.core.archive.ArchiveReader
import dev.qtremors.arcile.core.archive.ArchiveSafetyPolicy
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_BYTES
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_ENTRIES
import dev.qtremors.arcile.core.vault.domain.VaultArchiveSource
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.ClosedChannelException
import java.nio.channels.NonWritableChannelException
import java.nio.channels.SeekableByteChannel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

private val vaultArchivePolicy =
    ArchiveSafetyPolicy(maxEntries = VAULT_ARCHIVE_MAX_ENTRIES, maxUncompressedBytes = VAULT_ARCHIVE_MAX_BYTES)
private val vaultArchiveReader = ArchiveReader(vaultArchivePolicy, allowBackslashes = false)
private val vaultArchivePreviewReader =
    ArchiveReader(vaultArchivePolicy.copy(maxEntries = VAULT_ARCHIVE_MAX_ENTRIES + 1), allowBackslashes = false)

internal suspend fun <T> readVaultArchive(
    node: VaultNodeMetadata,
    openReader: (VaultNodeRef) -> Result<VaultSeekableReader>,
    password: CharArray?,
    encoding: ArchiveNameEncoding,
    preview: Boolean = false,
    useArchive: suspend (ArchiveReadSession) -> T
): T = withContext(Dispatchers.IO) {
    val format = ArchiveFormat.fromPath(node.name) ?: throw VaultFailure.InvalidPath("Unsupported archive")
    val coroutine = currentCoroutineContext()
    openReader(node.ref).getOrThrow().use { reader ->
        if (reader.sizeBytes > VAULT_ARCHIVE_MAX_BYTES) {
            throw VaultFailure.Unavailable("Archive exceeds the vault archive size limit")
        }
        val budget = if (preview && (format == ArchiveFormat.ZIP || format == ArchiveFormat.SEVEN_Z)) {
            2L * 1024 * 1024
        } else VAULT_ARCHIVE_MAX_BYTES * 2
        val source = ArchiveReadSource(node.name, reader.sizeBytes, node.modifiedAtMillis) {
            VaultArchiveChannel(reader, budget) { coroutine.ensureActive() }
        }
        val engine = if (preview) vaultArchivePreviewReader else vaultArchiveReader
        val limit = if (preview) VAULT_ARCHIVE_MAX_ENTRIES + 1 else Int.MAX_VALUE
        engine.open(source, format, password, encoding, limit) { coroutine.ensureActive() }.use { archive ->
            useArchive(archive)
        }
    }
}

internal fun ArchiveReadSession.vaultSources(selection: VaultZipSelection): List<VaultArchiveSource> =
    entries.mapNotNull { entry ->
        val path = entry.segments
        if (path == null) {
            if (selection.folder.isEmpty() && selection.selected.isEmpty()) {
                throw VaultFailure.InvalidPath("Archive contains an unsafe entry path or compression ratio")
            }
            return@mapNotNull null
        }
        val relative = selection.relativePath(path) ?: return@mapNotNull null
        if (!entry.canRead) throw VaultFailure.Unavailable("Archive contains an unsupported entry")
        if (entry.isDirectory) VaultArchiveSource.Folder(relative)
        else VaultArchiveSource.File(relative, entry.size, entry.crc32, entry::openStream)
    }

internal class VaultArchiveChannel(
    private val reader: VaultSeekableReader,
    readBudget: Long,
    private val checkActive: () -> Unit
) : SeekableByteChannel {
    private var open = true
    private var offset = 0L
    private var remainingBudget = readBudget
    private val buffer = ByteArray(8192)

    override fun read(dst: ByteBuffer): Int {
        checkActive()
        if (!open) throw ClosedChannelException()
        if (!dst.hasRemaining()) return 0
        if (offset >= reader.sizeBytes) return -1
        if (remainingBudget <= 0) throw IOException("Archive reads exceeded the safety limit")
        val count = minOf(dst.remaining().toLong(), buffer.size.toLong(), remainingBudget, reader.sizeBytes - offset).toInt()
        val read = reader.readAt(offset, buffer, 0, count)
        if (read <= 0) throw IOException("Archive source ended before its declared size")
        dst.put(buffer, 0, read)
        offset += read
        remainingBudget -= read
        return read
    }

    override fun position(): Long = offset
    override fun position(newPosition: Long): SeekableByteChannel {
        if (!open) throw ClosedChannelException()
        require(newPosition >= 0L)
        offset = newPosition
        return this
    }

    override fun size(): Long = reader.sizeBytes
    override fun isOpen(): Boolean = open
    override fun close() {
        open = false
        buffer.fill(0)
    }
    override fun write(src: ByteBuffer): Int = throw NonWritableChannelException()
    override fun truncate(size: Long): SeekableByteChannel = throw NonWritableChannelException()
}
