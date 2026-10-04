package dev.qtremors.arcile.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLyricsTest {
    @Test
    fun `lyric offsets shift cues and never seek before the start`() {
        assertEquals(listOf(1500L, 3000L),
            parseLrc("[offset:500]\n[00:01]One\n[00:02.5]Two").map(TimedLyricLine::timeMs))
        assertEquals(listOf(0L, 1000L),
            parseLrc("[offset:-1500]\n[00:01]One\n[00:02.5]Two").map(TimedLyricLine::timeMs))
    }

    @Test
    fun `active cue follows seeking in either direction and timestamp boundaries`() {
        val lines = parseLrc("[00:01]One\n[00:03]Two\n[00:03]Translation\n[00:05]Three")
        assertEquals(-1, activeLyricIndex(emptyList(), 5000L))
        assertEquals(-1, activeLyricIndex(lines, 999L))
        assertEquals(0, activeLyricIndex(lines, 1000L))
        assertEquals(2, activeLyricIndex(lines, 3000L))
        assertEquals(3, activeLyricIndex(lines, 60_000L))
        assertEquals(0, activeLyricIndex(lines, 2999L))
    }

    @Test
    fun `LRC parser sorts cues and converts fractional timestamps`() {
        val parsed = parseLrc("""
            [00:12.50]Second line
            [00:01.005]First line
            [01:00.1]Minute line
        """.trimIndent())

        assertEquals(listOf("First line", "Second line", "Minute line"),
            parsed.map(TimedLyricLine::text))
        assertEquals(listOf(1005L, 12_500L, 60_100L),
            parsed.map(TimedLyricLine::timeMs))
    }

    @Test
    fun `multiple timestamps on a line repeat the same lyric`() {
        val parsed = parseLrc("[00:01.00][00:03.00]Refrain")
        assertEquals(listOf(1000L, 3000L), parsed.map(TimedLyricLine::timeMs))
        assertTrue(parsed.all { it.text == "Refrain" })
    }

    @Test
    fun `invalid cues and metadata headers are ignored`() {
        val parsed = parseLrc("""
            [ar:Artist]
            [00:65.00]Bad seconds
            [00:10.00]Good
            unrelated text
        """.trimIndent())
        assertEquals(listOf(TimedLyricLine(10_000L, "Good")), parsed)
    }
}
