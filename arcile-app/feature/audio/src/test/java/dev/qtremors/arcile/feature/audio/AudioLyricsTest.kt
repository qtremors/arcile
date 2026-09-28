package dev.qtremors.arcile.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLyricsTest {
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
