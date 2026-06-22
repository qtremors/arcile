package dev.qtremors.arcile.shared.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

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

    val clickableModifier = if (onCheckedChange != null) {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled
        ) {
            onCheckedChange(!checked)
        }
    } else Modifier

    Box(
        modifier = modifier
            .size(52.dp, 32.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .border(
                width = if (checked) 0.dp else 2.dp,
                color = borderColor,
                shape = RoundedCornerShape(50)
            )
            .then(clickableModifier),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .size(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(24.dp)) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxRadius = size.minDimension / 2f
                val numLobes = 7
                val totalPoints = numLobes * 2
                val targetInnerRatio = 0.84f + (1f - 0.84f) * (1f - checkedFraction)
                
                val points = List(totalPoints) { index ->
                    val angle = (index * 2.0 * PI / totalPoints).toFloat() + (checkedFraction * 0.25f * PI).toFloat()
                    val r = if (index % 2 == 0) maxRadius else maxRadius * targetInnerRatio
                    val x = cx + r * cos(angle)
                    val y = cy + r * sin(angle)
                    Offset(x, y)
                }
                
                val path = Path().apply {
                    val firstMid = Offset(
                        (points[0].x + points[totalPoints - 1].x) / 2f,
                        (points[0].y + points[totalPoints - 1].y) / 2f
                    )
                    moveTo(firstMid.x, firstMid.y)
                    for (i in 0 until totalPoints) {
                        val current = points[i]
                        val next = points[(i + 1) % totalPoints]
                        val mid = Offset((current.x + next.x) / 2f, (current.y + next.y) / 2f)
                        quadraticTo(current.x, current.y, mid.x, mid.y)
                    }
                    close()
                }
                drawPath(path, color = thumbColor)
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
