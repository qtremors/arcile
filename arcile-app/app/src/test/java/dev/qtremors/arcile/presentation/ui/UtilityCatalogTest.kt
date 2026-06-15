package dev.qtremors.arcile.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilityCatalogTest {
    @Test
    fun `utility catalog ids are unique`() {
        assertEquals(
            ArcileUtilityCatalog.size,
            ArcileUtilityCatalog.map { it.id }.toSet().size
        )
    }

    @Test
    fun `home utility catalog contains only unique implemented home utilities`() {
        assertEquals(
            HomeUtilityCatalog.size,
            HomeUtilityCatalog.map { it.id }.toSet().size
        )
        assertTrue(HomeUtilityCatalog.all { it.showOnHome && it.isImplemented })
        assertTrue(HomeUtilityCatalog.any { it.action == UtilityAction.Trash })
        assertTrue(HomeUtilityCatalog.any { it.action == UtilityAction.Cleaner })
        assertTrue(HomeUtilityCatalog.any { it.action == UtilityAction.Activity })
    }

    @Test
    fun `utility catalog does not duplicate cleaner sub tools`() {
        val ids = ArcileUtilityCatalog.map { it.id }.toSet()

        assertTrue("analyze" !in ids)
        assertTrue("duplicates" !in ids)
        assertTrue("large" !in ids)
    }
}
