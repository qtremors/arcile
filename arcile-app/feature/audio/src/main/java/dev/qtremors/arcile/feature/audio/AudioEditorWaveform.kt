package dev.qtremors.arcile.feature.audio

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToLong


@Composable
internal fun WaveformSelectionCanvas(
    waveform: AudioWaveform?,
    durationMs: Long,
    selectionStartMs: Long,
    selectionEndMs: Long,
    allSelections: List<AudioSelectionRange>,
    playheadMs: Long,
    viewportStartMs: Long,
    viewportDurationMs: Long,
    enabled: Boolean,
    onSelectionChange: (Long, Long) -> Unit,
    onPlayheadChange: (Long) -> Unit
) {
    val selectedColor = MaterialTheme.colorScheme.primary
    val unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    val selectionFill = MaterialTheme.colorScheme.primary.copy(alpha = 0.13f)
    val inactiveSelectionFill = MaterialTheme.colorScheme.secondary.copy(alpha = 0.08f)
    val handleColor = MaterialTheme.colorScheme.primary
    val playheadColor = MaterialTheme.colorScheme.tertiary
    val density = LocalDensity.current
    val handleHitPx = with(density) { 30.dp.toPx() }
    val currentSelectionStartMs by rememberUpdatedState(selectionStartMs)
    val currentSelectionEndMs by rememberUpdatedState(selectionEndMs)
    val currentOnSelectionChange by rememberUpdatedState(onSelectionChange)
    val currentOnPlayheadChange by rememberUpdatedState(onPlayheadChange)

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(
                enabled,
                durationMs,
                viewportStartMs,
                viewportDurationMs
            ) {
                if (!enabled) return@pointerInput
                var target = WaveformDragTarget.PLAYHEAD
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val startX = timeToX(
                            currentSelectionStartMs,
                            viewportStartMs,
                            viewportDurationMs,
                            width
                        )
                        val endX = timeToX(
                            currentSelectionEndMs,
                            viewportStartMs,
                            viewportDurationMs,
                            width
                        )
                        target = when {
                            kotlin.math.abs(offset.x - startX) <= handleHitPx ->
                                WaveformDragTarget.START
                            kotlin.math.abs(offset.x - endX) <= handleHitPx ->
                                WaveformDragTarget.END
                            else -> WaveformDragTarget.PLAYHEAD
                        }
                        val position = xToTime(
                            offset.x,
                            width,
                            viewportStartMs,
                            viewportDurationMs,
                            durationMs
                        )
                        when (target) {
                            WaveformDragTarget.START -> currentOnSelectionChange(
                                position.coerceAtMost(currentSelectionEndMs - 1),
                                currentSelectionEndMs
                            )
                            WaveformDragTarget.END -> currentOnSelectionChange(
                                currentSelectionStartMs,
                                position.coerceAtLeast(currentSelectionStartMs + 1)
                            )
                            WaveformDragTarget.PLAYHEAD -> currentOnPlayheadChange(position)
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    val position = xToTime(
                        change.position.x,
                        size.width.toFloat().coerceAtLeast(1f),
                        viewportStartMs,
                        viewportDurationMs,
                        durationMs
                    )
                    when (target) {
                        WaveformDragTarget.START -> currentOnSelectionChange(
                            position.coerceAtMost(currentSelectionEndMs - 1),
                            currentSelectionEndMs
                        )
                        WaveformDragTarget.END -> currentOnSelectionChange(
                            currentSelectionStartMs,
                            position.coerceAtLeast(currentSelectionStartMs + 1)
                        )
                        WaveformDragTarget.PLAYHEAD -> currentOnPlayheadChange(position)
                    }
                }
            }
    ) {
        val visibleEnd = (viewportStartMs + viewportDurationMs).coerceAtMost(durationMs)
        val selectionLeft = timeToX(selectionStartMs, viewportStartMs, viewportDurationMs, size.width)
        val selectionRight = timeToX(selectionEndMs, viewportStartMs, viewportDurationMs, size.width)
        allSelections.forEach { range ->
            if (range.startMs == selectionStartMs && range.endMs == selectionEndMs) return@forEach
            val left = timeToX(range.startMs, viewportStartMs, viewportDurationMs, size.width)
            val right = timeToX(range.endMs, viewportStartMs, viewportDurationMs, size.width)
            if (right > 0 && left < size.width) {
                drawRect(
                    color = inactiveSelectionFill,
                    topLeft = Offset(left.coerceAtLeast(0f), 0f),
                    size = Size(
                        (right.coerceAtMost(size.width) - left.coerceAtLeast(0f))
                            .coerceAtLeast(0f),
                        size.height
                    )
                )
            }
        }
        if (selectionRight > 0 && selectionLeft < size.width) {
            drawRect(
                color = selectionFill,
                topLeft = Offset(selectionLeft.coerceAtLeast(0f), 0f),
                size = Size(
                    (selectionRight.coerceAtMost(size.width) - selectionLeft.coerceAtLeast(0f))
                        .coerceAtLeast(0f),
                    size.height
                )
            )
        }
        drawWaveform(
            waveform = waveform,
            durationMs = durationMs,
            visibleStartMs = viewportStartMs,
            visibleEndMs = visibleEnd,
            selectionStartMs = selectionStartMs,
            selectionEndMs = selectionEndMs,
            selectedColor = selectedColor,
            unselectedColor = unselectedColor
        )
        drawHandle(selectionLeft, handleColor)
        drawHandle(selectionRight, handleColor)
        if (playheadMs in viewportStartMs..visibleEnd) {
            val playheadX = timeToX(playheadMs, viewportStartMs, viewportDurationMs, size.width)
            drawLine(
                color = playheadColor,
                start = Offset(playheadX, 0f),
                end = Offset(playheadX, size.height),
                strokeWidth = 2.dp.toPx()
            )
            drawCircle(playheadColor, radius = 4.dp.toPx(), center = Offset(playheadX, 8.dp.toPx()))
        }
    }
}

