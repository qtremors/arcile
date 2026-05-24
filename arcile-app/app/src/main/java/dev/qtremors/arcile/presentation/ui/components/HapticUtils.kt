package dev.qtremors.arcile.presentation.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import dev.qtremors.arcile.ui.theme.LocalHapticsEnabled

/**
 * Centered haptics helper for Arcile.
 * Wraps [View.performHapticFeedback] which respects system-wide haptics settings.
 */
class ArcileHaptics(private val view: View, private val enabled: Boolean) {
    
    fun selectionStart() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun selectionChanged() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun success() {
        if (!enabled) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun warning() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun error() {
        if (!enabled) return
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            // Fallback long press vibration pattern
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun destructiveConfirm() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    
    fun toggleMenu() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

@Composable
fun rememberArcileHaptics(): ArcileHaptics {
    val view = LocalView.current
    val enabled = LocalHapticsEnabled.current
    return remember(view, enabled) { ArcileHaptics(view, enabled) }
}
