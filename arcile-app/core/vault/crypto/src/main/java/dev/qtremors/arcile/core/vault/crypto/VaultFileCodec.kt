package dev.qtremors.arcile.core.vault.crypto

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultObjectId
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.min

data class VaultFileWriteResult(
    val fileId: String,
    val sizeBytes: Long,
    val objectId: VaultObjectId = VaultObjectId.of(fileId),
    val revision: Long = 1L,
    val chunkCount: Long = 0L
)

/**
 * Authenticated, random-access encrypted object format. The content key is deliberately absent
 * from the object and must be supplied from its authenticated parent-directory manifest.
 */
class VaultFileCodec(val chunkSizeBytes: Int = DEFAULT_CHUNK_SIZE_BYTES) {
    init {
        requireValidChunkSize(chunkSizeBytes)
    }

    fun write(
        destination: File,
        vaultId: VaultId,
        contentKey: ByteArray,
        input: InputStream,
        onBytesWritten: ((Long) -> Unit)? = null
    ): VaultFileWriteResult = write(
        directory = FileVaultDirectory(requireNotNull(destination.parentFile)),
        relativePath = destination.name,
        vaultId = vaultId,
        contentKey = contentKey,
        input = input,
        onBytesWritten = onBytesWritten
    )

    fun write(
        directory: VaultDirectoryAccess,
        relativePath: String,
        vaultId: VaultId,
        contentKey: ByteArray,
        input: InputStream,
        onBytesWritten: ((Long) -> Unit)? = null
    ): VaultFileWriteResult = writeObject(
        directory = directory,
        relativePath = relativePath,
        vaultId = vaultId,
        objectId = VaultObjectId.fromRandomBytes(VaultCryptography.randomBytes(32)),
        revision = 1L,
        contentKey = contentKey,
        input = input,
        onBytesWritten = onBytesWritten
    )

    fun writeObject(
        directory: VaultDirectoryAccess,
        relativePath: String,
        vaultId: VaultId,
        objectId: VaultObjectId,
        revision: Long,
        contentKey: ByteArray,
        input: InputStream,
        onBytesWritten: ((Long) -> Unit)? = null
    ): VaultFileWriteResult {
        require(revision >= 1L)
        require(contentKey.size == VaultCryptography.KEY_SIZE_BYTES)
        require(!directory.exists(relativePath)) { "Committed encrypted content is never overwritten in place" }
        var totalBytes = 0L
        var chunkCount = 0L
        try {
            directory.openRandom(relativePath, writable = true).use { output ->
                output.setLength(0L)
                writeHeader(output, vaultId, objectId, revision, contentKey, totalBytes)
                val buffer = ByteArray(chunkSizeBytes)
                try {
                    BufferedInputStream(input, chunkSizeBytes).use { source ->
                        while (true) {
                            val count = source.readFullyOrToEnd(buffer)
                            if (count == 0) break
                            val plaintext = if (count == buffer.size) buffer else buffer.copyOf(count)
                            try {
                                val sealed = VaultCryptography.seal(
                                    contentKey,
                                    plaintext,
                                    chunkAssociatedData(vaultId, objectId, revision, chunkCount, count)
                                )
                                output.writeInt(count)
                                output.write(sealed.nonce)
                                output.write(sealed.ciphertext)
                            } finally {
                                if (plaintext !== buffer) plaintext.fill(0)
                            }
                            totalBytes = Math.addExact(totalBytes, count.toLong())
                            chunkCount++
                            onBytesWritten?.invoke(totalBytes)
                        }
                    }
                } finally {
                    buffer.fill(0)
                }
                writeHeader(output, vaultId, objectId, revision, contentKey, totalBytes)
                output.sync()
            }

            openObject(directory, relativePath, vaultId, objectId, revision, contentKey).use { reader ->
                require(reader.sizeBytes == totalBytes) { "Encrypted object verification failed" }
            }
            return VaultFileWriteResult(objectId.value, totalBytes, objectId, revision, chunkCount)
        } catch (error: Throwable) {
            directory.delete(relativePath)
            throw error
        }
    }

    fun open(file: File, vaultId: VaultId, contentKey: ByteArray): VaultSeekableReader =
        open(FileVaultDirectory(requireNotNull(file.parentFile)), file.name, vaultId, contentKey)

    fun open(
        directory: VaultDirectoryAccess,
        relativePath: String,
        vaultId: VaultId,
        contentKey: ByteArray
    ): VaultSeekableReader = EncryptedObjectReader(directory, relativePath, vaultId, null, null, contentKey)

    fun openObject(
        directory: VaultDirectoryAccess,
        relativePath: String,
        vaultId: VaultId,
        expectedObjectId: VaultObjectId,
        expectedRevision: Long,
        contentKey: ByteArray
    ): VaultSeekableReader = EncryptedObjectReader(
        directory,
        relativePath,
        vaultId,
        expectedObjectId,
        expectedRevision,
        contentKey
    )

