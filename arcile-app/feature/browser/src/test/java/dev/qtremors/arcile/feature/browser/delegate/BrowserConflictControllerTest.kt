package dev.qtremors.arcile.feature.browser.delegate

import dev.qtremors.arcile.core.storage.domain.FileConflict
import dev.qtremors.arcile.core.storage.domain.FileModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserConflictControllerTest {

    @Test
    fun `dismiss clears the active conflict owner and conflicts`() {
        val file = FileModel("test.txt", "/test.txt", 0L, 0L, false, "txt", false)
        val existing = FileModel("test.txt", "/dest/test.txt", 0L, 0L, false, "txt", false)
        val conflict = FileConflict("/test.txt", file, existing)
        var mirroredState = BrowserConflictState()
        val controller = BrowserConflictController(BrowserConflictState()) {
            mirroredState = it
        }

        controller.show(BrowserConflictOwner.PASTE, listOf(conflict))

        assertEquals(BrowserConflictOwner.PASTE, controller.state.value.owner)
        assertEquals(listOf(conflict), controller.state.value.conflicts)
        assertTrue(controller.state.value.isVisible)

        controller.dismiss()

        assertEquals(BrowserConflictState(), controller.state.value)
        assertEquals(BrowserConflictState(), mirroredState)
        assertFalse(controller.state.value.isVisible)
        assertTrue(controller.state.value.conflicts.isEmpty())
    }
}
