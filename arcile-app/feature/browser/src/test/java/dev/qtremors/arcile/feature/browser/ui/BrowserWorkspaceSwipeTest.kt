package dev.qtremors.arcile.feature.browser.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserWorkspaceSwipeTest {
    @Test
    fun `horizontal swipes resolve to adjacent workspace directions`() {
        assertEquals(1, browserWorkspaceSwipeDirection(-80f, 10f, 72f))
        assertEquals(-1, browserWorkspaceSwipeDirection(80f, -10f, 72f))
    }

    @Test
    fun `short vertical and invalid gestures do not switch workspaces`() {
        assertNull(browserWorkspaceSwipeDirection(71f, 0f, 72f))
        assertNull(browserWorkspaceSwipeDirection(90f, 100f, 72f))
        assertNull(browserWorkspaceSwipeDirection(100f, 0f, 0f))
    }
}
