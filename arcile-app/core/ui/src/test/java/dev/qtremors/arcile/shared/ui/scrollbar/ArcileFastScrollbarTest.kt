package dev.qtremors.arcile.core.ui.scrollbar

import org.junit.Assert.assertEquals
import org.junit.Test

class ArcileFastScrollbarTest {

    @Test
    fun `continuous progress includes visible item offset`() {
        val fraction = continuousScrollFraction(
            firstVisibleIndex = 40f,
            firstVisibleOffset = 50f,
            stride = 100f,
            totalItems = 100,
            estimatedVisibleItems = 10f,
            canScrollBackward = true,
            canScrollForward = true
        )

        assertEquals(0.45f, fraction, 0.0001f)
    }

    @Test
    fun `continuous progress pins exact list boundaries`() {
        assertEquals(
            0f,
            continuousScrollFraction(50f, 20f, 100f, 100, 10f, false, true),
            0f
        )
        assertEquals(
            1f,
            continuousScrollFraction(50f, 20f, 100f, 100, 10f, true, false),
            0f
        )
    }

    @Test
    fun `drag fractions map across all item indices`() {
        assertEquals(0, fractionToIndex(-1f, 50))
        assertEquals(24, fractionToIndex(0.5f, 50))
        assertEquals(49, fractionToIndex(1f, 50))
        assertEquals(49, fractionToIndex(2f, 50))
    }

    @Test
    fun `scrollbar stretch is subtle and capped for motion`() {
        assertEquals(1f, scrollbarStretchForDelta(0f), 0f)
        assertEquals(1.11f, scrollbarStretchForDelta(0.01f), 0.0001f)
        assertEquals(1.11f, scrollbarStretchForDelta(-0.01f), 0.0001f)
        assertEquals(1.18f, scrollbarStretchForDelta(1f), 0f)
    }
}
