package dev.qtremors.arcile.core.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfPageSizeTest {
    @Test
    fun `aspect ratio uses bounded page height`() {
        assertEquals(0.5f, PdfPageSize(width = 100, height = 200).aspectRatio)
        assertEquals(100f, PdfPageSize(width = 100, height = 0).aspectRatio)
    }
}
