package dev.qtremors.arcile.core.storage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class StorageNodeTypesTest {
    @Test
    fun `storage node path requires absolute canonicalizable path`() {
        expectInvalid {
            StorageNodePath.of("relative/path")
        }

        val path = StorageNodePath.of(File("/tmp/example").absolutePath)
        assertTrue(path.absolutePath.endsWith("${File.separator}tmp${File.separator}example"))
    }

    @Test
    fun `storage node ref keeps display path and canonical identity distinct`() {
        val ref = StorageNodeRef.local(File("/tmp/../tmp/example").absolutePath, volumeId = "primary")

        assertEquals(StorageVolumeId.of("primary"), ref.volumeId)
        assertTrue(ref.displayPath.absolutePath.endsWith("${File.separator}tmp${File.separator}..${File.separator}tmp${File.separator}example"))
        assertTrue(ref.canonicalIdentity.value.endsWith("${File.separator}tmp${File.separator}example"))
    }

    @Test
    fun `semantic values reject invalid primitive states`() {
        expectInvalid { ByteCount.of(-1L) }
        expectInvalid { EpochMillis.of(-1L) }
        expectInvalid { StorageVolumeId.of("") }
        expectInvalid { TrashItemId.of("") }
        expectInvalid { CategoryId.of(" ") }
    }

    private fun expectInvalid(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
