package dev.qtremors.arcile.feature.onlyfiles

import dev.qtremors.arcile.core.vault.domain.VaultNode
import dev.qtremors.arcile.core.vault.domain.VaultPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnlyFilesFormattingTest {
    @Test
    fun `media recognition uses mime type and safe extension fallback`() {
        assertTrue(node("photo.HEIC", null).isViewableImage())
        assertTrue(node("opaque", "image/png").isViewableImage())
        assertTrue(node("movie.mkv", null).isViewableVideo())
        assertFalse(node("notes.txt", "text/plain").isViewableImage())
        assertFalse(node("notes.txt", "text/plain").isViewableVideo())
    }

    @Test
    fun `file sizes stay compact and readable`() {
        assertEquals("42 B", formatBytes(42L))
        assertEquals("2 KB", formatBytes(2048L))
        assertEquals("3 MB", formatBytes(3L * 1024L * 1024L))
        assertEquals("1.5 GB", formatBytes(3L * 1024L * 1024L * 1024L / 2L))
    }

    private fun node(name: String, mimeType: String?) = VaultNode(
        id = name,
        path = VaultPath.of(name),
        sizeBytes = 0L,
        modifiedAtMillis = 0L,
        isDirectory = false,
        mimeType = mimeType
    )
}
