package dev.qtremors.arcile.core.ui.texteditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextEditorPersistenceTest {
    @Test
    fun `verified persistence writes the requested snapshot`() {
        var persisted = "old"

        val result = persistVerifiedText(
            content = "saved snapshot",
            write = { persisted = it },
            read = { persisted }
        )

        assertTrue(result.isSuccess)
        assertEquals("saved snapshot", persisted)
    }

    @Test
    fun `verified persistence rejects providers that retain stale content`() {
        val result = persistVerifiedText(
            content = "new",
            write = { },
            read = { "old" }
        )

        assertTrue(result.isFailure)
    }

    @Test
    fun `word count handles blank and repeated whitespace`() {
        assertEquals(0, "  \n ".wordCount())
        assertEquals(3, "one  two\nthree".wordCount())
    }
}
