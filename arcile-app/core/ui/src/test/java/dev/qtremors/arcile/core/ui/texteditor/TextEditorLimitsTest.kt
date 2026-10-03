package dev.qtremors.arcile.core.ui.texteditor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import java.io.ByteArrayInputStream
import java.io.InputStream
import org.junit.Assert.*
import org.junit.Test

class TextEditorLimitsTest {
    @Test fun `unknown length stream stops after the budget and one probe byte`() {
        var consumed = 0
        val stream = object : InputStream() {
            override fun read(): Int { consumed++; return 'a'.code }
            override fun available(): Int = 0
        }
        assertTrue(runCatching { readBoundedText(stream, 1024) }.exceptionOrNull() is TextTooLargeException)
        assertEquals(1025, consumed)
        assertEquals("hello", readBoundedText(ByteArrayInputStream("hello".toByteArray()), 5))
    }

    @Test fun `UTF8 limits count multibyte text and surrogate pairs`() {
        assertTrue(textFitsEditor("a".repeat(MAX_TEXT_BYTES)))
        assertFalse(textFitsEditor("a".repeat(MAX_TEXT_BYTES + 1)))
        assertTrue(textFitsEditor("\uD83D\uDE00".repeat(MAX_TEXT_BYTES / 4)))
        assertFalse(textFitsEditor("\uD83D\uDE00".repeat(MAX_TEXT_BYTES / 4 + 1)))
        assertFalse(textFitsEditor("界".repeat(MAX_TEXT_BYTES / 3 + 1)))
    }

    @Test fun `history remains bounded across undo and redo while retaining nearest states`() {
        val states = (1..20).map { TextFieldValue("$it" + "a".repeat(400_000)) }
        val (undo, redo) = boundedEditorHistory(states.take(10), states.drop(10))
        assertTrue((undo + redo).sumOf { it.text.length.toLong() * 2 } <= MAX_HISTORY_BYTES)
        assertEquals(states[9], undo.last())
        assertEquals(states.last(), redo.last())
        val small = (1..60).map { TextFieldValue(it.toString()) }
        assertEquals(small.takeLast(50), boundedEditorHistory(small, emptyList()).first)
    }

    @Test fun `formatting handles forward backward and collapsed selections`() {
        listOf("**" to "**", "*" to "*", "[" to "](url)").forEach { (prefix, suffix) ->
            val forward = formatEditorSelection(TextFieldValue("abc selected xyz", TextRange(4, 12)), prefix, suffix)
            val backward = formatEditorSelection(TextFieldValue("abc selected xyz", TextRange(12, 4)), prefix, suffix)
            assertEquals(forward, backward)
            assertEquals("abc ${prefix}selected${suffix} xyz", forward.text)
            val collapsed = formatEditorSelection(TextFieldValue("abc", TextRange(1)), prefix, suffix)
            assertEquals("a$prefix${suffix}bc", collapsed.text)
            assertEquals(TextRange(1 + prefix.length), collapsed.selection)
        }
    }
}
