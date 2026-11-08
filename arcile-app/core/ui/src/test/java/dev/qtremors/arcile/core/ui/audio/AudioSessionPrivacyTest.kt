package dev.qtremors.arcile.core.ui.audio

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import dev.qtremors.arcile.core.storage.domain.VaultThumbnailRequest
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@OptIn(ExperimentalCoroutinesApi::class)
class AudioSessionPrivacyTest {
    @Test
    fun `protected artwork bypasses generic memory and disk caches`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = VaultThumbnailRequest("vault", "node", "root", 1L, 512)
        val request = audioArtworkRequest(context, model) as ImageRequest
        assertEquals(CachePolicy.DISABLED, request.memoryCachePolicy)
        assertEquals(CachePolicy.DISABLED, request.diskCachePolicy)
        val normal = "normal-thumbnail"
        assertSame(normal, audioArtworkRequest(context, normal))
    }

    @Test
    fun `private spectrum clearing cannot alter another player's spectrum`() {
        val normal = AudioPlaybackSpectrum()
        val private = AudioPlaybackSpectrum()
        for (spectrum in listOf(normal, private)) {
            spectrum.flush(48_000, 1, C.ENCODING_PCM_16BIT)
            spectrum.setSpectrumVisible(true)
            spectrum.setPlaying(true)
        }
        val tone = ByteBuffer.allocateDirect(8_192).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(4_096) { putShort((sin(2 * PI * it * 90 / 48_000) * 14_000).toInt().toShort()) }
            flip()
        }
        private.handleBuffer(tone)
        assertTrue(private.levels.value.any { it > 0.2f })
        assertTrue(normal.levels.value.all { it == 0f })
        normal.handleBuffer(tone)
        val retained = normal.levels.value
        private.clear()
        assertTrue(private.levels.value.all { it == 0f })
        assertEquals(retained, normal.levels.value)
        normal.clear()
    }

    @Test
    fun `closing a player cancels its timer and replacement timers fire only once`() = runTest {
        var elapsed = 0
        val timer = AudioSleepTimer(this, { testScheduler.currentTime }) { elapsed++ }
        timer.setMinutes(1)
        runCurrent()
        advanceTimeBy(59_999)
        assertEquals(0, elapsed)
        timer.cancel()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(0, elapsed)
        timer.setMinutes(1)
        runCurrent()
        timer.setMinutes(2)
        runCurrent()
        advanceTimeBy(120_000)
        runCurrent()
        assertEquals(1, elapsed)
        assertEquals(null, timer.endElapsedMs)
    }

    @Test
    fun `normal and vault players share sanitized non-sensitive playback preferences`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val normal = AudioPlayerPreferences(context)
        val private = AudioPlayerPreferences(context)
        normal.setPlaybackParameters(1.5f, 0.75f)
        assertEquals(1.5f, private.playbackSpeed)
        assertEquals(0.75f, private.playbackPitch)
        private.setPlaybackParameters(Float.NaN, Float.POSITIVE_INFINITY)
        assertEquals(1f, normal.playbackSpeed)
        assertEquals(1f, normal.playbackPitch)
        assertEquals(setOf("speed", "pitch"), context.getSharedPreferences("audio_playback_settings", Context.MODE_PRIVATE).all.keys)
    }

    @Test
    fun `connected players observe speed and pitch changed by another source`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val normal = AudioPlayerPreferences(context)
        val private = AudioPlayerPreferences(context)
        normal.setPlaybackParameters(1f, 1f)
        shadowOf(Looper.getMainLooper()).idle()
        val observed = mutableListOf<PlaybackParameters>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            normal.playbackParametersFlow.take(2).toList(observed)
        }
        runCurrent()
        private.setPlaybackParameters(1.25f, 0.75f)
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals(listOf(PlaybackParameters(1f, 1f), PlaybackParameters(1.25f, 0.75f)), observed)
    }
}
