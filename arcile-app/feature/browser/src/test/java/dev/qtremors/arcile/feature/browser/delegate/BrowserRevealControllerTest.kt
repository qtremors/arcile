package dev.qtremors.arcile.feature.browser.delegate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRevealControllerTest {

    @Test
    fun `only the requested path can consume an armed reveal`() {
        var mirrored = BrowserRevealState()
        val controller = BrowserRevealController(BrowserRevealState()) { mirrored = it }

        controller.request("/Pictures/photo.jpg")
        controller.arm()
        controller.consume("/Pictures/other.jpg")

        assertEquals("/Pictures/photo.jpg", controller.state.value.path)
        assertTrue(controller.state.value.isReady)

        controller.consume("/Pictures/photo.jpg")

        assertNull(controller.state.value.path)
        assertFalse(controller.state.value.isReady)
        assertEquals(BrowserRevealState(), mirrored)
    }

    @Test
    fun `arming without a request does nothing`() {
        val controller = BrowserRevealController(BrowserRevealState()) {}

        controller.arm()

        assertEquals(BrowserRevealState(), controller.state.value)
    }
}
