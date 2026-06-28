package dev.qtremors.arcile.core.operation.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImportFilePolicyTest {

    @Test
    fun `sanitizes traversal control characters and invalid separators`() {
        val sanitized = sanitizeIncomingFileName("../evil\u0000:name.txt")

        assertEquals("evil_name.txt", sanitized)
        assertFalse(sanitized.contains(".."))
    }

    @Test
    fun `replaces reserved Windows file names without losing extension`() {
        assertEquals("shared-file.txt", sanitizeIncomingFileName("CON.txt"))
        assertEquals("shared-file", sanitizeIncomingFileName("NUL"))
    }

    @Test
    fun `applies validated fallback extension when name has none`() {
        assertEquals("shared-photo.jpg", sanitizeIncomingFileName("shared-photo", "jpg"))
        assertEquals("shared-photo.png", sanitizeIncomingFileName("shared-photo.png", "jpg"))
    }

    @Test
    fun `generates case insensitive keep-both names`() {
        val sanitized = sanitizeIncomingFileName(
            rawName = "photo.jpg",
            existingNames = setOf("PHOTO.JPG", "photo (1).jpg")
        )

        assertEquals("photo (2).jpg", sanitized)
    }

    @Test
    fun `limits long names while retaining extension`() {
        val sanitized = sanitizeIncomingFileName("${"a".repeat(300)}.archive.tar.gz")

        assertEquals(255, sanitized.length)
        assertEquals("gz", sanitized.substringAfterLast('.'))
    }

    @Test
    fun `blank names receive a stable fallback`() {
        assertEquals("shared-file", sanitizeIncomingFileName(null))
        assertEquals("shared-file", sanitizeIncomingFileName(" \u0000 "))
    }
}
