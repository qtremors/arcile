package dev.qtremors.arcile.plugin.glb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelViewerStateTest {

    @Test
    fun `viewer state starts with visible loading chrome and neutral controls`() {
        val state = ModelViewerState()

        assertTrue(state.uiVisible)
        assertTrue(state.loading)
        assertFalse(state.infoVisible)
        assertEquals(ModelViewerControl.None, state.activeControl)
        assertEquals(ModelViewerBackground.Theme, state.backgroundMode)
        assertNull(state.errorMessage)
    }

    @Test
    fun `selecting active control toggles it off while another control replaces it`() {
        assertEquals(
            ModelViewerControl.None,
            ModelViewerControl.Zoom.toggled(ModelViewerControl.Zoom)
        )
        assertEquals(
            ModelViewerControl.Brightness,
            ModelViewerControl.Zoom.toggled(ModelViewerControl.Brightness)
        )
        assertEquals(
            ModelViewerControl.Background,
            ModelViewerControl.None.toggled(ModelViewerControl.Background)
        )
    }
}
