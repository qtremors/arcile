package dev.qtremors.arcile.presentation.ui

import dev.qtremors.arcile.core.presentation.UiText
import dev.qtremors.arcile.core.ui.ArcileFeedbackEvent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnedFeedbackEventTest {
    private val feedback = ArcileFeedbackEvent(UiText.Dynamic("Done"))

    @Test
    fun `feedback is delivered only while its navigation owner is active`() {
        val owned = OwnedFeedbackEvent(ownerId = "gallery-entry", event = feedback)

        assertTrue(owned.belongsTo("gallery-entry"))
        assertFalse(owned.belongsTo("home-entry"))
        assertFalse(owned.belongsTo(null))
    }

    @Test
    fun `unowned feedback is never delivered into a later screen`() {
        val owned = OwnedFeedbackEvent(ownerId = null, event = feedback)

        assertFalse(owned.belongsTo(null))
        assertFalse(owned.belongsTo("home-entry"))
    }
}
