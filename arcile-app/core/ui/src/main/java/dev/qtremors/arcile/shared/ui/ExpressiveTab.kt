package dev.qtremors.arcile.shared.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Tab
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun ExpressiveTab(
    selected: Boolean,
    onClick: () -> Unit,
    index: Int,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null
) {
    val hapticFeedback = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }

    val animationSpec = tween<Float>(durationMillis = 250, easing = FastOutSlowInEasing)

    LaunchedEffect(selected) {
        if (selected) {
            launch {
                scale.animateTo(1.06f, animationSpec = animationSpec)
                scale.animateTo(1f, animationSpec = animationSpec)
            }
        } else {
            scale.snapTo(1f)
        }
    }

    LaunchedEffect(selectedIndex) {
        if (!selected) {
            val distance = index - selectedIndex
            if (abs(distance) == 1) {
                val direction = if (distance > 0) 1 else -1
                val offsetValue = 8f * direction
                launch {
                    offsetX.animateTo(offsetValue, animationSpec = animationSpec)
                    offsetX.animateTo(0f, animationSpec = animationSpec)
                }
            } else {
                offsetX.snapTo(0f)
            }
        } else {
            offsetX.snapTo(0f)
        }
    }

    Tab(
        selected = selected,
        onClick = {
            hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        modifier = modifier
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                translationX = offsetX.value * density
                transformOrigin = TransformOrigin.Center
            },
        enabled = enabled,
        text = text,
        icon = icon
    )
}
