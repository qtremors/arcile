package dev.qtremors.arcile.core.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.nio.channels.Channels
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream

val ArchiveFormat.isSingleStreamCompression: Boolean
    get() = this == ArchiveFormat.GZIP || this == ArchiveFormat.BZIP2 || this == ArchiveFormat.XZ

internal fun openStreamArchive(
    source: ArchiveReadSource,
    format: ArchiveFormat,
    policy: ArchiveSafetyPolicy,
    allowBackslashes: Boolean,
    memoryKiB: Int,
    listingLimit: Int,
    checkActive: () -> Unit
): ArchiveReadSession {
    fun openDecoded() = source.openDecoded(format, memoryKiB)
    val safety = ArchiveSafetyTally(policy)
    if (format.isSingleStreamCompression) {
        val name = source.name.dropLast(format.extension.length + 1).ifBlank { "decompressed" }
        val limiter = ArchiveExpansionLimiter(policy, source.size)
        openDecoded().use { input ->
            val buffer = ByteArray(128 * 1024)
            try {
                while (true) {
                    checkActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    limiter.accept(count)
                }
            } finally {
                buffer.fill(0)
            }
        }
        val size = limiter.expandedBytes
        val path = safety.entryPath(name, size, source.size, allowBackslashes)
        val entry = ArchiveReadEntry(path, size, source.size, source.modifiedAt, false, true) {
            openDecoded()
        }
        return ArchiveReadSession(listOf(entry)) {}
    }

    // Headers, extension records and skipped members also consume decoder work.
    val headerAllowance = policy.maxEntries.toLong() * 4_096L + 10_240L
    val limit = policy.maxUncompressedBytes.coerceAtMost(Long.MAX_VALUE - headerAllowance) + headerAllowance
    fun openTar() = TarArchiveInputStream(ExpandedTarStream(openDecoded(), limit, checkActive))
    val cursor = TarCursor(::openTar, checkActive)
    val entries = openTar().use { input ->
        val result = mutableListOf<ArchiveReadEntry>()
        var index = 0
        while (result.size < listingLimit) {
            checkActive()
            val entry = input.nextEntry ?: break
            val position = index++
            if (entry.isDirectory && (entry.name == "." || entry.name == "./")) {
                safety.accept(entry.name, 0L, null)
                continue
            }
            val path = safety.entryPath(entry.name, entry.size, null, allowBackslashes)
            val regular = (entry.isFile || entry.isDirectory) &&
                !entry.isSymbolicLink && !entry.isLink && !entry.isSparse
            result += ArchiveReadEntry(
                path, entry.size.coerceAtLeast(0L), null, entry.modTime?.time,
                entry.isDirectory, regular && input.canReadEntryData(entry)
            ) { cursor.open(position, entry) }
        }
        result
    }
    return ArchiveReadSession(entries, cursor::close)
}

private fun ArchiveReadSource.openDecoded(format: ArchiveFormat, memoryKiB: Int): InputStream =
    BufferedInputStream(Channels.newInputStream(openChannel())).retainOnSuccess { input ->
        when (format) {
            ArchiveFormat.TAR -> input
            ArchiveFormat.TAR_GZIP, ArchiveFormat.TGZ, ArchiveFormat.GZIP ->
                GzipCompressorInputStream.builder().setInputStream(input).setDecompressConcatenated(true).get()
            ArchiveFormat.TAR_BZIP2, ArchiveFormat.TBZ2, ArchiveFormat.BZIP2 ->
                BZip2CompressorInputStream(input, true)
            ArchiveFormat.TAR_XZ, ArchiveFormat.TXZ, ArchiveFormat.XZ ->
                XZCompressorInputStream.builder().setInputStream(input).setDecompressConcatenated(true)
                    .setMemoryLimitKiB(memoryKiB).get()
            else -> throw IOException("Unsupported streaming archive")
        }
    }

private class TarCursor(
    private val openTar: () -> TarArchiveInputStream,
    private val checkActive: () -> Unit
) {
    private var input: TarArchiveInputStream? = null
    private var index = -1

    fun open(position: Int, expected: TarArchiveEntry): InputStream {
        check(position > index) { "TAR entries must be read in archive order" }
        val tar = input ?: openTar().also { input = it }
        var actual: TarArchiveEntry? = null
        while (index < position) {
            checkActive()
            actual = tar.nextEntry ?: throw IOException("TAR ended before the selected entry")
            index++
        }
        val current = requireNotNull(actual)
        if (current.name != expected.name || current.size != expected.size ||
            current.isDirectory || !current.isFile || current.isLink || current.isSymbolicLink || current.isSparse
        ) throw IOException("TAR entry changed before extraction")
        return object : InputStream() {
            override fun read(): Int = tar.read()
            override fun read(b: ByteArray, off: Int, len: Int): Int = tar.read(b, off, len)
        }
    }

    fun close() {
        input?.close()
        input = null
    }
}

private class ExpandedTarStream(
    input: InputStream,
    private var remaining: Long,
    private val checkActive: () -> Unit
) : FilterInputStream(input) {
    override fun read(): Int {
        checkActive()
        return super.read().also { if (it >= 0) consume(1L) }
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        checkActive()
        val request = minOf(len.toLong(), remaining.coerceAtMost(Long.MAX_VALUE - 1) + 1).toInt()
        return super.read(b, off, request).also { if (it > 0) consume(it.toLong()) }
    }

    override fun skip(n: Long): Long {
        checkActive()
        val request = minOf(n, remaining.coerceAtMost(Long.MAX_VALUE - 1) + 1, 128L * 1024)
        return super.skip(request).also(::consume)
    }

    private fun consume(bytes: Long) {
        if (bytes > remaining) throw IOException("TAR is too large to process safely")
        remaining -= bytes
    }
}
