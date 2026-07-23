package dev.qtremors.arcile.core.vault.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultPathTest {
    @Test
    fun `paths normalize separators and preserve hierarchy`() {
        val path = VaultPath.of("/Pictures\\Trips/photo.jpg/")

        assertEquals("Pictures/Trips/photo.jpg", path.value)
        assertEquals("photo.jpg", path.name)
        assertEquals("Pictures/Trips", path.parent?.value)
        assertTrue(path.isDescendantOf(VaultPath.of("Pictures")))
        assertFalse(path.isDescendantOf(VaultPath.of("Documents")))
    }

    @Test
    fun `root has no parent and resolves safe names`() {
        assertTrue(VaultPath.Root.isRoot)
        assertNull(VaultPath.Root.parent)
        assertEquals("Folder", VaultPath.Root.resolve(" Folder ").value)
    }

    @Test
    fun `relative traversal and embedded separators are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { VaultPath.of("safe/../escape") }
        assertThrows(IllegalArgumentException::class.java) { VaultPath.Root.resolve("a/b") }
        assertThrows(IllegalArgumentException::class.java) { VaultPath.Root.resolve("\u0000") }
    }
}
