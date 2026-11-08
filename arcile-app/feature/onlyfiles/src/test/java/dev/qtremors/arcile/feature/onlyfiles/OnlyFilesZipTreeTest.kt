package dev.qtremors.arcile.feature.onlyfiles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlyFilesZipTreeTest {
    @Test
    fun `implicit directories and empty folders appear beside files`() {
        val tree = VaultZipTree(listOf(
            VaultZipEntry("root.txt", false, 8),
            VaultZipEntry("docs/chapter/part.txt", false, 32),
            VaultZipEntry("empty/", true)
        ))

        assertEquals(listOf("docs", "empty", "root.txt"), tree.list(emptyList()).map { it.name })
        assertEquals(listOf("chapter"), tree.list(listOf("docs")).map { it.name })
        assertTrue(tree.list(listOf("empty")).isEmpty())
        val file = tree.list(listOf("docs", "chapter")).single()
        assertEquals("part.txt", file.name)
        assertEquals(32L, file.size)
    }

    @Test
    fun `unsafe names do not become browsable paths`() {
        val tree = VaultZipTree(listOf(
            VaultZipEntry("../escaped.txt", false),
            VaultZipEntry("safe/../../outside.txt", false),
            VaultZipEntry("/absolute.txt", false),
            VaultZipEntry("C:/drive.txt", false),
            VaultZipEntry("folder\\windows.txt", false),
            VaultZipEntry("valid/file.txt", false)
        ))

        assertEquals(listOf("valid"), tree.list(emptyList()).map { it.name })
        assertEquals(listOf("file.txt"), tree.list(listOf("valid")).map { it.name })
    }
}