    private fun writeHeader(
        output: VaultRandomAccess,
        vaultId: VaultId,
        objectId: VaultObjectId,
        revision: Long,
        contentKey: ByteArray,
        sizeBytes: Long
    ) {
        val identity = headerIdentity(vaultId, objectId, revision, chunkSizeBytes, sizeBytes)
        val authenticator = VaultCryptography.seal(contentKey, ByteArray(0), identity)
        require(authenticator.ciphertext.size == VaultCryptography.GCM_TAG_SIZE_BYTES)
        output.position = 0L
        output.write(MAGIC)
        output.writeInt(FORMAT_VERSION)
        output.writeInt(chunkSizeBytes)
        output.writeLong(sizeBytes)
        output.writeLong(revision)
        output.write(objectId.value.hexToBytes())
        output.write(authenticator.nonce)
        output.write(authenticator.ciphertext)
        require(output.position == HEADER_SIZE_BYTES.toLong())
    }

    private inner class EncryptedObjectReader(
        directory: VaultDirectoryAccess,
        relativePath: String,
        private val vaultId: VaultId,
        expectedObjectId: VaultObjectId?,
        expectedRevision: Long?,
        contentKey: ByteArray
    ) : VaultSeekableReader {
        private val source = directory.openRandom(relativePath, writable = false)
        private val key = contentKey.copyOf()
        private val objectId: VaultObjectId
        private val revision: Long
        private val storedChunkSize: Int
        override val sizeBytes: Long
        private var cachedChunkIndex = -1L
        private var cachedChunk = ByteArray(0)
        private var closed = false

        init {
            require(key.size == VaultCryptography.KEY_SIZE_BYTES)
            try {
                val magic = ByteArray(MAGIC.size).also(source::readFully)
                require(magic.contentEquals(MAGIC)) { "Invalid encrypted object marker" }
                val format = source.readInt()
                if (format != FORMAT_VERSION) throw VaultFailure.UnsupportedFormat(format)
                storedChunkSize = source.readInt().also(::requireValidChunkSize)
                sizeBytes = source.readLong()
                require(sizeBytes >= 0L)
                revision = source.readLong()
                require(revision >= 1L)
                objectId = VaultObjectId.of(ByteArray(32).also(source::readFully).toHex())
                val nonce = ByteArray(VaultCryptography.NONCE_SIZE_BYTES).also(source::readFully)
                val tag = ByteArray(VaultCryptography.GCM_TAG_SIZE_BYTES).also(source::readFully)
                require(source.position == HEADER_SIZE_BYTES.toLong())
                require(expectedObjectId == null || expectedObjectId == objectId) { "Encrypted object identity changed" }
                require(expectedRevision == null || expectedRevision == revision) { "Encrypted object revision changed" }
                VaultCryptography.open(
                    key,
                    VaultSealedValue(nonce, tag),
                    headerIdentity(vaultId, objectId, revision, storedChunkSize, sizeBytes)
                ).also { require(it.isEmpty()) }
                validatePhysicalLength(source.length, sizeBytes, storedChunkSize)
            } catch (error: Throwable) {
                key.fill(0)
                source.close()
                if (error is VaultFailure) throw error
                throw VaultFailure.IntegrityFailed("Encrypted object header is damaged", error)
            }
        }

        @Synchronized
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            check(!closed) { "Reader is closed" }
            require(position >= 0L)
            require(offset >= 0 && length >= 0 && offset <= target.size - length)
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
                if (copied <= 0) throw VaultFailure.IntegrityFailed("Invalid encrypted chunk boundary")
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
            val expectedSize = plaintextChunkSize(index, sizeBytes, storedChunkSize)
            if (expectedSize <= 0) throw VaultFailure.IntegrityFailed("Invalid encrypted chunk index")
            val recordOffset = Math.addExact(
                HEADER_SIZE_BYTES.toLong(),
                Math.multiplyExact(index, fullRecordSize(storedChunkSize))
            )
            try {
                source.position = recordOffset
                val storedSize = source.readInt()
                require(storedSize == expectedSize)
                val nonce = ByteArray(VaultCryptography.NONCE_SIZE_BYTES).also(source::readFully)
                val ciphertext = ByteArray(storedSize + VaultCryptography.GCM_TAG_SIZE_BYTES).also(source::readFully)
                val plaintext = VaultCryptography.open(
                    key,
                    VaultSealedValue(nonce, ciphertext),
                    chunkAssociatedData(vaultId, objectId, revision, index, storedSize)
                )
                require(plaintext.size == storedSize)
                cachedChunkIndex = index
                cachedChunk = plaintext
                return plaintext
            } catch (error: Throwable) {
                if (error is VaultFailure.IntegrityFailed) throw error
                throw VaultFailure.IntegrityFailed("Encrypted object chunk $index is damaged", error)
            }
        }

