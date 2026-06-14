package dev.qtremors.arcile.feature.storagecleaner.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CleanerFilePreviewTest {
    @Test
    fun `packageNameFromPath resolves app-like folder segments`() {
        assertEquals(
            "com.whatsapp",
            packageNameFromPath("/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/photo.jpg")
        )
        assertEquals(
            "com.example.app",
            packageNameFromPath("/storage/emulated/0/com.example.app/cache/debug.log")
        )
    }

    @Test
    fun `packageNameFromPath ignores normal user folders`() {
        assertNull(packageNameFromPath("/storage/emulated/0/Download/photo.jpg"))
    }
}
