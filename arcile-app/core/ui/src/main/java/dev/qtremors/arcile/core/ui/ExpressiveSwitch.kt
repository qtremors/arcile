package dev.qtremors.arcile.core.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset

@Composable
fun ExpressiveSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val interactionSource = remember { MutableInteractionSource() }

    val trackColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        label = "switchTrackColor"
    )

    val borderColor by animateColorAsState(
        targetValue = if (checked) {
            Color.Transparent
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "switchBorderColor"
    )

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 24.dp else 4.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "switchThumbOffset"
    )

    val checkedFraction by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "switchCheckedFraction"
    )

    val thumbColor by animateColorAsState(
        targetValue = if (checked) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.outline
        },
        label = "switchThumbColor"
    )

    // Announced as a switch with its on/off state. Without a callback the parent row is the
    // toggle, so the switch stays a plain visual and does not steal the row's touch target.
    val toggleModifier = if (onCheckedChange != null) {
        Modifier
            .minimumInteractiveComponentSize()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
    } else Modifier

    Box(
        modifier = modifier
            .then(toggleModifier)
            .graphicsLayer { alpha = if (enabled) 1f else DISABLED_ALPHA }
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(
                width = if (checked) 0.dp else 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(thumbOffset.roundToPx(), 0) }
                .size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(24.dp)) {
                drawCircle(
                    color = thumbColor,
                    radius = size.minDimension / 2f
                )
            }

            Box(
                modifier = Modifier.size(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer {
                            alpha = checkedFraction
                            scaleX = checkedFraction
                            scaleY = checkedFraction
                        }
                )
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.surface,
                    modifier = Modifier
                        .size(12.dp)
                        .graphicsLayer {
                            alpha = 1f - checkedFraction
                            scaleX = 1f - checkedFraction
                            scaleY = 1f - checkedFraction
                        }
                )
            }
        }
    }
}

private const val DISABLED_ALPHA = 0.38f
