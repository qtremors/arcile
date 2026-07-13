package dev.qtremors.arcile.core.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

class SelectionReducerTest {
    @Test
    fun `toggle adds and removes paths`() {
        assertEquals(setOf("/a"), SelectionReducer.toggle(emptySet(), "/a"))
        assertEquals(emptySet<String>(), SelectionReducer.toggle(setOf("/a"), "/a"))
    }

    @Test
    fun `add all and invert are deterministic`() {
        assertEquals(setOf("/a", "/b"), SelectionReducer.add(setOf("/a"), listOf("/b", "/b")))
        assertEquals(setOf("/a", "/b"), SelectionReducer.all(listOf("/a", "/b", "/a")))
        assertEquals(setOf("/b"), SelectionReducer.invert(setOf("/a"), listOf("/a", "/b")))
    }

    @Test
    fun `retain and remove discard unavailable paths`() {
        val selected = setOf("/a", "/b", "/c")
        assertEquals(setOf("/a", "/c"), SelectionReducer.retain(selected, listOf("/a", "/c")))
        assertEquals(setOf("/a"), SelectionReducer.remove(selected, listOf("/b", "/c")))
    }
}
