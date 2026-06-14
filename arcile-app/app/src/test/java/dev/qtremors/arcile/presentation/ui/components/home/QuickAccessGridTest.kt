package dev.qtremors.arcile.presentation.ui.components.home

import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import dev.qtremors.arcile.shared.ui.packageNameForQuickAccessItem
import dev.qtremors.arcile.shared.ui.selectDocumentsUiPackageName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QuickAccessGridTest {
    @Test
    fun `whatsapp media quick access resolves installed app package`() {
        val item = QuickAccessItem(
            id = "standard_whatsapp_media",
            label = "WhatsApp",
            path = "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media",
            type = QuickAccessType.STANDARD
        )

        assertEquals("com.whatsapp", packageNameForQuickAccessItem(item))
    }

    @Test
    fun `whatsapp business media quick access resolves business package`() {
        val item = QuickAccessItem(
            id = "standard_whatsapp_media",
            label = "WhatsApp Business",
            path = "/storage/emulated/0/Android/media/com.whatsapp.w4b/WhatsApp Business/Media",
            type = QuickAccessType.STANDARD
        )

        assertEquals("com.whatsapp.w4b", packageNameForQuickAccessItem(item))
    }

    @Test
    fun `files quick access prefers documents ui package`() {
        val packageName = selectDocumentsUiPackageName(
            listOf("com.android.providers.media", "com.google.android.documentsui")
        )

        assertEquals("com.google.android.documentsui", packageName)
    }

    @Test
    fun `unknown quick access item has no package icon resolver`() {
        val item = QuickAccessItem(
            id = "custom_downloads",
            label = "Downloads",
            path = "/storage/emulated/0/Download",
            type = QuickAccessType.CUSTOM
        )

        assertNull(packageNameForQuickAccessItem(item))
    }
}