        @Synchronized
        override fun close() {
            if (closed) return
            closed = true
            cachedChunk.fill(0)
            key.fill(0)
            source.close()
        }
    }

    companion object {
        const val DEFAULT_CHUNK_SIZE_BYTES = 256 * 1024
        const val MIN_CHUNK_SIZE_BYTES = 32 * 1024
        const val MAX_CHUNK_SIZE_BYTES = 4 * 1024 * 1024
        const val FORMAT_VERSION = 1
        private val MAGIC = "AOFIL001".toByteArray(StandardCharsets.US_ASCII)
        private const val HEADER_SIZE_BYTES = 8 + 4 + 4 + 8 + 8 + 32 +
            VaultCryptography.NONCE_SIZE_BYTES + VaultCryptography.GCM_TAG_SIZE_BYTES

        private fun requireValidChunkSize(value: Int) {
            require(value in MIN_CHUNK_SIZE_BYTES..MAX_CHUNK_SIZE_BYTES)
            require(value and (value - 1) == 0) { "Vault chunk size must be a power of two" }
        }

        private fun headerIdentity(
            vaultId: VaultId,
            objectId: VaultObjectId,
            revision: Long,
            chunkSize: Int,
            sizeBytes: Long
        ): ByteArray = structuredAssociatedData(
            purpose = "file-header",
            vaultId = vaultId,
            objectId = objectId,
            revision = revision,
            index = -1L,
            plaintextLength = sizeBytes,
            chunkSize = chunkSize
        )

        private fun chunkAssociatedData(
            vaultId: VaultId,
            objectId: VaultObjectId,
            revision: Long,
            chunkIndex: Long,
            plaintextLength: Int
        ): ByteArray = structuredAssociatedData(
            purpose = "file-chunk",
            vaultId = vaultId,
            objectId = objectId,
            revision = revision,
            index = chunkIndex,
            plaintextLength = plaintextLength.toLong(),
            chunkSize = 0
        )

        private fun structuredAssociatedData(
            purpose: String,
            vaultId: VaultId,
            objectId: VaultObjectId,
            revision: Long,
            index: Long,
            plaintextLength: Long,
            chunkSize: Int
        ): ByteArray {
            val prefix = "Arcile/OnlyFiles/v1/$purpose".toByteArray(StandardCharsets.US_ASCII)
            val vault = vaultId.value.toByteArray(StandardCharsets.UTF_8)
            val objectBytes = objectId.value.hexToBytes()
            return ByteBuffer.allocate(prefix.size + 4 + vault.size + objectBytes.size + 8 + 8 + 8 + 4).apply {
                put(prefix)
                putInt(vault.size); put(vault)
                put(objectBytes)
                putLong(revision)
                putLong(index)
                putLong(plaintextLength)
                putInt(chunkSize)
            }.array()
        }

        private fun fullRecordSize(chunkSize: Int): Long =
            Int.SIZE_BYTES.toLong() + VaultCryptography.NONCE_SIZE_BYTES + chunkSize + VaultCryptography.GCM_TAG_SIZE_BYTES

        private fun plaintextChunkSize(index: Long, totalSize: Long, chunkSize: Int): Int {
            if (index < 0L || index > Long.MAX_VALUE / chunkSize) return 0
            val start = index * chunkSize
            if (start >= totalSize) return 0
            return min(chunkSize.toLong(), totalSize - start).toInt()
        }

        private fun validatePhysicalLength(physicalSize: Long, plainSize: Long, chunkSize: Int) {
            require(physicalSize == encryptedSizeForPlaintext(plainSize, chunkSize)) {
                "Encrypted object is truncated or has trailing data"
            }
        }

        fun encryptedSizeForPlaintext(
            plainSize: Long,
            chunkSize: Int = DEFAULT_CHUNK_SIZE_BYTES
        ): Long {
            require(plainSize >= 0L)
            requireValidChunkSize(chunkSize)
            val fullChunks = plainSize / chunkSize
            val tail = (plainSize % chunkSize).toInt()
            return Math.addExact(
                HEADER_SIZE_BYTES.toLong(),
                Math.addExact(
                    Math.multiplyExact(fullChunks, fullRecordSize(chunkSize)),
                    if (tail == 0) 0L else Int.SIZE_BYTES + VaultCryptography.NONCE_SIZE_BYTES +
                        tail.toLong() + VaultCryptography.GCM_TAG_SIZE_BYTES
                )
            )
        }
    }
}

private fun InputStream.readFullyOrToEnd(buffer: ByteArray): Int {
    var offset = 0
    while (offset < buffer.size) {
        val count = read(buffer, offset, buffer.size - offset)
        if (count < 0) break
        if (count == 0) {
            val single = read()
            if (single < 0) break
            buffer[offset++] = single.toByte()
        } else {
            offset += count
        }
    }
    return offset
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0 && all { it in '0'..'9' || it in 'a'..'f' })
    return ByteArray(length / 2) { index -> substring(index * 2, index * 2 + 2).toInt(16).toByte() }
}
