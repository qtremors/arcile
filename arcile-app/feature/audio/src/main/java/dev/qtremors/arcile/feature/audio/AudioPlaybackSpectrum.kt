package dev.qtremors.arcile.feature.audio

import android.content.Context
import androidx.media3.common.util.UnstableApi

internal val AudioPlaybackSpectrum = dev.qtremors.arcile.core.ui.audio.AudioPlaybackSpectrum()

@androidx.annotation.OptIn(UnstableApi::class)
internal fun AudioVisualizerRenderersFactory(context: Context) =
    dev.qtremors.arcile.core.ui.audio.AudioVisualizerRenderersFactory(context, AudioPlaybackSpectrum)
