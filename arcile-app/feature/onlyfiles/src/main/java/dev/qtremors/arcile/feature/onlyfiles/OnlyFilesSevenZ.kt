package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_BYTES
import dev.qtremors.arcile.core.vault.domain.VAULT_ARCHIVE_MAX_ENTRIES
import dev.qtremors.arcile.core.vault.domain.VaultArchiveSource
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.PasswordRequiredException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel

internal fun openVaultSevenZ(channel: SeekableByteChannel): SevenZFile = try {
    SevenZFile.builder()
        .setSeekableByteChannel(channel)
        .setMaxMemoryLimitKiB(SEVEN_Z_DECODER_LIMIT_KIB)
        .get()
} catch (error: PasswordRequiredException) {
    throw VaultFailure.Unavailable("Encrypted 7z archives need a compatible external app", error)
}

internal fun SevenZFile.vaultArchiveSources(
    selection: VaultZipSelection,
    checkActive: () -> Unit
): List<VaultArchiveSource> {
    val entries = entries.take(VAULT_ARCHIVE_MAX_ENTRIES + 1)
    if (entries.size > VAULT_ARCHIVE_MAX_ENTRIES) throw VaultFailure.Unavailable("7z has too many entries")
    val cursor = SevenZEntryCursor(this, checkActive)
    var decodedBytes = 0L
    return entries.mapIndexedNotNull { index, entry ->
        checkActive()
        if (entry.size < 0L || entry.size > VAULT_ARCHIVE_MAX_BYTES - decodedBytes) {
            throw VaultFailure.Unavailable("7z exceeds the vault extraction size limit")
        }
        decodedBytes += entry.size
        val relative = selection.safeRelativePath(entry.name.orEmpty()) ?: return@mapIndexedNotNull null
        val unixKind = if (entry.hasWindowsAttributes) (entry.windowsAttributes ushr 16) and 0xF000 else 0
        if (entry.isAntiItem || unixKind == 0xA000) {
            throw VaultFailure.Unavailable("7z contains an unsupported entry")
        }
        if (entry.isDirectory) VaultArchiveSource.Folder(relative)
        else {
            val crc = entry.crcValue.takeIf { entry.hasCrc }
            VaultArchiveSource.File(relative, entry.size, crc) { cursor.open(index) }
        }
    }
}

private class SevenZEntryCursor(
    private val archive: SevenZFile,
    private val checkActive: () -> Unit
) {
    private var index = -1

    fun open(entryIndex: Int): InputStream {
        check(entryIndex > index) { "7z entries must be read in archive order" }
        try {
            while (index < entryIndex) {
                checkActive()
                if (archive.nextEntry == null) throw VaultFailure.IntegrityFailed("7z ended before the selected entry")
                index++
            }
        } catch (error: PasswordRequiredException) {
            throw VaultFailure.Unavailable("Encrypted 7z archives need a compatible external app", error)
        }
        return object : InputStream() {
            override fun read(): Int = archive.read()
            override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                archive.read(buffer, offset, length)
        }
    }
}

private const val SEVEN_Z_DECODER_LIMIT_KIB = 64 * 1024
