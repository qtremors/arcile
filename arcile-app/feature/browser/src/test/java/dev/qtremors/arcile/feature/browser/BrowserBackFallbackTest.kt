package dev.qtremors.arcile.feature.browser

import dev.qtremors.arcile.feature.browser.ui.BrowserBackAction
import dev.qtremors.arcile.feature.browser.ui.BrowserBackState
import dev.qtremors.arcile.feature.browser.ui.resolveBrowserBackAction
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserBackFallbackTest {

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

    @Test
    fun `browser back priority closes modal before every other action`() {
        assertEquals(
            BrowserBackAction.CloseModal,
            resolveBrowserBackAction(
                BrowserBackState(
                    hasModal = true,
                    hasSheet = true,
                    hasSearch = true,
                    hasSelection = true,
                    canNavigateFolderUp = true,
                    canPopRoute = true
                )
            )
        )
    }

    @Test
    fun `browser back priority follows sheet search selection folder route exit order`() {
        assertEquals(BrowserBackAction.CloseSheet, resolveBrowserBackAction(BrowserBackState(hasSheet = true, hasSearch = true)))
        assertEquals(BrowserBackAction.CloseSearch, resolveBrowserBackAction(BrowserBackState(hasSearch = true, hasSelection = true)))
        assertEquals(BrowserBackAction.ClearSelection, resolveBrowserBackAction(BrowserBackState(hasSelection = true, canNavigateFolderUp = true)))
        assertEquals(BrowserBackAction.NavigateFolderUp, resolveBrowserBackAction(BrowserBackState(canNavigateFolderUp = true, canPopRoute = true)))
        assertEquals(BrowserBackAction.PopRoute, resolveBrowserBackAction(BrowserBackState(canPopRoute = true)))
        assertEquals(BrowserBackAction.ExitApp, resolveBrowserBackAction(BrowserBackState()))
    }
}
