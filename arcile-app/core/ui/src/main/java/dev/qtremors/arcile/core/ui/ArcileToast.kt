package dev.qtremors.arcile.core.ui

import android.content.Context
import android.widget.Toast

/** The single app-wide entry point for feedback shown before a Compose snackbar host exists. */
fun Context.showArcileToast(
    message: CharSequence,
    longDuration: Boolean = false
) {
    Toast.makeText(
        applicationContext,
        message,
        if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
    ).show()
}
