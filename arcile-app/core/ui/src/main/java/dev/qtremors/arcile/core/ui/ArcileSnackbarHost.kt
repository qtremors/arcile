package dev.qtremors.arcile.core.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.qtremors.arcile.core.ui.theme.spacing
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
            .padding(8.dp)
    ) { data ->
        val severity = severityFor(data)
        var swipeOffset by remember(data.visuals.message) { mutableFloatStateOf(0f) }
        val hasAction = data.visuals.actionLabel != null
        val messageShape = if (hasAction) {
            RoundedCornerShape(50, 15, 15, 50)
        } else {
            CircleShape
        }
        val actionShape = RoundedCornerShape(15, 50, 50, 15)

        Box(
            modifier = Modifier
                .wrapContentWidth(Alignment.CenterHorizontally)
                .offset { IntOffset(swipeOffset.roundToInt(), 0) }
                .pointerInput(data) {
                    var velocityTracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            velocityTracker = VelocityTracker()
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
                            swipeOffset += dragAmount
                        },
                        onDragEnd = {
                            val velocity = velocityTracker.calculateVelocity()
                            val direction = arcileSwipeDirection(
                                axis = ArcileGestureAxis.Horizontal,
                                deltaX = swipeOffset,
                                deltaY = 0f,
                                velocityX = velocity.x,
                                velocityY = velocity.y,
                                minimumDistance = 96.dp.toPx(),
                                minimumVelocity = ArcileGestureDefaults.MinimumSwipeVelocity.toPx()
                            )
                            if (direction != null) data.dismiss() else swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .wrapContentWidth(),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = messageShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 6.dp,
                    shadowElevation = 3.dp,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = severity.containerColor(),
                            contentColor = severity.tint(),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = severity.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Text(
                            text = data.visuals.message,
                            overflow = TextOverflow.Ellipsis,
                            maxLines = 3,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                data.visuals.actionLabel?.let { label ->
                    Surface(
                        onClick = data::performAction,
                        shape = actionShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        tonalElevation = 6.dp,
                        shadowElevation = 3.dp,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge
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
