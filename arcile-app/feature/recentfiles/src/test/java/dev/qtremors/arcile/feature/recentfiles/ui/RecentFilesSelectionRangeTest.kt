package dev.qtremors.arcile.feature.recentfiles.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFilesSelectionRangeTest {
    private val paths = listOf("/one", "/two", "/three", "/four")

    @Test
    fun `range selection follows visible order in both directions`() {
        assertEquals(
            listOf("/one", "/two", "/three"),
            recentSelectionRange("/one", "/three", paths)
        )
        assertEquals(
            listOf("/two", "/three", "/four"),
            recentSelectionRange("/four", "/two", paths)
        )
    }

    @Test
    fun `missing anchor selects only the current item`() {
        assertEquals(listOf("/three"), recentSelectionRange(null, "/three", paths))
        assertEquals(listOf("/three"), recentSelectionRange("/gone", "/three", paths))
    }

    @Test
    fun `duplicate defensive rows do not duplicate selected paths`() {
        assertEquals(
            listOf("/one", "/two"),
            recentSelectionRange("/one", "/two", listOf("/one", "/one", "/two"))
        )
    }
}
