package dev.qtremors.arcile.feature.onlyfiles

import android.graphics.Bitmap
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeKind
import dev.qtremors.arcile.core.vault.domain.VaultNodeMetadata
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `vault image resolution and properties use closed private streams`() = runTest {
        val encoded = ByteArrayOutputStream().use { output ->
            val bitmap = Bitmap.createBitmap(48, 24, Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(0xff336699.toInt())
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            bitmap.recycle()
            output.toByteArray()
        }
        val ref = VaultNodeRef(VaultId.of("vault"), NodeId.of("image"), DirectoryId.Root, VaultNodeCapabilities())
        val node = VaultNodeMetadata(
            ref, "photo.png", VaultNodeKind.FILE, encoded.size.toLong(), 0L, 1L, mimeType = "image/png"
        )
        val readers = mutableListOf<ByteArrayReader>()
        val open: (VaultNodeRef) -> Result<VaultSeekableReader> = {
            assertEquals(ref, it)
            Result.success(ByteArrayReader(encoded).also(readers::add))
        }
        try {
            assertEquals("48 x 24", readVaultImageResolution(ref, open))
            val metadata = requireNotNull(readVaultImageMetadata(node, open))
            assertEquals(48, metadata.width)
            assertEquals(24, metadata.height)
            assertEquals(encoded.size.toLong(), metadata.size)
            assertEquals("image/png", metadata.mimeType)
            assertFalse(metadata.isEditable)
            assertEquals(3, readers.size)
            assertTrue(readers.all { it.closed })
        } finally {
            encoded.fill(0)
        }
    }

    @Test
    fun `bounded vault stream cannot read or skip beyond its metadata window`() {
        val reader = ByteArrayReader(byteArrayOf(3, 4, 5, 6, 7))
        VaultReaderInputStream(reader, byteLimit = 3L).use { stream ->
            assertEquals(3, stream.available())
            assertEquals(3, stream.read())
            assertEquals(1L, stream.skip(1L))
            assertEquals(1, stream.available())
            assertEquals(5, stream.read())
            assertEquals(-1, stream.read())
            assertEquals(0L, stream.skip(Long.MAX_VALUE))
        }
        assertTrue(reader.closed)
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
