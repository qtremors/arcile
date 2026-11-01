package dev.qtremors.arcile.core.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.nio.channels.SeekableByteChannel
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZMethod
import org.apache.commons.compress.archivers.sevenz.SevenZMethodConfiguration
import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.tukaani.xz.LZMA2Options

/** One 7z encoder for Browser files and protected vault streams. Publication belongs to the caller. */
class SevenZipArchiveWriter private constructor(
    private val archive: SevenZOutputFile,
    policy: ArchiveSafetyPolicy
) : Closeable {
    private val safety = ArchiveSafetyTally(policy)
    private var closed = false
    private var failed = false

    /** Retains the native file dates used by Browser without exposing a File requirement to vaults. */
    fun entryFrom(file: File, name: String): SevenZArchiveEntry = archive.createArchiveEntry(file, name).apply {
        size = if (isDirectory) 0L else file.length()
    }

    fun add(entry: SevenZArchiveEntry, checkActive: () -> Unit = {}, onBytes: (Int) -> Unit = {}, open: () -> InputStream) {
        check(!closed && !failed)
        try {
            checkActive()
            require(!entry.isAntiItem && entry.size >= 0L) { "Only regular files and folders can be archived" }
            requireNotNull(archiveEntrySegments(entry.name)) { "Invalid archive entry path" }
            safety.accept(entry.name, if (entry.isDirectory) 0L else entry.size, null)
            val declaredSize = entry.size
            archive.putArchiveEntry(entry)
            if (!entry.isDirectory) copyArchiveEntry(declaredSize, checkActive, onBytes, open) { bytes, count ->
                archive.write(bytes, 0, count)
            }
            archive.closeArchiveEntry()
        } catch (error: Throwable) {
            failed = true
            throw error
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        archive.close()
    }

    companion object {
        fun open(
            channel: SeekableByteChannel,
            password: CharArray? = null,
            level: ArchiveCompressionLevel = ArchiveCompressionLevel.DEFAULT,
            policy: ArchiveSafetyPolicy = ArchiveSafetyPolicy(),
            closeChannel: Boolean = true
        ): SevenZipArchiveWriter {
            var output: SevenZOutputFile? = null
            try {
                require(channel.size() == 0L) { "Archive output must be empty" }
                output = SevenZOutputFile(if (closeChannel) channel else BorrowedChannel(channel), password?.takeIf { it.isNotEmpty() })
                if (level == ArchiveCompressionLevel.STORE) output.setContentCompression(SevenZMethod.COPY)
                else output.setContentMethods(listOf(SevenZMethodConfiguration(SevenZMethod.LZMA2, LZMA2Options(
                    when (level) {
                        ArchiveCompressionLevel.FAST -> 0
                        ArchiveCompressionLevel.DEFAULT -> 3
                        ArchiveCompressionLevel.MAXIMUM -> 6
                        else -> error("Unexpected compression level")
                    }
                ))))
                return SevenZipArchiveWriter(output, policy)
            } catch (error: Throwable) {
                runCatching {
                    if (output != null) output.close() else if (closeChannel) channel.close()
                }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }
}

/** SevenZOutputFile owns its channel; protected publication needs to read it after finish. */
private class BorrowedChannel(private val delegate: SeekableByteChannel) : SeekableByteChannel by delegate {
    override fun position(newPosition: Long): SeekableByteChannel { delegate.position(newPosition); return this }
    override fun truncate(size: Long): SeekableByteChannel { delegate.truncate(size); return this }
    override fun close() = Unit
}
