package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.math.min

data class VaultFileWriteResult(
    val fileId: String,
    val sizeBytes: Long
)

class VaultFileCodec(
    val chunkSizeBytes: Int = DEFAULT_CHUNK_SIZE_BYTES
) {
    init {
        require(chunkSizeBytes in MIN_CHUNK_SIZE_BYTES..MAX_CHUNK_SIZE_BYTES)
        require(chunkSizeBytes and (chunkSizeBytes - 1) == 0) { "Vault chunk size must be a power of two" }
    }

    fun write(
        destination: File,
        vaultId: VaultId,
        masterKey: ByteArray,
        input: InputStream,
        onBytesWritten: ((Long) -> Unit)? = null
    ): VaultFileWriteResult {
        destination.parentFile?.mkdirs()
        val fileId = UUID.randomUUID().toString()
        val fileKey = VaultCryptography.randomBytes(VaultCryptography.KEY_SIZE_BYTES)
        var totalBytes = 0L
        try {
            RandomAccessFile(destination, "rw").use { output ->
                output.setLength(0L)
                writeHeader(output, vaultId, fileId, masterKey, fileKey, totalBytes)
                val buffer = ByteArray(chunkSizeBytes)
                BufferedInputStream(input, chunkSizeBytes).use { source ->
                    var chunkIndex = 0L
                    while (true) {
                        val read = source.readFullyOrToEnd(buffer)
                        if (read <= 0) break
                        val plaintext = if (read == buffer.size) buffer else buffer.copyOf(read)
                        val sealed = VaultCryptography.seal(
                            fileKey,
                            plaintext,
                            chunkAssociatedData(vaultId, fileId, chunkIndex, chunkSizeBytes)
                        )
                        output.writeInt(read)
                        output.write(sealed.nonce)
                        output.write(sealed.ciphertext)
                        if (plaintext !== buffer) plaintext.fill(0)
                        totalBytes += read
                        chunkIndex++
                        onBytesWritten?.invoke(totalBytes)
                    }
                    buffer.fill(0)
                }
                writeHeader(output, vaultId, fileId, masterKey, fileKey, totalBytes)
                output.fd.sync()
            }

            open(destination, vaultId, masterKey).use { reader ->
                require(reader.sizeBytes == totalBytes) { "Encrypted file size verification failed" }
            }
            return VaultFileWriteResult(fileId, totalBytes)
        } catch (error: Throwable) {
            destination.delete()
            throw error
        } finally {
            fileKey.fill(0)
        }
    }

    fun open(file: File, vaultId: VaultId, masterKey: ByteArray): VaultSeekableReader =
        VaultEncryptedFileReader(file, vaultId, masterKey)

    private fun writeHeader(
        file: RandomAccessFile,
        vaultId: VaultId,
        fileId: String,
        masterKey: ByteArray,
        fileKey: ByteArray,
        sizeBytes: Long
    ) {
        val sealedKey = VaultCryptography.seal(
            masterKey,
            fileKey,
            headerAssociatedData(vaultId, fileId, chunkSizeBytes, sizeBytes)
        )
        require(sealedKey.ciphertext.size == WRAPPED_KEY_SIZE_BYTES)
        file.seek(0L)
        file.write(MAGIC)
        file.writeInt(FORMAT_VERSION)
        file.writeInt(chunkSizeBytes)
        file.writeLong(sizeBytes)
        file.writeLong(UUID.fromString(fileId).mostSignificantBits)
        file.writeLong(UUID.fromString(fileId).leastSignificantBits)
        file.write(sealedKey.nonce)
        file.writeInt(sealedKey.ciphertext.size)
        file.write(sealedKey.ciphertext)
        require(file.filePointer == HEADER_SIZE_BYTES.toLong())
    }

    private inner class VaultEncryptedFileReader(
        file: File,
        private val vaultId: VaultId,
        masterKey: ByteArray
    ) : VaultSeekableReader {
        private val source = RandomAccessFile(file, "r")
        private val fileId: String
        private val fileKey: ByteArray
        private val storedChunkSize: Int
        override val sizeBytes: Long

        private var cachedChunkIndex = -1L
        private var cachedChunk = ByteArray(0)

        init {
            try {
                val magic = ByteArray(MAGIC.size).also(source::readFully)
                require(magic.contentEquals(MAGIC)) { "Invalid encrypted file marker" }
                require(source.readInt() == FORMAT_VERSION) { "Unsupported encrypted file format" }
                storedChunkSize = source.readInt()
                require(storedChunkSize in MIN_CHUNK_SIZE_BYTES..MAX_CHUNK_SIZE_BYTES)
                require(storedChunkSize and (storedChunkSize - 1) == 0)
                sizeBytes = source.readLong()
                require(sizeBytes >= 0L)
                val uuid = UUID(source.readLong(), source.readLong())
                fileId = uuid.toString()
                val keyNonce = ByteArray(VaultCryptography.NONCE_SIZE_BYTES).also(source::readFully)
                val wrappedSize = source.readInt()
                require(wrappedSize == WRAPPED_KEY_SIZE_BYTES)
                val wrappedKey = ByteArray(wrappedSize).also(source::readFully)
                require(source.filePointer == HEADER_SIZE_BYTES.toLong())
                fileKey = VaultCryptography.open(
                    masterKey,
                    VaultSealedValue(keyNonce, wrappedKey),
                    headerAssociatedData(vaultId, fileId, storedChunkSize, sizeBytes)
                )
                validatePhysicalLength(source.length(), sizeBytes, storedChunkSize)
            } catch (error: Throwable) {
                source.close()
                throw VaultFailure.IntegrityFailed("Encrypted file header is damaged", error)
            }
        }

        @Synchronized
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            require(position >= 0L)
            require(offset >= 0 && length >= 0 && offset + length <= target.size)
            if (length == 0) return 0
            if (position >= sizeBytes) return -1

            var remaining = min(length.toLong(), sizeBytes - position).toInt()
            var outputOffset = offset
            var cursor = position
            while (remaining > 0) {
                val chunkIndex = cursor / storedChunkSize
                val chunkOffset = (cursor % storedChunkSize).toInt()
                val chunk = readChunk(chunkIndex)
                val copied = min(remaining, chunk.size - chunkOffset)
                require(copied > 0) { "Invalid encrypted chunk boundary" }
                chunk.copyInto(target, outputOffset, chunkOffset, chunkOffset + copied)
                outputOffset += copied
                cursor += copied
                remaining -= copied
            }
            return outputOffset - offset
        }

        private fun readChunk(index: Long): ByteArray {
            if (cachedChunkIndex == index) return cachedChunk
            cachedChunk.fill(0)
            val expectedPlainSize = plainChunkSize(index, sizeBytes, storedChunkSize)
            require(expectedPlainSize > 0)
            val recordOffset = HEADER_SIZE_BYTES + index * fullRecordSize(storedChunkSize)
            try {
                source.seek(recordOffset)
                val storedPlainSize = source.readInt()
                require(storedPlainSize == expectedPlainSize)
                val nonce = ByteArray(VaultCryptography.NONCE_SIZE_BYTES).also(source::readFully)
                val ciphertext = ByteArray(storedPlainSize + TAG_SIZE_BYTES).also(source::readFully)
                val plaintext = VaultCryptography.open(
                    fileKey,
                    VaultSealedValue(nonce, ciphertext),
                    chunkAssociatedData(vaultId, fileId, index, storedChunkSize)
                )
                require(plaintext.size == storedPlainSize)
                cachedChunkIndex = index
                cachedChunk = plaintext
                return plaintext
            } catch (error: Throwable) {
                throw VaultFailure.IntegrityFailed("Encrypted file chunk $index is damaged", error)
            }
        }

        @Synchronized
        override fun close() {
            cachedChunk.fill(0)
            fileKey.fill(0)
            source.close()
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE_BYTES = 256 * 1024
        private const val MIN_CHUNK_SIZE_BYTES = 32 * 1024
        private const val MAX_CHUNK_SIZE_BYTES = 4 * 1024 * 1024
        private const val FORMAT_VERSION = 1
        private const val TAG_SIZE_BYTES = 16
        private const val WRAPPED_KEY_SIZE_BYTES = VaultCryptography.KEY_SIZE_BYTES + TAG_SIZE_BYTES
        private val MAGIC = byteArrayOf('O'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(), '1'.code.toByte())
        private const val HEADER_SIZE_BYTES = 4 + 4 + 4 + 8 + 16 + VaultCryptography.NONCE_SIZE_BYTES + 4 + WRAPPED_KEY_SIZE_BYTES

        private fun headerAssociatedData(
            vaultId: VaultId,
            fileId: String,
            chunkSize: Int,
            sizeBytes: Long
        ): ByteArray =
            "ONLYFILES|FILE|$FORMAT_VERSION|${vaultId.value}|$fileId|$chunkSize|$sizeBytes"
                .toByteArray(StandardCharsets.UTF_8)

        private fun chunkAssociatedData(
            vaultId: VaultId,
            fileId: String,
            chunkIndex: Long,
            chunkSize: Int
        ): ByteArray {
            val prefix = "ONLYFILES|CHUNK|$FORMAT_VERSION|${vaultId.value}|$fileId|$chunkSize|"
                .toByteArray(StandardCharsets.UTF_8)
            return ByteBuffer.allocate(prefix.size + Long.SIZE_BYTES)
                .put(prefix)
                .putLong(chunkIndex)
                .array()
        }

        private fun fullRecordSize(chunkSize: Int): Long =
            Int.SIZE_BYTES.toLong() + VaultCryptography.NONCE_SIZE_BYTES + chunkSize + TAG_SIZE_BYTES

        private fun plainChunkSize(index: Long, totalSize: Long, chunkSize: Int): Int {
            val start = index * chunkSize
            if (start >= totalSize) return 0
            return min(chunkSize.toLong(), totalSize - start).toInt()
        }

        private fun validatePhysicalLength(physicalSize: Long, plainSize: Long, chunkSize: Int) {
            val fullChunks = plainSize / chunkSize
            val tail = (plainSize % chunkSize).toInt()
            val expected = HEADER_SIZE_BYTES.toLong() +
                fullChunks * fullRecordSize(chunkSize) +
                if (tail > 0) Int.SIZE_BYTES + VaultCryptography.NONCE_SIZE_BYTES + tail + TAG_SIZE_BYTES else 0
            require(physicalSize == expected) { "Encrypted file is truncated or has trailing data" }
        }
    }
}

private fun InputStream.readFullyOrToEnd(buffer: ByteArray): Int {
    var offset = 0
    while (offset < buffer.size) {
        val read = read(buffer, offset, buffer.size - offset)
        if (read < 0) break
        if (read == 0) continue
        offset += read
    }
    return offset
}
