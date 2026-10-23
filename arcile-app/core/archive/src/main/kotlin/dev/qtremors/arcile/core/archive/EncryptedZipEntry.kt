package dev.qtremors.arcile.core.archive

import java.io.IOException
import java.nio.charset.Charset
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.io.inputstream.ZipInputStream
import net.lingala.zip4j.model.FileHeader
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import java.io.FilterInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

/** Opens only the selected encrypted entry, with sizes supplied by the central directory. */
internal fun openEncryptedZipEntry(
    channel: SeekableByteChannel,
    entry: ZipArchiveEntry,
    password: CharArray,
    charset: Charset
): InputStream {
    val offset = entry.localHeaderOffset
    val compressedSize = entry.compressedSize
    if (entry.diskNumberStart != 0L || offset < 0 || compressedSize < 0 ||
        offset > channel.size() || compressedSize > channel.size() - offset || entry.crc !in 0..0xFFFFFFFFL
    ) {
        throw IOException("ZIP entry has an invalid location")
    }
    channel.position(offset)
    val limit = minOf(channel.size() - offset, compressedSize + MAX_LOCAL_ZIP_HEADER_BYTES)
    val input = ZipInputStream(ZipEntryRange(channel, limit), password, charset)
    try {
        val central = FileHeader().apply {
            crc = entry.crc
            this.compressedSize = compressedSize
            uncompressedSize = entry.size
            isDirectory = false
        }
        val local = input.getNextEntry(central, false)
        if (local == null || !local.isEncrypted || local.isDirectory || local.fileName != entry.name) {
            throw IOException("ZIP entry headers do not agree")
        }
        return PasswordCheckedZipStream(input)
    } catch (error: Exception) {
        input.close()
        throw error.asArchiveZipError()
    }
}

private class ZipEntryRange(
    private val channel: SeekableByteChannel,
    private var remaining: Long
) : InputStream() {
    private val singleByte = ByteArray(1)

    override fun read(): Int = if (read(singleByte, 0, 1) < 0) -1 else singleByte[0].toInt() and 0xff

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return -1
        val count = channel.read(ByteBuffer.wrap(buffer, offset, minOf(remaining, length.toLong()).toInt()))
        if (count > 0) remaining -= count
        return count
    }

    override fun close() {
        singleByte.fill(0)
    }
}

private class PasswordCheckedZipStream(input: InputStream) : FilterInputStream(input) {
    override fun read(): Int = readChecked { super.read() }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        readChecked { super.read(buffer, offset, length) }

    private inline fun readChecked(read: () -> Int): Int = try {
        read()
    } catch (error: ZipException) {
        throw error.asArchiveZipError()
    }
}

private fun Exception.asArchiveZipError(): Exception =
    if (this is ZipException && type == ZipException.Type.WRONG_PASSWORD) {
        ArchivePasswordRequired(this)
    } else this

// Fixed header, maximum 16-bit name and extra lengths, and a ZIP64 data descriptor.
private const val MAX_LOCAL_ZIP_HEADER_BYTES = 30L + 2L * 65_535L + 24L
