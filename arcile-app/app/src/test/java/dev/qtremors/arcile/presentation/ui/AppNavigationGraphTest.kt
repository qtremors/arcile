package dev.qtremors.arcile.presentation.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AppNavigationGraphTest {

    @Test
    fun `browser back fallback pops app stack when browser was opened from another screen`() {
        assertEquals(
            BrowserBackFallback.PopAppBackStack,
            browserBackFallback(hasPreviousBackStackEntry = true)
        )
    }

    @Test
    fun `browser back fallback returns to home pager when browser is root main screen`() {
        assertEquals(
            BrowserBackFallback.ShowHomePager,
            browserBackFallback(hasPreviousBackStackEntry = false)
        )
    }
}
