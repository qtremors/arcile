package dev.qtremors.arcile.core.ui

import android.content.Context
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult

/** Presents feedback consistently for the app shell and protected, session-owned screens. */
suspend fun SnackbarHostState.showArcileFeedback(context: Context, event: ArcileFeedbackEvent) {
    val result = showSnackbar(
        message = event.message.asString(context),
        actionLabel = event.actionLabel?.asString(context),
        withDismissAction = false,
        duration = SnackbarDuration.Short
    )
    if (result == SnackbarResult.ActionPerformed) event.onAction?.invoke()
    else event.onDismiss?.invoke()
}
