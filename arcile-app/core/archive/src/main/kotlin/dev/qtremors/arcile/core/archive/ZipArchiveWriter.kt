package dev.qtremors.arcile.core.archive

import dev.qtremors.arcile.core.storage.domain.ArchiveCompressionLevel
import dev.qtremors.arcile.core.storage.domain.ArchiveNameEncoding
import java.io.Closeable
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.zip.Deflater
import net.lingala.zip4j.io.outputstream.ZipOutputStream
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.AesKeyStrength
import net.lingala.zip4j.model.enums.CompressionLevel
import net.lingala.zip4j.model.enums.CompressionMethod
import net.lingala.zip4j.model.enums.EncryptionMethod
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream

/** Browser and vault ZIP creation share encoding, compression, safety, and exact stream checks. */
class ZipArchiveWriter private constructor(
    private val sink: ZipSink,
    policy: ArchiveSafetyPolicy
) : Closeable {
    private val safety = ArchiveSafetyTally(policy)
    private var closed = false
    private var failed = false

    fun add(
        name: String,
        size: Long,
        directory: Boolean,
        modifiedAtMillis: Long,
        checkActive: () -> Unit = {},
        onBytes: (Int) -> Unit = {},
        open: () -> InputStream
    ) {
        check(!closed && !failed)
        try {
            checkActive()
            require(size >= 0L)
            requireNotNull(archiveEntrySegments(name)) { "Invalid archive entry path" }
            safety.accept(name, if (directory) 0L else size, null)
            sink.begin(name.trimEnd('/') + if (directory) "/" else "", size, directory, modifiedAtMillis)
            if (!directory) copyArchiveEntry(size, checkActive, onBytes, open, sink::write)
            sink.end()
        } catch (error: Throwable) {
            failed = true
            throw error
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        sink.close()
    }

    companion object {
        fun open(
            output: OutputStream,
            password: CharArray? = null,
            encoding: ArchiveNameEncoding = ArchiveNameEncoding.UTF_8,
            level: ArchiveCompressionLevel = ArchiveCompressionLevel.DEFAULT,
            policy: ArchiveSafetyPolicy = ArchiveSafetyPolicy()
        ): ZipArchiveWriter {
            try {
                val charset = Charset.forName(encoding.charsetName)
                val sink = if (password == null || password.isEmpty()) {
                    val zip = ZipArchiveOutputStream(output)
                    zip.setEncoding(charset.name())
                    zip.setUseLanguageEncodingFlag(encoding == ArchiveNameEncoding.UTF_8)
                    zip.setLevel(when (level) {
                        ArchiveCompressionLevel.STORE -> Deflater.NO_COMPRESSION
                        ArchiveCompressionLevel.FAST -> 3
                        ArchiveCompressionLevel.DEFAULT -> Deflater.DEFAULT_COMPRESSION
                        ArchiveCompressionLevel.MAXIMUM -> Deflater.BEST_COMPRESSION
                    })
                    PlainZipSink(zip)
                } else ProtectedZipSink(ZipOutputStream(output, password, charset), level)
                return ZipArchiveWriter(sink, policy)
            } catch (error: Throwable) {
                runCatching { output.close() }.exceptionOrNull()?.let(error::addSuppressed)
                throw error
            }
        }
    }
}

private interface ZipSink : Closeable {
    fun begin(name: String, size: Long, directory: Boolean, modifiedAt: Long)
    fun write(bytes: ByteArray, count: Int)
    fun end()
}

private class PlainZipSink(private val zip: ZipArchiveOutputStream) : ZipSink {
    override fun begin(name: String, size: Long, directory: Boolean, modifiedAt: Long) {
        zip.putArchiveEntry(ZipArchiveEntry(name).apply { time = modifiedAt; this.size = if (directory) 0L else size })
    }
    override fun write(bytes: ByteArray, count: Int) = zip.write(bytes, 0, count)
    override fun end() = zip.closeArchiveEntry()
    override fun close() = zip.close()
}

private class ProtectedZipSink(private val zip: ZipOutputStream, private val level: ArchiveCompressionLevel) : ZipSink {
    override fun begin(name: String, size: Long, directory: Boolean, modifiedAt: Long) {
        zip.putNextEntry(ZipParameters().apply {
            fileNameInZip = name
            entrySize = if (directory) 0L else size
            lastModifiedFileTime = modifiedAt
            compressionMethod = CompressionMethod.DEFLATE
            compressionLevel = when (level) {
                ArchiveCompressionLevel.STORE -> CompressionLevel.NO_COMPRESSION
                ArchiveCompressionLevel.FAST -> CompressionLevel.FAST
                ArchiveCompressionLevel.DEFAULT -> CompressionLevel.NORMAL
                ArchiveCompressionLevel.MAXIMUM -> CompressionLevel.MAXIMUM
            }
            isEncryptFiles = !directory
            encryptionMethod = EncryptionMethod.AES
            aesKeyStrength = AesKeyStrength.KEY_STRENGTH_256
        })
    }
    override fun write(bytes: ByteArray, count: Int) = zip.write(bytes, 0, count)
    override fun end() { zip.closeEntry() }
    override fun close() = zip.close()
}
