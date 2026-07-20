package dev.qtremors.arcile.core.vault.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaDataSource
import android.media.MediaMetadataRetriever
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.qtremors.arcile.core.storage.domain.VaultThumbnailRequest
import dev.qtremors.arcile.core.vault.crypto.VaultCryptography
import dev.qtremors.arcile.core.vault.crypto.VaultKeyDomain
import dev.qtremors.arcile.core.vault.crypto.VaultSealedValue
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import dev.qtremors.arcile.core.vault.domain.VaultThumbnailCache
import dev.qtremors.arcile.core.vault.domain.VaultThumbnailCacheStats
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
internal class DefaultVaultThumbnailCache @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: DefaultVaultRepository
) : VaultThumbnailCache {
    private val root = File(context.noBackupFilesDir, "onlyfiles-thumbnail-cache")
    private val mutex = Mutex()

    override suspend fun loadOrCreate(ref: VaultNodeRef, revision: Long, requestedSizePx: Int): Result<ByteArray> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(revision >= 0L)
                val bucket = VaultThumbnailRequest.sizeBucket(requestedSizePx)
                mutex.withLock { loadOrCreateLocked(ref, revision, bucket) }
            }
        }

    override suspend fun clear(): Result<VaultThumbnailCacheStats> = withContext(Dispatchers.IO) {
        runCatching {
            mutex.withLock {
                if (root.exists() && !root.deleteRecursively() && root.exists()) {
                    throw IllegalStateException("The encrypted thumbnail cache could not be cleared")
                }
                VaultThumbnailCacheStats(0, 0L)
            }
        }
    }

    override suspend fun stats(): Result<VaultThumbnailCacheStats> = withContext(Dispatchers.IO) {
        runCatching { mutex.withLock { calculateStats() } }
    }

    private fun loadOrCreateLocked(ref: VaultNodeRef, revision: Long, bucket: Int): ByteArray {
        val (operationSession, metadata) = repository.createExternalAccessSession(ref)
        val key = VaultCryptography.deriveDomainKey(operationSession.masterSecret, ref.vaultId, VaultKeyDomain.THUMBNAILS)
        val identity = "${ref.vaultId.value}:${ref.nodeId.value}:$revision:$bucket"
        val associatedData = "Arcile/OnlyFiles/v1/thumbnail/$identity".toByteArray()
        val destination = cacheFile(ref, identity)
        try {
            if (metadata.revision != revision) throw VaultFailure.Unavailable("The file changed before its thumbnail was loaded")
            readEncrypted(destination, key, associatedData)?.let { return it }
            val reader = repository.openReader(ref).getOrThrow()
            val encoded = reader.use {
                if (metadata.mimeType?.startsWith("video/") == true) createVideoThumbnail(it, bucket)
                else createImageThumbnail(it, bucket)
            }
            require(encoded.size in 1..MAX_PLAINTEXT_BYTES) { "Thumbnail output is invalid" }
            val sealed = VaultCryptography.seal(key, encoded, associatedData)
            try {
                writeAtomically(destination, sealed)
            } finally {
                sealed.nonce.fill(0)
                sealed.ciphertext.fill(0)
            }
            return encoded
        } finally {
            key.fill(0)
            associatedData.fill(0)
            operationSession.destroy()
        }
    }

    private fun readEncrypted(file: File, key: ByteArray, associatedData: ByteArray): ByteArray? {
        if (!file.isFile || file.length() !in 1..MAX_ENCRYPTED_BYTES.toLong()) return null
        return runCatching {
            DataInputStream(FileInputStream(file)).use { input ->
                require(input.readInt() == CACHE_VERSION)
                val nonceSize = input.readUnsignedByte()
                require(nonceSize == VaultCryptography.NONCE_SIZE_BYTES)
                val nonce = ByteArray(nonceSize).also(input::readFully)
                val ciphertextSize = input.readInt()
                require(ciphertextSize in 1..MAX_ENCRYPTED_BYTES)
                val ciphertext = ByteArray(ciphertextSize).also(input::readFully)
                require(input.read() == -1)
                try {
                    VaultCryptography.open(key, VaultSealedValue(nonce, ciphertext), associatedData).also {
                        require(it.size in 1..MAX_PLAINTEXT_BYTES)
                    }
                } finally {
                    nonce.fill(0)
                    ciphertext.fill(0)
                }
            }
        }.getOrElse {
            file.delete()
            null
        }
    }

    private fun writeAtomically(destination: File, sealed: VaultSealedValue) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, ".${destination.name}.${java.util.UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temporary).use { output ->
                DataOutputStream(output).use { data ->
                    data.writeInt(CACHE_VERSION)
                    data.writeByte(sealed.nonce.size)
                    data.write(sealed.nonce)
                    data.writeInt(sealed.ciphertext.size)
                    data.write(sealed.ciphertext)
                    data.flush()
                    output.fd.sync()
                }
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            temporary.delete()
        }
    }

    private fun createImageThumbnail(reader: VaultSeekableReader, bucket: Int): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(ReaderStream(reader), null, bounds)
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Unsupported image" }
        var sample = 1
        while (bounds.outWidth / sample > bucket * 2 || bounds.outHeight / sample > bucket * 2) sample = sample shl 1
        val bitmap = BitmapFactory.decodeStream(ReaderStream(reader), null, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IllegalArgumentException("Unsupported image")
        return encodeScaled(bitmap, bucket)
    }

    private fun createVideoThumbnail(reader: VaultSeekableReader, bucket: Int): ByteArray {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(ReaderMediaDataSource(reader))
            var width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            var height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation % 180 != 0) width = height.also { height = width }
            require(width > 0 && height > 0) { "Unsupported video dimensions" }
            val scale = minOf(1f, bucket.toFloat() / maxOf(width, height))
            val targetWidth = (width * scale).toInt().coerceAtLeast(1)
            val targetHeight = (height * scale).toInt().coerceAtLeast(1)
            val bitmap = retriever.getScaledFrameAtTime(
                -1L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                targetWidth,
                targetHeight
            )
                ?: throw IllegalArgumentException("Unsupported video")
            encodeScaled(bitmap, bucket)
        } finally {
            retriever.release()
        }
    }

    private fun encodeScaled(source: Bitmap, bucket: Int): ByteArray {
        val scale = minOf(1f, bucket.toFloat() / maxOf(source.width, source.height).coerceAtLeast(1))
        val bitmap = if (scale < 1f) Bitmap.createScaledBitmap(
            source, (source.width * scale).toInt().coerceAtLeast(1), (source.height * scale).toInt().coerceAtLeast(1), true
        ) else source
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 90, output))
                output.toByteArray()
            }
        } finally {
            if (bitmap !== source) bitmap.recycle()
            source.recycle()
        }
    }

    private fun cacheFile(ref: VaultNodeRef, identity: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return File(File(root, ref.vaultId.value), "${digest.take(2)}/$digest.bin")
    }

    private fun calculateStats(): VaultThumbnailCacheStats {
        val files = root.walkTopDown().filter(File::isFile).toList()
        return VaultThumbnailCacheStats(files.size, files.sumOf(File::length))
    }

    private class ReaderStream(private val reader: VaultSeekableReader) : InputStream() {
        private var position = 0L
        private val one = ByteArray(1)
        override fun read(): Int = if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xff
        override fun read(target: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            if (position >= reader.sizeBytes) return -1
            return reader.readAt(position, target, offset, minOf(length.toLong(), reader.sizeBytes - position).toInt()).also {
                if (it <= 0) throw IllegalStateException("Thumbnail source ended early")
                position += it
            }
        }
    }

    private class ReaderMediaDataSource(private val reader: VaultSeekableReader) : MediaDataSource() {
        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int =
            if (position >= reader.sizeBytes) -1 else reader.readAt(position, buffer, offset, minOf(size.toLong(), reader.sizeBytes - position).toInt())
        override fun getSize(): Long = reader.sizeBytes
        override fun close() = Unit
    }

    private companion object {
        const val CACHE_VERSION = 1
        const val MAX_PLAINTEXT_BYTES = 8 * 1024 * 1024
        const val MAX_ENCRYPTED_BYTES = MAX_PLAINTEXT_BYTES + 1024
    }
}
