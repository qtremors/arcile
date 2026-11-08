package dev.qtremors.arcile.core.vault.data

import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryAccess
import dev.qtremors.arcile.core.vault.crypto.VaultDirectoryManifestCodec
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.VaultFailure
import java.security.MessageDigest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.CancellationException

/** Checks encrypted roots without decrypting and ranking every manifest page again. */
internal class VaultSearchVersions {
    private val roots = linkedMapOf<DirectoryId, String>()
    var cacheable = true
        private set

    suspend fun capture(directory: VaultDirectoryAccess, id: DirectoryId) {
        if (!cacheable || id in roots) return
        if (roots.size >= MAX_DIRECTORIES) {
            invalidate()
            return
        }
        roots[id] = fingerprint(directory, id)
    }

    fun invalidate() {
        roots.clear()
        cacheable = false
    }

    suspend fun matches(directory: VaultDirectoryAccess): Boolean {
        if (!cacheable) return false
        for ((id, expected) in roots) {
            currentCoroutineContext().ensureActive()
            val actual = try {
                fingerprint(directory, id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                return false
            }
            if (actual != expected) return false
        }
        return true
    }

    private suspend fun fingerprint(directory: VaultDirectoryAccess, id: DirectoryId): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(16 * 1024)
        try {
            repeat(2) { slot ->
                currentCoroutineContext().ensureActive()
                val path = VaultDirectoryManifestCodec.rootSlot(id, slot.toLong())
                if (!directory.exists(path)) {
                    digest.update(0.toByte())
                } else {
                    digest.update(1.toByte())
                    directory.openRandom(path, writable = false).use { file ->
                        val length = file.length
                        if (length !in 1L..MAX_ROOT_BYTES) throw VaultFailure.IntegrityFailed("Invalid search directory root")
                        digest.update(java.nio.ByteBuffer.allocate(8).putLong(length).array())
                        var remaining = length
                        while (remaining > 0L) {
                            currentCoroutineContext().ensureActive()
                            val count = file.read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
                            if (count <= 0) throw VaultFailure.IntegrityFailed("Incomplete search directory root")
                            digest.update(buffer, 0, count)
                            remaining -= count
                        }
                        if (file.length != length) throw VaultFailure.ConcurrentMutation()
                    }
                }
            }
            return digest.digest().toHexDigest()
        } finally {
            buffer.fill(0)
        }
    }

    private companion object {
        const val MAX_DIRECTORIES = 2_048
        const val MAX_ROOT_BYTES = 8L * 1024L * 1024L
    }
}
