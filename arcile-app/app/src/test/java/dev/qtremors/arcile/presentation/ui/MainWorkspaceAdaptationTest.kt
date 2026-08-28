package dev.qtremors.arcile.presentation.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainWorkspaceAdaptationTest {
    @Test
    fun `compact portrait keeps swipe navigation and one pane`() {
        val result = adaptation(compact = true)
        assertFalse(result.persistentNavigation)
        assertFalse(result.dualPane)
    }

    @Test
    fun `medium width uses persistent navigation`() {
        val result = adaptation(compact = false, medium = true)
        assertTrue(result.persistentNavigation)
        assertFalse(result.dualPane)
    }

    @Test
    fun `expanded width uses persistent navigation and two panes`() {
        val result = adaptation(compact = false, medium = false)
        assertTrue(result.persistentNavigation)
        assertTrue(result.dualPane)
    }

    @Test
    fun `vertical separating hinge enables two panes`() {
        assertTrue(adaptation(compact = true, verticalHinge = true).dualPane)
    }

    @Test
    fun `tabletop posture avoids a side by side split`() {
        assertFalse(
            adaptation(compact = false, medium = false, tabletop = true, verticalHinge = true).dualPane
        )
    }

    private fun adaptation(
        compact: Boolean,
        medium: Boolean = false,
        landscape: Boolean = false,
        tabletop: Boolean = false,
        verticalHinge: Boolean = false,
        legacyLandscapeSetting: Boolean = false
    ) = resolveMainWorkspaceAdaptation(
        isCompactWidth = compact,
        isMediumWidth = medium,
        isLandscape = landscape,
        isTabletop = tabletop,
        hasSeparatingVerticalHinge = verticalHinge,
        landscapeDualPaneEnabled = legacyLandscapeSetting
    )
}
