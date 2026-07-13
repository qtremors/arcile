package dev.qtremors.arcile.core.ui.theme

import dev.qtremors.arcile.core.ui.settings.displayedAccentColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentColorSelectorTest {
    @Test
    fun `displayed accent colors are unique`() {
        val accents = displayedAccentColors()
        val uniqueKeys = accents.map { it.color?.value?.toString() ?: it.name }.toSet()

        assertEquals(accents.size, uniqueKeys.size)
        assertEquals(1, accents.count { it == AccentColor.DYNAMIC })
        assertEquals(1, accents.count { it == AccentColor.MONOCHROME })
    }

    @Test
    fun `monochrome scheme uses neutral primary secondary and tertiary colors`() {
        val scheme = buildMonochromeScheme(isDark = false)

        assertTrue(scheme.primary.isNeutral())
        assertTrue(scheme.secondary.isNeutral())
        assertTrue(scheme.tertiary.isNeutral())
        assertTrue(scheme.surfaceContainer.isNeutral())
    }
}

private fun androidx.compose.ui.graphics.Color.isNeutral(): Boolean {
    val channels = listOf(red, green, blue)
    return channels.maxOrNull()!! - channels.minOrNull()!! < 0.02f
}
