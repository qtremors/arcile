package dev.qtremors.arcile.feature.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayerPresentationTest {
    @Test
    fun `expansion waits until the mini player is anchored in a full-height parent`() {
        assertFalse(
            shouldStartAudioPlayerExpansion(
                presentation = AudioPlayerPresentation.PREPARING_EXPANSION,
                parentHeightPx = 120,
                miniPlayerHeightPx = 120
            )
        )

        assertTrue(
            shouldStartAudioPlayerExpansion(
                presentation = AudioPlayerPresentation.PREPARING_EXPANSION,
                parentHeightPx = 2400,
                miniPlayerHeightPx = 120
            )
        )
    }

    @Test
    fun `layout changes cannot restart expansion from another player state`() {
        AudioPlayerPresentation.entries
            .filterNot { it == AudioPlayerPresentation.PREPARING_EXPANSION }
            .forEach { presentation ->
                assertFalse(
                    shouldStartAudioPlayerExpansion(
                        presentation = presentation,
                        parentHeightPx = 2400,
                        miniPlayerHeightPx = 120
                    )
                )
            }
    }
}
