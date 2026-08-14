package dev.qtremors.arcile.core.storage.data

import dev.qtremors.arcile.core.storage.domain.StorageNodeRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageNodePersistenceIdentityTest {
    @Test
    fun `identity round trip supports Unicode newlines and delimiter characters`() {
        val original = StorageNodePersistenceIdentity(
            backendId = StorageNodeRef.ROOT_BACKEND_ID,
            canonicalIdentity = "root:/data/用户/a:b|c\nreport.txt",
            displayPath = "/data/用户/a:b|c\nreport.txt"
        )

        val encoded = original.encode()
        val decoded = StorageNodePersistenceIdentity.decode(encoded)

        assertEquals(original, decoded)
        assertTrue(encoded.startsWith("node-v1:"))
        assertFalse(encoded.contains("用户"))
        assertFalse(encoded.contains('\n'))
        assertEquals(3, encoded.count { it == ':' })
    }

    @Test
    fun `Root and Shizuku identities remain distinct for an identical path`() {
        val root = StorageNodePersistenceIdentity.from(
            StorageNodeRef.root("/data", "/data")
        )
        val shizuku = StorageNodePersistenceIdentity.from(
            StorageNodeRef.shizuku("/data", "/data")
        )

        assertFalse(root.encode() == shizuku.encode())
        assertEquals("root", StorageNodePersistenceIdentity.decode(root.encode())?.backendId)
        assertEquals("shizuku", StorageNodePersistenceIdentity.decode(shizuku.encode())?.backendId)
    }

    @Test
    fun `malformed and unsupported identity versions are rejected`() {
        assertNull(StorageNodePersistenceIdentity.decode(""))
        assertNull(StorageNodePersistenceIdentity.decode("node-v2:a:b:c"))
        assertNull(StorageNodePersistenceIdentity.decode("node-v1:not-base64:still:not"))
        assertNull(StorageNodePersistenceIdentity.decode("node-v1:only:three"))
        assertNull(StorageNodePersistenceIdentity.decode("node-v1:too:many:parts:here"))
    }

    @Test
    fun `factory retains remote canonical identity separately from display path`() {
        val node = StorageNodeRef.root(
            displayPath = "/data/../data/user/0",
            remoteCanonicalIdentity = "/data/user/0"
        )

        val identity = StorageNodePersistenceIdentity.from(node)

        assertEquals("root:/data/user/0", identity.canonicalIdentity)
        assertEquals("/data/../data/user/0", identity.displayPath)
        assertEquals(identity, StorageNodePersistenceIdentity.decode(identity.encode()))
    }
}
