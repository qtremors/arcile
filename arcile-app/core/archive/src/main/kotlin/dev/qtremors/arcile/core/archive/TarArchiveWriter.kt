package dev.qtremors.arcile.core.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveFormat
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.Deflater
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream
import org.apache.commons.compress.compressors.gzip.GzipParameters
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream

/** Shared streaming encoder. Callers supply local or protected entry streams and publish the output. */
class TarArchiveWriter private constructor(
    private val tar: TarArchiveOutputStream,
    policy: ArchiveSafetyPolicy
) : Closeable {
    private val safety = ArchiveSafetyTally(policy)
    private var closed = false
    private var failed = false

    fun add(entry: TarArchiveEntry, checkActive: () -> Unit = {}, onBytes: (Int) -> Unit = {}, open: () -> InputStream) {
        check(!closed && !failed)
        try {
            checkActive()
            require(entry.isDirectory || entry.isFile) { "Only regular files and folders can be archived" }
            require(!entry.isSymbolicLink && !entry.isLink && entry.size >= 0)
            requireNotNull(archiveEntrySegments(entry.name)) { "Invalid archive entry path" }
            safety.accept(entry.name, if (entry.isDirectory) 0 else entry.size, null)
            tar.putArchiveEntry(entry)
            if (!entry.isDirectory) open().use { input ->
                val buffer = ByteArray(32 * 1024)
                try {
                    var remaining = entry.size
                    while (remaining > 0) {
                        checkActive()
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count <= 0) throw IOException("An archive source ended before its declared size")
                        tar.write(buffer, 0, count)
                        remaining -= count
                        onBytes(count)
                    }
                    checkActive()
                    if (input.read() != -1) throw IOException("An archive source grew while it was being read")
                } finally { buffer.fill(0) }
            }
            tar.closeArchiveEntry()
        } catch (error: Throwable) {
            failed = true
            throw error
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        tar.close()
    }

    companion object {
        val formats = listOf(ArchiveFormat.TAR, ArchiveFormat.TAR_GZIP, ArchiveFormat.TGZ,
            ArchiveFormat.TAR_BZIP2, ArchiveFormat.TBZ2, ArchiveFormat.TAR_XZ, ArchiveFormat.TXZ)

        fun open(output: OutputStream, format: ArchiveFormat, level: ArchiveCompressionLevel = ArchiveCompressionLevel.DEFAULT,
                 policy: ArchiveSafetyPolicy = ArchiveSafetyPolicy()): TarArchiveWriter {
            try {
                require(format in formats) { "Unsupported TAR format" }
                val compressed = when (format) {
                    ArchiveFormat.TAR -> output
                    ArchiveFormat.TAR_GZIP, ArchiveFormat.TGZ -> GzipCompressorOutputStream(output, GzipParameters().apply {
                        compressionLevel = when (level) {
                            ArchiveCompressionLevel.STORE -> Deflater.NO_COMPRESSION
                            ArchiveCompressionLevel.FAST -> Deflater.BEST_SPEED
                            ArchiveCompressionLevel.DEFAULT -> Deflater.DEFAULT_COMPRESSION
                            ArchiveCompressionLevel.MAXIMUM -> Deflater.BEST_COMPRESSION
                        }
                    })
                    ArchiveFormat.TAR_BZIP2, ArchiveFormat.TBZ2 -> BZip2CompressorOutputStream(output,
                        if (level == ArchiveCompressionLevel.FAST || level == ArchiveCompressionLevel.STORE) 1 else 9)
                    ArchiveFormat.TAR_XZ, ArchiveFormat.TXZ -> XZCompressorOutputStream(output, when (level) {
                        ArchiveCompressionLevel.STORE, ArchiveCompressionLevel.FAST -> 0
                        ArchiveCompressionLevel.DEFAULT -> 3
                        ArchiveCompressionLevel.MAXIMUM -> 6
                    })
                    else -> error("Unsupported TAR format")
                }
                val tar = TarArchiveOutputStream(compressed).apply { setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX) }
                return TarArchiveWriter(tar, policy)
            } catch (error: Throwable) {
                runCatching { output.close() }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }
}
