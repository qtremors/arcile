package dev.qtremors.arcile.feature.audio

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal typealias AudioPlayerPreferences = dev.qtremors.arcile.core.ui.audio.AudioPlayerPreferences
@Composable internal fun rememberAudioPlayerPreferences() = dev.qtremors.arcile.core.ui.audio.rememberAudioPlayerPreferences()
@Composable internal fun AudioPlayerPreferences.visualizerEnabledState() =
    visualizerEnabledFlow.collectAsStateWithLifecycle(initialValue = visualizerEnabled)
