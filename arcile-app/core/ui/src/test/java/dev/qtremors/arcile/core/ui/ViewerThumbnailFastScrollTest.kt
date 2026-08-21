package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class ViewerThumbnailFastScrollTest {
    @Test
    fun `held thumbnail drag accelerates along the strip orientation`() {
        assertEquals(
            -20f,
            viewerThumbnailFastScrollDelta(
                dragAmount = Offset(4f, 12f),
                orientation = Orientation.Horizontal
            )
        )
        assertEquals(
            -60f,
            viewerThumbnailFastScrollDelta(
                dragAmount = Offset(4f, 12f),
                orientation = Orientation.Vertical
            )
        )
    }
}
