package dev.qtremors.arcile.core.storage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultStorageNodeRefTest {
    @Test
    fun `vault reference exposes opaque backend identity without a logical name`() {
        val ref = StorageNodeRef.vault("vault-id", "node-id")

        assertEquals(StorageNodeRef.ONLYFILES_BACKEND_ID, ref.backendId)
        assertEquals("vault-id:node-id", ref.backendIdentity)
        assertEquals("/.onlyfiles/vault-id/node-id", ref.displayPath.absolutePath)
        assertFalse(ref.displayPath.absolutePath.contains("private photo"))
        assertTrue(ref.capabilities.canDelete)
        assertFalse(ref.capabilities.canTrash)
        assertFalse(ref.capabilities.canArchive)
    }

    @Test
    fun `vault reference rejects path-shaped identifiers`() {
        assertThrows(IllegalArgumentException::class.java) { StorageNodeRef.vault("vault/id", "node") }
        assertThrows(IllegalArgumentException::class.java) { StorageNodeRef.vault("vault", "node\\id") }
    }
}
