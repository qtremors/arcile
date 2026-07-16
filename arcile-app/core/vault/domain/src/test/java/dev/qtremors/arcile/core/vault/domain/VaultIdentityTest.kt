package dev.qtremors.arcile.core.vault.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.text.Normalizer

class VaultIdentityTest {
    @Test
    fun `logical names normalize to NFC and compare without case`() {
        val decomposed = "Cafe\u0301.txt"
        val canonical = VaultName.of(decomposed)

        assertEquals(Normalizer.normalize(decomposed, Normalizer.Form.NFC), canonical.value)
        assertEquals(VaultName.of("CAFÉ.TXT").comparisonKey, canonical.comparisonKey)
    }

    @Test
    fun `logical names reject traversal separators NUL and oversized UTF-8`() {
        listOf("", "   ", ".", "..", "a/b", "a\\b", "a\u0000b", "界".repeat(86)).forEach { hostile ->
            assertThrows(IllegalArgumentException::class.java) { VaultName.of(hostile) }
        }
        assertEquals(255, VaultName.of("a".repeat(255)).value.length)
    }

    @Test
    fun `opaque identities do not expose names or hierarchy`() {
        val vaultId = VaultId.random()
        val first = NodeId.random()
        val second = NodeId.random()
        val parent = DirectoryId.random()
        val ref = VaultNodeRef(vaultId, first, parent, VaultNodeCapabilities())

        assertNotEquals(first, second)
        assertEquals("${vaultId.value}:${first.value}", ref.backendIdentity)
        assertEquals(2, ref.backendIdentity.split(':').size)
    }

    @Test
    fun `object ids map to opaque sharded paths`() {
        val value = (0 until 32).joinToString("") { it.toString(16).padStart(2, '0') }
        val objectId = VaultObjectId.of(value)

        assertEquals("objects/00/01/$value.obj", objectId.shardedPath())
        assertThrows(IllegalArgumentException::class.java) { VaultObjectId.of("not-an-object") }
    }
}
