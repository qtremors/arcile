package dev.qtremors.arcile.feature.audio

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import dev.qtremors.arcile.core.storage.domain.AudioTrack

@Composable
internal fun AudioArtwork(track: AudioTrack, modifier: Modifier = Modifier, shape: Shape = MaterialTheme.shapes.large) =
    dev.qtremors.arcile.core.ui.audio.AudioArtwork(track, modifier, shape)
