package dev.qtremors.arcile.core.presentation

import android.content.Context
import android.text.format.Formatter

fun formatFileSize(context: Context, bytes: Long): String {
    val clamped = if (bytes < 0L) 0L else bytes
    return Formatter.formatShortFileSize(context, clamped)
}
