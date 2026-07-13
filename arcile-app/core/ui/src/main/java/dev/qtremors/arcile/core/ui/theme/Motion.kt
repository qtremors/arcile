package dev.qtremors.arcile.core.ui.theme

import android.provider.Settings
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.composed
import androidx.compose.ui.semantics.Role


val LocalReducedMotionEnabled = staticCompositionLocalOf { false }

object ArcileMotion {
    // Easing curves
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val EmphasizedDecelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1.0f)
    val EmphasizedAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 0.8f, 0.15f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val StandardDecelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val StandardAccelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)
    val Decelerate: Easing = CubicBezierEasing(0.0f, 0.0f, 0.2f, 1.0f)
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0.0f, 1.0f, 1.0f)

    // Durations
    const val Short1 = 100
    const val Short2 = 150
    const val Short3 = 200
    const val Medium1 = 250
    const val Medium2 = 300
    const val Medium3 = 350
    const val Medium4 = 400
    const val Long1 = 450
    const val Long2 = 500
    const val Long3 = 550
    const val Long4 = 600

    @Composable
    fun <T> rememberTween(
        durationMillis: Int,
        easing: Easing = Standard
    ): TweenSpec<T> {
        val reducedMotion = LocalReducedMotionEnabled.current
        return remember(durationMillis, easing, reducedMotion) {
            if (reducedMotion) {
                tween(durationMillis = 0, easing = easing)
            } else {
                tween(durationMillis = durationMillis, easing = easing)
            }
        }
    }

    @Composable
    fun <T> rememberSpring(
        dampingRatio: Float = androidx.compose.animation.core.Spring.DampingRatioLowBouncy,
        stiffness: Float = androidx.compose.animation.core.Spring.StiffnessMedium
    ): FiniteAnimationSpec<T> {
        val reducedMotion = LocalReducedMotionEnabled.current
        return remember(dampingRatio, stiffness, reducedMotion) {
            if (reducedMotion) {
                tween(durationMillis = 0)
            } else {
                spring(dampingRatio = dampingRatio, stiffness = stiffness)
            }
        }
    }
}

fun Modifier.bounceClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotionEnabled.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "bounceClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                try {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                } catch (e: Exception) {
                    // Ignore haptic failures
                }
                onClick()
            }
        )
}

@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bounceCombinedClickable(
    enabled: Boolean = true,
    role: Role? = Role.Button,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit
): Modifier = composed {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotionEnabled.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) 0.96f else 1f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "bounceCombinedClickScale"
    )

    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            enabled = enabled,
            role = role,
            onClick = {
                try {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                } catch (e: Exception) {
                    // Ignore haptic failures
                }
                onClick()
            },
            onLongClick = onLongClick?.let {
                {
                    try {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    } catch (e: Exception) {
                        // Ignore haptic failures
                    }
                    it()
                }
            }
        )
}

fun Modifier.pressScale(
    interactionSource: androidx.compose.foundation.interaction.InteractionSource,
    scaleOnPress: Float = 0.95f
): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotionEnabled.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && !reducedMotion) scaleOnPress else 1f,
        animationSpec = spring(
            dampingRatio = 0.75f,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow
        ),
        label = "pressScale"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
