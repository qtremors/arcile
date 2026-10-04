package dev.qtremors.arcile.feature.audio

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AudioPlaybackSpectrumTest {
    @Test
    fun `live PCM frequencies drive separate visualizer bands`() {
        AudioPlaybackSpectrum.flush(48_000, 1, C.ENCODING_PCM_16BIT)
        AudioPlaybackSpectrum.setSpectrumVisible(true)
        AudioPlaybackSpectrum.setPlaying(true)
        try {
            AudioPlaybackSpectrum.handleBuffer(tone(90.0))
            val bass = AudioPlaybackSpectrum.levels.value
            assertTrue(bass[0] > 0.2f)
            assertTrue(bass[0] > bass[7] * 2f)

            AudioPlaybackSpectrum.flush(48_000, 1, C.ENCODING_PCM_16BIT)
            AudioPlaybackSpectrum.handleBuffer(tone(4_000.0))
            val treble = AudioPlaybackSpectrum.levels.value
            assertTrue(treble[6] > 0.2f)
            assertTrue(treble[6] > treble[0] * 2f)

            AudioPlaybackSpectrum.handleBuffer(ByteBuffer.allocateDirect(4_096 * 2))
            assertTrue(AudioPlaybackSpectrum.levels.value[6] < treble[6] * 0.5f)

            AudioPlaybackSpectrum.setPlaying(false)
            assertTrue(AudioPlaybackSpectrum.levels.value.all { it == 0f })
        } finally {
            AudioPlaybackSpectrum.setPlaying(false)
            AudioPlaybackSpectrum.setSpectrumVisible(false)
        }
    }

    private fun tone(frequencyHz: Double): ByteBuffer =
        ByteBuffer.allocateDirect(4_096 * 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(4_096) { sample ->
                putShort((sin(2.0 * PI * sample * frequencyHz / 48_000.0) * 14_000).toInt().toShort())
            }
            flip()
        }
}
