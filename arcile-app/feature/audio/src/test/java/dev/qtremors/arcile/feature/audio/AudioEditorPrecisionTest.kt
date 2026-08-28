package dev.qtremors.arcile.feature.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEditorPrecisionTest {
    @Test
    fun timecodeIncludesMilliseconds() {
        assertEquals("00:00.000", formatEditorTime(0))
        assertEquals("01:05.042", formatEditorTime(65_042))
        assertEquals("1:02:03.004", formatEditorTime(3_723_004))
    }
}
