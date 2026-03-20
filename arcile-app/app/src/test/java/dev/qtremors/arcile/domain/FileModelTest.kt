package dev.qtremors.arcile.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FileModelTest {

    @Test
    fun `FileModel uses sensible defaults for optional fields`() {
        val file = FileModel(
            name = "example.txt",
            absolutePath = "/storage/emulated/0/Download/example.txt"
        )

        assertEquals("example.txt", file.name)
        assertEquals("/storage/emulated/0/Download/example.txt", file.absolutePath)
        assertEquals(0L, file.size)
        assertEquals(0L, file.lastModified)
        assertFalse(file.isDirectory)
        assertEquals("", file.extension)
        assertFalse(file.isHidden)
        assertNull(file.mimeType)
    }

    @Test
    fun `FileModel preserves explicitly provided metadata`() {
        val file = FileModel(
            name = ".photo.jpg",
            absolutePath = "/storage/emulated/0/DCIM/.photo.jpg",
            size = 2048L,
            lastModified = 123456789L,
            isDirectory = false,
            extension = "jpg",
            isHidden = true,
            mimeType = "image/jpeg"
        )

        assertEquals(2048L, file.size)
        assertEquals(123456789L, file.lastModified)
        assertEquals("jpg", file.extension)
        assertEquals("image/jpeg", file.mimeType)
        assertEquals(true, file.isHidden)
    }
}
