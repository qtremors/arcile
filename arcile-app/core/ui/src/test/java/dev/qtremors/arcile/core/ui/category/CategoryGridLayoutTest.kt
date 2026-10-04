package dev.qtremors.arcile.core.ui.category

import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGridLayoutTest {
    @Test
    fun `every slider option produces its selected column count at phone and tablet widths`() {
        for (layout in CategoryGridLayout.entries) {
            for (width in listOf(280f, 320f, 360f, 400f, 600f, 840f, 1024f, 1280f)) {
                for (columns in layout.options(width)) {
                    assertEquals("$layout at $width dp", columns, layout.columnCount(layout.cellSize(columns, width), width))
                }
            }
        }
    }
}
