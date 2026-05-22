package dev.qtremors.arcile.presentation.ui.components

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView

/**
 * Centered haptics helper for Arcile.
 * Wraps [View.performHapticFeedback] which respects system-wide haptics settings.
 */
class ArcileHaptics(private val view: View) {
    
    fun selectionStart() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun selectionChanged() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun success() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun warning() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun error() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            // Fallback long press vibration pattern
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }

    fun destructiveConfirm() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }
    
    fun toggleMenu() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }
}

@Composable
fun rememberArcileHaptics(): ArcileHaptics {
    val view = LocalView.current
    return remember(view) { ArcileHaptics(view) }
}
