package dev.qtremors.arcile.feature.onlyfiles

import android.net.Uri
import androidx.media3.datasource.DataSpec
import dev.qtremors.arcile.core.vault.domain.DirectoryId
import dev.qtremors.arcile.core.vault.domain.NodeId
import dev.qtremors.arcile.core.vault.domain.VaultId
import dev.qtremors.arcile.core.vault.domain.VaultNodeCapabilities
import dev.qtremors.arcile.core.vault.domain.VaultNodeRef
import dev.qtremors.arcile.core.vault.domain.VaultSeekableReader
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VaultMediaDataSourceTest {
    private val ref = VaultNodeRef(
        VaultId.of("vault"), NodeId.of("node"), DirectoryId.Root, VaultNodeCapabilities()
    )

    @Test
    fun `opens an opaque source at arbitrary offset and bounded length`() {
        val bytes = ByteArray(1_024) { (it % 251).toByte() }
        val closed = AtomicBoolean(false)
        val source = VaultMediaDataSource(mapOf("node" to ref)) {
            Result.success(ByteArrayReader(bytes, closed))
        }
        val spec = DataSpec.Builder()
            .setUri(Uri.parse("onlyfiles://playback/node"))
            .setPosition(400)
            .setLength(100)
            .build()

        assertEquals(100L, source.open(spec))
        val actual = ByteArray(100)
        assertEquals(100, source.read(actual, 0, actual.size))
        assertArrayEquals(bytes.copyOfRange(400, 500), actual)
        assertEquals(-1, source.read(actual, 0, actual.size))
        source.close()
        assertTrue(closed.get())
    }

    @Test
    fun `rejects unknown and malformed sources without opening a reader`() {
        var opened = false
        val source = VaultMediaDataSource(mapOf("node" to ref)) {
            opened = true
            Result.success(ByteArrayReader(byteArrayOf(1), AtomicBoolean()))
        }

        assertThrows(Exception::class.java) {
            source.open(DataSpec(Uri.parse("content://playback/node")))
        }
        assertThrows(Exception::class.java) {
            source.open(DataSpec(Uri.parse("onlyfiles://playback/other")))
        }
        assertTrue(!opened)
    }

    @Test
    fun `declared-size truncation is an error rather than clean end of input`() {
        val source = VaultMediaDataSource(mapOf("node" to ref)) {
            Result.success(object : VaultSeekableReader {
                override val sizeBytes = 10L
                override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int) = -1
                override fun close() = Unit
            })
        }
        source.open(DataSpec(Uri.parse("onlyfiles://playback/node")))

        assertThrows(Exception::class.java) { source.read(ByteArray(10), 0, 10) }
        source.close()
    }

    private class ByteArrayReader(
        private val bytes: ByteArray,
        private val closed: AtomicBoolean
    ) : VaultSeekableReader {
        override val sizeBytes: Long get() = bytes.size.toLong()
        override fun readAt(position: Long, target: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(length, bytes.size - position.toInt())
            bytes.copyInto(target, offset, position.toInt(), position.toInt() + count)
            return count
        }
        override fun close() { closed.set(true) }
    }
}
