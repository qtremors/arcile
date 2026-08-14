package dev.qtremors.arcile.feature.videoplayer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class GestureZone { LEFT, CENTER, RIGHT }

internal fun videoPlayerGestureZone(x: Float, totalWidth: Float): GestureZone {
    if (totalWidth <= 0f) return GestureZone.CENTER
    return when {
        x < totalWidth / 3f -> GestureZone.LEFT
        x >= 2f * totalWidth / 3f -> GestureZone.RIGHT
        else -> GestureZone.CENTER
    }
}

internal sealed interface GestureHudState {
    data class Seek(val text: String, val isForward: Boolean) : GestureHudState
    data class PlayPause(val isPlaying: Boolean) : GestureHudState
    data class Brightness(val percentage: Int) : GestureHudState
    data class Volume(val percentage: Int) : GestureHudState
}

@Composable
internal fun VideoPlayerGestureHud(
    state: GestureHudState,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Black.copy(alpha = 0.75f),
        contentColor = Color.White
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            when (state) {
                is GestureHudState.Seek -> {
                    Icon(
                        imageVector = if (state.isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = state.text,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
                is GestureHudState.PlayPause -> {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                is GestureHudState.Brightness -> {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    GesturePercentage(state.percentage)
                }
                is GestureHudState.Volume -> {
                    val icon = when {
                        state.percentage == 0 -> Icons.AutoMirrored.Filled.VolumeOff
                        state.percentage < 50 -> Icons.AutoMirrored.Filled.VolumeDown
                        else -> Icons.AutoMirrored.Filled.VolumeUp
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    GesturePercentage(state.percentage)
                }
            }
        }
    }
}

@Composable
private fun GesturePercentage(percentage: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "$percentage%",
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
            color = Color.White
        )
        LinearProgressIndicator(
            progress = { percentage / 100f },
            modifier = Modifier
                .width(70.dp)
                .height(4.dp),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
    }
}
