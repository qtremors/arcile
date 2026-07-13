package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.presentation.UiText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTransientControllerTest {
    @Test
    fun `finishing one workflow preserves other active workflow busy state`() {
        val controller = BrowserTransientController()

        controller.setBusy(BrowserBusySource.CLIPBOARD, true)
        controller.setBusy(BrowserBusySource.OPERATION, true)
        controller.setBusy(BrowserBusySource.CLIPBOARD, false)

        assertTrue(controller.state.value.isBusy)
        assertEquals(setOf(BrowserBusySource.OPERATION), controller.state.value.busySources)

        controller.setBusy(BrowserBusySource.OPERATION, false)

        assertFalse(controller.state.value.isBusy)
    }

    @Test
    fun `transient feedback is owned and explicitly cleared`() {
        val controller = BrowserTransientController()
        val error = UiText.Dynamic("operation failed")

        controller.reportError(error)
        assertEquals(error, controller.state.value.error)

        controller.clearError()
        assertEquals(null, controller.state.value.error)
    }
}
