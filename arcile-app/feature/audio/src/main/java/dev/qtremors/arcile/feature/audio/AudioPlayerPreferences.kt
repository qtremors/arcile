package dev.qtremors.arcile.feature.audio

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow

internal class AudioPlayerPreferences(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("audio_player_preferences", Context.MODE_PRIVATE)

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
internal fun rememberAudioPlayerPreferences(): AudioPlayerPreferences {
    val context = LocalContext.current.applicationContext
    return remember(context) { AudioPlayerPreferences(context) }
}

@Composable
internal fun AudioPlayerPreferences.visualizerEnabledState() =
    visualizerEnabledFlow.collectAsStateWithLifecycle(initialValue = visualizerEnabled)
