package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.domain.VaultFailure
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.security.DigestInputStream
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** A move is allowed to remove an original only after its encrypted copy has been verified and published. */
internal class LocalVaultMove private constructor(private val source: Path, private val original: Stamp) {
    private val importedDigest = MessageDigest.getInstance("SHA-256")
    private var verifiedHash: ByteArray? = null

    fun openOriginal(): InputStream {
        requireUnchanged()
        return DigestInputStream(Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS), importedDigest)
    }

    suspend fun verifyCopy(reader: VaultSeekableReader) {
        if (reader.sizeBytes != original.size) throw VaultFailure.SourceChanged()
        val expected = importedDigest.digest()
        val verification = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(BUFFER_SIZE)
        val coroutine = currentCoroutineContext()
        try {
            var position = 0L
            while (position < reader.sizeBytes) {
                coroutine.ensureActive()
                val count = reader.readAt(position, buffer, 0, minOf(buffer.size.toLong(), reader.sizeBytes - position).toInt())
                if (count <= 0) throw VaultFailure.IntegrityFailed("Encrypted copy ended before its declared size")
                verification.update(buffer, 0, count)
                position += count
            }
            val actual = verification.digest()
            try {
                if (!MessageDigest.isEqual(expected, actual)) {
                    throw VaultFailure.IntegrityFailed("Encrypted copy does not match the original")
                }
                verifiedHash = expected.copyOf()
            } finally {
                actual.fill(0)
            }
        } finally {
            buffer.fill(0)
            expected.fill(0)
        }
    }

    suspend fun removeOriginal() {
        val expected = requireNotNull(verifiedHash)
        requireUnchanged()
        val check = MessageDigest.getInstance("SHA-256")
        val coroutine = currentCoroutineContext()
        val buffer = ByteArray(BUFFER_SIZE)
        try {
            Files.newInputStream(source, LinkOption.NOFOLLOW_LINKS).use { input ->
                while (true) {
                    coroutine.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    check.update(buffer, 0, count)
                }
            }
            val actual = check.digest()
            try {
                if (!MessageDigest.isEqual(expected, actual)) throw VaultFailure.SourceChanged()
            } finally {
                actual.fill(0)
            }
            coroutine.ensureActive()
            requireUnchanged()
            Files.delete(source)
        } finally {
            buffer.fill(0)
        }
    }

    fun clear() {
        verifiedHash?.fill(0)
        verifiedHash = null
    }

    private fun requireUnchanged() {
        if (readStamp(source) != original) throw VaultFailure.SourceChanged()
    }

    private data class Stamp(
        val fileKey: Any?,
        val createdAt: java.nio.file.attribute.FileTime,
        val modifiedAt: java.nio.file.attribute.FileTime,
        val size: Long
    )

    companion object {
        private const val BUFFER_SIZE = 128 * 1024

        fun prepare(path: String, protectedRoots: List<Path>): LocalVaultMove {
            val file = java.io.File(path)
            if (!file.isAbsolute) throw VaultFailure.InvalidPath("Only local files can be moved into a vault")
            val selected = file.toPath()
            val original = readStamp(selected)
            val resolved = selected.toRealPath()
            if (protectedRoots.any(resolved::startsWith)) throw VaultFailure.InvalidPath("Vault storage cannot be moved")
            if (!Files.isWritable(resolved.parent)) throw VaultFailure.Unavailable("The original cannot be removed")
            return LocalVaultMove(resolved, original)
        }

        private fun readStamp(path: Path): Stamp {
            val attributes = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            if (!attributes.isRegularFile || attributes.isSymbolicLink) {
                throw VaultFailure.InvalidPath("Select a regular local file to move into the vault")
            }
            return Stamp(attributes.fileKey(), attributes.creationTime(), attributes.lastModifiedTime(), attributes.size())
        }
    }
}
