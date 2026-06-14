package dev.qtremors.arcile.presentation.ui.components.home

import dev.qtremors.arcile.core.storage.domain.CategoryStorage
import org.junit.Assert.assertEquals
import org.junit.Test

class StorageSummaryCardsTest {
    @Test
    fun `system inaccessible bytes are used bytes not covered by visible categories or trash`() {
        val categories = listOf(
            CategoryStorage(name = "Images", sizeBytes = 20L, extensions = emptySet()),
            CategoryStorage(name = "Videos", sizeBytes = 30L, extensions = emptySet())
        )

        val bytes = systemInaccessibleBytes(
            totalBytes = 100L,
            freeBytes = 10L,
            categoryStorages = categories,
            trashBytes = 5L
        )

        assertEquals(35L, bytes)
    }

    @Test
    fun `system inaccessible bytes never go negative when visible totals exceed used bytes`() {
        val categories = listOf(
            CategoryStorage(name = "Images", sizeBytes = 80L, extensions = emptySet())
        )

        val bytes = systemInaccessibleBytes(
            totalBytes = 100L,
            freeBytes = 40L,
            categoryStorages = categories,
            trashBytes = 10L
        )

        assertEquals(0L, bytes)
    }
}
