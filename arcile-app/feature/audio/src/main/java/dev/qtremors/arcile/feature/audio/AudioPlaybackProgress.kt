package dev.qtremors.arcile.feature.audio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

@Composable
internal fun AudioPlaybackProgress(
    positionMs: Float,
    durationMs: Long,
    isPlaying: Boolean,
    onPositionChange: (Float) -> Unit,
    onPositionChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val safeDuration = durationMs.coerceAtLeast(1L)
    val progress = (positionMs / safeDuration.toFloat()).coerceIn(0f, 1f)
    val transition = rememberInfiniteTransition(label = "audioProgressWave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "audioProgressPhase"
    )
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            ) {
                val centerY = size.height / 2f
                val activeX = size.width * progress
                val strokeWidth = 4.dp.toPx()
                val amplitude = if (isPlaying) 3.dp.toPx() else 0f
                val wavelength = 40.dp.toPx()

                drawLine(
                    color = inactiveColor,
                    start = Offset(activeX, centerY),
                    end = Offset(size.width, centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round
                )
                if (activeX > 0f) {
                    val path = Path().apply { moveTo(0f, centerY) }
                    var x = 0f
                    while (x <= activeX) {
                        val ramp = (x / (size.width * 0.03f).coerceAtLeast(1f))
                            .coerceIn(0f, 1f)
                        val angle = ((x / wavelength) + phase) * 2f * PI.toFloat()
                        path.lineTo(x, centerY + sin(angle) * amplitude * ramp)
                        x += 2.dp.toPx()
                    }
                    path.lineTo(activeX, centerY)
                    drawPath(
                        path = path,
                        color = activeColor,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }
            }
            Slider(
                value = positionMs.coerceIn(0f, safeDuration.toFloat()),
                onValueChange = onPositionChange,
                onValueChangeFinished = onPositionChangeFinished,
                valueRange = 0f..safeDuration.toFloat(),
                enabled = durationMs > 0L,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                formatAudioDuration(positionMs.toLong()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                formatAudioDuration(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