private fun DrawScope.drawWaveform(
    waveform: AudioWaveform?,
    durationMs: Long,
    visibleStartMs: Long,
    visibleEndMs: Long,
    selectionStartMs: Long,
    selectionEndMs: Long,
    selectedColor: Color,
    unselectedColor: Color
) {
    val peaks = waveform?.peaks ?: return
    val visibleDuration = (visibleEndMs - visibleStartMs).coerceAtLeast(1)
    val bars = (size.width / 3.dp.toPx()).toInt().coerceAtLeast(1)
    val firstPeak = (visibleStartMs * peaks.size / durationMs).toInt().coerceIn(peaks.indices)
    val lastPeak = ceil(visibleEndMs.toDouble() * peaks.size / durationMs)
        .toInt()
        .coerceIn(firstPeak + 1, peaks.size)
    val peakSpan = lastPeak - firstPeak
    val centerY = size.height / 2f
    repeat(bars) { bar ->
        val rangeStart = firstPeak + bar * peakSpan / bars
        val rangeEnd = (firstPeak + (bar + 1) * peakSpan / bars).coerceAtLeast(rangeStart + 1)
            .coerceAtMost(peaks.size)
        var peak = 0f
        for (index in rangeStart until rangeEnd) peak = max(peak, peaks[index])
        val timeMs = visibleStartMs + bar.toLong() * visibleDuration / bars
        val color = if (timeMs in selectionStartMs..selectionEndMs) selectedColor
        else unselectedColor
        val halfHeight = max(2.dp.toPx(), peak * size.height * 0.43f)
        val x = (bar + 0.5f) * size.width / bars
        drawLine(
            color = color,
            start = Offset(x, centerY - halfHeight),
            end = Offset(x, centerY + halfHeight),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private fun DrawScope.drawHandle(x: Float, color: Color) {
    if (x !in -1f..size.width + 1f) return
    drawLine(
        color = color,
        start = Offset(x, 0f),
        end = Offset(x, size.height),
        strokeWidth = 4.dp.toPx()
    )
    drawRoundRect(
        color = color,
        topLeft = Offset(x - 7.dp.toPx(), 4.dp.toPx()),
        size = Size(14.dp.toPx(), 28.dp.toPx()),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx())
    )
}


private enum class WaveformDragTarget { START, END, PLAYHEAD }

internal enum class AudioRangeStyle { WAVEFORM, CLASSIC }

internal data class AudioSelectionRange(
    val id: Long,
    val startMs: Long,
    val endMs: Long
)

internal fun nextSelectionRange(
    selections: List<AudioSelectionRange>,
    durationMs: Long
): AudioSelectionRange? {
    if (selections.size >= MAX_SELECTION_SEGMENTS || durationMs <= 1) return null
    val gaps = buildList {
        var cursorMs = 0L
        selections.sortedBy(AudioSelectionRange::startMs).forEach { range ->
            if (range.startMs > cursorMs) add(cursorMs..range.startMs)
            cursorMs = maxOf(cursorMs, range.endMs)
        }
        if (cursorMs < durationMs) add(cursorMs..durationMs)
    }
    val gap = gaps.maxByOrNull { it.last - it.first }
        ?.takeIf { it.last - it.first > 1 }
        ?: return null
    val inset = (gap.last - gap.first) / 4
    val startMs = gap.first + inset
    val endMs = (gap.last - inset).coerceAtLeast(startMs + 1)
    return AudioSelectionRange(
        id = (selections.maxOfOrNull(AudioSelectionRange::id) ?: 0) + 1,
        startMs = startMs,
        endMs = endMs
    )
}


private fun timeToX(timeMs: Long, viewportStartMs: Long, viewportDurationMs: Long, width: Float): Float =
    (timeMs - viewportStartMs).toFloat() / viewportDurationMs.coerceAtLeast(1) * width

private fun xToTime(
    x: Float,
    width: Float,
    viewportStartMs: Long,
    viewportDurationMs: Long,
    durationMs: Long
): Long = (viewportStartMs + x.coerceIn(0f, width) / width * viewportDurationMs)
    .roundToLong()
    .coerceIn(0, durationMs)

internal fun viewportDuration(durationMs: Long, zoom: Float): Long =
    (durationMs / zoom.coerceAtLeast(1f)).roundToLong().coerceAtLeast(1)

internal fun maxViewportStart(durationMs: Long, zoom: Float): Long =
    (durationMs - viewportDuration(durationMs, zoom)).coerceAtLeast(0)

internal fun formatEditorTime(durationMs: Long): String {
    val safe = durationMs.coerceAtLeast(0)
    val hours = safe / 3_600_000
    val minutes = safe % 3_600_000 / 60_000
    val seconds = safe % 60_000 / 1_000
    val millis = safe % 1_000
    return if (hours > 0) {
        "%d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    } else {
        "%02d:%02d.%03d".format(minutes, seconds, millis)
    }
}

internal fun formatPrecisionStep(stepMs: Long): String = when {
    stepMs >= 1_000 -> "${stepMs / 1_000} s"
    else -> "$stepMs ms"
}

internal fun List<AudioEditorSource>.moved(from: Int, to: Int): List<AudioEditorSource> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

internal val PRECISION_STEPS = listOf(1L, 10L, 100L, 1_000L)
private const val MAX_SELECTION_SEGMENTS = 12
