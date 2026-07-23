package dev.qtremors.arcile.feature.onlyfiles

import android.graphics.Bitmap
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnlyFilesImageDecoderTest {
    @Test
    fun `large vault image is bounds-checked then sampled for the display`() {
        val encoded = ByteArrayOutputStream().use { output ->
            Bitmap.createBitmap(800, 400, Bitmap.Config.ARGB_8888).also { bitmap ->
                bitmap.eraseColor(0xff336699.toInt())
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                bitmap.recycle()
            }
            output.toByteArray()
        }
        val ref = VaultNodeRef(
            VaultId.of("vault"), NodeId.of("image"), DirectoryId.Root, VaultNodeCapabilities()
        )

        val decoded = decodeSampledVaultImage(ref, 100, 100) {
            Result.success(ByteArrayReader(encoded))
        }.getOrThrow()

        assertNotNull(decoded)
        val bitmap = requireNotNull(decoded)
        assertTrue(bitmap.width <= 200)
        assertTrue(bitmap.height <= 200)
        bitmap.recycle()
        encoded.fill(0)
    }

    @Test
    fun `reader authentication failure is propagated`() {
        val ref = VaultNodeRef(
            VaultId.of("vault"), NodeId.of("image"), DirectoryId.Root, VaultNodeCapabilities()
        )
        val result = decodeSampledVaultImage(ref, 100, 100) {
            Result.failure(IllegalStateException("authentication failed"))
        }

        assertTrue(result.isFailure)
    }

    private class ByteArrayReader(private val bytes: ByteArray) : VaultSeekableReader {
        var closed = false
        override val sizeBytes: Long get() = bytes.size.toLong()
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(length, bytes.size - position.toInt())
            bytes.copyInto(target, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override fun close() { closed = true }
    }
}
