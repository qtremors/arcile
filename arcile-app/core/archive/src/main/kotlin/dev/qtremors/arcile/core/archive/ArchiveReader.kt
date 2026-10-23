package dev.qtremors.arcile.core.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveEntryModel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import java.io.Closeable
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import java.nio.charset.Charset
import java.util.zip.CRC32
import org.apache.commons.compress.PasswordRequiredException
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile

class ArchiveReadSource(
    val name: String,
    val size: Long,
    val modifiedAt: Long?,
    val openChannel: () -> SeekableByteChannel
)

class ArchivePasswordRequired(cause: Throwable? = null) :
    IOException("A password is required or the password is incorrect", cause)

class ArchiveReadEntry internal constructor(
    val segments: List<String>?,
    val size: Long,
    val compressedSize: Long?,
    val modifiedAt: Long?,
    val isDirectory: Boolean,
    val canRead: Boolean,
    val encrypted: Boolean = false,
    val crc32: Long? = null,
    private val open: (() -> InputStream)? = null
) {
    fun toModel(): ArchiveEntryModel? {
        val path = segments ?: return null
        return ArchiveEntryModel(
            path.last(), path.joinToString("/") + if (isDirectory) "/" else "",
            size, compressedSize, modifiedAt, isDirectory, canRead
        )
    }

    fun openStream(): InputStream {
        if (!canRead || segments == null || isDirectory || open == null) {
            throw IOException("Archive contains unsupported entries")
        }
        return ExactArchiveEntryStream(open(), size, crc32)
    }
}

class ArchiveReadSession internal constructor(
    val entries: List<ArchiveReadEntry>,
    private val release: () -> Unit
) : Closeable {
    override fun close() = release()
}

class ArchiveReader(
    private val policy: ArchiveSafetyPolicy = ArchiveSafetyPolicy(),
    private val allowBackslashes: Boolean = true,
    private val decoderMemoryKiB: Int = 64 * 1024
) {
    fun open(
        source: ArchiveReadSource,
        format: ArchiveFormat,
        password: CharArray? = null,
        encoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
        listingLimit: Int = Int.MAX_VALUE,
        checkActive: () -> Unit = {}
    ): ArchiveReadSession {
        require(format.canBrowse) { "${format.displayName} archives are not supported in this build" }
        require(source.size >= 0L && listingLimit > 0)
        checkActive()
        return when (format) {
            ArchiveFormat.ZIP -> zip(source, password, Charset.forName(encoding.charsetName), listingLimit, checkActive)
            ArchiveFormat.SEVEN_Z -> sevenZ(source, password, listingLimit, checkActive)
            else -> openStreamArchive(source, format, policy, allowBackslashes, decoderMemoryKiB, listingLimit, checkActive)
        }
    }

    private fun zip(
        source: ArchiveReadSource,
        password: CharArray?,
        charset: Charset,
        listingLimit: Int,
        checkActive: () -> Unit
    ): ArchiveReadSession = source.openChannel().retainOnSuccess { channel ->
        ZipFile.builder().setSeekableByteChannel(channel).setCharset(charset).setIgnoreLocalFileHeader(true)
            .get().retainOnSuccess { archive ->
                val safety = ArchiveSafetyTally(policy)
                val entries = archive.entries.asSequence().take(listingLimit).map { entry ->
                    checkActive()
                    val encrypted = entry.generalPurposeBit.usesEncryption()
                    val compressed = entry.compressedSize.takeIf { it >= 0L }
                    val path = safety.entryPath(entry.name, entry.size, compressed, allowBackslashes)
                    ArchiveReadEntry(
                        path, entry.size.coerceAtLeast(0L), compressed, entry.lastModifiedDate?.time,
                        entry.isDirectory, !entry.isUnixSymlink && (encrypted || archive.canReadEntryData(entry)),
                        encrypted, entry.crc.takeIf { !encrypted && it >= 0L }
                    ) {
                        checkActive()
                        if (encrypted) {
                            if (password == null || password.isEmpty()) throw ArchivePasswordRequired()
                            openEncryptedZipEntry(channel, entry, password, charset)
                        } else archive.getInputStream(entry)
                    }
                }.toList()
                ArchiveReadSession(entries, archive::close)
            }
    }

    private fun sevenZ(
        source: ArchiveReadSource,
        password: CharArray?,
        listingLimit: Int,
        checkActive: () -> Unit
    ): ArchiveReadSession = source.openChannel().retainOnSuccess { channel ->
        val archive = passwordError {
            SevenZFile.builder().setSeekableByteChannel(channel).setMaxMemoryLimitKiB(decoderMemoryKiB)
                .apply { if (password != null && password.isNotEmpty()) setPassword(password) }.get()
        }
        archive.retainOnSuccess {
            val safety = ArchiveSafetyTally(policy)
            var cursor = -1
            var decodedBytes = 0L
            val entries = archive.entries.asSequence().take(listingLimit).mapIndexed { index, entry ->
                checkActive()
                require(entry.size in 0L..(policy.maxUncompressedBytes - decodedBytes)) {
                    "Archive is too large to process safely"
                }
                decodedBytes += entry.size
                val name = entry.name.orEmpty()
                val path = safety.entryPath(name, entry.size, null, allowBackslashes)
                val unixKind = if (entry.hasWindowsAttributes) (entry.windowsAttributes ushr 16) and 0xF000 else 0
                ArchiveReadEntry(
                    path, entry.size.coerceAtLeast(0L), null,
                    if (entry.hasLastModifiedDate) entry.lastModifiedDate.time else null,
                    entry.isDirectory, !entry.isAntiItem && unixKind != 0xA000,
                    crc32 = entry.crcValue.takeIf { entry.hasCrc }
                ) {
                    check(index > cursor) { "7z entries must be read in archive order" }
                    passwordError {
                        while (cursor < index) {
                            checkActive()
                            if (archive.nextEntry == null) throw IOException("7z ended before the selected entry")
                            cursor++
                        }
                    }
                    object : InputStream() {
                        override fun read(): Int = passwordError { archive.read() }
                        override fun read(b: ByteArray, off: Int, len: Int): Int =
                            passwordError { archive.read(b, off, len) }
                    }
                }
            }.toList()
            ArchiveReadSession(entries, archive::close)
        }
    }
}

