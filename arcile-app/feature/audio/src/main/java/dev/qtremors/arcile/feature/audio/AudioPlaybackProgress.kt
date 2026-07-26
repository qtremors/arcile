package dev.qtremors.arcile.feature.audio

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
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
    val progress = audioProgressFraction(positionMs, durationMs)
    val thumbColor = MaterialTheme.colorScheme.onSurface

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            contentAlignment = Alignment.Center
        ) {
            LinearWavyProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                amplitude = { if (isPlaying) 1f else 0f }
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(36.dp)
            ) {
                val trackWidth = size.width
                val centerY = size.height / 2f
                val activeX = trackWidth * progress
                val thumbWidth = 4.dp.toPx()
                val thumbHeight = 28.dp.toPx()
                val clampedActiveX = activeX.coerceIn(0f, trackWidth)
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(clampedActiveX - thumbWidth / 2f, centerY - thumbHeight / 2f),
                    size = androidx.compose.ui.geometry.Size(thumbWidth, thumbHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )
            }
            Slider(
                value = positionMs.coerceIn(0f, safeDuration.toFloat()),
                onValueChange = onPositionChange,
                onValueChangeFinished = onPositionChangeFinished,
                valueRange = 0f..safeDuration.toFloat(),
                enabled = durationMs > 0L,
                colors = SliderDefaults.colors(
                    thumbColor = Color.Transparent,
                    activeTrackColor = Color.Transparent,
                    inactiveTrackColor = Color.Transparent,
                    disabledActiveTrackColor = Color.Transparent,
                    disabledInactiveTrackColor = Color.Transparent,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
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

@Composable
internal fun AudioMiniPlaybackProgress(
    positionMs: Long,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = audioProgressFraction(positionMs.toFloat(), durationMs)
    val activeColor = MaterialTheme.colorScheme.primary
    val inactiveColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Canvas(
        modifier = modifier
            .padding(horizontal = 10.dp)
            .height(4.dp)
    ) {
        val centerY = size.height / 2f
        val strokeWidth = 2.dp.toPx()
        val activeX = size.width * progress
        drawLine(
            color = inactiveColor,
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
        if (activeX > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(0f, centerY),
                end = Offset(activeX, centerY),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

internal fun audioProgressFraction(positionMs: Float, durationMs: Long): Float =
    if (durationMs <= 0L) {
        0f
    } else {
        (positionMs / durationMs.toFloat()).coerceIn(0f, 1f)
    }
