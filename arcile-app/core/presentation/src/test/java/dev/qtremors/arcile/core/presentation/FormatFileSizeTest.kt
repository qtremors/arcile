package dev.qtremors.arcile.core.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatUtilsTest {

    @Test
    fun `formatFileSize returns zero bytes for non-positive sizes`() {
        assertEquals("0 B", formatFileSize(0))
        assertEquals("0 B", formatFileSize(-128))
    }

    @Test
    fun `formatFileSize keeps bytes below one kilobyte`() {
        assertEquals("1.0 B", formatFileSize(1))
        assertEquals("512.0 B", formatFileSize(512))
        assertEquals("999.0 B", formatFileSize(999))
    }

    @Test
    fun `formatFileSize converts values across decimal units`() {
        assertEquals("1.0 KB", formatFileSize(1000))
        assertEquals("1.5 KB", formatFileSize(1500))
        assertEquals("1.0 MB", formatFileSize(1000L * 1000))
        assertEquals("1.0 GB", formatFileSize(1000L * 1000 * 1000))
        assertEquals("1.0 TB", formatFileSize(1000L * 1000 * 1000 * 1000))
    }

    @Test
    fun `formatFileSize promotes rounded boundaries to the next unit`() {
        assertEquals("1.0 MB", formatFileSize((1000L * 1000) - 1))
        assertEquals("1.0 GB", formatFileSize((1000L * 1000 * 1000) - 1))
    }
}
