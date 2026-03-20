package dev.qtremors.arcile.utils

import androidx.compose.ui.graphics.Color
import dev.qtremors.arcile.ui.theme.CategoryColors
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryColorsTest {

    private val categoryColors = CategoryColors(
        images = Color.Red,
        videos = Color.Blue,
        audio = Color.Green,
        docs = Color.Yellow,
        archives = Color.Cyan,
        apks = Color.Magenta
    )

    @Test
    fun `getCategoryColor returns mapped category colors`() {
        assertEquals(Color.Red, getCategoryColor("Images", categoryColors, Color.Black))
        assertEquals(Color.Blue, getCategoryColor("Videos", categoryColors, Color.Black))
        assertEquals(Color.Green, getCategoryColor("Audio", categoryColors, Color.Black))
        assertEquals(Color.Yellow, getCategoryColor("Docs", categoryColors, Color.Black))
        assertEquals(Color.Cyan, getCategoryColor("Archives", categoryColors, Color.Black))
        assertEquals(Color.Magenta, getCategoryColor("APKs", categoryColors, Color.Black))
    }

    @Test
    fun `getCategoryColor returns fallback for unknown category`() {
        assertEquals(Color.Black, getCategoryColor("Other", categoryColors, Color.Black))
    }
}
