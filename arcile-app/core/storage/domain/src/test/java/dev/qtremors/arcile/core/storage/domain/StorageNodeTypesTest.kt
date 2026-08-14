package dev.qtremors.arcile.core.storage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun `privileged ref trusts remote canonical identity without local resolution`() {
        val ref = StorageNodeRef.root(
            displayPath = "/data/user/0/example/../protected",
            remoteCanonicalIdentity = "/data/user/0/protected",
            capabilities = StorageNodeCapabilities(canWrite = false)
        )

        assertEquals(StorageNodeRef.ROOT_BACKEND_ID, ref.backendId)
        assertEquals("/data/user/0/example/../protected", ref.displayPath.absolutePath)
        assertEquals("root:/data/user/0/protected", ref.canonicalIdentity.value)
        assertEquals("/data/user/0/protected", ref.backendIdentity)
        assertFalse(ref.capabilities.canWrite)
        assertTrue(ref.isPrivileged)
    }

    @Test
    fun `privileged ref rejects non privileged backends and invalid identity`() {
        expectInvalid {
            StorageNodeRef.privileged(
                backendId = StorageNodeRef.LOCAL_BACKEND_ID,
                displayPath = "/data",
                remoteCanonicalIdentity = "/data"
            )
        }
        expectInvalid {
            StorageNodeRef.shizuku(
                displayPath = "/storage/emulated/0",
                remoteCanonicalIdentity = ""
            )
        }
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
