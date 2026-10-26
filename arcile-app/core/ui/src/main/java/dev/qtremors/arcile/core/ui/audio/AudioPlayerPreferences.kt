package dev.qtremors.arcile.core.ui.audio

import android.content.Context
import android.content.SharedPreferences
import androidx.media3.common.PlaybackParameters
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class AudioPlayerPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("audio_player_preferences", Context.MODE_PRIVATE)

    private val playback = context.applicationContext
        .getSharedPreferences("audio_playback_settings", Context.MODE_PRIVATE)
    val playbackSpeed: Float get() = safePlaybackParameter(playback.getFloat("speed", 1f))
    val playbackPitch: Float get() = safePlaybackParameter(playback.getFloat("pitch", 1f))

    fun setPlaybackParameters(speed: Float, pitch: Float) {
        playback.edit().putFloat("speed", safePlaybackParameter(speed))
            .putFloat("pitch", safePlaybackParameter(pitch)).apply()
    }

    val playbackParametersFlow = callbackFlow {
        fun current() = PlaybackParameters(playbackSpeed, playbackPitch)
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "speed" || key == "pitch") trySend(current())
        }
        playback.registerOnSharedPreferenceChangeListener(listener)
        trySend(current())
        awaitClose { playback.unregisterOnSharedPreferenceChangeListener(listener) }
    }.distinctUntilChanged()

    var visualizerEnabled: Boolean
        get() = preferences.getBoolean(KEY_VISUALIZER, false)
        set(enabled) {
            preferences.edit().putBoolean(KEY_VISUALIZER, enabled).apply()
        }

    val visualizerEnabledFlow = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_VISUALIZER) trySend(visualizerEnabled)
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        trySend(visualizerEnabled)
        awaitClose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    private companion object {
        const val KEY_VISUALIZER = "visualizer_enabled"
    }
}

@Composable
fun rememberAudioPlayerPreferences(): AudioPlayerPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { AudioPlayerPreferences(context) }
}

@Composable
fun AudioPlayerPreferences.visualizerEnabledState() =
    visualizerEnabledFlow.collectAsStateWithLifecycle(initialValue = visualizerEnabled)

private fun safePlaybackParameter(value: Float): Float = value.takeIf { it.isFinite() }?.coerceIn(0.5f, 2f) ?: 1f