internal inline fun <T : Closeable, R> T.retainOnSuccess(block: (T) -> R): R = try {
    block(this)
} catch (error: Throwable) {
    try {
        close()
    } catch (cleanup: Exception) {
        error.addSuppressed(cleanup)
    }
    throw error
}

private inline fun <T> passwordError(block: () -> T): T = try {
    block()
} catch (error: PasswordRequiredException) {
    throw ArchivePasswordRequired(error)
}

internal class ExactArchiveEntryStream(
    input: InputStream,
    private var remaining: Long,
    private val expectedCrc: Long?
) : FilterInputStream(input) {
    private val crc = expectedCrc?.let { CRC32() }

    override fun read(): Int {
        val value = super.read()
        verify(if (value < 0) -1 else 1)
        if (value >= 0) crc?.update(value)
        return value
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        val request = minOf(len.toLong(), remaining.coerceAtMost(Long.MAX_VALUE - 1) + 1).toInt()
        val count = super.read(b, off, request)
        verify(count)
        if (count > 0) crc?.update(b, off, count)
        return count
    }

    override fun skip(n: Long): Long {
        val buffer = ByteArray(minOf(n.coerceAtLeast(0L), 8_192L).toInt())
        var skipped = 0L
        try {
            while (skipped < n) {
                val count = read(buffer, 0, minOf(n - skipped, buffer.size.toLong()).toInt())
                if (count <= 0) break
                skipped += count
            }
            return skipped
        } finally {
            buffer.fill(0)
        }
    }

    override fun markSupported(): Boolean = false
    override fun mark(readlimit: Int) = Unit
    override fun reset() = throw IOException("Archive entry streams cannot be reset")

    private fun verify(read: Int) {
        if (read < 0) {
            if (remaining != 0L) throw IOException("Archive entry ended before its declared size")
            if (crc != null && crc.value != expectedCrc) throw IOException("Archive entry checksum does not match")
        } else {
            if (read.toLong() > remaining) throw IOException("Archive entry exceeds its declared size")
            remaining -= read
        }
    }
}
