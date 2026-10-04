package dev.qtremors.arcile.core.storage.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class FileModelTest {

    @Suppress("DEPRECATION")
    @Test
    fun `content reference preserves its typed identity and compatibility access`() {
        val reference = "content://media/external/audio/media/42"
        val identity = StorageNodeRef.mediaStore(
            id = 42L,
            volumeName = "external",
            contentUri = reference,
            displayPath = "/Music/song.mp3"
        )
        val file = FileModel(name = "song.mp3", reference = reference, nodeRef = identity)

        assertEquals(reference, file.reference)
        assertEquals(reference, file.absolutePath)
        assertEquals(identity, file.nodeRef)
        assertEquals(reference, file.nodeRef.contentUri)
    }

    @Test
    fun `FileModel uses sensible defaults for optional fields`() {
        val file = FileModel(
            name = "example.txt",
            reference = "/storage/emulated/0/Download/example.txt"
        )

        assertEquals("example.txt", file.name)
        assertEquals("/storage/emulated/0/Download/example.txt", file.reference)
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
            reference = "/storage/emulated/0/DCIM/.photo.jpg",
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
