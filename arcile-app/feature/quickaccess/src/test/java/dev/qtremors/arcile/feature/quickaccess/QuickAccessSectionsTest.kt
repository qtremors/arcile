package dev.qtremors.arcile.feature.quickaccess

import dev.qtremors.arcile.core.storage.domain.QuickAccessItem
import dev.qtremors.arcile.core.storage.domain.QuickAccessType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickAccessSectionsTest {

    @Test
    fun `section mapping assigns every supported item to exactly one section`() {
        val items = listOf(
            item("downloads", "/storage/emulated/0/Download", QuickAccessType.STANDARD),
            item("standard_whatsapp_media", "/storage/emulated/0/Android/media/com.whatsapp", QuickAccessType.STANDARD),
            item("work", "/work/whatsapp-backups", QuickAccessType.CUSTOM),
            item("tree", "content://tree", QuickAccessType.SAF_TREE),
            item("files", "content://files", QuickAccessType.FILES_APP),
            item("data", "content://data", QuickAccessType.EXTERNAL_HANDOFF)
        )

        val sections = items.toQuickAccessSections()
        val mapped = sections.custom + sections.system + sections.apps + sections.files

        assertEquals(items.map { it.id }.toSet(), mapped.map { it.id }.toSet())
        assertEquals(items.size, mapped.size)
        assertEquals(listOf("work"), sections.custom.map { it.id })
        assertEquals(listOf("downloads"), sections.system.map { it.id })
        assertEquals(listOf("standard_whatsapp_media"), sections.apps.map { it.id })
        assertEquals(listOf("tree", "files", "data"), sections.files.map { it.id })
    }

    @Test
    fun `whatsapp matching is case insensitive but does not reclassify custom paths`() {
        val sections = listOf(
            item("app", "/Android/media/COM.WHATSAPP/Media", QuickAccessType.STANDARD),
            item("custom", "/Backups/WhatsApp", QuickAccessType.CUSTOM)
        ).toQuickAccessSections()

        assertEquals(listOf("app"), sections.apps.map { it.id })
        assertEquals(listOf("custom"), sections.custom.map { it.id })
        assertTrue(sections.system.isEmpty())
    }

    private fun item(id: String, path: String, type: QuickAccessType) = QuickAccessItem(
        id = id,
        label = id,
        path = path,
        type = type
    )
}
