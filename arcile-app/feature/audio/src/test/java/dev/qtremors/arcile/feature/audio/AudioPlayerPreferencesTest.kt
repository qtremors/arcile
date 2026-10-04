package dev.qtremors.arcile.feature.audio

import android.content.Context
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import android.os.Looper
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AudioPlayerPreferencesTest {
    private lateinit var context: Context

    @Before
    fun reset() {
        context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("audio_player_preferences", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun `new player starts with the visualizer disabled`() {
        assertFalse(AudioPlayerPreferences(context).visualizerEnabled)
    }

    @Test
    fun `both enabled and disabled choices survive player recreation`() {
        AudioPlayerPreferences(context).visualizerEnabled = true
        val reopened = AudioPlayerPreferences(context)
        assertTrue(reopened.visualizerEnabled)
        reopened.visualizerEnabled = false
        assertFalse(AudioPlayerPreferences(context).visualizerEnabled)
    }

    @Test
    fun `a resumed player observes the choice made by another player`() = runTest {
        val first = AudioPlayerPreferences(context)
        val second = AudioPlayerPreferences(context)
        second.visualizerEnabled = true
        assertTrue(first.visualizerEnabledFlow.first())
        second.visualizerEnabled = false
        assertFalse(first.visualizerEnabledFlow.first())
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    @Test
    fun `visible players observe visualizer changes from another player`() = runTest {
        val first = AudioPlayerPreferences(context)
        val second = AudioPlayerPreferences(context)
        val observed = mutableListOf<Boolean>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            first.visualizerEnabledFlow.take(3).toList(observed)
        }
        runCurrent()
        second.visualizerEnabled = true
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        second.visualizerEnabled = false
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        assertEquals(listOf(false, true, false), observed)
    }
}
