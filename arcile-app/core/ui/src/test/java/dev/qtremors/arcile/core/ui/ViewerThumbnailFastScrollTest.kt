package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerThumbnailFastScrollTest {
    @Test
    fun `pointer follows horizontal and vertical strip axis`() {
        assertEquals(
            4f,
            viewerThumbnailFastScrollAxisPosition(Offset(4f, 12f), Orientation.Horizontal)
        )
        assertEquals(
            12f,
            viewerThumbnailFastScrollAxisPosition(Offset(4f, 12f), Orientation.Vertical)
        )
    }

    @Test
    fun `absolute pointer progress maps across the full queue`() {
        assertEquals(0, viewerThumbnailFastScrollTarget(10f, 10f, 110f, 11))
        assertEquals(5, viewerThumbnailFastScrollTarget(60f, 10f, 110f, 11))
        assertEquals(10, viewerThumbnailFastScrollTarget(110f, 10f, 110f, 11))
    }

    @Test
    fun `pointer progress clamps at both viewport bounds`() {
        assertEquals(0, viewerThumbnailFastScrollTarget(-50f, 0f, 100f, 8))
        assertEquals(7, viewerThumbnailFastScrollTarget(250f, 0f, 100f, 8))
    }

    @Test
    fun `empty and invalid viewports do not request scrolling`() {
        assertEquals(null, viewerThumbnailFastScrollTarget(10f, 0f, 100f, 0))
        assertEquals(null, viewerThumbnailFastScrollTarget(10f, 20f, 20f, 10))
        assertEquals(0, viewerThumbnailFastScrollTarget(10f, 0f, 100f, 1))
    }
}
