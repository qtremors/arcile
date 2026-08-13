package dev.qtremors.arcile.feature.videoplayer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.dp

@Composable
internal fun FastVideoScrubSlider(
    value: Float,
    enabled: Boolean,
    onScrubStart: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubEnd: (Float) -> Unit,
    colors: SliderColors,
    modifier: Modifier = Modifier
) {
    val normalizedValue = value.coerceIn(0f, 1f)
    val currentOnScrubStart by rememberUpdatedState(onScrubStart)
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    Box(
        modifier = modifier
            .height(48.dp)
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(normalizedValue, 0f..1f)
                if (enabled) {
                    setProgress { requestedValue ->
                        val progress = requestedValue.coerceIn(0f, 1f)
                        currentOnScrubStart()
                        currentOnScrub(progress)
                        currentOnScrubEnd(progress)
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    down.consume()
                    var lastProgress = videoScrubProgressForPointer(down.position.x, size.width.toFloat())
                    currentOnScrubStart()
                    currentOnScrub(lastProgress)
                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            lastProgress = videoScrubProgressForPointer(
                                change.position.x,
                                size.width.toFloat()
                            )
                            change.consume()
                            if (!change.pressed) break
                            currentOnScrub(lastProgress)
                        }
                    } finally {
                        currentOnScrubEnd(lastProgress)
                    }
                }
            }
    ) {
        Slider(
            value = normalizedValue,
            enabled = false,
            onValueChange = {},
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .clearAndSetSemantics { }
        )
    }
}
