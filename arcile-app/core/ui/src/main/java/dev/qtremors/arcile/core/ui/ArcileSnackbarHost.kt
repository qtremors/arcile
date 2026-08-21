package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.spacing
import kotlin.math.abs
import kotlin.math.roundToInt

enum class ArcileFeedbackSeverity {
    Success,
    Error,
    Warning,
    Info
}

/**
 * A customized SnackbarHost that follows the Arcile Material 3 Expressive design language.
 * 
 * Features:
 * - Squircle shape (extraLarge)
 * - Tonal container colors (surfaceContainerHigh)
 * - Content-sized feedback bubbles with a capped long-message width
 * - Standardized spacing and typography
 */
@Composable
fun ArcileSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    severityFor: (SnackbarData) -> ArcileFeedbackSeverity = { ArcileFeedbackSeverity.Info }
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
            .wrapContentWidth(Alignment.CenterHorizontally)
            .padding(MaterialTheme.spacing.space12)
    ) { data ->
        val severity = severityFor(data)
        var swipeOffset by remember(data.visuals.message) { mutableFloatStateOf(0f) }
        Box(
            modifier = Modifier
                .wrapContentWidth(Alignment.CenterHorizontally)
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .pointerInput(data) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeOffset += dragAmount
                        },
                        onDragEnd = {
                            if (abs(swipeOffset) >= 96.dp.toPx()) data.dismiss()
                            else swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 6.dp,
                shadowElevation = 3.dp,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .wrapContentWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = severity.containerColor(),
                        contentColor = severity.tint(),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = severity.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Text(
                        text = data.visuals.message,
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 3,
                        modifier = Modifier.widthIn(max = 360.dp)
                    )
                    data.visuals.actionLabel?.let { label ->
                        Surface(
                            onClick = data::performAction,
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ) {
                            Text(
                                text = label,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

fun ArcileFeedbackSeverity.icon(): ImageVector =
    when (this) {
        ArcileFeedbackSeverity.Success -> Icons.Default.CheckCircle
        ArcileFeedbackSeverity.Error -> Icons.Default.Error
        ArcileFeedbackSeverity.Warning -> Icons.Default.Warning
        ArcileFeedbackSeverity.Info -> Icons.Default.Info
    }

@Composable
private fun ArcileFeedbackSeverity.tint() =
    when (this) {
        ArcileFeedbackSeverity.Success -> MaterialTheme.colorScheme.primary
        ArcileFeedbackSeverity.Error -> MaterialTheme.colorScheme.error
        ArcileFeedbackSeverity.Warning -> MaterialTheme.colorScheme.tertiary
        ArcileFeedbackSeverity.Info -> MaterialTheme.colorScheme.primary
    }

@Composable
private fun ArcileFeedbackSeverity.containerColor() =
    when (this) {
        ArcileFeedbackSeverity.Success -> MaterialTheme.colorScheme.primaryContainer
        ArcileFeedbackSeverity.Error -> MaterialTheme.colorScheme.errorContainer
        ArcileFeedbackSeverity.Warning -> MaterialTheme.colorScheme.tertiaryContainer
        ArcileFeedbackSeverity.Info -> MaterialTheme.colorScheme.secondaryContainer
    }
