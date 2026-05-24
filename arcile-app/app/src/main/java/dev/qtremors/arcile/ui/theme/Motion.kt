package dev.qtremors.arcile.ui.theme

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

val LocalReducedMotionEnabled = staticCompositionLocalOf { false }

object ArcileMotion {
    // Easing curves
    val Emphasized: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    val Standard: Easing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
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
        dampingRatio: Float = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
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
